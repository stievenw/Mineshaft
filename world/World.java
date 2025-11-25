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
 * ⚡ OPTIMIZED World v3.0 - Minecraft-Style Lighting System
 * 
 * ============================================================================
 * MINECRAFT-STYLE LIGHTING CONCEPT:
 * ============================================================================
 * 
 * 1. LIGHT VALUES (0-15) are STATIC and stored per-block:
 * - Skylight: Always 15 for blocks that can see the sky
 * - Blocklight: Based on nearby light sources (torches, etc.)
 * 
 * 2. LIGHT VALUES DO NOT CHANGE WITH TIME OF DAY!
 * - Time change only affects RENDER brightness, not light values
 * - ChunkRenderer.setTimeOfDayBrightness() handles this
 * - NO mesh rebuild when time changes!
 * 
 * 3. MESH REBUILD only happens when:
 * - Block is placed/removed (geometry + shadow propagation)
 * - Chunk first loads
 * 
 * ============================================================================
 */
public class World {
    private Map<ChunkPos, Chunk> chunks = new HashMap<>();
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
        generationManager = new ChunkGenerationManager();

        // ✅ Initialize renderer with current time brightness
        if (timeOfDay != null) {
            renderer.setTimeOfDayBrightness(timeOfDay.getBrightness());
        }

        System.out.println(
                "[World] Created with Minecraft-style lighting (render distance: " + renderDistance + " chunks)");
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

    // ========== TIME-OF-DAY BRIGHTNESS (NEW!) ==========

    /**
     * ✅ NEW: Update time-of-day brightness for rendering
     * 
     * This is called from Game.java when TimeOfDay updates.
     * It ONLY updates the render brightness - NO mesh rebuild!
     * 
     * @param brightness The brightness multiplier (0.0-1.0)
     */
    public void setTimeOfDayBrightness(float brightness) {
        if (renderer != null) {
            renderer.setTimeOfDayBrightness(brightness);
        }
    }

    /**
     * ✅ NEW: Get current time-of-day brightness
     */
    public float getTimeOfDayBrightness() {
        return renderer != null ? renderer.getTimeOfDayBrightness() : 1.0f;
    }

    // ========== CHUNK MANAGEMENT ==========

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
     * ✅ REVISED: Initialize lighting for newly generated chunks
     * 
     * MINECRAFT-STYLE: Skylight is always 15 (full) - time brightness is applied at
     * render time
     */
    private void updateLighting() {
        for (Chunk chunk : chunks.values()) {
            // Only process chunks that have terrain generated but no lighting yet
            if (chunk.getState() == ChunkState.LIGHT_PENDING && !chunk.isLightInitialized()) {
                // ✅ MINECRAFT-STYLE: Always use full skylight (15)
                // Time-of-day brightness is applied by ChunkRenderer, not here
                lightingEngine.initializeSkylightForChunk(chunk);
                lightingEngine.initializeBlocklightForChunk(chunk);

                chunk.setLightInitialized(true);
                chunk.setState(ChunkState.READY);
                chunk.setNeedsGeometryRebuild(true); // Initial mesh generation

                // Mark neighbors for rebuild (edge seams)
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
                // ✅ MINECRAFT-STYLE: Always use full skylight
                lightingEngine.initializeSkylightForChunk(chunk);
                lightingEngine.initializeBlocklightForChunk(chunk);
                chunk.setLightInitialized(true);
                chunk.setState(ChunkState.READY);
                chunk.setNeedsGeometryRebuild(true); // Need initial mesh
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
            if (lightingEngine != null) {
                lightingEngine.cancelChunkUpdates(chunk);
            }

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
                neighborChunk.setNeedsGeometryRebuild(true);
            }
        }
    }

    // ========== SKYLIGHT UPDATE METHODS ==========

