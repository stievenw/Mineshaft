// src/main/java/com/mineshaft/world/Chunk.java
package com.mineshaft.world;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.util.SimplexNoise;

import java.util.Random;

/**
 * ✅ ASYNC-READY Chunk v3.0 - Minecraft-Style Lighting System
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
 * 3. GEOMETRY REBUILD only when:
 * - Block shape changes (place/remove)
 * - Chunk first loads
 * 
 * 4. LIGHTING-ONLY UPDATE when:
 * - Block opacity changes (affects shadow propagation)
 * - Light source placed/removed
 * - But this doesn't require full geometry rebuild!
 * 
 * ============================================================================
 * 
 * ✅ FIXED: Proper Y=-64 to Y=320 support (Minecraft 1.18+ standard)
 * ⚡ Terrain generation can be called from background thread
 */
public class Chunk {
    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_HEIGHT = Settings.WORLD_HEIGHT; // 384 blocks

    // ✅ MINECRAFT-STYLE: Chunk divided into 16×16×16 sections
    public static final int SECTION_COUNT = CHUNK_HEIGHT / ChunkSection.SECTION_SIZE; // 24 sections

    private ChunkSection[] sections; // 24 sections (Y=-64 to Y=320)

    private int chunkX, chunkZ;

    // ========== MINECRAFT-STYLE DIRTY FLAGS ==========
    /**
     * ✅ GEOMETRY REBUILD needed when block SHAPE changes:
     * - Block placed/removed
     * - Initial chunk generation
     * 
     * This triggers full mesh rebuild for affected sections.
     */
    private boolean needsGeometryRebuild = true;

    /**
     * ✅ LIGHTING UPDATE needed when light VALUES change:
     * - Light source placed/removed (torch, glowstone)
     * - Opacity changed (solid block blocking skylight)
     * 
     * This updates light arrays but may not need full mesh rebuild
     * if the lighting system supports dynamic updates.
     * 
     * NOTE: Time-of-day changes do NOT set this flag!
     * Time brightness is applied at render time via glColor.
     */
    private boolean needsLightingUpdate = true;

    // ✅ Async generation support
    private volatile ChunkState state = ChunkState.EMPTY;
    private boolean lightInitialized = false;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.sections = new ChunkSection[SECTION_COUNT];

        // Sections are created lazily during terrain generation
        // Empty sections (all air) are never allocated, saving memory

