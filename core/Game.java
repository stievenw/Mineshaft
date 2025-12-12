// src/main/java/com/mineshaft/core/Game.java
package com.mineshaft.core;

import com.mineshaft.block.GameBlock;
import com.mineshaft.entity.Camera;
import com.mineshaft.input.InputHandler;
import com.mineshaft.player.Inventory;
import com.mineshaft.player.Player;
import com.mineshaft.render.BlockTextures;
import com.mineshaft.render.ChatOverlay;
import com.mineshaft.render.DebugScreen;
import com.mineshaft.render.HUD;
import com.mineshaft.render.SkyRenderer;
import com.mineshaft.render.BlockOutlineRenderer;
import com.mineshaft.render.TextureManager;
import com.mineshaft.render.gui.GameState;
import com.mineshaft.render.gui.MenuManager;
import com.mineshaft.render.gui.Screen;
import com.mineshaft.render.gui.screens.SingleplayerScreen;
import com.mineshaft.util.Screenshot;
import com.mineshaft.player.GameMode;
import com.mineshaft.world.RayCast;
import com.mineshaft.world.World;
import com.mineshaft.world.Chunk;
import com.mineshaft.world.ChunkState;
import com.mineshaft.world.WorldInfo;
import com.mineshaft.world.interaction.BlockInteractionHandler;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * ✅ Main game class with Menu System Integration
 * ✅ MINECRAFT-STYLE LIGHTING: Time change doesn't rebuild meshes!
 */
public class Game {
    private long window;

    private boolean running = false;

    // Menu System
    private MenuManager menuManager;
    private boolean worldLoaded = false;
    private boolean isLoadingTerrain = false; // ✅ Loading State
    private long loadingStartTime = 0;
    private int lastReadyChunks = 0;
    private int readyChunkStabilityCounter = 0;

    // Game World (null until world is loaded)
    private World world;
    private Player player;
    private Camera camera;
    private TimeOfDay timeOfDay;
    private Inventory inventory;
    private HUD hud;
    private DebugScreen debugScreen;
    private InputHandler input;
    private ChatOverlay chatOverlay;
    private CommandHandler commandHandler;
    private SkyRenderer skyRenderer;
    private BlockInteractionHandler interactionHandler;
    private BlockOutlineRenderer outlineRenderer;

    // Stats
    private int fps = 0;
    private int tps = 0;
    private int peakFps = 0;

    // Settings
    private boolean vsyncEnabled = Settings.VSYNC;
    private boolean fullscreen = false;
    private boolean guiVisible = true;
    private boolean pauseOnLostFocus = true;
    private boolean renderSky = true;

    private GameMode previousGameMode = GameMode.CREATIVE;

    private static final float REACH_DISTANCE = 5.0f;

    private int cameraMode = 0;

    private double scrollOffset = 0;

    // Current world info
    private WorldInfo currentWorldInfo;

    // ✅ Autosave system
    private long lastAutoSave = 0;
    private static final long AUTOSAVE_INTERVAL = 60000; // 60 seconds

    public void start() {
        try {
            System.out.println("===============================================");
            System.out.println("   MINESHAFT - Minecraft-style Voxel Engine");
            System.out.println("   " + Settings.VERSION);
            System.out.println("===============================================");

            initDisplay();
            initGL();
            initMenuSystem();

            System.out.println("Game initialized successfully");
            System.out.println("===============================================");
            running = true;
            gameLoop();

        } catch (Exception e) {
            System.err.println("Failed to initialize!");
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void initDisplay() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        window = glfwCreateWindow(
                Settings.WINDOW_WIDTH,
                Settings.WINDOW_HEIGHT,
                Settings.FULL_TITLE,
                NULL,
                NULL);

        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(
                        window,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2);
            }
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0); // Force Unlimited FPS for performance testing
        glfwShowWindow(window);

        GL.createCapabilities();

