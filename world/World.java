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
 * ⚡ OPTIMIZED World v4.0 - With ProLightingEngine for cross-chunk lighting
 * 
 * ============================================================================
 * MINECRAFT-STYLE LIGHTING CONCEPT:
 * ============================================================================
 * 
 * 1. LIGHT VALUES (0-15) are STATIC and stored per-block:
 * - Skylight: Always 15 for blocks that can see the sky
 * - Blocklight: Based on nearby light sources (torches, etc.)
 * 
 * 2. LIGHT PROPAGATES INTO CAVES:
 * - ProLightingEngine handles cross-chunk BFS propagation
 * - Light gradually decreases (15→14→13→...→0)
 * 
 * 3. MESH REBUILD only happens when:
 * - Block is placed/removed (geometry + shadow propagation)
 * - Chunk first loads
 * - Light values change
 * 
 * ============================================================================
 */
public class World {
    private Map<ChunkPos, Chunk> chunks = new HashMap<>();
    private long seed;
    private ChunkRenderer renderer = new ChunkRenderer();
    // private LightingEngine lightingEngine; // REMOVED: Duplicate system
    private com.mineshaft.world.lighting.SimpleLightEngine simpleLightEngine; // ✅ NEW: Better cave lighting
    private ChunkGenerationManager generationManager;

    // ✅ NEW: WorldInfo for save/load support
    private WorldInfo worldInfo;

    private int renderDistance = Settings.RENDER_DISTANCE;
    private TimeOfDay timeOfDay;

    // Debug tracking
    private int lastChunkCount = 0;
    private long lastChunkCountLog = 0;
    private static final long CHUNK_LOG_INTERVAL = 2000;

    public World(TimeOfDay timeOfDay) {
        this(timeOfDay, null); // Backward compatibility
    }

    /**
     * ✅ NEW: Constructor with WorldInfo for save/load support
     */
    public World(TimeOfDay timeOfDay, WorldInfo worldInfo) {
        this.timeOfDay = timeOfDay;
        this.worldInfo = worldInfo;

        renderer.setWorld(this);
        // lightingEngine = new LightingEngine(this, timeOfDay); // REMOVED
        simpleLightEngine = new com.mineshaft.world.lighting.SimpleLightEngine(this); // ✅ NEW
        // renderer.setLightingEngine(lightingEngine); // REMOVED
        generationManager = new ChunkGenerationManager(worldInfo); // ✅ Pass WorldInfo

        // ✅ Initialize renderer with current time brightness
        if (timeOfDay != null) {
            renderer.setTimeOfDayBrightness(timeOfDay.getBrightness());
        }

        System.out.println(
                "[World] Created with SimpleLightEngine (render distance: " + renderDistance + " chunks)" +
                        (worldInfo != null ? " for world: " + worldInfo.getName() : ""));
    }

