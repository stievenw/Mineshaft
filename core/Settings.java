// src/main/java/com/mineshaft/core/Settings.java
package com.mineshaft.core;

/**
 * ✅ COMPLETE v4 - Minecraft Java Edition Accurate Settings
 * 
 * Mengikuti aturan MC Java Edition:
 * - Simulation distance minimum 5, maximum = render distance
 * - Entity visibility dengan hard limit 128 blocks horizontal
 * - Proper chunk loading prioritization
 */
public class Settings {

    // ================================================================
    // VERSION & WINDOW
    // ================================================================

    public static final String VERSION = "Alpha v0.9.1 - Performance Update";
    public static final String TITLE = "Mineshaft";
    public static final String FULL_TITLE = TITLE + " " + VERSION;

    public static int WINDOW_WIDTH = 1280;
    public static int WINDOW_HEIGHT = 720;
    public static boolean VSYNC = false;
    public static final int TARGET_FPS = 0; // 0 = unlimited
    public static final boolean SHOW_FPS = true;

    // ================================================================
    // WORLD & TERRAIN (Minecraft Overworld Style)
    // ================================================================

    // ✅ Minecraft-accurate Y range
    public static final int WORLD_MIN_Y = -64; // Bedrock floor
    public static final int WORLD_MAX_Y = 319; // Build limit (inclusive)
    public static final int WORLD_HEIGHT = 384; // Total: 319 - (-64) + 1 = 384

    // ✅ Sea level & terrain
    public static final int SEA_LEVEL = 63; // Minecraft standard
    public static final int SURFACE_LEVEL = 64; // Average surface

    // ✅ Bedrock generation
    public static final int BEDROCK_FLOOR = -64; // Absolute bottom
    public static final int BEDROCK_LAYERS = 5; // -64 to -60 (random pattern)

    // World generation
    public static final long WORLD_SEED = 12345678L;
    public static final int CHUNK_SIZE = 16;

    // ✅ Terrain noise parameters
    public static final double CONTINENT_SCALE = 0.0005;
    public static final double TERRAIN_SCALE = 0.008;
    public static final double DETAIL_SCALE = 0.03;
    public static final double EROSION_SCALE = 0.004;

    // ✅ Height limits
    public static final int MIN_TERRAIN_HEIGHT = 50;
    public static final int MAX_TERRAIN_HEIGHT = 120;
    public static final int MOUNTAIN_START_HEIGHT = 95;
    public static final int SNOW_HEIGHT = 110;

    // ================================================================
    // ✅ NEW: RENDER & SIMULATION DISTANCE (MC Java Edition Rules)
    // ================================================================

    /**
     * Render Distance: Chunks yang di-render (visual only)
     * - Minimum: 2 chunks
     * - Maximum: 32 chunks (64-bit Java with 1GB+ RAM) or 16 chunks (otherwise)
     * - Default: 12 chunks
     */
    public static int RENDER_DISTANCE = 12;

    /**
     * Simulation Distance: Chunks yang di-tick (entities, redstone, crops, etc)
     * - Minimum: 5 chunks (MC Java Edition rule)
     * - Maximum: sama dengan render distance
     * - Jika render distance < simulation distance, gunakan render distance
     * - Default: 8 chunks (lebih rendah dari render untuk performa)
     */
    public static int SIMULATION_DISTANCE = 8;

    /**
     * ✅ Simulation Distance Constants
     */
    public static final int MIN_SIMULATION_DISTANCE = 5; // MC Java Edition minimum
    public static final int MIN_RENDER_DISTANCE = 2;
    public static final int MAX_RENDER_DISTANCE_64BIT = 32;
    public static final int MAX_RENDER_DISTANCE_32BIT = 16;

    // ================================================================
    // ✅ NEW: ENTITY VISIBILITY (MC Java Edition Rules)
    // ================================================================

    /**
     * Hard visibility limit: 128 blocks horizontal (circular column)
     * Entities beyond this are NEVER rendered regardless of other settings
     */
    public static final int ENTITY_HARD_LIMIT_HORIZONTAL = 128;

