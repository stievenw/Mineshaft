// src/main/java/com/mineshaft/world/ChunkSection.java
package com.mineshaft.world;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;

/**
 * ✅ Minecraft-style Chunk Section v3.0 (16×16×16 blocks)
 * 
 * ============================================================================
 * MINECRAFT-STYLE LIGHTING CONCEPT:
 * ============================================================================
 * 
 * 1. LIGHT VALUES (0-15) are STATIC per-block:
 * - Skylight: Can this block see the sky? (shadow propagation)
 * - Blocklight: Is there a torch/glowstone nearby?
 * 
 * 2. LIGHT VALUES DO NOT CHANGE WITH TIME OF DAY!
 * - Time-of-day brightness is applied at RENDER time (via glColor)
 * - NO mesh rebuild when time changes!
 * 
 * 3. REBUILD FLAGS are separated:
 * - needsGeometryRebuild: Block SHAPE changed (place/remove)
 * - needsLightingUpdate: Light VALUES changed (torch, shadow)
 * 
 * ============================================================================
 * 
 * Chunks are divided vertically into sections to optimize:
 * - Memory (empty sections not allocated)
 * - Rendering (section-level culling)
 * - Mesh building (only rebuild affected section)
 */
public class ChunkSection {
    public static final int SECTION_SIZE = 16;
    public static final int TOTAL_BLOCKS = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE; // 4096

    private GameBlock[][][] blocks; // 16×16×16
    private byte[][][] skyLight; // 16×16×16
    private byte[][][] blockLight; // 16×16×16

    private final int sectionY; // Section index (0-23 for Y=-64 to Y=320)
    private final Chunk parentChunk; // Reference to parent chunk
    private boolean isEmpty = true;
    private int nonAirBlockCount = 0;

    // ========== MINECRAFT-STYLE REBUILD FLAGS ==========

    /**
     * ✅ GEOMETRY REBUILD needed when block SHAPE changes:
     * - Block placed/removed
     * - Initial section creation
     * 
     * This triggers full mesh rebuild for this section.
     */
    private boolean needsGeometryRebuild = true;

    /**
     * ✅ LIGHTING UPDATE needed when light VALUES change:
     * - Light source placed/removed (torch, glowstone)
     * - Opacity changed (solid block blocking skylight)
     * 
     * This updates light arrays. In current implementation,
     * this also triggers mesh rebuild because vertex colors need update.
     * 
     * NOTE: Time-of-day changes do NOT set this flag!
     * Time brightness is applied at render time via glColor.
     */
    private boolean needsLightingUpdate = true;

    /**
     * ✅ Prevent concurrent mesh builds
     */
    private volatile boolean isBuilding = false;