        // ✅ Start with cursor visible for menu
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);

        System.out.println("Display: " + Settings.WINDOW_WIDTH + "x" + Settings.WINDOW_HEIGHT);
        System.out.println("VSync: " + (vsyncEnabled ? "ON" : "OFF"));
    }

    private void initGL() {
        glViewport(0, 0, Settings.WINDOW_WIDTH, Settings.WINDOW_HEIGHT);
        updateProjectionMatrix(Settings.WINDOW_WIDTH, Settings.WINDOW_HEIGHT);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        glShadeModel(GL_SMOOTH);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glClearColor(0.1f, 0.1f, 0.15f, 1.0f);

        glEnable(GL_TEXTURE_2D);

        if (Settings.ENABLE_FOG) {
            glEnable(GL_FOG);
            glFogi(GL_FOG_MODE, GL_EXP2);
            glFogf(GL_FOG_DENSITY, Settings.FOG_DENSITY);
            glHint(GL_FOG_HINT, GL_NICEST);
        }

        System.out.println("OpenGL: " + glGetString(GL_VERSION));
        System.out.println("Renderer: " + glGetString(GL_RENDERER));

        BlockTextures.init();
    }

    /**
     * ✅ Initialize menu system first, before game world
     */
    private void initMenuSystem() {
        menuManager = new MenuManager(window);
        menuManager.setOnQuitGame(() -> running = false);
        menuManager.setOnWorldLoad(this::loadWorld);

        input = new InputHandler(window);
        debugScreen = new DebugScreen(window);
        outlineRenderer = new BlockOutlineRenderer();

        System.out.println("[Game] Menu system initialized");
    }

    /**
     * ✅ Load world from WorldInfo (called by MenuManager)
     */
    private void loadWorld(WorldInfo worldInfo) {
        System.out.println("===============================================");
        System.out.println("Loading world: " + worldInfo.getName());
        System.out.println("Seed: " + worldInfo.getSeed());
        System.out.println("Game Mode: " + worldInfo.getGameMode());
        System.out.println("===============================================");

        cleanupWorld();
        currentWorldInfo = worldInfo;

        // ✅ Create TimeOfDay first
        timeOfDay = new TimeOfDay();

        // ✅ Create World with TimeOfDay AND WorldInfo for save/load support
        world = new World(timeOfDay, worldInfo);
        world.setSeed(worldInfo.getSeed());

        player = new Player(world, window);
        player.setPosition(0, 80, 0);

        switch (worldInfo.getGameMode()) {
            case "Creative":
                player.setGameMode(GameMode.CREATIVE);
                break;
            case "Survival":
                player.setGameMode(GameMode.SURVIVAL);
                break;
            case "Hardcore":
                player.setGameMode(GameMode.SURVIVAL);
                break;
            case "Spectator":
                player.setGameMode(GameMode.SPECTATOR);
                break;
            default:
                player.setGameMode(GameMode.CREATIVE);
        }

        previousGameMode = player.getGameMode();
        camera = new Camera(player, window);
        inventory = player.getInventory();
        hud = new HUD(window);
        chatOverlay = new ChatOverlay(window);
        commandHandler = new CommandHandler(world, player, timeOfDay, chatOverlay);
        interactionHandler = new BlockInteractionHandler(world, player, camera, REACH_DISTANCE);
        skyRenderer = new SkyRenderer(timeOfDay);

        // ✅ FIX: Register ChatOverlay with MenuManager for proper key routing
        menuManager.setChatOverlay(chatOverlay);
        chatOverlay.setMenuManager(menuManager);

        // ✅ FIX: Register Camera with MenuManager for proper mouse routing
        menuManager.setCamera(camera);

        setupGameCallbacks();
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        worldLoaded = true;
        isLoadingTerrain = true; // ✅ Enable loading screen state
        loadingStartTime = System.currentTimeMillis(); // ✅ Start Timer
        System.out.println("[Game] World loaded successfully, strictly loading terrain...");

        // ✅ Reset autosave timer
        lastAutoSave = System.currentTimeMillis();
    }

    /**
     * ✅ Setup callbacks for in-game use
     */
    private void setupGameCallbacks() {
        glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            if (menuManager.isMenuActive()) {
                Screen currentScreen = menuManager.getCurrentScreen();
                if (currentScreen instanceof SingleplayerScreen) {
                    ((SingleplayerScreen) currentScreen).scroll(yoffset);
                }
            } else if (chatOverlay != null && !chatOverlay.isChatOpen()) {
                scrollOffset += yoffset;
            }
        });

        glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            glViewport(0, 0, width, height);
            updateProjectionMatrix(width, height);

            // ✅ FIX: Notify MenuManager of resize so buttons allow clicking
            if (menuManager != null) {
                menuManager.resize(width, height);
            }

            if (chatOverlay != null) {
                if (input.isKeyPressed(GLFW_KEY_T)) {
                    chatOverlay.openChat();
                }
                if (input.isKeyPressed(GLFW_KEY_SLASH)) {
                    chatOverlay.openChatWithCommand();
                }
            }
        });
    }

    /**
     * ✅ Cleanup world resources without closing game
     */
    private void cleanupWorld() {
        if (world != null) {
            world.cleanup();
            world = null;
        }

        if (skyRenderer != null) {
            skyRenderer.cleanup();
            skyRenderer = null;
        }

        if (chatOverlay != null) {
            chatOverlay.cleanup();
            chatOverlay = null;
        }

        if (hud != null) {
            hud.cleanup();
            hud = null;
        }

        player = null;
        camera = null;
        inventory = null;
        commandHandler = null;
        interactionHandler = null;
        timeOfDay = null;
        currentWorldInfo = null;

        worldLoaded = false;

        System.out.println("[Game] World cleaned up");
    }

    /**
     * ✅ Return to main menu
     */
    private void returnToMainMenu() {
        // ✅ Save world before returning to menu
        if (currentWorldInfo != null && world != null && player != null) {
            System.out.println("[Game] Saving world before returning to menu...");
            currentWorldInfo.setLastPlayedTime(System.currentTimeMillis());
            com.mineshaft.world.WorldSaveManager.saveWorldData(currentWorldInfo, world, player);
        }

        cleanupWorld();
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        menuManager.setGameState(GameState.MAIN_MENU);
    }

    private void updateProjectionMatrix(int width, int height) {
        if (height == 0)
            height = 1;

        float aspect = (float) width / (float) height;
        float fov = Settings.FOV;
        float near = Settings.NEAR_PLANE;
        float far = Settings.FAR_PLANE;

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        float fH = (float) Math.tan(Math.toRadians(fov) / 2) * near;
        float fW = fH * aspect;
        glFrustum(-fW, fW, -fH, fH, near, far);

        glMatrixMode(GL_MODELVIEW);
    }

    /**
     * ✅ Main game loop with menu integration
     */
    private void gameLoop() {
        long lastTime = System.nanoTime();
        long lastFrameTime = System.nanoTime();
        long timer = System.currentTimeMillis();

        double nsPerTick = 1000000000.0 / Settings.TARGET_TPS;
        double delta = 0;

        int frames = 0;
        int ticks = 0;

        while (running && !glfwWindowShouldClose(window)) {
            // ✅ Handle Return to Main Menu (from Pause Screen)
            if (worldLoaded && menuManager.getGameState() == GameState.MAIN_MENU) {
                returnToMainMenu();
                continue;
            }

            long now = System.nanoTime();
            delta += (now - lastTime) / nsPerTick;
            lastTime = now;

            boolean isPlaying = menuManager.getGameState() == GameState.PLAYING && worldLoaded;

            // ✅ LOADING SCREEN LOGIC (Minecraft-Style)
            if (isLoadingTerrain && isPlaying) {
                // 1. Calculate Progress (Strict Readiness)
                int readyChunks = 0;
                for (Chunk c : world.getChunks()) {
                    if (c.getState() == ChunkState.READY) {
                        readyChunks++;
                    }
                }

                // ✅ FIX: Calculate target based on CIRCULAR load area (like World.java)
                // Previous logic used Square ((2R+1)^2) which expected 625 chunks for R=12
                // But World only loads ~450 chunks (Circle).
                // Target should be approx PI * R^2
                int r = Settings.RENDER_DISTANCE;
                int target = (int) (Math.PI * r * r);

                // Safety: Allow for some variability (chunk edge cases)
                // If the world actually loaded slightly more/less, we don't want to get stuck.
                // We use dynamic thresholding below.

                // ✅ User requested: "Wait for 100 mesh builds"
                int absoluteMin = Math.min(target, 100);
                int percentMin = (int) (target * 0.90f); // Require 90% of the CIRCLE
                int threshold = Math.max(absoluteMin, percentMin);

                // Fallback: If readyChunks is STABLE (not increasing) for a long time, we
                // should proceed
                if (readyChunks > 0 && readyChunks == lastReadyChunks) {
                    readyChunkStabilityCounter++;
                    if (readyChunkStabilityCounter > 100) { // ~1-2 seconds of stuck count
                        // Force threshold down to current count if reasonable
                        if (readyChunks > target * 0.8) {
                            threshold = readyChunks;
                        }
                    }
                } else {
                    lastReadyChunks = readyChunks;
                    readyChunkStabilityCounter = 0;
                }

                int pendingMeshes = world.getRenderer().getPendingBuilds();
                int pendingGen = world.getPendingGenerations();

                String status = "Loading Terrain...";

                // Prioritize Mesh Build status if high
                if (pendingMeshes > 100) {
                    status = "Building Meshes (" + pendingMeshes + " left)...";
                } else if (readyChunks < threshold || pendingGen > 0) {
                    status = "Generating Chunks (" + readyChunks + "/" + target + ")...";
                } else if (pendingMeshes > 0) { // Still low amount of meshes building
                    status = "Stabilizing (" + pendingMeshes + " left)...";
                } else {
                    status = "Stabilizing...";
                }

                // Debug: Clear to Magenta (If you see this, DebugScreen failed)
                glClearColor(1.0f, 0.0f, 1.0f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                // 2. Render Loading Screen
                if (debugScreen != null) {
                    debugScreen.drawLoadingScreen(readyChunks, target, status);
                }

                if (System.currentTimeMillis() % 1000 < 20) {
                    System.out.println("[Game] Loading: Ready=" + readyChunks + "/" + target +
                            " (Req: " + threshold + ") | MeshQ: " + pendingMeshes + " | GenQ: " + pendingGen);
                }

                // 3. Force Update World Generation (High Speed / No Tick Limit)
                int px = (int) Math.floor(player.getX()) >> 4;
                int pz = (int) Math.floor(player.getZ()) >> 4;
                world.updateChunks(px, pz);
                world.getRenderer().update();

                glfwSwapBuffers(window);
                glfwPollEvents();

                // 4. Check Exit Condition
                // Strict: Met threshold AND queues empty AND min time passed
                boolean minTimePassed = (System.currentTimeMillis() - loadingStartTime) > 2000;

                // Wait for mesh builds to drop below 100 (stable enough)
                if (readyChunks >= threshold && pendingMeshes < 100 && pendingGen == 0 && minTimePassed) {
                    isLoadingTerrain = false;
                    // ✅ UPDATED: Load only player position and time from save
                    // Chunks are now loaded during generation in ChunkGenerationManager
                    if (currentWorldInfo != null) {
                        com.mineshaft.world.WorldSaveManager.loadWorldData(currentWorldInfo, world, player);
                    }
                }
                continue; // Skip physics/game render
            }

            if (isPlaying) {
                // ✅ FIX: Removed manual ESC check - now handled by MenuManager callback
                // which properly routes to PauseScreen.keyPressed()

                while (delta >= 1) {
                    if (chatOverlay != null && !chatOverlay.isChatOpen()) {
                        player.updateInput();
                    }

                    updatePhysics();
                    ticks++;
                    delta--;
                }
            } else {
                while (delta >= 1) {
                    delta--;
                    ticks++;
                }

                menuManager.update();
            }

            float partialTicks = (float) delta;

            if (isPlaying && interactionHandler != null) {
                float frameDelta = (now - lastFrameTime) / 1000000000.0f;
                interactionHandler.update(frameDelta);
            }
            lastFrameTime = now;

            // ✅ FIX: Render game world when playing OR paused (for background)
            // Then render pause menu overlay on top if paused
            if (worldLoaded && world != null && player != null) {
                renderGame(partialTicks);

                // Render pause menu overlay if paused
                if (menuManager.getGameState() == GameState.PAUSED) {
                    menuManager.render();
                }
            } else {
                // No world loaded, render regular menus
                renderMenu();
            }

            frames++;

            glfwSwapBuffers(window);
            glfwPollEvents();

            // ✅ Autosave system (Minecraft-style)
            if (isPlaying && world != null && player != null && currentWorldInfo != null) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastAutoSave >= AUTOSAVE_INTERVAL) {
                    System.out.println("[Game] Autosaving...");
                    com.mineshaft.world.WorldSaveManager.saveWorldData(currentWorldInfo, world, player);
                    lastAutoSave = currentTime;
                }
            }

            // ✅ FIX: Only update input when actually playing, not when paused
            // This prevents InputHandler from consuming key events that should go to pause
            // menu
            if (isPlaying) {
                input.update();
                handleGameInput();
            }

            if (System.currentTimeMillis() - timer > 1000) {
                timer += 1000;
                fps = frames;
                tps = ticks;

                if (fps > peakFps)
                    peakFps = fps;

                if (Settings.SHOW_FPS) {
                    String title;
                    if (isPlaying) {
                        title = String.format("%s | FPS: %d | TPS: %d | %s",
                                Settings.FULL_TITLE, fps, tps, getCameraModeName());
                    } else {
                        title = String.format("%s | FPS: %d", Settings.FULL_TITLE, fps);
                    }
                    glfwSetWindowTitle(window, title);
                }

                frames = 0;
                ticks = 0;
            }
        }
    }

    /**
     * ✅ MINECRAFT-STYLE: Physics and world updates (20 TPS)
     * Time change ONLY updates renderer brightness, NOT mesh!
     */
    private void updatePhysics() {
        if (!worldLoaded || player == null || world == null)
            return;

        player.tick();

        // ✅ MINECRAFT-STYLE FIX: Update time-of-day
        timeOfDay.update();

        // ✅ FIX: Update renderer brightness multiplier (NO MESH REBUILD!)
        if (world.getRenderer() != null) {
            float brightness = timeOfDay.getBrightness();
            world.getRenderer().setTimeOfDayBrightness(brightness);

            // ✅ DEBUG: Log brightness updates (throttled to avoid spam)
            if (Settings.DEBUG_MODE && System.currentTimeMillis() % 5000 < 50) {
                System.out.printf("[Game] Applied brightness to renderer: %.3f (Sky Light: %d)%n",
                        brightness, timeOfDay.getSkylightLevel());
            }
        }

        // ❌ REMOVED: Don't update skylight values for time change!
        // Skylight values (0-15) are STATIC based on block height
        // Only brightness MULTIPLIER changes with time
        //
        // OLD CODE (CAUSED LAG):
        // int oldSkylight = timeOfDay.getSkylightLevel();
        // timeOfDay.update();
        // int newSkylight = timeOfDay.getSkylightLevel();
        // if (oldSkylight != newSkylight) {
        // world.updateSkylightForTimeChange(); // ❌ This rebuilt all meshes!
        // }

        // ✅ Update sun direction (for future directional shadows)
        world.updateSunLight();

        // ✅ Process lighting updates (block light changes only)
        // world.getLightingEngine().update();

        // ✅ Update sky renderer
        if (skyRenderer != null) {
            skyRenderer.update(1.0f / Settings.TARGET_TPS);
        }

        // ✅ Update chunk loading/unloading
        int playerChunkX = (int) Math.floor(player.getX() / 16);
        int playerChunkZ = (int) Math.floor(player.getZ() / 16);
        world.updateChunks(playerChunkX, playerChunkZ);
    }

    /**
     * ✅ Render menu screen
     */
    private void renderMenu() {
        glClearColor(0.1f, 0.1f, 0.15f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        menuManager.render();
    }

    /**
     * ✅ Render game world
     */
    private void renderGame(float partialTicks) {
        if (!worldLoaded || world == null || player == null)
            return;

        float[] skyColor = timeOfDay.getSkyColor();

        glClearColor(skyColor[0], skyColor[1], skyColor[2], 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glLoadIdentity();

        // ✅ CRITICAL: Apply fog BEFORE world render!
        camera.applyUnderwaterEffect(skyColor);

        if (cameraMode == 0) {
            camera.applyTranslations(partialTicks);
        } else if (cameraMode == 1) {
            applyThirdPersonCamera(4.0f, partialTicks);
        } else {
            applyThirdPersonCamera(-4.0f, partialTicks);
        }

        if (renderSky && skyRenderer != null) {
            skyRenderer.renderSky(player.getX(), player.getY(), player.getZ());
        }

        glColor3f(1.0f, 1.0f, 1.0f);
        world.render(camera);
        world.getRenderer().update();

        if (debugScreen.showChunkBorders()) {
            renderChunkBorders();
        }

        if (player.getGameMode() != GameMode.SPECTATOR) {
            renderBlockOutline();
        }

        glColor3f(1.0f, 1.0f, 1.0f);

        if (guiVisible && hud != null) {
            hud.render(inventory.getSelectedSlot());
        }

        if (debugScreen != null) {
            debugScreen.render(camera, world, player.getGameMode(), fps, tps, vsyncEnabled, timeOfDay);
        }

        if (chatOverlay != null) {
            chatOverlay.render();
        }
    }

    private void applyThirdPersonCamera(float distance, float partialTicks) {
        glTranslatef(0, 0, -Math.abs(distance));
        glRotatef(player.getPitch(), 1, 0, 0);
        float yawOffset = (distance < 0) ? 180.0f : 0.0f;
        glRotatef(player.getYaw() + yawOffset, 0, 1, 0);

        float renderX = player.getRenderX(partialTicks);
        float renderY = player.getRenderY(partialTicks) + player.getCurrentEyeHeight();
        float renderZ = player.getRenderZ(partialTicks);

        glTranslatef(-renderX, -renderY, -renderZ);
    }

    private void renderChunkBorders() {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glLineWidth(2.0f);
        glColor3f(0, 0, 1);

        int px = (int) player.getX();
        int pz = (int) player.getZ();

        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                int chunkX = (px / 16 + cx) * 16;
                int chunkZ = (pz / 16 + cz) * 16;

                glBegin(GL_LINES);
                for (int i = 0; i <= 16; i += 16) {
                    for (int j = 0; j <= 16; j += 16) {
                        glVertex3f(chunkX + i, 0, chunkZ + j);
                        glVertex3f(chunkX + i, 128, chunkZ + j);
                    }
                }
                glEnd();
            }
        }

        glLineWidth(1.0f);
        glEnable(GL_TEXTURE_2D);
    }

    private void renderBlockOutline() {
        if (camera == null)
            return;

        float[] dir = camera.getForwardVector();
        RayCast.RayResult ray = RayCast.cast(
                world,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                dir[0], dir[1], dir[2],
                REACH_DISTANCE);

        if (ray.hit) {
            outlineRenderer.render(ray.x, ray.y, ray.z);
        }
    }

    /**
     * ✅ Handle input when playing game
     */
    private void handleGameInput() {
        if (!worldLoaded || player == null)
            return;

        if (chatOverlay != null) {
            String chatMessage = chatOverlay.getPendingMessage();
            if (chatMessage != null) {
                commandHandler.executeCommand(chatMessage);
                return;
            }

            if (chatOverlay.isChatOpen()) {
                return;
            }
        }

        if (input.isKeyPressed(GLFW_KEY_F3)) {
            boolean hasCombination = input.isKeyDown(GLFW_KEY_Q) ||
                    input.isKeyDown(GLFW_KEY_A) ||
                    input.isKeyDown(GLFW_KEY_B) ||
                    input.isKeyDown(GLFW_KEY_C) ||
                    input.isKeyDown(GLFW_KEY_G) ||
                    input.isKeyDown(GLFW_KEY_N) ||
                    input.isKeyDown(GLFW_KEY_P) ||
                    input.isKeyDown(GLFW_KEY_S);

            if (!hasCombination) {
                debugScreen.toggle();
            }
        }

        if (input.isKeyDown(GLFW_KEY_F3)) {
            handleF3Combinations();
        }

        if (!input.isKeyDown(GLFW_KEY_F3)) {
            if (input.isKeyPressed(GLFW_KEY_F1)) {
                guiVisible = !guiVisible;
            }

            if (input.isKeyPressed(GLFW_KEY_F2)) {
                Screenshot.takeScreenshot(window);
            }

            if (input.isKeyPressed(GLFW_KEY_F4)) {
                cycleGameMode(1);
            }

            if (input.isKeyPressed(GLFW_KEY_F5)) {
                cameraMode = (cameraMode + 1) % 3;
                System.out.println("Camera mode: " + getCameraModeName());
            }

            if (input.isKeyPressed(GLFW_KEY_F11)) {
                toggleFullscreen();
            }
        }

        if (input.isKeyPressed(GLFW_KEY_V) && !input.isKeyDown(GLFW_KEY_F3)) {
            vsyncEnabled = !vsyncEnabled;
            glfwSwapInterval(vsyncEnabled ? 1 : 0);
        }

        if (input.isKeyPressed(GLFW_KEY_F) && !input.isKeyDown(GLFW_KEY_F3)) {
            player.toggleFlying();
        }

        for (int i = 0; i < 9; i++) {
            if (input.isKeyPressed(GLFW_KEY_1 + i)) {
                inventory.selectSlot(i);
            }
        }

        int dwheel = getScrollDelta();
        if (dwheel > 0) {
            inventory.prevSlot();
        } else if (dwheel < 0) {
            inventory.nextSlot();
        }

        if (player.getGameMode() != GameMode.SPECTATOR && interactionHandler != null) {
            boolean leftMouseHeld = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
            interactionHandler.handleBreakInput(leftMouseHeld);
        }

        if (player.getGameMode() != GameMode.SPECTATOR && interactionHandler != null) {
            boolean rightMouseHeld = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
            GameBlock selectedBlock = inventory.getSelectedBlock();
            interactionHandler.handlePlaceInput(rightMouseHeld, selectedBlock);
        }

        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_MIDDLE) == GLFW_PRESS) {
            float[] dir = camera.getForwardVector();
            RayCast.RayResult ray = RayCast.cast(
                    world,
                    player.getX(),
                    player.getEyeY(),
                    player.getZ(),
                    dir[0], dir[1], dir[2],
                    REACH_DISTANCE);

            if (ray.hit) {
                GameBlock block = world.getBlock(ray.x, ray.y, ray.z);
                if (block != null && !block.isAir()) {
                    inventory.setSlot(inventory.getSelectedSlot(), block);
                }
            }
        }

        if (chatOverlay != null) {
            if (input.isKeyPressed(GLFW_KEY_T)) {
                chatOverlay.openChat();
            }
            if (input.isKeyPressed(GLFW_KEY_SLASH)) {
                chatOverlay.openChatWithCommand();
            }
        }
    }

    private int getScrollDelta() {
        int delta = 0;
        if (scrollOffset > 0) {
            delta = 1;
            scrollOffset = 0;
        } else if (scrollOffset < 0) {
            delta = -1;
            scrollOffset = 0;
        }
        return delta;
    }

    private void handleF3Combinations() {
        // ✅ FIX: F3+A now only rebuilds geometry, not lighting
        if (input.isKeyPressed(GLFW_KEY_A)) {
            if (world != null) {
                // Force geometry rebuild for all chunks
                for (com.mineshaft.world.Chunk chunk : world.getChunks()) {
                    chunk.setNeedsGeometryRebuild(true);
                }
                System.out.println("[Game] Force rebuilding all chunk meshes (geometry only)");
            }
        }

        if (input.isKeyPressed(GLFW_KEY_B)) {
            debugScreen.toggleHitboxes();
        }

        if (input.isKeyPressed(GLFW_KEY_G)) {
            debugScreen.toggleChunkBorders();
        }

        if (input.isKeyPressed(GLFW_KEY_N)) {
            toggleSpectatorMode();
        }

        if (input.isKeyPressed(GLFW_KEY_P)) {
            pauseOnLostFocus = !pauseOnLostFocus;
        }

        if (input.isKeyPressed(GLFW_KEY_S)) {
            renderSky = !renderSky;
        }
    }

    private void cycleGameMode(int direction) {
        if (player == null)
            return;

        GameMode current = player.getGameMode();
        GameMode next = direction > 0 ? current.next() : current.previous();

        previousGameMode = current;
        player.setGameMode(next);
    }

    private void toggleSpectatorMode() {
        if (player == null)
            return;

        GameMode current = player.getGameMode();

        if (current == GameMode.SPECTATOR) {
            player.setGameMode(previousGameMode);
        } else {
            previousGameMode = current;
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;

        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidmode = glfwGetVideoMode(monitor);

        if (vidmode != null) {
            if (fullscreen) {
                glfwSetWindowMonitor(
                        window,
                        monitor,
                        0, 0,
                        vidmode.width(),
                        vidmode.height(),
                        vidmode.refreshRate());
            } else {
                glfwSetWindowMonitor(
                        window,
                        NULL,
                        100, 100,
                        Settings.WINDOW_WIDTH,
                        Settings.WINDOW_HEIGHT,
                        GLFW_DONT_CARE);
            }
        }
    }

    private String getCameraModeName() {
        switch (cameraMode) {
            case 0:
                return "First Person";
            case 1:
                return "Third Person Back";
            case 2:
                return "Third Person Front";
            default:
                return "Unknown";
        }
    }

    private void cleanup() {
        System.out.println("===============================================");
        System.out.println("Shutting down...");

        if (currentWorldInfo != null) {
            currentWorldInfo.setLastPlayedTime(System.currentTimeMillis());
            com.mineshaft.world.WorldSaveManager.saveWorldInfo(currentWorldInfo);
        }

        cleanupWorld();

        if (menuManager != null) {
            menuManager.cleanup();
        }

        BlockTextures.cleanup();
        TextureManager.cleanup();

        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }

        System.out.println("Peak FPS: " + peakFps);
        System.out.println("Game closed successfully");
        System.out.println("===============================================");
    }

    public int getFPS() {
        return fps;
    }

    public int getTPS() {
        return tps;
    }

    public long getWindow() {
        return window;
    }

    public boolean isWorldLoaded() {
        return worldLoaded;
    }

    public World getWorld() {
        return world;
    }

    public Player getPlayer() {
        return player;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }
}