    /**
     * Entity distance percentage: 0.0 to 1.0 (100%)
     * Controls the sphere radius for entity visibility
     */
    public static float ENTITY_DISTANCE_PERCENTAGE = 1.0f;

    /**
     * Magic numbers for different entity types
     * Sphere radius = render_distance * 16 * multiplier *
     * entity_distance_percentage
     */
    public static final float MOB_VISIBILITY_MULTIPLIER = 8.5f;
    public static final float ITEM_VISIBILITY_MULTIPLIER = 2.0f;
    public static final float SPIDER_VISIBILITY_MULTIPLIER = 10.0f;
    public static final float GHAST_VISIBILITY_MULTIPLIER = 12.0f;
    public static final float PLAYER_VISIBILITY_MULTIPLIER = 10.0f;

    // ================================================================
    // ✅ NEW: CHUNK LOADING PERFORMANCE
    // ================================================================

    /**
     * Maximum chunks to load per frame (prevents lag spikes)
     */
    public static final int MAX_CHUNKS_LOAD_PER_FRAME = 2;

    /**
     * Maximum chunks to unload per frame
     */
    public static final int MAX_CHUNKS_UNLOAD_PER_FRAME = 4;

    /**
     * Maximum mesh builds per frame (VBO creation)
     */
    public static final int MAX_MESH_BUILDS_PER_FRAME = 3;

    /**
     * Maximum VBO uploads per frame (GPU operations)
     */
    public static final int MAX_VBO_UPLOADS_PER_FRAME = 5;

    /**
     * Chunk unload buffer (extra distance before unloading)
     */
    public static final int CHUNK_UNLOAD_BUFFER = 2;

    /**
     * Priority loading: closer chunks load first
     */
    public static final boolean PRIORITY_CHUNK_LOADING = true;

    // ================================================================
    // CAMERA & MOVEMENT
    // ================================================================

    public static final float FOV = 70.0f;
    public static final float NEAR_PLANE = 0.1f;
    public static final float FAR_PLANE = 512.0f; // Increased for larger render distance

    public static final float MOUSE_SENSITIVITY = 0.15f;

    public static final float WALK_SPEED = 4.317f;
    public static final float SPRINT_SPEED = 5.612f;
    public static final float FLY_SPEED = 10.92f;
    public static final float SNEAK_SPEED = 1.295f;

    // ================================================================
    // CAMERA STABILIZER
    // ================================================================

    public static boolean CAMERA_SMOOTHING = true;
    public static final float CAMERA_SMOOTH_SPEED = 20.0f;
    public static final float CAMERA_Y_SMOOTH_MULTIPLIER = 0.7f;
    public static final float CAMERA_MIN_MOVE_THRESHOLD = 0.001f;

    // ================================================================
    // RENDERING & GRAPHICS
    // ================================================================

    public static final boolean ENABLE_FOG = true;
    public static final float FOG_DENSITY = 0.012f;
    public static final boolean FRUSTUM_CULLING = true;
    public static final boolean BACKFACE_CULLING = true;
    public static final int CHUNK_UPDATES_PER_FRAME = 4;
    public static final boolean REBUILD_DIRTY_CHUNKS = true;

    // ================================================================
    // LIGHTING & BRIGHTNESS
    // ================================================================

    public static final float GAMMA = 1.2f;
    public static final float BRIGHTNESS_BOOST = 0.15f;
    public static final float MIN_BRIGHTNESS = 0.35f;
    public static final boolean SMOOTH_LIGHTING = true;
    public static final boolean AMBIENT_OCCLUSION = true;
    public static final int MAX_LIGHT_UPDATES_PER_FRAME = 1000;

    // ================================================================
    // PHYSICS & GAMEPLAY
    // ================================================================

