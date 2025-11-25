// src/main/java/com/mineshaft/world/World.java
package com.mineshaft.world;

import com.mineshaft.block.GameBlock;
import com.mineshaft.block.BlockRegistry;
import com.mineshaft.core.Settings;
import com.mineshaft.core.TimeOfDay;
import com.mineshaft.entity.Camera;
import com.mineshaft.render.ChunkRenderer;
import com.mineshaft.world.lighting.LightingEngine;

import java.util.*;

/**
 * ⚡ OPTIMIZED World with Async Generation & Lighting
 * ✅ Multi-threaded terrain generation
 * ✅ Progressive chunk loading
 * ✅ No FPS drops
 */
public class World {
    private Map<ChunkPos, Chunk> chunks = new HashMap<>();
    // ✅ NEW: Seed handling for world generation
    private long seed;
    private ChunkRenderer renderer = new ChunkRenderer();
    private LightingEngine lightingEngine;
    private ChunkGenerationManager generationManager;

    private int renderDistance = Settings.RENDER_DISTANCE;
    private TimeOfDay timeOfDay;

    // Debug tracking
    private int lastChunkCount = 0;
    private long lastChunkCountLog = 0;
    private static final long CHUNK_LOG_INTERVAL = 2000;

    public World(TimeOfDay timeOfDay) {
        this.timeOfDay = timeOfDay;
        renderer.setWorld(this);
        lightingEngine = new LightingEngine(this, timeOfDay);
        renderer.setLightingEngine(lightingEngine);
        // Initialize async generation system
        generationManager = new ChunkGenerationManager();

        System.out.println("World created (render distance: " + renderDistance + " chunks)");
    }

    /**
     * ✅ Set the world seed used for terrain generation.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * ✅ Retrieve the current world seed.
     */
    public long getSeed() {
        return this.seed;
    }

    public TimeOfDay getTimeOfDay() {
        return timeOfDay;
    }

    public LightingEngine getLightingEngine() {
        return lightingEngine;
    }

    public ChunkRenderer getRenderer() {
        return renderer;
    }

    public Collection<Chunk> getChunks() {
        return chunks.values();
    }

    /**
     * ✅ OPTIMIZED - Async chunk loading
     */
    public void updateChunks(int centerChunkX, int centerChunkZ) {
        Set<ChunkPos> chunksToLoad = new HashSet<>();
        Set<ChunkPos> chunksToUnload = new HashSet<>();

        // Calculate which chunks should be loaded
        for (int x = centerChunkX - renderDistance; x <= centerChunkX + renderDistance; x++) {
            for (int z = centerChunkZ - renderDistance; z <= centerChunkZ + renderDistance; z++) {
                int dx = x - centerChunkX;
                int dz = z - centerChunkZ;
                if (dx * dx + dz * dz <= renderDistance * renderDistance) {
                    ChunkPos pos = new ChunkPos(x, z);
                    chunksToLoad.add(pos);
                }
            }
        }

        // Find chunks to unload
        for (ChunkPos pos : chunks.keySet()) {
            if (!chunksToLoad.contains(pos)) {
                chunksToUnload.add(pos);
            }
        }

        // Unload chunks
        for (ChunkPos pos : chunksToUnload) {
            unloadChunkInternal(pos);
        }

        // Load new chunks
        for (ChunkPos pos : chunksToLoad) {
            if (!chunks.containsKey(pos)) {
                loadChunkInternal(pos.x, pos.z, centerChunkX, centerChunkZ);
            }
        }

        // Update async generation system
        generationManager.update();

        // Update lighting for generated chunks
        updateLighting();

        logChunkCount();
    }

    /**
     * ✅ Internal: Load chunk and queue for async generation
     */
    private void loadChunkInternal(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);