    /**
     * ✅ NEW: Update skylight when blocks change (shadow propagation)
     * 
     * Called when a block is placed/removed that affects shadow propagation.
     * This DOES trigger lighting recalculation and mesh rebuild.
     */
    public void updateSkylightForBlockChange(int chunkX, int chunkZ) {
        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk != null && chunk.isGenerated() && chunk.isLightInitialized()) {
            lightingEngine.queueChunkForLightUpdate(chunk);
        }
    }

    /**
     * ✅ NEW: Update skylight for block change at world coordinates
     */
    public void updateSkylightForBlockChangeAt(int worldX, int worldZ) {
        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);
        updateSkylightForBlockChange(chunkX, chunkZ);
    }

    /**
     * ✅ DEPRECATED: Time change should NOT trigger skylight update!
     * 
     * Skylight value (0-15) stays constant, only brightness multiplier changes.
     * Time-of-day brightness is handled by ChunkRenderer.setTimeOfDayBrightness()
     * 
     * This method is kept for backward compatibility but does NOTHING.
     * 
     * @deprecated Use setTimeOfDayBrightness() instead for time changes
     */
    @Deprecated
    public void updateSkylightForTimeChange() {
        // ✅ DO NOTHING - time change is handled by
        // ChunkRenderer.setTimeOfDayBrightness()
        // Light values don't change, only the render brightness multiplier

        if (Settings.DEBUG_MODE) {
            System.out.println("[World] updateSkylightForTimeChange() called but ignored (Minecraft-style lighting)");
        }
    }

    /**
     * ✅ Update sun lighting direction (for visual effects only)
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

    /**
     * ✅ REVISED: Set block with proper lighting update
     */
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

            // Get old block to check if lighting needs update
            GameBlock oldBlock = chunk.getBlock(localX, worldY, localZ);

            // Set the new block
            chunk.setBlock(localX, worldY, localZ, block);

            // ✅ Update lighting based on block change
            if (block.isAir()) {
                lightingEngine.onBlockRemoved(chunk, localX, worldY, localZ);
            } else {
                lightingEngine.onBlockPlaced(chunk, localX, worldY, localZ, block);
            }

            // ✅ Check if shadow propagation changed (solid block placed/removed)
            boolean oldBlockedLight = oldBlock != null && oldBlock.isSolid() && !oldBlock.isAir();
            boolean newBlockedLight = block != null && block.isSolid() && !block.isAir();

            if (oldBlockedLight != newBlockedLight) {
                // Shadow propagation changed - update skylight for this column
                updateSkylightForBlockChange(chunkX, chunkZ);
            }

            // Mark neighbor chunks for rebuild if block is on edge
            if (localX == 0 || localX == Chunk.CHUNK_SIZE - 1 ||
                    localZ == 0 || localZ == Chunk.CHUNK_SIZE - 1) {
                markNeighborsForRebuild(chunkX, chunkZ);
            }
        }
    }

    // ========== LIGHT ACCESS ==========

    /**
     * ✅ Get combined light value (max of skylight and blocklight)
     * Returns RAW light value (0-15), NOT time-adjusted
     */
    public int getLight(int worldX, int worldY, int worldZ) {
        int skyLight = getSkyLight(worldX, worldY, worldZ);
        int blockLight = getBlockLight(worldX, worldY, worldZ);
        return Math.max(skyLight, blockLight);
    }

    /**
     * ✅ Get skylight value (0-15)
     * This is STATIC - doesn't change with time of day
     */
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

    /**
     * ✅ Get blocklight value (0-15)
     */
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

    /**
     * ✅ NEW: Get effective brightness at position (light value * time brightness)
     * This is what would actually be displayed on screen
     */
    public float getEffectiveBrightness(int worldX, int worldY, int worldZ) {
        int lightValue = getLight(worldX, worldY, worldZ);
        float baseBrightness = LightingEngine.getBrightness(lightValue);
        return baseBrightness * getTimeOfDayBrightness();
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

        // ✅ Time-of-day brightness is already applied in each render pass
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

    public int getPendingGenerations() {
        return generationManager != null ? generationManager.getPendingCount() : 0;
    }

    public int getActiveGenerationThreads() {
        return generationManager != null ? generationManager.getActiveThreads() : 0;
    }

    /**
     * ✅ Get lighting engine pending updates
     */
    public int getPendingLightUpdates() {
        return lightingEngine != null ? lightingEngine.getPendingUpdatesCount() : 0;
    }

    private void logChunkCount() {
        int currentCount = chunks.size();
        long now = System.currentTimeMillis();

        if (currentCount != lastChunkCount || (now - lastChunkCountLog > CHUNK_LOG_INTERVAL)) {
            if (currentCount != lastChunkCount && Settings.DEBUG_CHUNK_LOADING) {
                System.out.println(String.format(
                        "[World] Chunks: %d -> %d (%+d) | Pending Gen: %d | Pending Light: %d | Brightness: %.2f",
                        lastChunkCount, currentCount, (currentCount - lastChunkCount),
                        getPendingGenerations(), getPendingLightUpdates(), getTimeOfDayBrightness()));
            }
            lastChunkCount = currentCount;
            lastChunkCountLog = now;
        }
    }

    public void cleanup() {
        System.out.println("[World] Cleaning up (" + chunks.size() + " chunks)...");

        if (generationManager != null) {
            generationManager.shutdown();
        }

        if (lightingEngine != null) {
            lightingEngine.shutdown();
        }

        renderer.cleanup();
        chunks.clear();

        System.out.println("[World] Cleanup complete");
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