    public static final int TARGET_TPS = 20;
    public static final float GRAVITY = -0.08f;
    public static final float TERMINAL_VELOCITY = -3.92f;
    public static final float JUMP_STRENGTH = 0.50f;
    public static final float PLAYER_HEIGHT = 1.8f;
    public static final float PLAYER_WIDTH = 0.6f;
    public static final float EYE_HEIGHT = 1.62f;
    public static final float STEP_HEIGHT = 0.6f;

    public static final float WATER_GRAVITY = -0.04f;
    public static final float WATER_TERMINAL_VELOCITY = -0.12f;
    public static final float WATER_SWIM_SPEED = 0.20f;

    public static final float CREATIVE_REACH = 5.0f;
    public static final float SURVIVAL_REACH = 4.5f;

    // ================================================================
    // PERFORMANCE
    // ================================================================

    public static final int MAX_LOADED_CHUNKS = 2048; // Increased for larger distances
    public static final boolean AGGRESSIVE_CHUNK_UNLOAD = false;
    public static final boolean MULTITHREADED_CHUNK_LOADING = true;
    public static final int CHUNK_LOAD_THREADS = 4;
    public static final int MESH_BUILD_THREADS = 2;
    public static final boolean USE_VBO = true;
    public static final boolean DYNAMIC_VBO_UPDATES = true;

    // ================================================================
    // TEXTURE SETTINGS
    // ================================================================

    public static final boolean TEXTURE_FILTERING = false;
    public static final boolean MIPMAPS_ENABLED = false;
    public static final int ANISOTROPIC_FILTERING = 1;

    // ================================================================
    // DEBUG
    // ================================================================

    public static boolean DEBUG_MODE = false;
    public static boolean DEBUG_CHUNK_LOADING = false;
    public static boolean DEBUG_LIGHTING = false;
    public static boolean DEBUG_PHYSICS = false;
    public static boolean DEBUG_CAMERA = false;
    public static boolean DEBUG_ENTITY_VISIBILITY = false;
    public static boolean SHOW_CHUNK_BORDERS = false;
    public static boolean SHOW_COLLISION_BOXES = false;
    public static boolean SHOW_SIMULATION_BORDER = false;
    public static boolean LOG_PERFORMANCE = false;

    // ================================================================
    // CONSTANTS
    // ================================================================

    public static final int BLOCK_AIR = 0;
    public static final int BLOCK_STONE = 1;
    public static final int BLOCK_GRASS = 2;
    public static final int BLOCK_DIRT = 3;
    public static final int BLOCK_WATER = 8;

    public static final int MAX_LIGHT_LEVEL = 15;
    public static final int MIN_LIGHT_LEVEL = 0;
    public static final int SUNLIGHT_LEVEL = 15;

    public static final int TICKS_PER_DAY = 24000;

    public static final float PI = 3.14159265359f;
    public static final float DEG_TO_RAD = PI / 180.0f;
    public static final float RAD_TO_DEG = 180.0f / PI;

    // ================================================================
    // ✅ COORDINATE CONVERSION METHODS
    // ================================================================

    /**
     * Convert world Y coordinate to array index
     * Example: Y=-64 -> 0, Y=0 -> 64, Y=319 -> 383
     */
    public static int worldYToIndex(int worldY) {
        return worldY - WORLD_MIN_Y;
    }

    /**
     * Convert array index to world Y coordinate
     * Example: index=0 -> Y=-64, index=64 -> Y=0, index=383 -> Y=319
     */
    public static int indexToWorldY(int index) {
        return index + WORLD_MIN_Y;
    }

    /**
     * Check if world Y coordinate is valid
     */
    public static boolean isValidWorldY(int worldY) {
        return worldY >= WORLD_MIN_Y && worldY <= WORLD_MAX_Y;
    }

    /**
     * Check if array index is valid
     */
    public static boolean isValidIndex(int index) {
        return index >= 0 && index < WORLD_HEIGHT;
    }

    /**
     * Clamp world Y to valid range
     */
    public static int clampWorldY(int worldY) {
        return Math.max(WORLD_MIN_Y, Math.min(WORLD_MAX_Y, worldY));
    }

    // ================================================================
    // ✅ DISTANCE VALIDATION METHODS (MC Java Edition Rules)
    // ================================================================