        // ✅ DON'T generate terrain in constructor!
        // Will be called from background thread via generate()
    }

    // ========== ✅ ASYNC GENERATION ==========

    /**
     * ✅ Generate terrain (safe to call from background thread)
     */
    public void generate() {
        if (state != ChunkState.EMPTY) {
            return; // Already generated or generating
        }

        state = ChunkState.GENERATING;

        try {
            generateTerrain();
            state = ChunkState.GENERATED;

            // ✅ Mark for initial mesh generation
            needsGeometryRebuild = true;
            needsLightingUpdate = true;

        } catch (Exception e) {
            System.err.println("Error generating chunk [" + chunkX + ", " + chunkZ + "]: " + e.getMessage());
            e.printStackTrace();
            state = ChunkState.EMPTY; // Reset on error
        }
    }

    /**
     * ✅ Check if chunk is ready to render
     */
    public boolean isReady() {
        return state == ChunkState.READY;
    }

    /**
     * ✅ Check if terrain is generated
     */
    public boolean isGenerated() {
        return state.ordinal() >= ChunkState.GENERATED.ordinal();
    }

    /**
     * ✅ Get current state
     */
    public ChunkState getState() {
        return state;
    }

    /**
     * ✅ Set state (for lighting system)
     */
    public void setState(ChunkState state) {
        this.state = state;
    }

    // ========== ✅ SECTION MANAGEMENT ==========

    /**
     * Get section index from world Y coordinate
     */
    private int getSectionIndex(int worldY) {
        return (worldY - Settings.WORLD_MIN_Y) / ChunkSection.SECTION_SIZE;
    }

    /**
     * Get section at index, or null if not allocated
     */
    public ChunkSection getSection(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= SECTION_COUNT) {
            return null;
        }
        return sections[sectionIndex];
    }

    /**
     * Get or create section at index (lazy allocation)
     */
    private ChunkSection getOrCreateSection(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= SECTION_COUNT) {
            return null;
        }

        if (sections[sectionIndex] == null) {
            sections[sectionIndex] = new ChunkSection(this, sectionIndex);
        }

        return sections[sectionIndex];
    }

    /**
     * Convert world Y to local section Y coordinate (0-15)
     */
    private int toLocalY(int worldY) {
        return (worldY - Settings.WORLD_MIN_Y) % ChunkSection.SECTION_SIZE;
    }

    // ========== ✅ MESH REBUILD FLAGS (MINECRAFT-STYLE) ==========

    /**
     * ✅ Check if any section needs mesh rebuild
     */
    public boolean needsMeshRebuild() {
        return needsGeometryRebuild || needsLightingUpdate;
    }

    /**
     * ✅ Check if geometry rebuild is needed (block shape changed)
     */
    public boolean needsGeometryRebuild() {
        return needsGeometryRebuild;
    }

    /**
     * ✅ Check if only lighting update is needed (no geometry change)
     * 
     * This is used to optimize: if only lighting changed, we might
     * be able to update without full mesh rebuild in some implementations.
     */
    public boolean needsOnlyLightingUpdate() {
        return needsLightingUpdate && !needsGeometryRebuild;
    }

    /**
     * ✅ Check if lighting update is needed
     */
    public boolean needsLightingUpdate() {
        return needsLightingUpdate;
    }

    /**
     * ✅ Set geometry rebuild flag
     */
    public void setNeedsGeometryRebuild(boolean needs) {
        this.needsGeometryRebuild = needs;

        // If geometry changes, also mark all non-empty sections
        if (needs) {
            for (ChunkSection section : sections) {
                if (section != null && !section.isEmpty()) {
                    section.setNeedsGeometryRebuild(true);
                }
            }
        }
    }

    /**
     * ✅ Set lighting update flag
     * 
     * NOTE: This is for BLOCK lighting changes (torch, shadow propagation),
     * NOT for time-of-day changes! Time brightness is applied at render time.
     */
    public void setNeedsLightingUpdate(boolean needs) {
        this.needsLightingUpdate = needs;

        // Also mark sections
        if (needs) {
            for (ChunkSection section : sections) {
                if (section != null && !section.isEmpty()) {
                    section.setNeedsLightingUpdate(true);
                }
            }
        }
    }

    /**
     * ✅ Clear all rebuild flags (after mesh has been rebuilt)
     */
    public void clearRebuildFlags() {
        this.needsGeometryRebuild = false;
        this.needsLightingUpdate = false;

        for (ChunkSection section : sections) {
            if (section != null) {
                section.setNeedsGeometryRebuild(false);
                section.setNeedsLightingUpdate(false);
            }
        }
    }

    // ========== ✅ TERRAIN GENERATION (FIXED Y RANGE) ==========

    /**
     * ✅ SIMPLIFIED: Smooth, Minecraft-like terrain generation
     * ✅ OPTIMIZED: Uses section-based storage (lazy allocation)
     */
    private void generateTerrain() {
        Random random = new Random(Settings.WORLD_SEED + chunkX * 341873128712L + chunkZ * 132897987541L);

        // Pre-calculate heights for all columns
        int[][] heights = new int[CHUNK_SIZE][CHUNK_SIZE];

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;
                heights[x][z] = getTerrainHeight(worldX, worldZ);
            }
        }

        // ✅ Generate blocks for ENTIRE height range (Y=-64 to Y=319)
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int height = heights[x][z];

                for (int worldY = Settings.WORLD_MIN_Y; worldY <= Settings.WORLD_MAX_Y; worldY++) {
                    GameBlock block;

                    // ✅ Bedrock bottom layer (Y=-64)
                    if (worldY == Settings.BEDROCK_FLOOR) {
                        block = BlockRegistry.BEDROCK;
                    }
                    // ✅ Bedrock transitional layers (Y=-63 to Y=-60)
                    else if (worldY < Settings.BEDROCK_FLOOR + Settings.BEDROCK_LAYERS) {
                        int distanceFromBottom = worldY - Settings.BEDROCK_FLOOR;
                        double bedrockChance = 1.0 - (distanceFromBottom / (double) Settings.BEDROCK_LAYERS);
                        block = random.nextDouble() < bedrockChance ? BlockRegistry.BEDROCK : BlockRegistry.STONE;
                    }
                    // ✅ Above terrain = air
                    else if (worldY > height) {
                        block = BlockRegistry.AIR;
                    }
                    // ✅ Surface block
                    else if (worldY == height) {
                        block = getSurfaceBlock(height, random);
                    }
                    // ✅ Subsurface (3-5 blocks of dirt/sand below surface)
                    else if (worldY > height - 5) {
                        if (height >= Settings.SEA_LEVEL) {
                            block = BlockRegistry.DIRT;
                        } else {
                            block = random.nextDouble() > 0.5 ? BlockRegistry.SAND : BlockRegistry.GRAVEL;
                        }
                    }
                    // ✅ Deep underground = stone with ores
                    else {
                        block = generateOre(worldY, random);
                    }

                    // Set block in appropriate section (only if not air)
                    if (!block.isAir()) {
                        int sectionIndex = getSectionIndex(worldY);
                        ChunkSection section = getOrCreateSection(sectionIndex);

                        if (section != null) {
                            int localY = toLocalY(worldY);
                            section.setBlock(x, localY, z, block);
                        }
                    }
                }

                // ✅ Fill water from terrain surface to sea level
                if (height < Settings.SEA_LEVEL) {
                    for (int worldY = height + 1; worldY <= Settings.SEA_LEVEL; worldY++) {
                        int sectionIndex = getSectionIndex(worldY);
                        ChunkSection section = getOrCreateSection(sectionIndex);

                        if (section != null) {
                            int localY = toLocalY(worldY);
                            section.setBlock(x, localY, z, BlockRegistry.WATER);
                        }
                    }
                }

                // ✅ Trees (sparse, only on land)
                if (height >= Settings.SEA_LEVEL + 1 && height < 85) {
                    if (random.nextDouble() < 0.02) {
                        generateTree(x, height + 1, z, random);
                    }
                }
            }
        }

        generateCaves();
    }

    /**
     * ✅ SIMPLIFIED: Smooth terrain height generation
     */
    private int getTerrainHeight(int worldX, int worldZ) {
        double continentScale = 0.001;
        double continent = SimplexNoise.noise(worldX * continentScale, worldZ * continentScale);

        double terrainScale = 0.005;
        double terrain = SimplexNoise.noise(worldX * terrainScale, worldZ * terrainScale);

        double detailScale = 0.02;
        double detail = SimplexNoise.noise(worldX * detailScale, worldZ * detailScale);

        double combined = continent * 50 + terrain * 20 + detail * 5;

        int baseHeight = Settings.SEA_LEVEL + (int) combined;

        return Math.max(-58, Math.min(180, baseHeight));
    }

    /**
     * ✅ SIMPLIFIED: Get surface block based on height only
     */
    private GameBlock getSurfaceBlock(int height, Random random) {
        if (height < Settings.SEA_LEVEL - 5) {
            return random.nextDouble() > 0.5 ? BlockRegistry.GRAVEL : BlockRegistry.SAND;
        } else if (height < Settings.SEA_LEVEL) {
            return BlockRegistry.SAND;
        } else if (height <= Settings.SEA_LEVEL + 2) {
            return BlockRegistry.SAND;
        } else if (height > 90) {
            return BlockRegistry.STONE;
        } else {
            return BlockRegistry.GRASS_BLOCK;
        }
    }

    /**
     * ✅ FIXED: Ore generation with proper Y distribution (1.18+ style)
     */
    private GameBlock generateOre(int worldY, Random random) {
        // Coal: Y=-64 to Y=136
        if (worldY >= -64 && worldY <= 136 && random.nextDouble() < 0.012) {
            return BlockRegistry.COAL_ORE;
        }

        // Iron: Y=-64 to Y=72
        if (worldY >= -64 && worldY <= 72) {
            double ironChance = 0.008;
            if (worldY >= 0 && worldY <= 32) {
                ironChance = 0.012;
            }
            if (random.nextDouble() < ironChance) {
                return BlockRegistry.IRON_ORE;
            }
        }

        // Gold: Y=-64 to Y=32
        if (worldY >= -64 && worldY <= 32) {
            double goldChance = 0.004;
            if (worldY >= -32 && worldY <= 0) {
                goldChance = 0.006;
            }
            if (random.nextDouble() < goldChance) {
                return BlockRegistry.GOLD_ORE;
            }
        }

        // Diamond: Y=-64 to Y=16
        if (worldY >= -64 && worldY <= 16) {
            double diamondChance = 0.0015;
            if (worldY >= -64 && worldY <= -32) {
                diamondChance = 0.003;
            }
            if (random.nextDouble() < diamondChance) {
                return BlockRegistry.DIAMOND_ORE;
            }
        }

        return BlockRegistry.STONE;
    }

    /**
     * ✅ FIXED: Cave generation from bedrock to sea level
     */
    private void generateCaves() {
        Random random = new Random(Settings.WORLD_SEED + chunkX * 7919L + chunkZ * 5419L);

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;

                int minCaveY = Settings.BEDROCK_FLOOR + Settings.BEDROCK_LAYERS + 1;
                int maxCaveY = Settings.SEA_LEVEL;

                for (int worldY = minCaveY; worldY <= maxCaveY; worldY++) {
                    int index = toIndex(worldY);
                    if (!isValidIndex(index))
                        continue;

                    double caveNoise1 = SimplexNoise.noise(
                            worldX * 0.05,
                            (worldZ + worldY * 100) * 0.05);

                    double caveNoise2 = SimplexNoise.noise(
                            (worldX + worldY * 50) * 0.08,
                            worldZ * 0.08);

                    double combinedNoise = (caveNoise1 + caveNoise2 * 0.5);

                    if (combinedNoise > 0.7) {
                        GameBlock current = getBlock(x, worldY, z);

                        if (current != BlockRegistry.BEDROCK && current != BlockRegistry.WATER) {
                            if (worldY < Settings.SEA_LEVEL - 43 && random.nextDouble() < 0.3) {
                                setBlockInternal(x, worldY, z, BlockRegistry.WATER);
                            } else {
                                setBlockInternal(x, worldY, z, BlockRegistry.AIR);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * ✅ SIMPLIFIED: Generate simple oak tree
     */
    private void generateTree(int x, int worldY, int z, Random random) {
        int height = 5 + random.nextInt(2);

        if (!canPlaceTree(x, worldY, z, height + 4)) {
            return;
        }

        // Trunk
        for (int i = 0; i < height; i++) {
            setBlockInternal(x, worldY + i, z, BlockRegistry.OAK_LOG);
        }

        // Leaves
        int leafY = worldY + height;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < 5) {
                        setBlockSafe(x + dx, leafY + dy, z + dz, BlockRegistry.OAK_LEAVES);
                    }
                }
            }
        }
    }

    private boolean canPlaceTree(int x, int worldY, int z, int totalHeight) {
        if (x < 2 || x > CHUNK_SIZE - 3 || z < 2 || z > CHUNK_SIZE - 3) {
            return false;
        }

        for (int y = 0; y < totalHeight; y++) {
            GameBlock check = getBlock(x, worldY + y, z);
            if (check != null && !check.isAir()) {
                return false;
            }
        }

        return true;
    }

    private void setBlockSafe(int x, int worldY, int z, GameBlock block) {
        if (x >= 0 && x < CHUNK_SIZE && z >= 0 && z < CHUNK_SIZE && Settings.isValidWorldY(worldY)) {
            GameBlock current = getBlock(x, worldY, z);
            if (current != null && current.isAir()) {
                setBlockInternal(x, worldY, z, block);
            }
        }
    }

    /**
     * ✅ Internal block set (during generation, no flag updates)
     */
    private void setBlockInternal(int x, int worldY, int z, GameBlock block) {
        if (!Settings.isValidWorldY(worldY) || x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            return;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getOrCreateSection(sectionIndex);

        if (section != null) {
            int localY = toLocalY(worldY);
            section.setBlock(x, localY, z, block);
        }
    }

    // ========== ✅ COORDINATE CONVERSION ==========

    private int toIndex(int worldY) {
        return Settings.worldYToIndex(worldY);
    }

    private boolean isValidIndex(int index) {
        return index >= 0 && index < CHUNK_HEIGHT;
    }

    // ========== LIGHTING METHODS ==========

    /**
     * ✅ Get skylight level (0-15) at world Y coordinate
     * 
     * NOTE: This returns the STATIC skylight value.
     * It does NOT change with time of day!
     * Time brightness is applied at render time.
     */
    public int getSkyLight(int x, int worldY, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE || !Settings.isValidWorldY(worldY)) {
            return 0;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getSection(sectionIndex);

        if (section == null) {
            // ✅ MINECRAFT-STYLE: Empty sections above terrain have full skylight
            return 15;
        }

        int localY = toLocalY(worldY);
        return section.getSkyLight(x, localY, z);
    }

    /**
     * ✅ Set skylight level at world Y coordinate
     */
    public void setSkyLight(int x, int worldY, int z, int level) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE || !Settings.isValidWorldY(worldY)) {
            return;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getOrCreateSection(sectionIndex);

        if (section != null) {
            int localY = toLocalY(worldY);
            section.setSkyLight(x, localY, z, level);
        }
    }

    /**
     * ✅ Get blocklight level (0-15) at world Y coordinate
     */
    public int getBlockLight(int x, int worldY, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE || !Settings.isValidWorldY(worldY)) {
            return 0;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getSection(sectionIndex);

        if (section == null) {
            return 0; // Empty sections have no block light
        }

        int localY = toLocalY(worldY);
        return section.getBlockLight(x, localY, z);
    }

    /**
     * ✅ Set blocklight level at world Y coordinate
     */
    public void setBlockLight(int x, int worldY, int z, int level) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE || !Settings.isValidWorldY(worldY)) {
            return;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getOrCreateSection(sectionIndex);

        if (section != null) {
            int localY = toLocalY(worldY);
            section.setBlockLight(x, localY, z, level);
        }
    }

    /**
     * ✅ Get combined light (max of skylight and blocklight)
     * 
     * Returns RAW light value (0-15), NOT time-adjusted!
     */
    public int getCombinedLight(int x, int worldY, int z) {
        int sky = getSkyLight(x, worldY, z);
        int block = getBlockLight(x, worldY, z);
        return Math.max(sky, block);
    }

    public boolean isLightInitialized() {
        return lightInitialized;
    }

    public void setLightInitialized(boolean initialized) {
        this.lightInitialized = initialized;

        if (initialized) {
            // ✅ When lighting is initialized, move to LIGHT_PENDING state
            if (state == ChunkState.GENERATED) {
                state = ChunkState.LIGHT_PENDING;
            }
        }
    }

    // ========== BLOCK ACCESS ==========

    /**
     * ✅ Get block at world Y coordinate
     */
    public GameBlock getBlock(int x, int worldY, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE || !Settings.isValidWorldY(worldY)) {
            return null;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getSection(sectionIndex);

        if (section == null) {
            return BlockRegistry.AIR; // Empty sections are all air
        }

        int localY = toLocalY(worldY);
        return section.getBlock(x, localY, z);
    }

    /**
     * ✅ REVISED: Set block with proper Minecraft-style flag handling
     * 
     * This method properly separates:
     * - Geometry changes (block shape changed)
     * - Lighting changes (opacity changed, affecting shadows)
     */
    public void setBlock(int x, int worldY, int z, GameBlock block) {
        if (!Settings.isValidWorldY(worldY) || x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            return;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getOrCreateSection(sectionIndex);

        if (section != null) {
            int localY = toLocalY(worldY);

            // ✅ Get old block to determine what changed
            GameBlock oldBlock = section.getBlock(x, localY, z);

            // Set the new block
            section.setBlock(x, localY, z, block);

            // ========== DETERMINE WHAT CHANGED ==========

            // ✅ Check if block SHAPE changed (different block type)
            boolean shapeChanged = (oldBlock == null && block != null) ||
                    (oldBlock != null && block == null) ||
                    (oldBlock != null && block != null && oldBlock != block);

            // ✅ Check if block OPACITY changed (affects shadow propagation)
            boolean oldBlocksLight = oldBlock != null && !oldBlock.isAir() && oldBlock.isSolid();
            boolean newBlocksLight = block != null && !block.isAir() && block.isSolid();
            boolean opacityChanged = (oldBlocksLight != newBlocksLight);

            // ✅ Check if LIGHT EMISSION changed
            int oldLightLevel = (oldBlock != null) ? oldBlock.getLightLevel() : 0;
            int newLightLevel = (block != null) ? block.getLightLevel() : 0;
            boolean lightEmissionChanged = (oldLightLevel != newLightLevel);

            // ========== SET APPROPRIATE FLAGS ==========

            if (shapeChanged) {
                // ✅ Geometry changed - need mesh rebuild
                section.setNeedsGeometryRebuild(true);
                needsGeometryRebuild = true;
            }

            if (opacityChanged) {
                // ✅ Opacity changed - affects shadow propagation
                section.setNeedsLightingUpdate(true);
                needsLightingUpdate = true;

                // ✅ Mark sections BELOW for shadow update
                // (shadow propagates downward from blocked skylight)
                markSectionsBelowForShadowUpdate(sectionIndex);
            }

            if (lightEmissionChanged) {
                // ✅ Light source placed/removed - need light propagation update
                section.setNeedsLightingUpdate(true);
                needsLightingUpdate = true;
            }
        }
    }

    /**
     * ✅ Mark all sections below for rebuild when shadow/light changes
     * 
     * Called when an opaque block is placed or removed, affecting skylight
     * propagation down the column.
     */
    private void markSectionsBelowForShadowUpdate(int fromSectionIndex) {
        for (int i = fromSectionIndex - 1; i >= 0; i--) {
            ChunkSection belowSection = sections[i];
            if (belowSection != null && !belowSection.isEmpty()) {
                belowSection.setNeedsLightingUpdate(true);
                belowSection.setNeedsGeometryRebuild(true); // Need to update vertex colors
            }
        }
    }

    /**
     * ✅ Get block by array index (for internal use)
     */
    public GameBlock getBlockByIndex(int x, int index, int z) {
        int worldY = Settings.indexToWorldY(index);
        return getBlock(x, worldY, z);
    }

    // ========== GETTERS ==========

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    /**
     * ✅ Get count of allocated (non-empty) sections
     */
    public int getAllocatedSectionCount() {
        int count = 0;
        for (ChunkSection section : sections) {
            if (section != null && !section.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * ✅ Get total block count (approximate, for debugging)
     */
    public int getBlockCount() {
        int count = 0;
        for (ChunkSection section : sections) {
            if (section != null) {
                count += section.getBlockCount();
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return String.format("Chunk[%d, %d] state=%s sections=%d blocks=%d",
                chunkX, chunkZ, state, getAllocatedSectionCount(), getBlockCount());
    }
}