package com.mineshaft.core;

import com.mineshaft.entity.Camera;
import com.mineshaft.input.InputHandler;
import com.mineshaft.player.Inventory;
import com.mineshaft.render.BlockTextures;
import com.mineshaft.render.ChatOverlay;
import com.mineshaft.render.DebugScreen;
import com.mineshaft.render.HUD;
import com.mineshaft.render.SkyRenderer;
import com.mineshaft.render.TextureManager;
import com.mineshaft.util.Screenshot;
import com.mineshaft.world.GameMode;
import com.mineshaft.world.RayCast;
import com.mineshaft.world.World;
import com.mineshaft.block.Block;
import com.mineshaft.block.Blocks;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * ⚡ OPTIMIZED Game - Async Lighting & Mesh Building
 * ✅ FIXED Chat System - Event-based input handling
 */
public class Game {
    private long window;
    
    private boolean running = false;
    private World world;
    private Camera camera;
    private TimeOfDay timeOfDay;
    private Inventory inventory;
    private HUD hud;
    private DebugScreen debugScreen;
    private InputHandler input;
    private ChatOverlay chatOverlay;
    private CommandHandler commandHandler;
    private SkyRenderer skyRenderer;

    private int fps = 0;
    private int tps = 0;
    private int peakFps = 0;
    
    private boolean vsyncEnabled = Settings.VSYNC;
    private boolean fullscreen = false;
    private boolean guiVisible = true;
    private boolean pauseOnLostFocus = true;
    
    private boolean renderSky = true;
    
    private GameMode previousGameMode = GameMode.CREATIVE;
    
    private boolean leftMousePressed = false;
    private boolean rightMousePressed = false;
    private boolean middleMousePressed = false;
    
    private static final float REACH_DISTANCE = 5.0f;
    
    private int cameraMode = 0;
    
    private double scrollOffset = 0;