    /**
     * Validate and correct render/simulation distances
     * Called on startup and when settings change
     * 
     * Rules from MC Java Edition:
     * 1. Simulation distance minimum = 5
     * 2. Simulation distance maximum = render distance
     * 3. If render < simulation, use render for both
     */
    public static void validateDistances() {
        // Detect 64-bit Java
        boolean is64Bit = System.getProperty("os.arch").contains("64") ||
                System.getProperty("sun.arch.data.model", "32").equals("64");

        int maxRender = is64Bit ? MAX_RENDER_DISTANCE_64BIT : MAX_RENDER_DISTANCE_32BIT;

        // Clamp render distance
        RENDER_DISTANCE = Math.max(MIN_RENDER_DISTANCE, Math.min(maxRender, RENDER_DISTANCE));

        // Simulation distance: minimum 5, maximum = render distance
        SIMULATION_DISTANCE = Math.max(MIN_SIMULATION_DISTANCE, SIMULATION_DISTANCE);

        // If render < simulation, use render
        if (RENDER_DISTANCE < SIMULATION_DISTANCE) {
            SIMULATION_DISTANCE = RENDER_DISTANCE;
        }

        if (DEBUG_MODE) {
            System.out.println("[Settings] Distances validated:");
            System.out.println("  - Render Distance: " + RENDER_DISTANCE + " chunks");
            System.out.println("  - Simulation Distance: " + SIMULATION_DISTANCE + " chunks");
            System.out.println("  - Max Render (64-bit: " + is64Bit + "): " + maxRender + " chunks");
        }
    }

    /**
     * Set render distance with validation
     */
    public static void setRenderDistance(int distance) {
        RENDER_DISTANCE = distance;
        validateDistances();
    }

    /**
     * Set simulation distance with validation
     */
    public static void setSimulationDistance(int distance) {
        SIMULATION_DISTANCE = distance;
        validateDistances();
    }

    /**
     * Get effective simulation distance (considering render distance)
     */
    public static int getEffectiveSimulationDistance() {
        return Math.min(SIMULATION_DISTANCE, RENDER_DISTANCE);
    }

    // ================================================================
    // ✅ CHUNK DISTANCE METHODS
    // ================================================================

    /**
     * Check if chunk is within render distance (square region)
     */
    public static boolean isInRenderDistance(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ) {
        int dx = Math.abs(chunkX - playerChunkX);
        int dz = Math.abs(chunkZ - playerChunkZ);
        return dx <= RENDER_DISTANCE && dz <= RENDER_DISTANCE;
    }

    /**
     * Check if chunk is within simulation distance (square region)
     * Used for entity ticking, redstone, crop growth, etc.
     */
    public static boolean isInSimulationDistance(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ) {
        int dx = Math.abs(chunkX - playerChunkX);
        int dz = Math.abs(chunkZ - playerChunkZ);
        int simDist = getEffectiveSimulationDistance();
        return dx <= simDist && dz <= simDist;
    }

    /**
     * Check if chunk is in the "border" region (one chunk outside simulation)
     * In MC, this region has limited ticking: redstone, fluids, crops only
     */
    public static boolean isInSimulationBorder(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ) {
        int dx = Math.abs(chunkX - playerChunkX);
        int dz = Math.abs(chunkZ - playerChunkZ);
        int simDist = getEffectiveSimulationDistance();

        // In simulation border = exactly one chunk outside simulation distance
        boolean outsideSim = dx > simDist || dz > simDist;
        boolean insideBorder = dx <= simDist + 1 && dz <= simDist + 1;

        return outsideSim && insideBorder;
    }

    /**
     * Check if chunk should be unloaded
     */
    public static boolean shouldUnloadChunk(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ) {
        int dx = Math.abs(chunkX - playerChunkX);
        int dz = Math.abs(chunkZ - playerChunkZ);
        return dx > RENDER_DISTANCE + CHUNK_UNLOAD_BUFFER ||
                dz > RENDER_DISTANCE + CHUNK_UNLOAD_BUFFER;
    }

