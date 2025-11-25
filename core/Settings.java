package com.mineshaft.core;

/**
 * ✅ COMPLETE v3 - Minecraft-accurate world settings
 */
public class Settings {

    // ================================================================
    // VERSION & WINDOW
    // ================================================================

    public static final String VERSION = "Alpha v0.9.0 - Terrain Update";
    public static final String TITLE = "Mineshaft";
    public static final String FULL_TITLE = TITLE + " " + VERSION;

    public static int WINDOW_WIDTH = 1280;
    public static int WINDOW_HEIGHT = 720;
    public static boolean VSYNC = false;
    public static final int TARGET_FPS = 0;
    public static final boolean SHOW_FPS = true;

    // ================================================================
    // WORLD & TERRAIN (Minecraft Overworld Style)
    // ================================================================

    // ✅ UPDATED: Minecraft-accurate Y range
    public static final int WORLD_MIN_Y = -64; // Bedrock floor
    public static final int WORLD_MAX_Y = 320; // Build limit
    public static final int WORLD_HEIGHT = 384; // Total: 320 - (-64) = 384

    // ✅ UPDATED: Sea level & terrain
    public static final int SEA_LEVEL = 63; // Minecraft standard
    public static final int SURFACE_LEVEL = 64; // Average surface

    // ✅ NEW: Bedrock generation
    public static final int BEDROCK_FLOOR = -64; // Absolute bottom
    public static final int BEDROCK_LAYERS = 5; // -64 to -60 (random pattern)

    // World generation
    public static final long WORLD_SEED = 12345678L;
    public static final int RENDER_DISTANCE = 8;
    public static final int CHUNK_SIZE = 16;

    // ✅ NEW: Terrain noise parameters
    public static final double CONTINENT_SCALE = 0.0005; // Very large features
    public static final double TERRAIN_SCALE = 0.008; // Medium features
    public static final double DETAIL_SCALE = 0.03; // Small details
    public static final double EROSION_SCALE = 0.004; // Erosion pattern

    // ✅ NEW: Height limits
    public static final int MIN_TERRAIN_HEIGHT = 50; // Lowest normal terrain
    public static final int MAX_TERRAIN_HEIGHT = 120; // Highest normal terrain
    public static final int MOUNTAIN_START_HEIGHT = 95; // Where stone peaks start
    public static final int SNOW_HEIGHT = 110; // Snow on peaks (if implemented)

    // ================================================================
    // CAMERA & MOVEMENT
    // ================================================================

    public static final float FOV = 70.0f;
    public static final float NEAR_PLANE = 0.1f;
    public static final float FAR_PLANE = 256.0f;
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

    public static final int MAX_LOADED_CHUNKS = 1024;
    public static final boolean AGGRESSIVE_CHUNK_UNLOAD = false;
    public static final boolean MULTITHREADED_CHUNK_LOADING = true;
    public static final int CHUNK_LOAD_THREADS = 4;
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

    public static final boolean DEBUG_MODE = false;
    public static final boolean DEBUG_CHUNK_LOADING = false;
    public static final boolean DEBUG_LIGHTING = false;
    public static final boolean DEBUG_PHYSICS = false;
    public static final boolean DEBUG_CAMERA = false;
    public static final boolean SHOW_CHUNK_BORDERS = false;
    public static final boolean SHOW_COLLISION_BOXES = false;
    public static final boolean LOG_PERFORMANCE = false;

    // ================================================================
    // HELPER METHODS
    // ================================================================

    /**
     * Convert world Y to array index
     */
    public static int worldYToIndex(int worldY) {
        return worldY - WORLD_MIN_Y;
    }

    /**
     * Convert array index to world Y
     */
    public static int indexToWorldY(int index) {
        return index + WORLD_MIN_Y;
    }

    /**
     * Check if world Y is valid
     */
    public static boolean isValidWorldY(int worldY) {
        return worldY >= WORLD_MIN_Y && worldY <= WORLD_MAX_Y;
    }

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

    private Settings() {
        throw new AssertionError("Settings class cannot be instantiated");
    }
}