    public void start() {
        try {
            System.out.println("===============================================");
            System.out.println("   MINESHAFT - Minecraft-style Voxel Engine");
            System.out.println("   " + Settings.VERSION);
            System.out.println("===============================================");
            
            initDisplay();
            initGL();
            initGame();
            
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
            NULL
        );

        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        setupCallbacks();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
                );
            }
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(vsyncEnabled ? 1 : 0);
        glfwShowWindow(window);

        GL.createCapabilities();

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        System.out.println("Display: " + Settings.WINDOW_WIDTH + "x" + Settings.WINDOW_HEIGHT);
        System.out.println("VSync: " + (vsyncEnabled ? "ON (60 FPS cap)" : "OFF (Unlimited)"));
    }

    private void setupCallbacks() {
        glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            // ✅ Only process scroll if chat is closed
            if (!chatOverlay.isChatOpen()) {
                scrollOffset += yoffset;
            }
        });

        glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            glViewport(0, 0, width, height);
            updateProjectionMatrix(width, height);
            
            // ✅ Update chat overlay size
            if (chatOverlay != null) {
                chatOverlay.updateSize(width, height);
            }
        });
    }

    private void updateProjectionMatrix(int width, int height) {
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

        glClearColor(0.5f, 0.7f, 1.0f, 1.0f);

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

    private void initGame() {
        timeOfDay = new TimeOfDay();
        world = new World(timeOfDay);
        
        camera = new Camera(0, 80, 0, window);
        camera.setWorld(world);
        camera.setGameMode(GameMode.CREATIVE);
        
        inventory = new Inventory();
        hud = new HUD(window);
        debugScreen = new DebugScreen(window);
        input = new InputHandler(window);
        
        // ✅ Initialize chat overlay (will setup its own callbacks)
        chatOverlay = new ChatOverlay(window);
        commandHandler = new CommandHandler(world, camera, timeOfDay, chatOverlay);
        
        skyRenderer = new SkyRenderer(timeOfDay);

        printControls();
    }
    
    private void printControls() {
        System.out.println("");
        System.out.println("=== CONTROLS ===");
        System.out.println("Movement:");
        System.out.println("  WASD           - Move");
        System.out.println("  Mouse          - Look around");
        System.out.println("  SPACE          - Jump / Swim up / Fly up");
        System.out.println("  SHIFT          - Sneak / Fly down");
        System.out.println("  CTRL           - Sprint");
        System.out.println("");
        System.out.println("Interaction:");
        System.out.println("  Left Click     - Break block");
        System.out.println("  Right Click    - Place block");
        System.out.println("  Middle Click   - Pick block");
        System.out.println("  Mouse Wheel    - Cycle hotbar");
        System.out.println("  1-9            - Select hotbar slot");
        System.out.println("");
        System.out.println("Chat & Commands:");
        System.out.println("  T              - Open chat");
        System.out.println("  /              - Open chat with command");
        System.out.println("  Enter          - Send message/command");
        System.out.println("  ESC            - Close chat");
        System.out.println("");
        System.out.println("Display:");
        System.out.println("  F1             - Hide GUI");
        System.out.println("  F2             - Take screenshot");
        System.out.println("  F3             - Debug info");
        System.out.println("  F5             - Toggle camera view");
        System.out.println("");
        System.out.println("Commands:");
        System.out.println("  /time set <day|night|noon|midnight>");
        System.out.println("  /time add <ticks>");
        System.out.println("  /time speed set <multiplier>");
        System.out.println("  /gamemode <survival|creative|spectator>");
        System.out.println("  /help          - Show all commands");
        System.out.println("");
        System.out.println("Other:");
        System.out.println("  V              - Toggle VSync");
        System.out.println("  F11            - Toggle fullscreen");
        System.out.println("  ESC            - Exit (when chat closed)");
        System.out.println("===============================================");
    }

    private void gameLoop() {
        long lastTime = System.nanoTime();
        long timer = System.currentTimeMillis();

        double nsPerTick = 1000000000.0 / Settings.TARGET_TPS;
        double delta = 0;

        int frames = 0;
        int ticks = 0;

        while (running && !glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            delta += (now - lastTime) / nsPerTick;
            lastTime = now;

            while (delta >= 1) {
                update();
                ticks++;
                delta--;
            }

            render();
            frames++;

            glfwSwapBuffers(window);
            glfwPollEvents();

            if (System.currentTimeMillis() - timer > 1000) {
                timer += 1000;
                fps = frames;
                tps = ticks;
                
                if (fps > peakFps) peakFps = fps;

                if (Settings.SHOW_FPS && !debugScreen.isVisible()) {
                    glfwSetWindowTitle(window, String.format(
                        "%s | FPS: %d | Time: %s | Light: %d",
                        Settings.FULL_TITLE,
                        fps,
                        timeOfDay.getTimePhase(),
                        timeOfDay.getSkylightLevel()
                    ));
                }

                frames = 0;
                ticks = 0;
            }

            input.update();
            handleInput();
        }
    }

    /**
     * ⚡ OPTIMIZED: Update with lighting engine update
     */
    private void update() {
        // ✅ Only process camera input if chat is closed
        if (!chatOverlay.isChatOpen()) {
            camera.processInput(1.0f / Settings.TARGET_TPS);
        }
        
        int oldSkylight = timeOfDay.getSkylightLevel();
        timeOfDay.update();
        int newSkylight = timeOfDay.getSkylightLevel();
        
        if (oldSkylight != newSkylight) {
            world.updateSkylightForTimeChange();
        }

        world.updateSunLight();
        
        // ⚡ UPDATE LIGHTING ENGINE (smooth transitions)
        world.getLightingEngine().update();
        
        skyRenderer.update(1.0f / Settings.TARGET_TPS);

        int playerChunkX = (int) Math.floor(camera.getX() / 16);
        int playerChunkZ = (int) Math.floor(camera.getZ() / 16);
        world.updateChunks(playerChunkX, playerChunkZ);
    }

    /**
     * ⚡ OPTIMIZED: Render with async mesh building update
     */
    private void render() {
        float[] skyColor = timeOfDay.getSkyColor();
        float[] fogColor = timeOfDay.getFogColor();
        
        glClearColor(skyColor[0], skyColor[1], skyColor[2], 1.0f);
        
        if (Settings.ENABLE_FOG) {
            FloatBuffer fogColorBuffer = BufferUtils.createFloatBuffer(4);
            fogColorBuffer.put(fogColor[0]);
            fogColorBuffer.put(fogColor[1]);
            fogColorBuffer.put(fogColor[2]);
            fogColorBuffer.put(1.0f);
            fogColorBuffer.flip();
            glFogfv(GL_FOG_COLOR, fogColorBuffer);
        }

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glLoadIdentity();

        if (cameraMode == 0) {
            camera.applyTranslations();
        } else if (cameraMode == 1) {
            applyThirdPersonCamera(4.0f);
        } else {
            applyThirdPersonCamera(-4.0f);
        }

        if (renderSky) {
            skyRenderer.renderSky(camera.getX(), camera.getY(), camera.getZ());
        }

        glColor3f(1.0f, 1.0f, 1.0f);
        world.render(camera);
        
        // ⚡ PROCESS PENDING MESH BUILDS (async, throttled to 3 per frame)
        world.getRenderer().update();

        if (debugScreen.showChunkBorders()) {
            renderChunkBorders();
        }

        glColor3f(1.0f, 1.0f, 1.0f);
        camera.applyUnderwaterEffect();

        if (guiVisible) {
            hud.render(inventory.getSelectedSlot());
        }

        debugScreen.render(camera, world, camera.getGameMode(), fps, tps);
        
        // ✅ Render chat overlay (always last, on top of everything)
        chatOverlay.render();
    }
    
    private void applyThirdPersonCamera(float distance) {
        glRotatef(camera.getPitch(), 1, 0, 0);
        glRotatef(camera.getYaw(), 0, 1, 0);
        
        float yawRad = (float) Math.toRadians(camera.getYaw());
        float pitchRad = (float) Math.toRadians(camera.getPitch());
        
        float offsetX = (float) (Math.sin(yawRad) * Math.cos(pitchRad) * distance);
        float offsetY = (float) (-Math.sin(pitchRad) * distance);
        float offsetZ = (float) (-Math.cos(yawRad) * Math.cos(pitchRad) * distance);
        
        glTranslatef(-camera.getX() - offsetX, -camera.getY() - offsetY, -camera.getZ() - offsetZ);
    }
    
    private void renderChunkBorders() {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glLineWidth(2.0f);
        glColor3f(0, 0, 1);
        
        int px = (int) camera.getX();
        int pz = (int) camera.getZ();
        
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

    /**
     * ✅ FIXED: Handle input with proper chat priority
     */
    private void handleInput() {
        // ✅ PRIORITY 1: Check for pending chat messages
        String chatMessage = chatOverlay.getPendingMessage();
        if (chatMessage != null) {
            commandHandler.executeCommand(chatMessage);
            return; // Don't process other input this frame
        }
        
        // ✅ PRIORITY 2: If chat is open, BLOCK all game input
        // Chat handles T, /, Enter, Escape via its own callbacks
        if (chatOverlay.isChatOpen()) {
            return; 
        }
        
        // ✅ PRIORITY 3: Game input (only when chat is closed)
        
        // ⚠️ REMOVED: T and / are now handled by ChatOverlay callbacks
        // Don't handle them here to prevent conflicts!
        
        if (input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            running = false;
        }
        
        boolean f3Down = input.isKeyDown(GLFW_KEY_F3);
        
        if (f3Down) {
            handleF3Combinations();
        } else {
            if (input.isKeyPressed(GLFW_KEY_F3)) {
                debugScreen.toggle();
            }
        }
        
        if (!f3Down) {
            if (input.isKeyPressed(GLFW_KEY_F1)) {
                guiVisible = !guiVisible;
                System.out.println("GUI: " + (guiVisible ? "ON" : "OFF"));
            }
            
            if (input.isKeyPressed(GLFW_KEY_F2)) {
                Screenshot.takeScreenshot(window);
            }
            
            if (input.isKeyPressed(GLFW_KEY_F4)) {
                cycleGameMode(1);
            }
            
            if (input.isKeyPressed(GLFW_KEY_F5)) {
                cameraMode = (cameraMode + 1) % 3;
                String[] modes = {"First Person", "Third Person (Back)", "Third Person (Front)"};
                System.out.println("Camera: " + modes[cameraMode]);
            }
            
            if (input.isKeyPressed(GLFW_KEY_F11)) {
                toggleFullscreen();
            }
        }
        
        if (input.isKeyPressed(GLFW_KEY_V) && !f3Down) {
            vsyncEnabled = !vsyncEnabled;
            glfwSwapInterval(vsyncEnabled ? 1 : 0);
            System.out.println("VSync: " + (vsyncEnabled ? "ON" : "OFF"));
        }
        
        if (input.isKeyPressed(GLFW_KEY_F) && !f3Down) {
            camera.toggleFlying();
        }
        
        // Hotbar selection (1-9 keys)
        for (int i = 0; i < 9; i++) {
            if (input.isKeyPressed(GLFW_KEY_1 + i)) {
                inventory.selectSlot(i);
            }
        }
        
        // Mouse wheel scrolling
        int dwheel = getScrollDelta();
        if (dwheel > 0) {
            inventory.prevSlot();
        } else if (dwheel < 0) {
            inventory.nextSlot();
        }
        
        // Mouse button interactions
        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
            if (!leftMousePressed && camera.getGameMode() != GameMode.SPECTATOR) {
                breakBlock();
                leftMousePressed = true;
            }
        } else {
            leftMousePressed = false;
        }
        
        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS) {
            if (!rightMousePressed && camera.getGameMode() != GameMode.SPECTATOR) {
                placeBlock();
                rightMousePressed = true;
            }
        } else {
            rightMousePressed = false;
        }
        
        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_MIDDLE) == GLFW_PRESS) {
            if (!middleMousePressed) {
                pickBlock();
                middleMousePressed = true;
            }
        } else {
            middleMousePressed = false;
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
        if (input.isKeyPressed(GLFW_KEY_Q)) {
            printF3Help();
        }
        
        if (input.isKeyPressed(GLFW_KEY_A)) {
            System.out.println("Reloading all chunks...");
            for (com.mineshaft.world.Chunk chunk : world.getChunks()) {
                chunk.setNeedsRebuild(true);
            }
        }
        
        if (input.isKeyPressed(GLFW_KEY_B)) {
            debugScreen.toggleHitboxes();
        }
        
        if (input.isKeyPressed(GLFW_KEY_C)) {
            String coords = String.format("%.2f %.2f %.2f", 
                camera.getX(), camera.getY(), camera.getZ());
            System.out.println("Coordinates: " + coords);
        }
        
        if (input.isKeyPressed(GLFW_KEY_G)) {
            debugScreen.toggleChunkBorders();
        }
        
        if (input.isKeyPressed(GLFW_KEY_N)) {
            toggleSpectatorMode();
        }
        
        if (input.isKeyPressed(GLFW_KEY_P)) {
            pauseOnLostFocus = !pauseOnLostFocus;
            System.out.println("Pause on lost focus: " + (pauseOnLostFocus ? "ON" : "OFF"));
        }
        
        if (input.isKeyPressed(GLFW_KEY_S)) {
            renderSky = !renderSky;
            System.out.println("Sky rendering: " + (renderSky ? "ON" : "OFF"));
        }
    }
    
    private void cycleGameMode(int direction) {
        GameMode current = camera.getGameMode();
        GameMode next = direction > 0 ? current.next() : current.previous();
        
        previousGameMode = current;
        camera.setGameMode(next);
        System.out.println("Game mode: " + next.getName());
    }
    
    private void toggleSpectatorMode() {
        GameMode current = camera.getGameMode();
        
        if (current == GameMode.SPECTATOR) {
            camera.setGameMode(previousGameMode);
            System.out.println("Game mode: " + previousGameMode.getName());
        } else {
            previousGameMode = current;
            camera.setGameMode(GameMode.SPECTATOR);
            System.out.println("Game mode: Spectator");
        }
    }
    
    private void printF3Help() {
        System.out.println("=== F3 DEBUG COMMANDS ===");
        System.out.println("F3 + A = Reload all chunks");
        System.out.println("F3 + B = Toggle hitboxes");
        System.out.println("F3 + C = Copy coordinates");
        System.out.println("F3 + G = Toggle chunk borders");
        System.out.println("F3 + N = Toggle Creative/Spectator");
        System.out.println("F3 + P = Toggle pause on lost focus");
        System.out.println("F3 + S = Toggle sky rendering");
        System.out.println("F3 + Q = Show this help");
        System.out.println("=========================");
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
                    vidmode.refreshRate()
                );
                System.out.println("Fullscreen: ON");
            } else {
                glfwSetWindowMonitor(
                    window,
                    NULL,
                    100, 100,
                    Settings.WINDOW_WIDTH,
                    Settings.WINDOW_HEIGHT,
                    GLFW_DONT_CARE
                );
                System.out.println("Fullscreen: OFF");
            }
        }
    }
    
    private void breakBlock() {
        float[] dir = camera.getForwardVector();
        
        RayCast.RayResult ray = RayCast.cast(
            world,
            camera.getX(),
            camera.getY(),
            camera.getZ(),
            dir[0], dir[1], dir[2],
            REACH_DISTANCE
        );
        
        if (ray.hit) {
            world.setBlock(ray.x, ray.y, ray.z, Blocks.AIR);
        }
    }
    
    private void placeBlock() {
        Block selectedBlock = inventory.getSelectedBlock();
        if (selectedBlock == null || selectedBlock == Blocks.AIR) return;
        
        float[] dir = camera.getForwardVector();
        
        RayCast.RayResult ray = RayCast.cast(
            world,
            camera.getX(),
            camera.getY(),
            camera.getZ(),
            dir[0], dir[1], dir[2],
            REACH_DISTANCE
        );
        
        if (ray.hit) {
            world.setBlock(ray.px, ray.py, ray.pz, selectedBlock);
        }
    }
    
    private void pickBlock() {
        float[] dir = camera.getForwardVector();
        
        RayCast.RayResult ray = RayCast.cast(
            world,
            camera.getX(),
            camera.getY(),
            camera.getZ(),
            dir[0], dir[1], dir[2],
            REACH_DISTANCE
        );
        
        if (ray.hit) {
            Block block = world.getBlock(ray.x, ray.y, ray.z);
            if (block != null && !block.isAir()) {
                inventory.setSlot(inventory.getSelectedSlot(), block);
                System.out.println("Picked: " + block.getClass().getSimpleName());
            }
        }
    }

    /**
     * ✅ FIXED: Cleanup with chat overlay callback removal
     */
    private void cleanup() {
        System.out.println("===============================================");
        System.out.println("Shutting down...");
        
        if (world != null) {
            world.cleanup();
        }
        
        if (skyRenderer != null) {
            skyRenderer.cleanup();
        }
        
        // ✅ Cleanup chat overlay callbacks
        if (chatOverlay != null) {
            chatOverlay.cleanup();
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

    public int getFPS() { return fps; }
    public int getTPS() { return tps; }
    public long getWindow() { return window; }
}