    /**
     * Get chunk priority for loading (lower = higher priority)
     * Returns squared distance from player chunk
     */
    public static double getChunkLoadPriority(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ) {
        int dx = chunkX - playerChunkX;
        int dz = chunkZ - playerChunkZ;
        return dx * dx + dz * dz;
    }

    // ================================================================
    // ✅ ENTITY VISIBILITY METHODS (MC Java Edition Rules)
    // ================================================================

    /**
     * Check if entity is visible based on MC Java Edition rules
     * 
     * Three criteria:
     * 1. Server broadcast range (handled by server, not here)
     * 2. Sphere visibility: render_distance * 16 * multiplier *
     * entity_distance_percentage
     * 3. Hard limit: 128 blocks horizontal (circular column)
     * 
     * Entity is visible only if inside intersection of sphere and cylinder
     * 
     * @param entityX              Entity world X
     * @param entityY              Entity world Y
     * @param entityZ              Entity world Z
     * @param playerX              Player world X
     * @param playerY              Player world Y
     * @param playerZ              Player world Z
     * @param visibilityMultiplier Entity type multiplier (e.g., 8.5 for mobs)
     * @return true if entity should be rendered
     */
    public static boolean isEntityVisible(float entityX, float entityY, float entityZ,
            float playerX, float playerY, float playerZ,
            float visibilityMultiplier) {
        float dx = entityX - playerX;
        float dy = entityY - playerY;
        float dz = entityZ - playerZ;

        // ✅ Check 1: Hard horizontal limit (128 blocks circular column)
        float horizontalDistSq = dx * dx + dz * dz;
        if (horizontalDistSq > ENTITY_HARD_LIMIT_HORIZONTAL * ENTITY_HARD_LIMIT_HORIZONTAL) {
            return false;
        }

        // ✅ Check 2: Sphere visibility based on render distance
        // Radius = render_distance (chunks) * 16 (blocks/chunk) * multiplier *
        // percentage
        float sphereRadius = RENDER_DISTANCE * CHUNK_SIZE * visibilityMultiplier * ENTITY_DISTANCE_PERCENTAGE;
        float distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > sphereRadius * sphereRadius) {
            return false;
        }

