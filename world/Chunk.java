package com.mineshaft.world;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.util.SimplexNoise;

import java.util.Random;

/**
 * ✅ UPDATED v3: Minecraft-style terrain generation
 * 
 * Features:
 * - Proper Y range: -64 to 320
 * - Varied terrain (plains, hills, mountains)
 * - Grass covered hills (not stone)
 * - Natural height variation
 * - Biome-based generation
 * - Accurate bedrock layers
 */
public class Chunk {
    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_HEIGHT = Settings.WORLD_HEIGHT;

    private GameBlock[][][] blocks;
    private byte[][][] skyLight;
    private byte[][][] blockLight;

    private int chunkX, chunkZ;
    private boolean needsRebuild = true;
    private boolean generated = false;
    private boolean lightInitialized = false;

    private int[][] surfaceHeights;
    private double[][] continentalness;
    private double[][] erosion;
    private double[][] peaks;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = new GameBlock[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
        this.skyLight = new byte[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
        this.blockLight = new byte[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
        this.surfaceHeights = new int[CHUNK_SIZE][CHUNK_SIZE];
        this.continentalness = new double[CHUNK_SIZE][CHUNK_SIZE];
        this.erosion = new double[CHUNK_SIZE][CHUNK_SIZE];
        this.peaks = new double[CHUNK_SIZE][CHUNK_SIZE];

        initializeBlocks();
        generateTerrain();
    }

    // ========== COORDINATE CONVERSION ==========

    private int toIndex(int worldY) {
        return worldY - Settings.WORLD_MIN_Y;
    }

    private boolean isValidWorldY(int worldY) {
        return worldY >= Settings.WORLD_MIN_Y && worldY <= Settings.WORLD_MAX_Y;
    }

    private boolean isValidIndex(int index) {
        return index >= 0 && index < CHUNK_HEIGHT;
    }

    private void initializeBlocks() {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_HEIGHT; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    blocks[x][y][z] = BlockRegistry.AIR;
                    skyLight[x][y][z] = 0;
                    blockLight[x][y][z] = 0;
                }
            }
        }
    }

    // ========== MAIN TERRAIN GENERATION ==========

    private void generateTerrain() {
        Random random = new Random(Settings.WORLD_SEED + chunkX * 341873128712L + chunkZ * 132897987541L);

        // Phase 1: Calculate noise maps for entire chunk
        calculateNoiseMaps();

        // Phase 2: Generate base terrain
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                generateColumn(x, z, random);
            }
        }

        // Phase 3: Additional features
        generateOreVeins(random);
        generateCaves(random);
        fixUnderwaterAirPockets();
        generateDecorations(random);

        generated = true;
    }

    /**
     * ✅ NEW: Pre-calculate noise maps for smoother terrain
     */
    private void calculateNoiseMaps() {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;

                // Continentalness: Determines if land or ocean
                // Range: -1 to 1, where -1 = deep ocean, 1 = inland
                continentalness[x][z] = calculateContinentalness(worldX, worldZ);

                // Erosion: Determines terrain roughness
                // Range: 0 to 1, where 0 = flat, 1 = rough/mountainous
                erosion[x][z] = calculateErosion(worldX, worldZ);

                // Peaks and Valleys: Local height variation
                peaks[x][z] = calculatePeaksAndValleys(worldX, worldZ);
            }
        }
    }

    /**
     * ✅ NEW: Continentalness - Large scale land/ocean
     */
    private double calculateContinentalness(int worldX, int worldZ) {
        double n1 = SimplexNoise.noise(worldX * 0.0004, worldZ * 0.0004);
        double n2 = SimplexNoise.noise(worldX * 0.001, worldZ * 0.001) * 0.5;
        double n3 = SimplexNoise.noise(worldX * 0.002, worldZ * 0.002) * 0.25;

        return (n1 + n2 + n3) / 1.75; // Normalize to roughly -1 to 1
    }

    /**
     * ✅ NEW: Erosion - Terrain flatness/roughness
     */
    private double calculateErosion(int worldX, int worldZ) {
        double n1 = SimplexNoise.noise(worldX * 0.003 + 1000, worldZ * 0.003 + 1000);
        double n2 = SimplexNoise.noise(worldX * 0.006 + 1000, worldZ * 0.006 + 1000) * 0.5;

        // Convert to 0-1 range
        return (n1 + n2 + 1.5) / 3.0;
    }

    /**
     * ✅ NEW: Peaks and Valleys - Local height variation
     */
    private double calculatePeaksAndValleys(int worldX, int worldZ) {
        // Multiple octaves for natural looking hills
        double n1 = SimplexNoise.noise(worldX * 0.01, worldZ * 0.01) * 1.0;
        double n2 = SimplexNoise.noise(worldX * 0.02, worldZ * 0.02) * 0.5;
        double n3 = SimplexNoise.noise(worldX * 0.04, worldZ * 0.04) * 0.25;
        double n4 = SimplexNoise.noise(worldX * 0.08, worldZ * 0.08) * 0.125;

        return (n1 + n2 + n3 + n4) / 1.875;
    }

    /**
     * ✅ NEW: Generate single column with varied terrain
     */
    private void generateColumn(int x, int z, Random random) {
        int worldX = chunkX * CHUNK_SIZE + x;
        int worldZ = chunkZ * CHUNK_SIZE + z;

        double continental = continentalness[x][z];
        double erode = erosion[x][z];
        double peak = peaks[x][z];

        // Calculate terrain type and height
        TerrainType terrainType = getTerrainType(continental, erode);
        int height = calculateHeight(continental, erode, peak, terrainType);

        // Clamp height
        height = Math.max(Settings.MIN_TERRAIN_HEIGHT, Math.min(Settings.MAX_TERRAIN_HEIGHT, height));
        surfaceHeights[x][z] = height;

        // Temperature and humidity for biome
        double temperature = getTemperature(worldX, worldZ);
        double humidity = getHumidity(worldX, worldZ);

        // Generate blocks in column
        for (int worldY = Settings.WORLD_MIN_Y; worldY <= Settings.WORLD_MAX_Y; worldY++) {
            int index = toIndex(worldY);
            if (!isValidIndex(index))
                continue;

            GameBlock block = getBlockForPosition(worldY, height, terrainType, temperature, humidity, random);
            blocks[x][index][z] = block;
        }

        // Fill water up to sea level
        if (height < Settings.SEA_LEVEL) {
            for (int worldY = height + 1; worldY <= Settings.SEA_LEVEL; worldY++) {
                int index = toIndex(worldY);
                if (isValidIndex(index)) {
                    blocks[x][index][z] = BlockRegistry.WATER;
                }
            }
        }
    }

    // ========== TERRAIN TYPE SYSTEM ==========

    private enum TerrainType {
        DEEP_OCEAN, // Very deep water
        OCEAN, // Normal ocean
        BEACH, // Sandy beach
        PLAINS, // Flat grassland
        HILLS, // Rolling hills (GRASS covered!)
        PLATEAU, // Flat elevated area
        MOUNTAINS, // High mountains (stone peaks)
        RIVER // River (future use)
    }

    /**
     * ✅ NEW: Determine terrain type from noise
     */
    private TerrainType getTerrainType(double continental, double erosion) {
        // Continental: -1 = ocean, +1 = inland
        if (continental < -0.4) {
            return TerrainType.DEEP_OCEAN;
        } else if (continental < -0.1) {
            return TerrainType.OCEAN;
        } else if (continental < 0.0) {
            return TerrainType.BEACH;
        } else if (continental < 0.3) {
            // Low continentalness = near coast
            if (erosion < 0.4) {
                return TerrainType.PLAINS;
            } else {
                return TerrainType.HILLS;
            }
        } else if (continental < 0.6) {
            // Medium continentalness = inland
            if (erosion < 0.3) {
                return TerrainType.PLAINS;
            } else if (erosion < 0.6) {
                return TerrainType.HILLS;
            } else {
                return TerrainType.PLATEAU;
            }
        } else {
            // High continentalness = deep inland
            if (erosion < 0.4) {
                return TerrainType.HILLS;
            } else if (erosion < 0.7) {
                return TerrainType.PLATEAU;
            } else {
                return TerrainType.MOUNTAINS;
            }
        }
    }

    /**
     * ✅ NEW: Calculate height based on terrain type
     */
    private int calculateHeight(double continental, double erosion, double peaks, TerrainType type) {
        int baseHeight;
        double variation;

        switch (type) {
            case DEEP_OCEAN:
                baseHeight = 30;
                variation = peaks * 8;
                break;
            case OCEAN:
                baseHeight = 45;
                variation = peaks * 10;
                break;
            case BEACH:
                baseHeight = Settings.SEA_LEVEL - 1;
                variation = peaks * 3; // Very flat
                break;
            case PLAINS:
                baseHeight = Settings.SEA_LEVEL + 3;
                variation = peaks * 6; // Gentle rolling
                break;
            case HILLS:
                baseHeight = Settings.SEA_LEVEL + 8;
                variation = peaks * 18; // ✅ Varied hills
                break;
            case PLATEAU:
                baseHeight = Settings.SEA_LEVEL + 20;
                variation = peaks * 8; // Flat top
                break;
            case MOUNTAINS:
                baseHeight = Settings.SEA_LEVEL + 30;
                variation = peaks * 40; // ✅ Only mountains are very high
                break;
            default:
                baseHeight = Settings.SEA_LEVEL;
                variation = 0;
        }

        return baseHeight + (int) variation;
    }

    /**
     * ✅ NEW: Get block for specific position
     */
    private GameBlock getBlockForPosition(int worldY, int surfaceHeight, TerrainType terrainType,
            double temperature, double humidity, Random random) {

        // Bedrock layers (-64 to -60)
        if (worldY <= Settings.BEDROCK_FLOOR + Settings.BEDROCK_LAYERS) {
            return generateBedrockLayer(worldY, random);
        }

        // Above surface = air
        if (worldY > surfaceHeight) {
            return BlockRegistry.AIR;
        }

        // Surface block
        if (worldY == surfaceHeight) {
            return getSurfaceBlock(surfaceHeight, terrainType, temperature, humidity);
        }

        // Subsurface layers
        int depth = surfaceHeight - worldY;

        // Dirt/sand layer (3-4 blocks below surface)
        if (depth <= 3 + random.nextInt(2)) {
            return getSubsurfaceBlock(surfaceHeight, terrainType, temperature);
        }

        // Everything else is stone
        return BlockRegistry.STONE;
    }

    /**
     * ✅ FIXED: Surface block - Hills are GRASS, not stone!
     */
    private GameBlock getSurfaceBlock(int height, TerrainType terrainType,
            double temperature, double humidity) {
        switch (terrainType) {
            case DEEP_OCEAN:
            case OCEAN:
                return BlockRegistry.GRAVEL; // Ocean floor

            case BEACH:
                return BlockRegistry.SAND;

            case PLAINS:
            case HILLS: // ✅ FIXED: Hills are grass!
            case PLATEAU:
                // Biome-based surface
                if (temperature < 0.25) {
                    return BlockRegistry.GRASS_BLOCK; // Could be snow-covered
                } else if (temperature > 0.75 && humidity < 0.3) {
                    return BlockRegistry.SAND; // Desert
                } else {
                    return BlockRegistry.GRASS_BLOCK; // Normal grass
                }

            case MOUNTAINS:
                // ✅ FIXED: Only VERY high mountains have stone surface
                if (height > Settings.MOUNTAIN_START_HEIGHT) {
                    return BlockRegistry.STONE; // Stone peaks only at high altitude
                } else {
                    return BlockRegistry.GRASS_BLOCK; // Lower mountain slopes are grassy
                }

            default:
                return BlockRegistry.GRASS_BLOCK;
        }
    }

    /**
     * ✅ NEW: Subsurface block (below surface)
     */
    private GameBlock getSubsurfaceBlock(int surfaceHeight, TerrainType terrainType, double temperature) {
        switch (terrainType) {
            case DEEP_OCEAN:
            case OCEAN:
                return BlockRegistry.GRAVEL;
            case BEACH:
                return BlockRegistry.SAND;
            case MOUNTAINS:
                if (surfaceHeight > Settings.MOUNTAIN_START_HEIGHT) {
                    return BlockRegistry.STONE;
                }
                // Fall through to dirt
            default:
                return BlockRegistry.DIRT;
        }
    }

    /**
     * ✅ Bedrock layer with Minecraft-style randomness
     */
    private GameBlock generateBedrockLayer(int worldY, Random random) {
        int distanceFromBottom = worldY - Settings.BEDROCK_FLOOR;

        if (distanceFromBottom <= 0) {
            return BlockRegistry.BEDROCK; // Solid floor at -64
        } else {
            double bedrockChance = 1.0 - (distanceFromBottom / (double) Settings.BEDROCK_LAYERS);
            return random.nextDouble() < bedrockChance ? BlockRegistry.BEDROCK : BlockRegistry.STONE;
        }
    }

    // ========== BIOME HELPERS ==========

    private double getTemperature(int worldX, int worldZ) {
        double n1 = SimplexNoise.noise(worldX * 0.0008 + 5000, worldZ * 0.0008 + 5000);
        double n2 = SimplexNoise.noise(worldX * 0.002 + 5000, worldZ * 0.002 + 5000) * 0.3;
        return (n1 + n2 + 1.3) / 2.6; // 0 to 1
    }

    private double getHumidity(int worldX, int worldZ) {
        double n1 = SimplexNoise.noise(worldX * 0.001 + 10000, worldZ * 0.001 + 10000);
        double n2 = SimplexNoise.noise(worldX * 0.003 + 10000, worldZ * 0.003 + 10000) * 0.4;
        return (n1 + n2 + 1.4) / 2.8; // 0 to 1
    }

    // ========== ORE GENERATION ==========

    private void generateOreVeins(Random random) {
        // Coal: Common, high range
        generateOreVein(random, BlockRegistry.COAL_ORE, 20, -64, 192, 8, 17);

        // Iron: Medium
        generateOreVein(random, BlockRegistry.IRON_ORE, 15, -64, 72, 6, 9);

        // Gold: Rare, deep
        generateOreVein(random, BlockRegistry.GOLD_ORE, 6, -64, 32, 4, 9);

        // Diamond: Very rare, very deep
        generateOreVein(random, BlockRegistry.DIAMOND_ORE, 2, -64, 16, 3, 8);
    }

    private void generateOreVein(Random random, GameBlock ore, int attempts,
            int minY, int maxY, int minVeinSize, int maxVeinSize) {
        for (int i = 0; i < attempts; i++) {
            int x = random.nextInt(CHUNK_SIZE);
            int worldY = minY + random.nextInt(Math.max(1, maxY - minY));
            int z = random.nextInt(CHUNK_SIZE);

            if (!isValidWorldY(worldY))
                continue;

            int veinSize = minVeinSize + random.nextInt(Math.max(1, maxVeinSize - minVeinSize + 1));

            for (int j = 0; j < veinSize; j++) {
                int offsetX = x + random.nextInt(3) - 1;
                int offsetIndex = toIndex(worldY) + random.nextInt(3) - 1;
                int offsetZ = z + random.nextInt(3) - 1;

                if (offsetX >= 0 && offsetX < CHUNK_SIZE &&
                        offsetIndex >= 0 && offsetIndex < CHUNK_HEIGHT &&
                        offsetZ >= 0 && offsetZ < CHUNK_SIZE) {

                    if (blocks[offsetX][offsetIndex][offsetZ] == BlockRegistry.STONE) {
                        blocks[offsetX][offsetIndex][offsetZ] = ore;
                    }
                }
            }
        }
    }

    // ========== CAVE GENERATION ==========

    private void generateCaves(Random random) {
        generateWormCaves(random, 3);
        generateCheeseCaves();

        if (random.nextDouble() < 0.02) {
            generateRavine(random);
        }
    }

    private void generateWormCaves(Random random, int attempts) {
        for (int i = 0; i < attempts; i++) {
            int startX = random.nextInt(CHUNK_SIZE);
            int startWorldY = random.nextInt(60) - 20;
            int startZ = random.nextInt(CHUNK_SIZE);

            int length = 20 + random.nextInt(30);

            double angle = random.nextDouble() * Math.PI * 2;
            double pitch = (random.nextDouble() - 0.5) * 0.5;

            double x = startX;
            double y = toIndex(startWorldY);
            double z = startZ;

            for (int step = 0; step < length; step++) {
                angle += (random.nextDouble() - 0.5) * 0.3;
                pitch += (random.nextDouble() - 0.5) * 0.2;
                pitch = Math.max(-0.7, Math.min(0.7, pitch));

                x += Math.cos(angle) * Math.cos(pitch);
                y += Math.sin(pitch);
                z += Math.sin(angle) * Math.cos(pitch);

                int radius = 2 + random.nextInt(2);
                carveSphere((int) x, (int) y, (int) z, radius);
            }
        }
    }

    private void generateCheeseCaves() {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;

                for (int worldY = -60; worldY < 50; worldY++) {
                    int index = toIndex(worldY);
                    if (!isValidIndex(index))
                        continue;

                    double noise1 = SimplexNoise.noise(worldX * 0.05, worldY * 0.1, worldZ * 0.05);
                    double noise2 = SimplexNoise.noise(worldX * 0.08, worldY * 0.08, worldZ * 0.08);

                    double combined = noise1 * 0.7 + noise2 * 0.3;

                    if (combined > 0.6) {
                        GameBlock current = blocks[x][index][z];
                        if (current != BlockRegistry.BEDROCK && current != BlockRegistry.WATER) {
                            blocks[x][index][z] = BlockRegistry.AIR;
                        }
                    }
                }
            }
        }
    }

    private void generateRavine(Random random) {
        int startX = CHUNK_SIZE / 2;
        int startZ = CHUNK_SIZE / 2;
        int startWorldY = 20 + random.nextInt(30);

        double angle = random.nextDouble() * Math.PI * 2;
        int length = 30 + random.nextInt(40);

        double x = startX;
        double z = startZ;

        for (int i = 0; i < length; i++) {
            angle += (random.nextDouble() - 0.5) * 0.2;
            x += Math.cos(angle) * 0.5;
            z += Math.sin(angle) * 0.5;

            int width = 2 + random.nextInt(2);
            int depth = 20 + random.nextInt(30);

            for (int dy = 0; dy < depth; dy++) {
                int index = toIndex(startWorldY - dy);
                if (isValidIndex(index)) {
                    carveSphere((int) x, index, (int) z, width);
                }
            }
        }
    }

    private void carveSphere(int centerX, int centerIndex, int centerZ, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                        int x = centerX + dx;
                        int index = centerIndex + dy;
                        int z = centerZ + dz;

                        if (x >= 0 && x < CHUNK_SIZE &&
                                index >= 0 && index < CHUNK_HEIGHT &&
                                z >= 0 && z < CHUNK_SIZE) {

                            GameBlock current = blocks[x][index][z];
                            if (current != BlockRegistry.BEDROCK &&
                                    current != BlockRegistry.WATER &&
                                    current != BlockRegistry.AIR) {
                                blocks[x][index][z] = BlockRegistry.AIR;
                            }
                        }
                    }
                }
            }
        }
    }

    // ========== WATER FIX ==========

    private void fixUnderwaterAirPockets() {
        int seaLevelIndex = toIndex(Settings.SEA_LEVEL);

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int originalSurface = surfaceHeights[x][z];

                if (originalSurface < Settings.SEA_LEVEL) {
                    boolean foundWaterAbove = true;

                    for (int index = seaLevelIndex; index > 0; index--) {
                        GameBlock current = blocks[x][index][z];

                        if (current == BlockRegistry.WATER) {
                            foundWaterAbove = true;
                        } else if (current == BlockRegistry.AIR && foundWaterAbove) {
                            blocks[x][index][z] = BlockRegistry.WATER;
                        } else if (current != BlockRegistry.AIR && current != BlockRegistry.WATER) {
                            foundWaterAbove = false;
                        }
                    }
                }
            }
        }

        floodFillUnderwaterCaves();
    }

    private void floodFillUnderwaterCaves() {
        int seaLevelIndex = toIndex(Settings.SEA_LEVEL);
        boolean changed = true;
        int iterations = 0;

        while (changed && iterations < 50) {
            changed = false;
            iterations++;

            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    for (int index = 1; index <= seaLevelIndex; index++) {
                        if (blocks[x][index][z] == BlockRegistry.AIR) {
                            if (hasWaterNeighbor(x, index, z)) {
                                blocks[x][index][z] = BlockRegistry.WATER;
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean hasWaterNeighbor(int x, int index, int z) {
        if (index < CHUNK_HEIGHT - 1 && blocks[x][index + 1][z] == BlockRegistry.WATER)
            return true;
        if (index > 0 && blocks[x][index - 1][z] == BlockRegistry.WATER)
            return true;
        if (x > 0 && blocks[x - 1][index][z] == BlockRegistry.WATER)
            return true;
        if (x < CHUNK_SIZE - 1 && blocks[x + 1][index][z] == BlockRegistry.WATER)
            return true;
        if (z > 0 && blocks[x][index][z - 1] == BlockRegistry.WATER)
            return true;
        if (z < CHUNK_SIZE - 1 && blocks[x][index][z + 1] == BlockRegistry.WATER)
            return true;
        return false;
    }

    // ========== DECORATIONS ==========

    private void generateDecorations(Random random) {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;

                int surfaceWorldY = surfaceHeights[x][z];
                int surfaceIndex = toIndex(surfaceWorldY);

                if (surfaceWorldY < Settings.SEA_LEVEL || surfaceWorldY >= Settings.WORLD_MAX_Y - 15)
                    continue;

                int aboveIndex = surfaceIndex + 1;
                if (aboveIndex < CHUNK_HEIGHT && blocks[x][aboveIndex][z] == BlockRegistry.WATER)
                    continue;

                GameBlock surface = blocks[x][surfaceIndex][z];
                double temperature = getTemperature(worldX, worldZ);
                double humidity = getHumidity(worldX, worldZ);

                // Trees on grass
                if (surface == BlockRegistry.GRASS_BLOCK && surfaceWorldY < 100) {
                    double treeChance = getTreeChance(temperature, humidity);
                    if (random.nextDouble() < treeChance) {
                        generateRandomTree(x, surfaceIndex + 1, z, random, temperature, humidity);
                    }
                }

                // Boulders on stone (only on mountain peaks)
                if (surface == BlockRegistry.STONE && surfaceWorldY > Settings.MOUNTAIN_START_HEIGHT) {
                    if (random.nextDouble() < 0.02) {
                        generateBoulder(x, surfaceIndex + 1, z, random);
                    }
                }
            }
        }
    }

    private double getTreeChance(double temperature, double humidity) {
        // Desert = very few trees
        if (temperature > 0.75 && humidity < 0.3)
            return 0.001;

        // Cold = spruce forests (medium density)
        if (temperature < 0.3)
            return 0.012;

        // Humid = dense forest
        if (humidity > 0.6)
            return 0.025;

        // Normal = moderate trees
        return 0.015;
    }

    private void generateRandomTree(int x, int startIndex, int z, Random random,
            double temperature, double humidity) {
        // Cold biome = spruce
        if (temperature < 0.35) {
            generateSpruceTree(x, startIndex, z, random);
        }
        // Humid forest = oak varieties
        else if (humidity > 0.5) {
            if (random.nextInt(10) < 2) {
                generateLargeOakTree(x, startIndex, z, random);
            } else {
                generateOakTree(x, startIndex, z, random);
            }
        }
        // Plains = birch and oak mix
        else if (humidity > 0.3) {
            if (random.nextBoolean()) {
                generateBirchTree(x, startIndex, z, random);
            } else {
                generateOakTree(x, startIndex, z, random);
            }
        }
        // Dry = sparse oak
        else {
            generateOakTree(x, startIndex, z, random);
        }
    }

    // ========== TREE GENERATION ==========

    /**
     * ✅ Minecraft-accurate Oak Tree
     */
    private void generateOakTree(int x, int startIndex, int z, Random random) {
        if (!canPlaceTree(x, startIndex, z, 7))
            return;

        int trunkHeight = 4 + random.nextInt(3);

        // Trunk
        for (int i = 0; i < trunkHeight; i++) {
            setBlockSafe(x, startIndex + i, z, BlockRegistry.OAK_LOG);
        }

        int topIndex = startIndex + trunkHeight - 1;

        // Top leaf
        setLeafSafe(x, topIndex + 1, z, BlockRegistry.OAK_LEAVES);

        // Upper canopy (3x3)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) == 1 && Math.abs(dz) == 1 && random.nextBoolean())
                    continue;
                setLeafSafe(x + dx, topIndex, z + dz, BlockRegistry.OAK_LEAVES);
            }
        }

        // Main canopy (5x5 minus corners)
        for (int layer = 1; layer <= 2; layer++) {
            int layerIndex = topIndex - layer;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2)
                        continue;
                    if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && random.nextInt(3) == 0)
                        continue;
                    setLeafSafe(x + dx, layerIndex, z + dz, BlockRegistry.OAK_LEAVES);
                }
            }
        }

        // Bottom fringe
        int bottomIndex = topIndex - 3;
        if (bottomIndex > startIndex) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (random.nextInt(3) != 0) {
                        setLeafSafe(x + dx, bottomIndex, z + dz, BlockRegistry.OAK_LEAVES);
                    }
                }
            }
        }
    }

    /**
     * Large Oak Tree (2x2 trunk)
     */
    private void generateLargeOakTree(int x, int startIndex, int z, Random random) {
        if (!canPlaceTree(x, startIndex, z, 10))
            return;

        int trunkHeight = 6 + random.nextInt(3);

        // 2x2 Trunk
        for (int i = 0; i < trunkHeight; i++) {
            setBlockSafe(x, startIndex + i, z, BlockRegistry.OAK_LOG);
            setBlockSafe(x + 1, startIndex + i, z, BlockRegistry.OAK_LOG);
            setBlockSafe(x, startIndex + i, z + 1, BlockRegistry.OAK_LOG);
            setBlockSafe(x + 1, startIndex + i, z + 1, BlockRegistry.OAK_LOG);
        }

        int topIndex = startIndex + trunkHeight;

        // Large spherical canopy
        for (int dy = -4; dy <= 2; dy++) {
            int radius = (dy == 2 || dy == -4) ? 1 : (dy == 1 || dy == -3) ? 2 : 3;

            for (int dx = -radius; dx <= radius + 1; dx++) {
                for (int dz = -radius; dz <= radius + 1; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) <= radius + 0.5) {
                        setLeafSafe(x + dx, topIndex + dy, z + dz, BlockRegistry.OAK_LEAVES);
                    }
                }
            }
        }
    }

    /**
     * Birch Tree
     */
    private void generateBirchTree(int x, int startIndex, int z, Random random) {
        if (!canPlaceTree(x, startIndex, z, 8))
            return;

        int trunkHeight = 5 + random.nextInt(3);

        for (int i = 0; i < trunkHeight; i++) {
            setBlockSafe(x, startIndex + i, z, BlockRegistry.OAK_LOG);
        }

        int topIndex = startIndex + trunkHeight - 1;

        setLeafSafe(x, topIndex + 1, z, BlockRegistry.OAK_LEAVES);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) == 1 && Math.abs(dz) == 1 && random.nextBoolean())
                    continue;
                setLeafSafe(x + dx, topIndex, z + dz, BlockRegistry.OAK_LEAVES);
            }
        }

        for (int layer = 1; layer <= 2; layer++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2)
                        continue;
                    if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && random.nextInt(4) == 0)
                        continue;
                    setLeafSafe(x + dx, topIndex - layer, z + dz, BlockRegistry.OAK_LEAVES);
                }
            }
        }
    }

    /**
     * Spruce Tree (conical)
     */
    private void generateSpruceTree(int x, int startIndex, int z, Random random) {
        if (!canPlaceTree(x, startIndex, z, 12))
            return;

        int trunkHeight = 7 + random.nextInt(5);

        for (int i = 0; i < trunkHeight; i++) {
            setBlockSafe(x, startIndex + i, z, BlockRegistry.OAK_LOG);
        }

        int topIndex = startIndex + trunkHeight;

        setLeafSafe(x, topIndex, z, BlockRegistry.OAK_LEAVES);
        setLeafSafe(x, topIndex + 1, z, BlockRegistry.OAK_LEAVES);

        int leafLayers = trunkHeight - 2;
        for (int layer = 0; layer < leafLayers; layer++) {
            int layerIndex = topIndex - 1 - layer;
            int radius = (layer < 2) ? 1 : (layer < 4) ? 2 : 3;
            if (layer % 2 == 1)
                radius = Math.max(1, radius - 1);

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= radius + 1) {
                        if (!(dx == 0 && dz == 0)) {
                            setLeafSafe(x + dx, layerIndex, z + dz, BlockRegistry.OAK_LEAVES);
                        }
                    }
                }
            }
        }
    }

    private boolean canPlaceTree(int x, int startIndex, int z, int height) {
        for (int i = 1; i < height; i++) {
            int checkIndex = startIndex + i;
            if (checkIndex >= CHUNK_HEIGHT)
                return false;

            GameBlock block = blocks[x][checkIndex][z];
            if (block != BlockRegistry.AIR && block != BlockRegistry.OAK_LEAVES) {
                return false;
            }
        }

        if (x < 2 || x > CHUNK_SIZE - 3 || z < 2 || z > CHUNK_SIZE - 3) {
            return false;
        }

        return true;
    }

    private void generateBoulder(int x, int startIndex, int z, Random random) {
        int size = 1 + random.nextInt(2);

        for (int dx = -size; dx <= size; dx++) {
            for (int dy = 0; dy <= size; dy++) {
                for (int dz = -size; dz <= size; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= size * size) {
                        setBlockSafe(x + dx, startIndex + dy, z + dz,
                                random.nextDouble() < 0.7 ? BlockRegistry.STONE : BlockRegistry.COBBLESTONE);
                    }
                }
            }
        }
    }

    // ========== HELPER METHODS ==========

    private void setBlockSafe(int x, int index, int z, GameBlock block) {
        if (x >= 0 && x < CHUNK_SIZE && index >= 0 && index < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            GameBlock current = blocks[x][index][z];
            if (current.isAir() || current == BlockRegistry.WATER) {
                blocks[x][index][z] = block;
            }
        }
    }

    private void setLeafSafe(int x, int index, int z, GameBlock leaf) {
        if (x >= 0 && x < CHUNK_SIZE && index >= 0 && index < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            GameBlock current = blocks[x][index][z];
            if (current == BlockRegistry.AIR) {
                blocks[x][index][z] = leaf;
            }
        }
    }

    // ========== LIGHTING ==========

    public int getSkyLight(int x, int y, int z) {
        int index = toIndex(y);
        if (x < 0 || x >= CHUNK_SIZE || index < 0 || index >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return 0;
        }
        return skyLight[x][index][z] & 0xFF;
    }

    public void setSkyLight(int x, int y, int z, int level) {
        int index = toIndex(y);
        if (x < 0 || x >= CHUNK_SIZE || index < 0 || index >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return;
        }
        skyLight[x][index][z] = (byte) Math.max(0, Math.min(15, level));
    }

    public int getBlockLight(int x, int y, int z) {
        int index = toIndex(y);
        if (x < 0 || x >= CHUNK_SIZE || index < 0 || index >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return 0;
        }
        return blockLight[x][index][z] & 0xFF;
    }

    public void setBlockLight(int x, int y, int z, int level) {
        int index = toIndex(y);
        if (x < 0 || x >= CHUNK_SIZE || index < 0 || index >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return;
        }
        blockLight[x][index][z] = (byte) Math.max(0, Math.min(15, level));
    }

    public boolean isLightInitialized() {
        return lightInitialized;
    }

    public void setLightInitialized(boolean initialized) {
        this.lightInitialized = initialized;
    }

    // ========== PUBLIC BLOCK ACCESS ==========

    public GameBlock getBlock(int x, int worldY, int z) {
        int index = toIndex(worldY);
        if (x < 0 || x >= CHUNK_SIZE || index < 0 || index >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return null;
        }
        return blocks[x][index][z];
    }

    public void setBlock(int x, int worldY, int z, GameBlock block) {
        int index = toIndex(worldY);
        if (x >= 0 && x < CHUNK_SIZE && index >= 0 && index < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            blocks[x][index][z] = block;
            needsRebuild = true;
        }
    }

    public GameBlock getBlockByIndex(int x, int index, int z) {
        if (x < 0 || x >= CHUNK_SIZE || index < 0 || index >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return null;
        }
        return blocks[x][index][z];
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public boolean needsRebuild() {
        return needsRebuild;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setNeedsRebuild(boolean needsRebuild) {
        this.needsRebuild = needsRebuild;
    }
}