    /**
     * ✅ NEW: Set world info (for when world loads after World creation)
     */
    public void setWorldInfo(WorldInfo worldInfo) {
        this.worldInfo = worldInfo;
        if (generationManager != null) {
            generationManager.setWorldInfo(worldInfo);
        }
        System.out.println("[World] WorldInfo set to: " + (worldInfo != null ? worldInfo.getName() : "null"));
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

    // public LightingEngine getLightingEngine() { return lightingEngine; } //
    // REMOVED

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
     * ✅ FIXED: Priority-based chunk loading (spiral from player)
     * 
     * Key fix: Chunks are now loaded in distance order, not random HashSet order.
     * This ensures chunks near player always load first, and edge chunks
     * are processed in order when player approaches.
     */
    public void updateChunks(int centerChunkX, int centerChunkZ) {
        // ========== STEP 1: Build sorted list of chunks to load (by distance)
        // ==========
        java.util.List<ChunkPos> chunksToLoad = new java.util.ArrayList<>();

        for (int x = centerChunkX - renderDistance; x <= centerChunkX + renderDistance; x++) {
            for (int z = centerChunkZ - renderDistance; z <= centerChunkZ + renderDistance; z++) {
                int dx = x - centerChunkX;
                int dz = z - centerChunkZ;
                int distSq = dx * dx + dz * dz;

                if (distSq <= renderDistance * renderDistance) {
                    chunksToLoad.add(new ChunkPos(x, z));
                }
            }
        }

        // ✅ Sort by distance (closest first)
        final int cx = centerChunkX;
        final int cz = centerChunkZ;
        chunksToLoad.sort((a, b) -> {
            int distA = (a.x - cx) * (a.x - cx) + (a.z - cz) * (a.z - cz);
            int distB = (b.x - cx) * (b.x - cx) + (b.z - cz) * (b.z - cz);
            return Integer.compare(distA, distB);
        });

        // ========== STEP 2: Unload distant chunks ==========
        Set<ChunkPos> shouldExist = new HashSet<>(chunksToLoad);
        java.util.List<ChunkPos> chunksToUnload = new java.util.ArrayList<>();

        for (ChunkPos pos : chunks.keySet()) {
            if (!shouldExist.contains(pos)) {
                chunksToUnload.add(pos);
            }
        }

        // Unload in batches
        int unloaded = 0;
        for (ChunkPos pos : chunksToUnload) {
            if (unloaded >= Settings.MAX_CHUNKS_UNLOAD_PER_FRAME)
                break;
            unloadChunkInternal(pos);
            unloaded++;
        }

        // ========== STEP 3: Load new chunks (in distance order) ==========
        int loaded = 0;
        for (ChunkPos pos : chunksToLoad) {
            if (loaded >= Settings.MAX_CHUNKS_LOAD_PER_FRAME * 2)
                break; // Allow more loading

            if (!chunks.containsKey(pos)) {
                loadChunkInternal(pos.x, pos.z, centerChunkX, centerChunkZ);
                loaded++;
            }
        }

        // ========== STEP 4: Force-process chunks at edge that are stuck ==========
        // This fixes chunks that got queued but never completed
        for (ChunkPos pos : chunksToLoad) {
            Chunk chunk = chunks.get(pos);
            if (chunk != null && chunk.getState() != ChunkState.READY) {
                // Check if this chunk has been stuck for too long
                if (chunk.getState() == ChunkState.EMPTY) {
                    // Re-queue for generation
                    int dx = pos.x - centerChunkX;
                    int dz = pos.z - centerChunkZ;
                    generationManager.queueGeneration(chunk, dx * dx + dz * dz);
                }
            }
        }

        // Update async generation system
        generationManager.update();

        // ✅ Lighting is now done synchronously in ChunkGenerationManager - no separate
        // update needed

        // ✅ Process cross-chunk light propagation
        // ProLightingEngine removed in favor of SimpleLightEngine

        // ✅ NEW: Update sun light direction and sky light level
        // if (lightingEngine != null) {
        // lightingEngine.updateSunLight();
        // }

        logChunkCount();
    }

    /**
     * ✅ REWRITTEN: Direct chunk loading with immediate lighting
     * 
     * Key changes:
     * - Terrain + lighting done together in async thread
     * - No separate LIGHT_PENDING phase
     * - Chunk becomes READY immediately after async complete
     */
    private void loadChunkInternal(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);

        if (!chunks.containsKey(pos)) {
            // Create chunk immediately and add to map
            Chunk chunk = new Chunk(chunkX, chunkZ);
            chunks.put(pos, chunk);

            // Calculate distance for prioritization
            int dx = chunkX - playerChunkX;
            int dz = chunkZ - playerChunkZ;
            double distSq = dx * dx + dz * dz;

            // Queue for async generation (includes lighting)
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
            // if (lightingEngine != null) {
            // lightingEngine.cancelChunkUpdates(chunk);
            // }

            // Remove from renderer
            if (renderer != null) {
                renderer.removeChunk(chunk);
            }
        }
    }

    /**
     * ✅ REMOVED: updateLighting() is no longer needed
     * 
     * Lighting is now done synchronously in ChunkGenerationManager.
     * Chunks transition directly from GENERATED → READY.
     */

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

            // ✅ Lighting is now done in ChunkGenerationManager synchronously
            if (chunk.isGenerated() && !chunk.isLightInitialized()) {
                if (simpleLightEngine != null) {
                    simpleLightEngine.initializeChunk(chunk);
                }
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
            // if (lightingEngine != null) {
            // lightingEngine.cancelChunkUpdates(chunk);
            // }

            if (renderer != null) {
                renderer.removeChunk(chunk);
            }
        }
    }

    /**
     * ✅ Mark neighbor chunks for mesh rebuild
     */
    public void markNeighborsForRebuild(int chunkX, int chunkZ) {
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
        // Chunk chunk = getChunk(chunkX, chunkZ);
        // if (chunk != null && chunk.isGenerated() && chunk.isLightInitialized()) {
        // lightingEngine.queueChunkForLightUpdate(chunk);
        // }
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
        // if (lightingEngine != null) {
        // lightingEngine.updateSunLight();
        // }
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
     * ✅ REVISED v2: Set block with proper lighting + immediate visual update
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

            // Set the new block
            chunk.setBlock(localX, worldY, localZ, block);

            // ✅ IMMEDIATE: Force geometry rebuild with high priority
            chunk.setNeedsGeometryRebuild(true);

            // Request immediate mesh rebuild for responsive block breaking
            if (renderer != null) {
                renderer.requestImmediateRebuild(chunk);
            }

            // ✅ Use SimpleLightEngine for proper cave lighting
            if (simpleLightEngine != null) {
                if (block == null || block.isAir()) {
                    // Block broken - light floods in
                    simpleLightEngine.onBlockBroken(worldX, worldY, worldZ);
                } else {
                    // Block placed - may block light + propagate darkness
                    simpleLightEngine.onBlockPlaced(worldX, worldY, worldZ, block);
                }
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
        return 0; // lightingEngine != null ? lightingEngine.getPendingUpdatesCount() : 0;
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

        // if (lightingEngine != null) {
        // lightingEngine.shutdown();
        // }

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