        return true;
    }

    /**
     * Check if mob (generic) is visible
     */
    public static boolean isMobVisible(float entityX, float entityY, float entityZ,
            float playerX, float playerY, float playerZ) {
        return isEntityVisible(entityX, entityY, entityZ, playerX, playerY, playerZ, MOB_VISIBILITY_MULTIPLIER);
    }

    /**
     * Check if item entity is visible
     */
    public static boolean isItemVisible(float entityX, float entityY, float entityZ,
            float playerX, float playerY, float playerZ) {
        return isEntityVisible(entityX, entityY, entityZ, playerX, playerY, playerZ, ITEM_VISIBILITY_MULTIPLIER);
    }

    /**
     * Get visibility radius for entity type (in blocks)
     */
    public static float getEntityVisibilityRadius(float visibilityMultiplier) {
        return RENDER_DISTANCE * CHUNK_SIZE * visibilityMultiplier * ENTITY_DISTANCE_PERCENTAGE;
    }

    /**
     * Set entity distance percentage (video setting: Entity Distance)
     * 
     * @param percentage 0.5 to 5.0 (50% to 500%)
     */
    public static void setEntityDistancePercentage(float percentage) {
        ENTITY_DISTANCE_PERCENTAGE = Math.max(0.5f, Math.min(5.0f, percentage));
    }

    // ================================================================
    // ✅ PERFORMANCE HELPER METHODS
    // ================================================================

    /**
     * Get total chunks in render distance (square region)
     */
    public static int getTotalRenderChunks() {
        int diameter = RENDER_DISTANCE * 2 + 1;
        return diameter * diameter;
    }

    /**
     * Get total chunks in simulation distance (square region)
     */
    public static int getTotalSimulationChunks() {
        int simDist = getEffectiveSimulationDistance();
        int diameter = simDist * 2 + 1;
        return diameter * diameter;
    }

    /**
     * Estimate memory usage for chunks (rough estimate)
     * Each chunk: ~380KB for blocks + lighting data
     */
    public static long estimateChunkMemoryUsage() {
        int totalChunks = getTotalRenderChunks();
        long bytesPerChunk = (long) CHUNK_SIZE * WORLD_HEIGHT * CHUNK_SIZE * 2; // blocks + lighting
        return totalChunks * bytesPerChunk;
    }

    /**
     * Get recommended render distance based on available memory
     */
    public static int getRecommendedRenderDistance() {
        long maxMemory = Runtime.getRuntime().maxMemory();

        if (maxMemory >= 4L * 1024 * 1024 * 1024) { // 4GB+
            return 16;
        } else if (maxMemory >= 2L * 1024 * 1024 * 1024) { // 2GB+
            return 12;
        } else if (maxMemory >= 1L * 1024 * 1024 * 1024) { // 1GB+
            return 8;
        } else {
            return 6;
        }
    }

    // ================================================================
    // ✅ WINDOW & SETTINGS METHODS
    // ================================================================

    public static void updateWindowSize(int width, int height) {
        WINDOW_WIDTH = width;
        WINDOW_HEIGHT = height;
    }

    public static void toggleVSync() {
        VSYNC = !VSYNC;
        System.out.println("VSync: " + (VSYNC ? "ON" : "OFF"));
    }

    public static void toggleCameraSmoothing() {
        CAMERA_SMOOTHING = !CAMERA_SMOOTHING;
        System.out.println("Camera Smoothing: " + (CAMERA_SMOOTHING ? "ON" : "OFF"));
    }

    public static void toggleDebugMode() {
        DEBUG_MODE = !DEBUG_MODE;
        System.out.println("Debug Mode: " + (DEBUG_MODE ? "ON" : "OFF"));
    }

    /**
     * Initialize settings on startup
     */
    public static void initialize() {
        validateDistances();

        System.out.println("=== " + FULL_TITLE + " ===");
        System.out
                .println("World Height: Y=" + WORLD_MIN_Y + " to Y=" + WORLD_MAX_Y + " (" + WORLD_HEIGHT + " blocks)");
        System.out.println("Render Distance: " + RENDER_DISTANCE + " chunks");
        System.out.println("Simulation Distance: " + getEffectiveSimulationDistance() + " chunks");
        System.out.println("Total Render Chunks: " + getTotalRenderChunks());
        System.out.println("Estimated Memory: " + (estimateChunkMemoryUsage() / 1024 / 1024) + " MB");
        System.out.println("Recommended Render Distance: " + getRecommendedRenderDistance() + " chunks");
    }

    // ================================================================
    // ✅ PRESETS
    // ================================================================

    /**
     * Apply low performance preset
     */
    public static void applyLowPreset() {
        RENDER_DISTANCE = 6;
        SIMULATION_DISTANCE = 5;
        validateDistances();
        System.out.println("Applied LOW performance preset");
    }

    /**
     * Apply medium performance preset
     */
    public static void applyMediumPreset() {
        RENDER_DISTANCE = 10;
        SIMULATION_DISTANCE = 8;
        validateDistances();
        System.out.println("Applied MEDIUM performance preset");
    }

    /**
     * Apply high performance preset
     */
    public static void applyHighPreset() {
        RENDER_DISTANCE = 16;
        SIMULATION_DISTANCE = 10;
        validateDistances();
        System.out.println("Applied HIGH performance preset");
    }

    /**
     * Apply extreme performance preset (high-end PCs)
     */
    public static void applyExtremePreset() {
        RENDER_DISTANCE = 24;
        SIMULATION_DISTANCE = 12;
        validateDistances();
        System.out.println("Applied EXTREME performance preset");
    }

    // ================================================================
    // PRIVATE CONSTRUCTOR
    // ================================================================

    private Settings() {
        throw new AssertionError("Settings class cannot be instantiated");
    }
}