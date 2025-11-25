// src/main/java/com/mineshaft/world/lighting/LightingEngine.java
package com.mineshaft.world.lighting;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.core.TimeOfDay;
import com.mineshaft.world.Chunk;

import java.util.*;
import java.util.concurrent.*;

/**
 * ⚡ OPTIMIZED Lighting Engine v3.0 - Minecraft-Style Lighting System
 * 
 * ============================================================================
 * MINECRAFT-STYLE LIGHTING CONCEPT:
 * ============================================================================
 * 
 * 1. LIGHT VALUES (0-15) are STATIC and stored per-block:
 * - Skylight: Can this block see the sky? (shadow propagation)
 * - Blocklight: Is there a torch/glowstone nearby?
 * 
 * 2. LIGHT VALUES DO NOT CHANGE WITH TIME OF DAY!
 * - A block with skylight=15 ALWAYS has skylight=15
 * - At noon: displayed brightness = skylight * 1.0
 * - At midnight: displayed brightness = skylight * 0.2
 * 
 * 3. TIME-OF-DAY BRIGHTNESS is applied at RENDER TIME (via glColor)
 * - ChunkRenderer.setTimeOfDayBrightness() handles this
 * - NO mesh rebuild needed when time changes!
 * 
 * 4. MESH REBUILD only happens when:
 * - Block is placed (shadow propagation changes)
 * - Block is removed (shadow propagation changes)
 * - Chunk first loads
 * 
 * ============================================================================
 */
public class LightingEngine {

    private SunLightCalculator sunLight;
    private static final float[] BRIGHTNESS_TABLE = new float[16];

    // ⚡ PERFORMANCE OPTIMIZATIONS
    private final ExecutorService lightingExecutor;
    private final Queue<Chunk> pendingLightUpdates = new ConcurrentLinkedQueue<>();
    private final Set<Chunk> processingChunks = ConcurrentHashMap.newKeySet();

    // Throttling controls
    private static final int MAX_CHUNKS_PER_FRAME = 4;
    private static final int MAX_LIGHT_OPERATIONS_PER_CHUNK = 1000;

    /**
     * ✅ MINECRAFT-STYLE: Maximum skylight level (always 15)
     * This represents "can see sky" - the actual brightness is applied at render
     * time
     */
    private static final int MAX_SKYLIGHT_LEVEL = 15;

    // Cache for avoiding redundant updates
    private final Set<ChunkPosition> initializedChunks = ConcurrentHashMap.newKeySet();

    static {
        // ✅ Brightness table converts light level (0-15) to brightness (0.0-1.0)
        // This is STATIC - doesn't change with time of day
        for (int i = 0; i < 16; i++) {
            if (i <= 0) {
                BRIGHTNESS_TABLE[i] = 0.4f; // Minimum brightness (cave darkness)
            } else if (i >= 15) {
                BRIGHTNESS_TABLE[i] = 1.0f; // Maximum brightness
            } else {
                float normalized = i / 15.0f;
                BRIGHTNESS_TABLE[i] = 0.4f + (normalized * 0.6f);
            }
        }
    }

    private static class LightNode {
        int x, y, z;
        int lightLevel;

