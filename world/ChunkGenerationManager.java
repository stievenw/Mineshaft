// src/main/java/com/mineshaft/world/ChunkGenerationManager.java
package com.mineshaft.world;

import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;

import java.util.*;
import java.util.concurrent.*;

/**
 * ⚡ OPTIMIZED Async chunk terrain generation system v2.0
 * - Dynamic thread count based on CPU cores
 * - Higher throughput for faster loading
 * - Prioritizes chunks closest to player
 * - Thread-safe and efficient
 */
public class ChunkGenerationManager {

    private final ExecutorService generatorThreadPool;
    private final Queue<ChunkGenTask> generationQueue = new ConcurrentLinkedQueue<>();
    private final Set<ChunkPos> generating = ConcurrentHashMap.newKeySet();
    private final Queue<Chunk> pendingLighting = new ConcurrentLinkedQueue<>();

    // ✅ NEW: World info for loading saved chunks
    private WorldInfo worldInfo;

    // ⚙️ Configuration - ✅ INCREASED for faster loading
    private static final int GENERATOR_THREADS = Math.max(4, Runtime.getRuntime().availableProcessors());
    private static final int MAX_GENERATIONS_PER_FRAME = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
    private static final int MAX_PENDING_CHUNKS = 256; // Prevent queue bloat

    /**
     * Task for chunk generation with priority
     */
    private static class ChunkGenTask {
        Chunk chunk;
        double distanceSq;

        ChunkGenTask(Chunk chunk, double distSq) {
            this.chunk = chunk;
            this.distanceSq = distSq;
        }
    }

    public ChunkGenerationManager() {
        this(null); // Default constructor for backward compatibility
    }

    /**
     * ✅ NEW: Constructor with WorldInfo for save/load support
     */
    public ChunkGenerationManager(WorldInfo worldInfo) {
        this.worldInfo = worldInfo;

        generatorThreadPool = Executors.newFixedThreadPool(GENERATOR_THREADS, r -> {
            Thread t = new Thread(r, "ChunkGenerator");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY); // ✅ Normal priority for faster generation
            return t;
        });