        if (!chunks.containsKey(pos)) {
            // Create chunk WITHOUT generating terrain (instant)
            Chunk chunk = new Chunk(chunkX, chunkZ);
            chunks.put(pos, chunk);

            // Calculate distance for prioritization
            int dx = chunkX - playerChunkX;
            int dz = chunkZ - playerChunkZ;
            double distSq = dx * dx + dz * dz;

            // Queue for async terrain generation
            generationManager.queueGeneration(chunk, distSq);
        }
    }

    /**
     * ✅ Internal: Unload chunk and cleanup
     */
    private void unloadChunkInternal(ChunkPos pos) {
        Chunk chunk = chunks.remove(pos);
        if (chunk != null) {
            // Cancel any pending lighting updates
            lightingEngine.cancelChunkUpdates(chunk);

            // Remove from renderer
            renderer.removeChunk(chunk);
        }
    }

    /**
     * ✅ Initialize lighting for newly generated chunks
     */
    private void updateLighting() {
        int skylightLevel = (timeOfDay != null) ? timeOfDay.getSkylightLevel() : 15;

        for (Chunk chunk : chunks.values()) {
            // Only process chunks that have terrain generated but no lighting yet
            if (chunk.getState() == ChunkState.LIGHT_PENDING && !chunk.isLightInitialized()) {
                lightingEngine.initializeSkylightForChunk(chunk, skylightLevel);
                lightingEngine.initializeBlocklightForChunk(chunk);

                chunk.setLightInitialized(true);
                chunk.setState(ChunkState.READY);
                chunk.setNeedsRebuild(true);

                // Mark neighbors for rebuild
                markNeighborsForRebuild(chunk.getChunkX(), chunk.getChunkZ());
            }
        }
    }

    // ========== PUBLIC CHUNK LOADING API ==========

    /**
     * ✅ Get or create chunk at specified coordinates
     */
    public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);

        if (chunk == null) {
            chunk = new Chunk(chunkX, chunkZ);
            chunks.put(pos, chunk);
        }

        return chunk;
    }

    /**
     * ✅ Load chunk at specified coordinates (public API)
     */
    public void loadChunk(int chunkX, int chunkZ) {
        Chunk chunk = getOrCreateChunk(chunkX, chunkZ);
        if (chunk != null && !chunk.isGenerated()) {
            chunk.generate();

            // Initialize lighting after generation
            if (chunk.isGenerated() && !chunk.isLightInitialized()) {
                int skylightLevel = (timeOfDay != null) ? timeOfDay.getSkylightLevel() : 15;
                lightingEngine.initializeSkylightForChunk(chunk, skylightLevel);
                lightingEngine.initializeBlocklightForChunk(chunk);
                chunk.setLightInitialized(true);
                chunk.setState(ChunkState.READY);
                chunk.setNeedsRebuild(true);
            }
        }
    }

    /**
     * ✅ Unload chunk at specified coordinates (public API)
     */
    public void unloadChunk(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Chunk chunk = chunks.remove(pos);

        if (chunk != null) {
            // Cancel any pending lighting updates
            if (lightingEngine != null) {
                lightingEngine.cancelChunkUpdates(chunk);
            }

            // Remove from renderer
            if (renderer != null) {
                renderer.removeChunk(chunk);
            }
        }
    }

    /**
     * ✅ Mark neighbor chunks for mesh rebuild
     */
    private void markNeighborsForRebuild(int chunkX, int chunkZ) {
        int[][] neighbors = {
                { chunkX - 1, chunkZ },
                { chunkX + 1, chunkZ },
                { chunkX, chunkZ - 1 },
                { chunkX, chunkZ + 1 },
        };

        for (int[] neighbor : neighbors) {
            ChunkPos neighborPos = new ChunkPos(neighbor[0], neighbor[1]);
            Chunk neighborChunk = chunks.get(neighborPos);
            if (neighborChunk != null && neighborChunk.isReady()) {
                neighborChunk.setNeedsRebuild(true);
            }
        }
    }

    /**
     * ✅ Update skylight when time changes
     */
    public void updateSkylightForTimeChange() {
        if (timeOfDay == null)
            return;

        for (Chunk chunk : chunks.values()) {
            if (chunk.isGenerated() && chunk.isLightInitialized()) {
                lightingEngine.queueChunkForLightUpdate(chunk);
            }
        }
    }

    /**
     * ✅ Update sun lighting
     */
    public void updateSunLight() {
        if (lightingEngine != null) {
            lightingEngine.updateSunLight();
        }
    }

    // ========== BLOCK ACCESS ==========

    public GameBlock getBlock(int worldX, int worldY, int worldZ) {
        if (!Settings.isValidWorldY(worldY)) {
            return BlockRegistry.AIR;
        }

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);

        if (chunk == null || !chunk.isGenerated()) {
            return BlockRegistry.AIR;
        }

        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

        return chunk.getBlock(localX, worldY, localZ);
    }

    public void setBlock(int worldX, int worldY, int worldZ, GameBlock block) {
        if (!Settings.isValidWorldY(worldY)) {
            return;
        }

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);

        if (chunk != null && chunk.isGenerated()) {
            int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
            int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

            chunk.setBlock(localX, worldY, localZ, block);

            if (block.isAir()) {
                lightingEngine.onBlockRemoved(chunk, localX, worldY, localZ);
            } else {
                lightingEngine.onBlockPlaced(chunk, localX, worldY, localZ, block);
            }

            if (localX == 0 || localX == Chunk.CHUNK_SIZE - 1 ||
                    localZ == 0 || localZ == Chunk.CHUNK_SIZE - 1) {
                markNeighborsForRebuild(chunkX, chunkZ);
            }
        }
    }

    // ========== LIGHT ACCESS ==========

    public int getLight(int worldX, int worldY, int worldZ) {
        int skyLight = getSkyLight(worldX, worldY, worldZ);
        int blockLight = getBlockLight(worldX, worldY, worldZ);
        return Math.max(skyLight, blockLight);
    }

    public int getSkyLight(int worldX, int worldY, int worldZ) {
        if (!Settings.isValidWorldY(worldY))
            return 0;

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);

        if (chunk == null || !chunk.isLightInitialized())
            return 0;

        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

        return chunk.getSkyLight(localX, worldY, localZ);
    }

    public int getBlockLight(int worldX, int worldY, int worldZ) {
        if (!Settings.isValidWorldY(worldY))
            return 0;

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);

        if (chunk == null || !chunk.isLightInitialized())
            return 0;

        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

        return chunk.getBlockLight(localX, worldY, localZ);
    }

    // ========== RENDERING ==========

    public void render(Camera camera) {
        List<Chunk> visibleChunks = new ArrayList<>();

        for (Chunk chunk : chunks.values()) {
            // Only render chunks that are fully ready (terrain + lighting)
            if (chunk.isReady()) {
                float chunkCenterX = chunk.getChunkX() * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2.0f;
                float chunkCenterZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2.0f;

                float dx = chunkCenterX - camera.getX();
                float dz = chunkCenterZ - camera.getZ();
                float distance = (float) Math.sqrt(dx * dx + dz * dz);

                if (distance < renderDistance * Chunk.CHUNK_SIZE) {
                    renderer.renderChunk(chunk, camera);
                    visibleChunks.add(chunk);
                }
            }
        }

        renderer.renderSolidPass(visibleChunks);
        renderer.renderTranslucentPass(visibleChunks, camera);
        renderer.renderWaterPass(visibleChunks, camera);
    }

    // ========== UTILITY ==========

    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunks.get(new ChunkPos(chunkX, chunkZ));
    }

    public int getLoadedChunkCount() {
        return chunks.size();
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    public void setRenderDistance(int distance) {
        this.renderDistance = Math.max(2, Math.min(32, distance));
    }

    /**
     * ✅ Get generation stats
     */
    public int getPendingGenerations() {
        return generationManager != null ? generationManager.getPendingCount() : 0;
    }

    public int getActiveGenerationThreads() {
        return generationManager != null ? generationManager.getActiveThreads() : 0;
    }

    /**
     * ✅ Debug logging
     */
    private void logChunkCount() {
        int currentCount = chunks.size();
        long now = System.currentTimeMillis();

        if (currentCount != lastChunkCount || (now - lastChunkCountLog > CHUNK_LOG_INTERVAL)) {
            if (currentCount != lastChunkCount && Settings.DEBUG_CHUNK_LOADING) {
                System.out.println(String.format(
                        "[World] Chunks: %d -> %d (%+d) | Pending: %d | Gen Threads: %d",
                        lastChunkCount, currentCount, (currentCount - lastChunkCount),
                        getPendingGenerations(), getActiveGenerationThreads()));
            }
            lastChunkCount = currentCount;
            lastChunkCountLog = now;
        }
    }

    public void cleanup() {
        System.out.println("Cleaning up world (" + chunks.size() + " chunks)...");

        // Shutdown generation manager
        if (generationManager != null) {
            generationManager.shutdown();
        }

        if (lightingEngine != null) {
            lightingEngine.shutdown();
        }

        renderer.cleanup();
        chunks.clear();
    }

    // ========== CHUNK POSITION ==========

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