        LightNode(int x, int y, int z, int lightLevel) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lightLevel = lightLevel;
        }
    }

    private static class ChunkPosition {
        int x, z;

        ChunkPosition(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof ChunkPosition))
                return false;
            ChunkPosition that = (ChunkPosition) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }

    public LightingEngine(com.mineshaft.world.World world, TimeOfDay timeOfDay) {
        this.sunLight = new SunLightCalculator(timeOfDay);

        // Create optimized thread pool for lighting calculations
        this.lightingExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "LightingWorker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        if (Settings.DEBUG_MODE) {
            System.out
                    .println("[LightingEngine] Initialized with Minecraft-style lighting (no rebuild on time change)");
        }
    }

    /**
     * ⚡ Update sun light direction
     * 
     * ✅ MINECRAFT-STYLE: This only updates sun DIRECTION for visual effects
     * It does NOT change light values or trigger mesh rebuilds!
     * 
     * Sun direction affects:
     * - Sky color gradient
     * - Sun/moon position in skybox
     * - (Future) Shadow angles
     * 
     * Sun direction does NOT affect:
     * - Skylight values (always 15 for sky-visible blocks)
     * - Mesh geometry
     * - Chunk rebuilds
     * 
     * @return true if sun direction changed significantly
     */
    public boolean updateSunLight() {
        boolean directionChanged = sunLight.updateSunDirection();

        // ✅ MINECRAFT-STYLE: Do NOT trigger chunk updates for time change!
        // Light values are static. Time-of-day brightness is applied at render time.
        // See ChunkRenderer.setTimeOfDayBrightness()

        return directionChanged;
    }

    /**
     * ⚡ Call every frame for processing queued light updates
     * 
     * ✅ MINECRAFT-STYLE: This only processes block-change light updates,
     * NOT time-of-day updates (those don't exist anymore)
     */
    public void update() {
        // Process queued chunks (from block place/remove only)
        processQueuedChunks();
    }

    /**
     * ⚡ Process limited chunks per frame
     */
    private void processQueuedChunks() {
        int chunksProcessed = 0;

        while (chunksProcessed < MAX_CHUNKS_PER_FRAME && !pendingLightUpdates.isEmpty()) {
            Chunk chunk = pendingLightUpdates.poll();

            if (chunk == null || processingChunks.contains(chunk)) {
                continue;
            }

            processingChunks.add(chunk);

            // Process async - update skylight for block changes
            lightingExecutor.submit(() -> {
                try {
                    updateChunkSkylightForBlockChange(chunk);
                } finally {
                    processingChunks.remove(chunk);
                }
            });

            chunksProcessed++;
        }
    }

    /**
     * ⚡ Update chunk skylight after block placement/removal
     * 
     * ✅ MINECRAFT-STYLE: Skylight is always 15 for sky-visible blocks
     * This method recalculates shadow propagation, NOT time-based brightness
     */
    private void updateChunkSkylightForBlockChange(Chunk chunk) {
        if (chunk == null)
            return;

        boolean changed = false;
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                if (updateColumnSkylight(chunk, x, z)) {
                    changed = true;
                }
            }
        }

        // ✅ Only mark for rebuild if shadow propagation actually changed
        if (changed) {
            chunk.setNeedsLightingUpdate(true);
        }
    }

    /**
     * ⚡ Update single column skylight - shadow propagation
     * 
     * ✅ MINECRAFT-STYLE: Skylight is always MAX_SKYLIGHT_LEVEL (15) until blocked
     * This doesn't change with time - it represents "can this block see the sky?"
     */
    private boolean updateColumnSkylight(Chunk chunk, int x, int z) {
        int currentLight = MAX_SKYLIGHT_LEVEL; // ✅ Always start with full skylight
        boolean changed = false;

        // Top to bottom - propagate skylight down until blocked
        for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
            GameBlock block = chunk.getBlockByIndex(x, index, z);
            int newLight;

            if (block == null || block.isAir()) {
                // Air blocks receive full skylight from above
                newLight = currentLight;
            } else if (block == BlockRegistry.WATER || block == BlockRegistry.OAK_LEAVES) {
                // Transparent blocks reduce skylight slightly
                newLight = Math.max(0, currentLight - 1);
                currentLight = newLight;
            } else if (block.isSolid()) {
                // Solid blocks block all skylight
                newLight = 0;
                currentLight = 0;
            } else {
                // Non-solid, non-air blocks (like flowers) pass light through
                newLight = currentLight;
            }

            // Convert index to world Y
            int worldY = Settings.indexToWorldY(index);
            int oldLight = chunk.getSkyLight(x, worldY, z);

            if (oldLight != newLight) {
                chunk.setSkyLight(x, worldY, z, newLight);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * ⚡ Queue chunk for light update (called when blocks change)
     * 
     * ✅ MINECRAFT-STYLE: Only called for block place/remove, NOT for time change
     */
    public void queueChunkForLightUpdate(Chunk chunk) {
        if (chunk != null && !processingChunks.contains(chunk)) {
            pendingLightUpdates.offer(chunk);
        }
    }

    /**
     * ⚡ Batch queue chunks
     */
    public void queueChunksForLightUpdate(Collection<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            queueChunkForLightUpdate(chunk);
        }
    }

    public SunLightCalculator getSunLight() {
        return sunLight;
    }

    /**
     * ✅ MINECRAFT-STYLE: Get current skylight level (always 15)
     * This represents the MAXIMUM possible skylight, not time-adjusted brightness
     */
    public int getCurrentSkylightLevel() {
        return MAX_SKYLIGHT_LEVEL;
    }

    /**
     * ⚡ Initial skylight setup for newly loaded chunk
     * 
     * ✅ MINECRAFT-STYLE: Initialize with full skylight (15) propagating down
     * This is called once when chunk loads, NOT on every time change
     */
    public void initializeSkylightForChunk(Chunk chunk) {
        initializeSkylightForChunk(chunk, MAX_SKYLIGHT_LEVEL);
    }

    /**
     * ⚡ Initial skylight setup with specific level
     */
    public void initializeSkylightForChunk(Chunk chunk, int skylightLevel) {
        if (chunk == null)
            return;

        ChunkPosition pos = new ChunkPosition(chunk.getChunkX(), chunk.getChunkZ());

        // ✅ Skip if already initialized
        if (initializedChunks.contains(pos)) {
            return;
        }

        lightingExecutor.submit(() -> {
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                    updateColumnSkylightInitial(chunk, x, z, skylightLevel);
                }
            }

            initializedChunks.add(pos);
            chunk.setLightInitialized(true);

            if (Settings.DEBUG_CHUNK_LOADING) {
                System.out.printf("[LightingEngine] Initialized skylight for chunk [%d, %d]%n",
                        chunk.getChunkX(), chunk.getChunkZ());
            }
        });
    }

    /**
     * ⚡ Initial column skylight - same as update but for first time
     */
    private void updateColumnSkylightInitial(Chunk chunk, int x, int z, int skylightLevel) {
        int currentLight = skylightLevel;

        for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
            GameBlock block = chunk.getBlockByIndex(x, index, z);
            int newLight;

            if (block == null || block.isAir()) {
                newLight = currentLight;
            } else if (block == BlockRegistry.WATER || block == BlockRegistry.OAK_LEAVES) {
                newLight = Math.max(0, currentLight - 1);
                currentLight = newLight;
            } else if (block.isSolid()) {
                newLight = 0;
                currentLight = 0;
            } else {
                newLight = currentLight;
            }

            int worldY = Settings.indexToWorldY(index);
            chunk.setSkyLight(x, worldY, z, newLight);
        }
    }

    /**
     * ⚡ Blocklight initialization
     */
    public void initializeBlocklightForChunk(Chunk chunk) {
        if (chunk == null)
            return;

        lightingExecutor.submit(() -> {
            Queue<LightNode> lightQueue = new LinkedList<>();

            // Find all light-emitting blocks
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int index = 0; index < Chunk.CHUNK_HEIGHT; index++) {
                    for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                        GameBlock block = chunk.getBlockByIndex(x, index, z);
                        int lightLevel = (block != null) ? block.getLightLevel() : 0;

                        if (lightLevel > 0) {
                            int worldY = Settings.indexToWorldY(index);
                            chunk.setBlockLight(x, worldY, z, lightLevel);
                            lightQueue.add(new LightNode(x, worldY, z, lightLevel));
                        }
                    }
                }
            }

            // Propagate light from sources
            propagateLightOptimized(chunk, lightQueue, false);
        });
    }

    /**
     * ⚡ OPTIMIZED: Light propagation with operation limit
     */
    private void propagateLightOptimized(Chunk chunk, Queue<LightNode> queue, boolean isSkylight) {
        int[][] directions = {
                { 1, 0, 0 }, { -1, 0, 0 },
                { 0, 1, 0 }, { 0, -1, 0 },
                { 0, 0, 1 }, { 0, 0, -1 }
        };

        Set<Long> visited = new HashSet<>();
        int operations = 0;

        while (!queue.isEmpty() && operations < MAX_LIGHT_OPERATIONS_PER_CHUNK) {
            LightNode node = queue.poll();
            long key = ((long) node.x << 16) | ((long) (node.y & 0xFFFF) << 8) | (node.z & 0xFF);

            if (visited.contains(key))
                continue;
            visited.add(key);
            operations++;

            int newLight = node.lightLevel - 1;
            if (newLight <= 0)
                continue;

            for (int[] dir : directions) {
                int nx = node.x + dir[0];
                int ny = node.y + dir[1];
                int nz = node.z + dir[2];

                // Check bounds
                if (nx < 0 || nx >= Chunk.CHUNK_SIZE ||
                        !Settings.isValidWorldY(ny) ||
                        nz < 0 || nz >= Chunk.CHUNK_SIZE) {
                    continue;
                }

                long neighborKey = ((long) nx << 16) | ((long) (ny & 0xFFFF) << 8) | (nz & 0xFF);
                if (visited.contains(neighborKey))
                    continue;

                GameBlock neighbor = chunk.getBlock(nx, ny, nz);

                // Skip solid blocks (except leaves which are semi-transparent)
                if (neighbor != null && neighbor.isSolid() && neighbor != BlockRegistry.OAK_LEAVES) {
                    continue;
                }

                int currentLight = isSkylight ? chunk.getSkyLight(nx, ny, nz) : chunk.getBlockLight(nx, ny, nz);

                if (newLight > currentLight) {
                    if (isSkylight) {
                        chunk.setSkyLight(nx, ny, nz, newLight);
                    } else {
                        chunk.setBlockLight(nx, ny, nz, newLight);
                    }
                    queue.add(new LightNode(nx, ny, nz, newLight));
                }
            }
        }
    }

    /**
     * ⚡ Block placement - update lighting
     * 
     * ✅ MINECRAFT-STYLE: This triggers shadow recalculation, NOT time-based update
     */
    public void onBlockPlaced(Chunk chunk, int x, int y, int z, GameBlock block) {
        if (chunk == null || block == null)
            return;

        lightingExecutor.submit(() -> {
            // If solid block placed, it blocks skylight
            if (block.isSolid()) {
                chunk.setSkyLight(x, y, z, 0);
                propagateDarknessDown(chunk, x, y, z);
            }

            // If light-emitting block, propagate its light
            int lightLevel = block.getLightLevel();
            if (lightLevel > 0) {
                chunk.setBlockLight(x, y, z, lightLevel);
                Queue<LightNode> queue = new LinkedList<>();
                queue.add(new LightNode(x, y, z, lightLevel));
                propagateLightOptimized(chunk, queue, false);
            }

            // ✅ Mark for lighting update (NOT geometry rebuild)
            chunk.setNeedsLightingUpdate(true);
        });
    }

    /**
     * ⚡ Block removal - update lighting
     * 
     * ✅ MINECRAFT-STYLE: This triggers shadow recalculation, NOT time-based update
     */
    public void onBlockRemoved(Chunk chunk, int x, int y, int z) {
        if (chunk == null)
            return;

        lightingExecutor.submit(() -> {
            // Recalculate skylight for this column
            updateColumnSkylight(chunk, x, z);

            // Clear block light at this position
            chunk.setBlockLight(x, y, z, 0);

            // Recalculate block light from nearby sources
            recalculateBlocklightAround(chunk, x, y, z);

            // ✅ Mark for lighting update (NOT geometry rebuild)
            chunk.setNeedsLightingUpdate(true);
        });
    }

    /**
     * Propagate darkness downward when a solid block is placed
     */
    private void propagateDarknessDown(Chunk chunk, int x, int startY, int z) {
        for (int y = startY - 1; Settings.isValidWorldY(y); y--) {
            GameBlock block = chunk.getBlock(x, y, z);
            if (block != null && block.isSolid())
                break;

            int currentLight = chunk.getSkyLight(x, y, z);
            if (currentLight == 0)
                break;

            chunk.setSkyLight(x, y, z, 0);
        }
    }

    /**
     * Recalculate block light from nearby light sources
     */
    private void recalculateBlocklightAround(Chunk chunk, int x, int y, int z) {
        Queue<LightNode> queue = new LinkedList<>();

        int[][] directions = {
                { 1, 0, 0 }, { -1, 0, 0 },
                { 0, 1, 0 }, { 0, -1, 0 },
                { 0, 0, 1 }, { 0, 0, -1 }
        };

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            int nz = z + dir[2];

            if (nx < 0 || nx >= Chunk.CHUNK_SIZE ||
                    !Settings.isValidWorldY(ny) ||
                    nz < 0 || nz >= Chunk.CHUNK_SIZE) {
                continue;
            }

            int light = chunk.getBlockLight(nx, ny, nz);
            if (light > 1) {
                queue.add(new LightNode(nx, ny, nz, light));
            }
        }

        propagateLightOptimized(chunk, queue, false);
    }

    /**
     * ✅ Get combined light value (skylight + blocklight)
     * Returns the RAW light value (0-15), NOT time-adjusted brightness
     */
    public static int getCombinedLight(Chunk chunk, int x, int y, int z) {
        if (chunk == null)
            return 15;

        int skyLight = chunk.getSkyLight(x, y, z);
        int blockLight = chunk.getBlockLight(x, y, z);

        // Take the maximum of skylight and blocklight
        return Math.max(skyLight, blockLight);
    }

    /**
     * ✅ Convert light value (0-15) to brightness (0.0-1.0)
     * This is STATIC brightness from light level, NOT time-adjusted
     * 
     * Time-of-day adjustment is done in ChunkRenderer via glColor
     */
    public static float getBrightness(int lightLevel) {
        if (lightLevel < 0)
            return BRIGHTNESS_TABLE[0];
        if (lightLevel > 15)
            return BRIGHTNESS_TABLE[15];
        return BRIGHTNESS_TABLE[lightLevel];
    }

    /**
     * ✅ Get brightness with gamma correction
     */
    public static float getBrightnessWithGamma(int lightLevel, float gamma) {
        float brightness = getBrightness(lightLevel);
        return (float) Math.pow(brightness, 1.0f / gamma);
    }

    /**
     * Cancel pending updates for a chunk (when unloading)
     */
    public void cancelChunkUpdates(Chunk chunk) {
        if (chunk == null)
            return;

        pendingLightUpdates.remove(chunk);
        processingChunks.remove(chunk);

        ChunkPosition pos = new ChunkPosition(chunk.getChunkX(), chunk.getChunkZ());
        initializedChunks.remove(pos);
    }

    /**
     * Get number of pending light updates
     */
    public int getPendingUpdatesCount() {
        return pendingLightUpdates.size() + processingChunks.size();
    }

    /**
     * Flush all pending updates (blocking)
     */
    public void flush() {
        while (!pendingLightUpdates.isEmpty() || !processingChunks.isEmpty()) {
            processQueuedChunks();

            // Small sleep to allow async tasks to complete
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Shutdown the lighting engine
     */
    public void shutdown() {
        lightingExecutor.shutdown();
        try {
            if (!lightingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                lightingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            lightingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        pendingLightUpdates.clear();
        processingChunks.clear();
        initializedChunks.clear();

        System.out.println("[LightingEngine] Shutdown complete");
    }

    // ========== DEPRECATED METHODS (kept for compatibility) ==========

    /**
     * @deprecated Time-based skylight updates are no longer used in Minecraft-style
     *             lighting.
     *             Light values are static; time-of-day brightness is applied at
     *             render time.
     */
    @Deprecated
    public void updateSkylightForTimeChange() {
        // ✅ Do nothing - time change is handled by
        // ChunkRenderer.setTimeOfDayBrightness()
        // This method is kept for backward compatibility but should not be called
    }

    /**
     * @deprecated Use initializeSkylightForChunk() without skylightLevel parameter
     */
    @Deprecated
    public void initializeSkylightForChunk(Chunk chunk, int ignoredSkylightLevel, boolean ignored) {
        initializeSkylightForChunk(chunk);
    }
}