        System.out.println("[ChunkGen] Manager initialized with " + GENERATOR_THREADS + " threads, max "
                + MAX_GENERATIONS_PER_FRAME + "/frame" + (worldInfo != null ? ", world: " + worldInfo.getName() : ""));
    }

    /**
     * ✅ NEW: Set world info (for when world loads after manager creation)
     */
    public void setWorldInfo(WorldInfo worldInfo) {
        this.worldInfo = worldInfo;
        System.out.println("[ChunkGen] WorldInfo set to: " + (worldInfo != null ? worldInfo.getName() : "null"));
    }

    /**
     * Queue a chunk for generation
     * 
     * @param chunk            Chunk to generate
     * @param playerDistanceSq Distance from player (for prioritization)
     */
    public void queueGeneration(Chunk chunk, double playerDistanceSq) {
        ChunkPos pos = new ChunkPos(chunk.getChunkX(), chunk.getChunkZ());

        // Skip if already generated or generating
        if (chunk.getState() != ChunkState.EMPTY || generating.contains(pos)) {
            return;
        }

        // ✅ Prevent queue bloat
        if (generationQueue.size() >= MAX_PENDING_CHUNKS) {
            return; // Drop oldest tasks to keep queue size manageable
        }

        generationQueue.offer(new ChunkGenTask(chunk, playerDistanceSq));
    }

    /**
     * Update generation system (call from main thread every frame)
     */
    public void update() {
        startPendingGenerations();
        processCompletedChunks();
    }

    /**
     * Start new generation tasks (closest chunks first)
     */
    private void startPendingGenerations() {
        // Collect and sort tasks by distance
        List<ChunkGenTask> tasks = new ArrayList<>();
        ChunkGenTask task;
        while ((task = generationQueue.poll()) != null) {
            tasks.add(task);
        }

        // Sort by distance (closest first)
        tasks.sort(Comparator.comparingDouble(t -> t.distanceSq));

        // Start generation (limited per frame to avoid thread spam)
        int started = 0;
        for (ChunkGenTask t : tasks) {
            if (started >= MAX_GENERATIONS_PER_FRAME) {
                generationQueue.offer(t); // Re-queue for next frame
                continue;
            }

            ChunkPos pos = new ChunkPos(t.chunk.getChunkX(), t.chunk.getChunkZ());

            // Start generation if chunk is still EMPTY and not already generating
            if (t.chunk.getState() == ChunkState.EMPTY && generating.add(pos)) {
                generatorThreadPool.submit(() -> generateChunkAsync(t.chunk, pos));
                started++;
            }
        }
    }

    /**
     * ✅ REWRITTEN: Load saved chunk OR generate new terrain + lighting
     * 
     * Key changes:
     * - FIRST checks for saved chunk data on disk
     * - ONLY generates new terrain if no saved data exists
     * - Lighting done immediately in same thread (no waiting for main thread)
     * - Chunk becomes READY immediately
     * - Much faster chunk loading
     */
    private void generateChunkAsync(Chunk chunk, ChunkPos pos) {
        try {
            // This runs in background thread - no OpenGL calls allowed!

            // ✅ CRITICAL FIX: Try to load from disk FIRST
            boolean loadedFromDisk = false;
            if (worldInfo != null) {
                WorldSaveManager.loadChunk(chunk, worldInfo.getFolderName());

                // Check if chunk was successfully loaded
                if (chunk.isGenerated()) {
                    loadedFromDisk = true;
                    if (Settings.DEBUG_CHUNK_LOADING) {
                        System.out.println("[ChunkGen] Loaded chunk " + pos + " from disk");
                    }
                }
            }

            // ✅ FALLBACK: Generate new terrain if not loaded from disk
            if (!loadedFromDisk) {
                chunk.generate();
                if (Settings.DEBUG_CHUNK_LOADING) {
                    System.out.println("[ChunkGen] Generated new terrain for chunk " + pos);
                }
            }

            // ✅ INSTANT LIGHTING: Done here in background thread
            initializeChunkLighting(chunk);

            // ✅ Mark as ready immediately - no LIGHT_PENDING phase!
            chunk.setLightInitialized(true);
            chunk.setState(ChunkState.READY);
            chunk.setNeedsGeometryRebuild(true);

            // Queue for main thread notification (optional, for tracking)
            pendingLighting.offer(chunk);

        } catch (Exception e) {
            System.err.println("[ChunkGen] Error generating chunk " + pos + ": " + e.getMessage());
            e.printStackTrace();
            chunk.setState(ChunkState.EMPTY); // Reset on error
        } finally {
            generating.remove(pos);
        }
    }

    /**
     * ✅ PROPER Minecraft-style lighting with horizontal propagation
     * 
     * Step 1: Vertical pass - propagate skylight down from top
     * Step 2: Horizontal BFS - spread light into caves/overhangs
     * Step 3: Blocklight BFS - spread torch/glowstone light
     */
    private void initializeChunkLighting(Chunk chunk) {
        int skyLight = 15;

        // ========== STEP 1: Vertical skylight propagation ==========
        // Mark all sky-exposed blocks with light level 15
        // Track blocks that can spread light horizontally
        java.util.Queue<int[]> lightQueue = new java.util.LinkedList<>();

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                int currentLight = skyLight;

                for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
                    int worldY = Settings.indexToWorldY(index);
                    GameBlock block = chunk.getBlock(x, worldY, z);

                    if (block == null || block.isAir()) {
                        chunk.setSkyLight(x, worldY, z, currentLight);
                        // Queue for horizontal spread if has light
                        if (currentLight > 1) {
                            lightQueue.add(new int[] { x, worldY, z, currentLight });
                        }
                    } else {
                        // ✅ NEW: Use lightOpacity property for semi-transparent blocks
                        int opacity = block.getLightOpacity();

                        if (opacity > 0) {
                            // Semi-transparent block (e.g. leaves): reduce light by opacity
                            currentLight = Math.max(0, currentLight - opacity);
                            chunk.setSkyLight(x, worldY, z, currentLight);
                            if (currentLight > 1) {
                                lightQueue.add(new int[] { x, worldY, z, currentLight });
                            }
                        } else if (block.isSolid()) {
                            // Fully opaque: block light completely
                            currentLight = 0;
                            chunk.setSkyLight(x, worldY, z, 0);
                        } else {
                            // Transparent: light passes through
                            chunk.setSkyLight(x, worldY, z, currentLight);
                            if (currentLight > 1) {
                                lightQueue.add(new int[] { x, worldY, z, currentLight });
                            }
                        }
                    }
                }
            }
        }

        // ========== STEP 2: Horizontal BFS light spread ==========
        // Light spreads into caves, decreasing by 1 per block
        int[][] directions = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };

        while (!lightQueue.isEmpty()) {
            int[] node = lightQueue.poll();
            int x = node[0];
            int y = node[1];
            int z = node[2];
            int light = node[3];

            // Try to spread to neighbors
            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                // Skip out of chunk bounds
                if (nx < 0 || nx >= Chunk.CHUNK_SIZE)
                    continue;
                if (nz < 0 || nz >= Chunk.CHUNK_SIZE)
                    continue;
                if (!Settings.isValidWorldY(ny))
                    continue;

                // Get neighbor block
                GameBlock neighbor = chunk.getBlock(nx, ny, nz);

                // Skip fully opaque solid blocks
                if (neighbor != null && neighbor.isSolid() && neighbor.getLightOpacity() == 0) {
                    continue; // Fully opaque blocks block light
                }

                // Calculate new light level (decay by 1 base + opacity)
                int newLight = light - 1;
                if (neighbor != null) {
                    int opacity = neighbor.getLightOpacity();
                    if (opacity > 0) {
                        newLight -= opacity; // Additional decay for semi-transparent blocks
                    }
                }

                if (newLight <= 0)
                    continue;

                // Only update if new light is brighter
                int currentNeighborLight = chunk.getSkyLight(nx, ny, nz);
                if (newLight > currentNeighborLight) {
                    chunk.setSkyLight(nx, ny, nz, newLight);
                    // Continue spreading if still has light
                    if (newLight > 1) {
                        lightQueue.add(new int[] { nx, ny, nz, newLight });
                    }
                }
            }
        }

        // ========== STEP 3: Block light sources (torches, glowstone) ==========
        java.util.Queue<int[]> blockLightQueue = new java.util.LinkedList<>();

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                for (int index = 0; index < Chunk.CHUNK_HEIGHT; index++) {
                    int worldY = Settings.indexToWorldY(index);
                    GameBlock block = chunk.getBlock(x, worldY, z);

                    if (block != null && block.getLightLevel() > 0) {
                        int lightLevel = block.getLightLevel();
                        chunk.setBlockLight(x, worldY, z, lightLevel);
                        blockLightQueue.add(new int[] { x, worldY, z, lightLevel });
                    }
                }
            }
        }

        // Spread block light using BFS
        while (!blockLightQueue.isEmpty()) {
            int[] node = blockLightQueue.poll();
            int x = node[0];
            int y = node[1];
            int z = node[2];
            int light = node[3];

            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                if (nx < 0 || nx >= Chunk.CHUNK_SIZE)
                    continue;
                if (nz < 0 || nz >= Chunk.CHUNK_SIZE)
                    continue;
                if (!Settings.isValidWorldY(ny))
                    continue;

                GameBlock neighbor = chunk.getBlock(nx, ny, nz);

                // Skip fully opaque solid blocks
                if (neighbor != null && neighbor.isSolid() && neighbor.getLightOpacity() == 0) {
                    continue;
                }

                int newLight = light - 1;
                if (neighbor != null) {
                    int opacity = neighbor.getLightOpacity();
                    if (opacity > 0) {
                        newLight -= opacity;
                    }
                }
                if (newLight <= 0)
                    continue;

                int currentNeighborLight = chunk.getBlockLight(nx, ny, nz);
                if (newLight > currentNeighborLight) {
                    chunk.setBlockLight(nx, ny, nz, newLight);
                    if (newLight > 1) {
                        blockLightQueue.add(new int[] { nx, ny, nz, newLight });
                    }
                }
            }
        }
    }

    /**
     * Process chunks that finished generation (tracking only now)
     */
    private void processCompletedChunks() {
        // Drain the queue - chunks are already READY
        while (pendingLighting.poll() != null) {
            // Nothing to do - chunks processed in generateChunkAsync
        }
    }

    /**
     * Check if a chunk is currently being generated
     */
    public boolean isGenerating(int chunkX, int chunkZ) {
        return generating.contains(new ChunkPos(chunkX, chunkZ));
    }

    /**
     * Get total pending work count
     */
    public int getPendingCount() {
        return generationQueue.size() + generating.size() + pendingLighting.size();
    }

    /**
     * Get active generation threads count
     */
    public int getActiveThreads() {
        return ((ThreadPoolExecutor) generatorThreadPool).getActiveCount();
    }

    /**
     * Shutdown generation system
     */
    public void shutdown() {
        System.out.println("[ChunkGen] Shutting down...");

        generatorThreadPool.shutdown();
        try {
            if (!generatorThreadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                generatorThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            generatorThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("[ChunkGen] Shutdown complete");
    }

    /**
     * Chunk position identifier
     */
    private static class ChunkPos {
        final int x, z;

        ChunkPos(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof ChunkPos))
                return false;
            ChunkPos that = (ChunkPos) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }

        @Override
        public String toString() {
            return "[" + x + ", " + z + "]";
        }
    }
}