    /**
     * Create a new chunk section
     * 
     * @param parentChunk Parent chunk
     * @param sectionY    Section index (0 = Y=-64 to Y=-48, 23 = Y=304 to Y=320)
     */
    public ChunkSection(Chunk parentChunk, int sectionY) {
        this.parentChunk = parentChunk;
        this.sectionY = sectionY;
        this.blocks = new GameBlock[SECTION_SIZE][SECTION_SIZE][SECTION_SIZE];
        this.skyLight = new byte[SECTION_SIZE][SECTION_SIZE][SECTION_SIZE];
        this.blockLight = new byte[SECTION_SIZE][SECTION_SIZE][SECTION_SIZE];

        // Initialize all blocks to AIR with default lighting
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    blocks[x][y][z] = BlockRegistry.AIR;
                    skyLight[x][y][z] = 0; // Will be set by LightingEngine
                    blockLight[x][y][z] = 0;
                }
            }
        }
    }

    // ========== BLOCK ACCESS ==========

    /**
     * Get block at local section coordinates (0-15)
     */
    public GameBlock getBlock(int x, int y, int z) {
        if (!isValidLocalCoord(x, y, z)) {
            return null;
        }
        return blocks[x][y][z];
    }

    /**
     * Set block at local section coordinates (0-15)
     * 
     * NOTE: This method does NOT set rebuild flags automatically.
     * The Chunk class handles flag logic based on what changed.
     * Use setBlockWithFlags() if you need automatic flag handling.
     */
    public void setBlock(int x, int y, int z, GameBlock block) {
        if (!isValidLocalCoord(x, y, z)) {
            return;
        }

        GameBlock oldBlock = blocks[x][y][z];
        blocks[x][y][z] = block;

        // Update empty state tracking
        boolean wasAir = oldBlock == null || oldBlock.isAir();
        boolean isAir = block == null || block.isAir();

        if (wasAir && !isAir) {
            nonAirBlockCount++;
            isEmpty = false;
        } else if (!wasAir && isAir) {
            nonAirBlockCount--;
            if (nonAirBlockCount <= 0) {
                nonAirBlockCount = 0;
                isEmpty = true;
            }
        }
    }

    /**
     * ✅ Set block with automatic flag handling
     * 
     * This method determines what changed and sets appropriate flags:
     * - Shape change → needsGeometryRebuild
     * - Opacity change → needsLightingUpdate
     * - Light emission change → needsLightingUpdate
     */
    public void setBlockWithFlags(int x, int y, int z, GameBlock block) {
        if (!isValidLocalCoord(x, y, z)) {
            return;
        }

        GameBlock oldBlock = blocks[x][y][z];

        // Set the block
        setBlock(x, y, z, block);

        // ✅ Check what changed
        boolean shapeChanged = (oldBlock != block);

        boolean oldBlocksLight = oldBlock != null && !oldBlock.isAir() && oldBlock.isSolid();
        boolean newBlocksLight = block != null && !block.isAir() && block.isSolid();
        boolean opacityChanged = (oldBlocksLight != newBlocksLight);

        int oldLightLevel = (oldBlock != null) ? oldBlock.getLightLevel() : 0;
        int newLightLevel = (block != null) ? block.getLightLevel() : 0;
        boolean lightEmissionChanged = (oldLightLevel != newLightLevel);

        // ✅ Set appropriate flags
        if (shapeChanged) {
            needsGeometryRebuild = true;
        }

        if (opacityChanged || lightEmissionChanged) {
            needsLightingUpdate = true;
        }
    }

    /**
     * Check if local coordinates are valid (0-15)
     */
    private boolean isValidLocalCoord(int x, int y, int z) {
        return x >= 0 && x < SECTION_SIZE &&
                y >= 0 && y < SECTION_SIZE &&
                z >= 0 && z < SECTION_SIZE;
    }

    // ========== LIGHTING ACCESS ==========

    /**
     * ✅ Get skylight level (0-15)
     * 
     * NOTE: This returns the STATIC skylight value.
     * It does NOT change with time of day!
     * Time brightness is applied at render time via glColor.
     */
    public int getSkyLight(int x, int y, int z) {
        if (!isValidLocalCoord(x, y, z)) {
            return 0;
        }
        return skyLight[x][y][z] & 0x0F; // Mask to 0-15
    }

    /**
     * ✅ Set skylight level (0-15)
     */
    public void setSkyLight(int x, int y, int z, int level) {
        if (!isValidLocalCoord(x, y, z)) {
            return;
        }
        skyLight[x][y][z] = (byte) Math.max(0, Math.min(15, level));
    }

    /**
     * ✅ Get blocklight level (0-15)
     */
    public int getBlockLight(int x, int y, int z) {
        if (!isValidLocalCoord(x, y, z)) {
            return 0;
        }
        return blockLight[x][y][z] & 0x0F; // Mask to 0-15
    }

    /**
     * ✅ Set blocklight level (0-15)
     */
    public void setBlockLight(int x, int y, int z, int level) {
        if (!isValidLocalCoord(x, y, z)) {
            return;
        }
        blockLight[x][y][z] = (byte) Math.max(0, Math.min(15, level));
    }

    /**
     * ✅ Get combined light (max of skylight and blocklight)
     * 
     * Returns RAW light value (0-15), NOT time-adjusted!
     * Time brightness is applied at render time.
     */
    public int getCombinedLight(int x, int y, int z) {
        int sky = getSkyLight(x, y, z);
        int block = getBlockLight(x, y, z);
        return Math.max(sky, block);
    }

    /**
     * ✅ Fill entire section with specified skylight level
     * Used for initial lighting setup
     */
    public void fillSkyLight(int level) {
        byte lightByte = (byte) Math.max(0, Math.min(15, level));
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    skyLight[x][y][z] = lightByte;
                }
            }
        }
    }

    /**
     * ✅ Clear all blocklight in this section
     */
    public void clearBlockLight() {
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    blockLight[x][y][z] = 0;
                }
            }
        }
    }

    // ========== SECTION STATE ==========

    /**
     * Check if this section is empty (all air blocks)
     * Empty sections are not stored in memory and not rendered
     */
    public boolean isEmpty() {
        return isEmpty;
    }

    /**
     * Check if this section is fully opaque (all solid blocks)
     * Used for occlusion culling - fully opaque sections can hide adjacent sections
     */
    public boolean isFullyOpaque() {
        if (isEmpty) {
            return false;
        }

        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    GameBlock block = blocks[x][y][z];
                    if (block == null || block.isAir() || !block.isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * ✅ Check if this section has any light sources
     */
    public boolean hasLightSources() {
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    GameBlock block = blocks[x][y][z];
                    if (block != null && block.getLightLevel() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Get the section Y index (0-23)
     */
    public int getSectionY() {
        return sectionY;
    }

    /**
     * Get the number of non-air blocks in this section
     */
    public int getNonAirBlockCount() {
        return nonAirBlockCount;
    }

    /**
     * ✅ Alias for getNonAirBlockCount() for compatibility
     */
    public int getBlockCount() {
        return nonAirBlockCount;
    }

    /**
     * Get the minimum world Y coordinate for this section
     */
    public int getMinWorldY() {
        return sectionY * SECTION_SIZE + Settings.WORLD_MIN_Y;
    }

    /**
     * Get the maximum world Y coordinate for this section
     */
    public int getMaxWorldY() {
        return getMinWorldY() + SECTION_SIZE - 1;
    }

    /**
     * Get parent chunk reference
     */
    public Chunk getParentChunk() {
        return parentChunk;
    }

    // ========== MINECRAFT-STYLE REBUILD FLAGS ==========

    /**
     * ✅ Check if geometry rebuild is needed (block shape changed)
     */
    public boolean needsGeometryRebuild() {
        return needsGeometryRebuild;
    }

    /**
     * ✅ Set geometry rebuild flag
     */
    public void setNeedsGeometryRebuild(boolean needs) {
        this.needsGeometryRebuild = needs;
    }

    /**
     * ✅ Check if lighting update is needed (light values changed)
     * 
     * NOTE: This is for BLOCK lighting changes (torch, shadow propagation),
     * NOT for time-of-day changes! Time brightness is applied at render time.
     */
    public boolean needsLightingUpdate() {
        return needsLightingUpdate;
    }

    /**
     * ✅ Set lighting update flag
     */
    public void setNeedsLightingUpdate(boolean needs) {
        this.needsLightingUpdate = needs;
    }

    /**
     * ✅ Check if ONLY lighting update is needed (no geometry change)
     * 
     * This optimization allows skipping full geometry rebuild
     * if only vertex colors need to change (future optimization).
     */
    public boolean needsOnlyLightingUpdate() {
        return needsLightingUpdate && !needsGeometryRebuild;
    }

    /**
     * ✅ Check if any rebuild is needed (geometry OR lighting)
     */
    public boolean needsMeshRebuild() {
        return needsGeometryRebuild || needsLightingUpdate;
    }

    /**
     * ✅ Clear all rebuild flags (after mesh has been rebuilt)
     */
    public void clearRebuildFlags() {
        this.needsGeometryRebuild = false;
        this.needsLightingUpdate = false;
    }

    /**
     * Check if mesh is currently being built (prevent concurrent builds)
     */
    public boolean isBuilding() {
        return isBuilding;
    }

    /**
     * Set building state
     */
    public void setBuilding(boolean building) {
        this.isBuilding = building;
    }

    // ========== UTILITY METHODS ==========

    /**
     * ✅ Convert local Y to world Y
     */
    public int localYToWorldY(int localY) {
        return getMinWorldY() + localY;
    }

    /**
     * ✅ Convert world Y to local Y (if within this section)
     */
    public int worldYToLocalY(int worldY) {
        return worldY - getMinWorldY();
    }

    /**
     * ✅ Check if world Y is within this section's range
     */
    public boolean containsWorldY(int worldY) {
        return worldY >= getMinWorldY() && worldY <= getMaxWorldY();
    }

    /**
     * ✅ Get section center position (for distance calculations)
     */
    public float[] getSectionCenter() {
        float baseX = parentChunk != null ? parentChunk.getChunkX() * Chunk.CHUNK_SIZE : 0;
        float baseZ = parentChunk != null ? parentChunk.getChunkZ() * Chunk.CHUNK_SIZE : 0;

        return new float[] {
                baseX + SECTION_SIZE / 2.0f,
                getMinWorldY() + SECTION_SIZE / 2.0f,
                baseZ + SECTION_SIZE / 2.0f
        };
    }

    /**
     * ✅ Calculate memory usage estimate (bytes)
     */
    public long getMemoryUsage() {
        // blocks array: 4096 references (8 bytes each on 64-bit)
        // skyLight array: 4096 bytes
        // blockLight array: 4096 bytes
        // + overhead
        return TOTAL_BLOCKS * 8 + TOTAL_BLOCKS + TOTAL_BLOCKS + 64;
    }

    @Override
    public String toString() {
        int chunkX = parentChunk != null ? parentChunk.getChunkX() : 0;
        int chunkZ = parentChunk != null ? parentChunk.getChunkZ() : 0;

        return String.format("ChunkSection[chunk=%d,%d section=%d worldY=%d-%d blocks=%d empty=%s rebuild=%s light=%s]",
                chunkX, chunkZ, sectionY, getMinWorldY(), getMaxWorldY(),
                nonAirBlockCount, isEmpty, needsGeometryRebuild, needsLightingUpdate);
    }

    /**
     * ✅ Debug: Print lighting info for this section
     */
    public void debugPrintLighting() {
        if (!Settings.DEBUG_MODE)
            return;

        int totalSkyLight = 0;
        int totalBlockLight = 0;
        int maxSky = 0;
        int maxBlock = 0;

        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    int sky = getSkyLight(x, y, z);
                    int block = getBlockLight(x, y, z);
                    totalSkyLight += sky;
                    totalBlockLight += block;
                    maxSky = Math.max(maxSky, sky);
                    maxBlock = Math.max(maxBlock, block);
                }
            }
        }

        float avgSky = totalSkyLight / (float) TOTAL_BLOCKS;
        float avgBlock = totalBlockLight / (float) TOTAL_BLOCKS;

        System.out.printf("[ChunkSection] Section Y=%d: avgSky=%.2f maxSky=%d avgBlock=%.2f maxBlock=%d%n",
                sectionY, avgSky, maxSky, avgBlock, maxBlock);
    }
}