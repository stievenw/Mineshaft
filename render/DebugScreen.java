// src/main/java/com/mineshaft/render/DebugScreen.java
package com.mineshaft.render;

import com.mineshaft.entity.Camera;
import com.mineshaft.player.GameMode;
import com.mineshaft.world.World;
import com.mineshaft.core.Settings;

import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ Enhanced debug screen with async generation stats
 */
public class DebugScreen {

    private final long window;
    private SimpleFont font;

    private boolean visible = false;
    private boolean showHitboxes = false;
    private boolean showChunkBorders = false;

    private int windowWidth;
    private int windowHeight;

    // Memory tracking
    private long lastMemoryCheck = 0;
    private long usedMemoryMB = 0;
    private long allocatedMemoryMB = 0;
    private long maxMemoryMB = 0;
    private long systemTotalRAM = 0;
    private long systemFreeRAM = 0;
    private int memoryPercent = 0;

    private static final int LINE_HEIGHT = 18;
    private static final int MARGIN = 8;
    private static final float FONT_SCALE = 2.0f;

    public DebugScreen(long window) {
        this.window = window;
        this.font = new SimpleFont();

        updateWindowSize();
        getSystemRAM();

        System.out.println("[DebugScreen] Initialized with system RAM tracking");
    }

    private void getSystemRAM() {
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            systemTotalRAM = osBean.getTotalMemorySize() / (1024 * 1024);
            System.out.println("[DebugScreen] System RAM: " + systemTotalRAM + " MB");
        } catch (NoSuchMethodError e) {
            try {
                OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                @SuppressWarnings("deprecation")
                long totalRAM = osBean.getTotalPhysicalMemorySize() / (1024 * 1024);
                systemTotalRAM = totalRAM;
                System.out.println("[DebugScreen] System RAM (legacy): " + systemTotalRAM + " MB");
            } catch (Exception ex) {
                System.err.println("[DebugScreen] Could not get system RAM: " + ex.getMessage());
                systemTotalRAM = 0;
            }
        } catch (Exception e) {
            System.err.println("[DebugScreen] Could not get system RAM: " + e.getMessage());
            systemTotalRAM = 0;
        }
    }

    private void updateWindowSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);

            glfwGetWindowSize(window, width, height);

            windowWidth = width.get(0);
            windowHeight = height.get(0);
        }
    }

    public void toggle() {
        visible = !visible;
        System.out.println("Debug screen: " + (visible ? "ON" : "OFF"));
    }

    public void toggleHitboxes() {
        showHitboxes = !showHitboxes;
        System.out.println("Hitboxes: " + (showHitboxes ? "ON" : "OFF"));
    }

    public void toggleChunkBorders() {
        showChunkBorders = !showChunkBorders;
        System.out.println("Chunk borders: " + (showChunkBorders ? "ON" : "OFF"));
    }

    public void render(Camera camera, World world, GameMode gameMode, int fps, int tps, boolean vsyncEnabled) {
        if (!visible)
            return;

        updateWindowSize();
        updateMemoryStats();

        glPushAttrib(GL_ALL_ATTRIB_BITS);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_LIGHTING);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        renderLeftSide(camera, world, gameMode, fps, tps);
        renderRightSide(vsyncEnabled);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        glPopAttrib();
    }

    private void renderLeftSide(Camera camera, World world, GameMode gameMode, int fps, int tps) {
        float x = MARGIN;
        float y = MARGIN;

        drawText("Mineshaft " + Settings.VERSION, x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        y += 2;

        drawText(String.format("FPS: %d | TPS: %d", fps, tps), x, y, 1, 1, 0);
        y += LINE_HEIGHT;
        y += 2;

        drawText(String.format("XYZ: %.3f / %.3f / %.3f",
                camera.getX(), camera.getY(), camera.getZ()), x, y, 1, 1, 1);
        y += LINE_HEIGHT;

        int blockX = (int) Math.floor(camera.getX());
        int blockY = (int) Math.floor(camera.getY());
        int blockZ = (int) Math.floor(camera.getZ());
        drawText(String.format("Block: %d %d %d", blockX, blockY, blockZ), x, y, 1, 1, 1);
        y += LINE_HEIGHT;

        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        int localX = blockX & 15;
        int localZ = blockZ & 15;

        drawText(String.format("Chunk: %d %d in %d %d",
                localX, localZ, chunkX, chunkZ), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        y += 2;

        drawText(String.format("Facing: %s (%.1f / %.1f)",
                getCardinalDirection(camera.getYaw()),
                camera.getYaw(),
                camera.getPitch()), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        y += 2;

        if (world != null) {
            int lightLevel = world.getLight(blockX, blockY, blockZ);
            int skyLight = world.getSkyLight(blockX, blockY, blockZ);
            int blockLight = world.getBlockLight(blockX, blockY, blockZ);

            drawText(String.format("Light: %d (Sky: %d, Block: %d)",
                    lightLevel, skyLight, blockLight), x, y, 1, 1, 0);
            y += LINE_HEIGHT;
        }
        y += 2;

        drawText("Game Mode: " + gameMode.getName(), x, y, 0.5f, 1, 0.5f);
        y += LINE_HEIGHT;

        String state = "Walking";
        if (camera.getPlayer().isFlying()) {
            state = "Flying";
        } else if (camera.getPlayer().isInWater()) {
            state = "Swimming";
        } else if (camera.getPlayer().isSprinting()) {
            state = "Sprinting";
        }
        drawText("State: " + state, x, y, 0.5f, 1, 0.5f);
        y += LINE_HEIGHT;
        y += 2;

        drawText("Biome: Plains", x, y, 0.5f, 1, 1);
        y += LINE_HEIGHT;
        y += 2;

        // ✅ CHUNK STATS
        int renderDistance = Settings.RENDER_DISTANCE;
        drawText(String.format("Render Distance: %d chunks", renderDistance), x, y, 1, 1, 1);
        y += LINE_HEIGHT;

        int loadedChunks = world != null ? world.getLoadedChunkCount() : 0;
        drawText(String.format("Loaded Chunks: %d", loadedChunks), x, y, 1, 1, 1);
        y += LINE_HEIGHT;

        // ✅ NEW: Async generation stats
        if (world != null) {
            int pendingGen = world.getPendingGenerations();
            int activeThreads = world.getActiveGenerationThreads();

            if (pendingGen > 0 || activeThreads > 0) {
                drawText(String.format("Generating: %d pending (%d threads)",
                        pendingGen, activeThreads), x, y, 1, 0.7f, 0);
                y += LINE_HEIGHT;
            }

            // ✅ Mesh building stats
            int meshBuilds = world.getRenderer().getPendingBuilds();
            if (meshBuilds > 0) {
                drawText(String.format("Mesh Builds: %d", meshBuilds), x, y, 1, 0.7f, 0);
                y += LINE_HEIGHT;
            }
        }
        y += 2;

        if (showChunkBorders) {
            drawText("Chunk Borders: ON", x, y, 1, 0.5f, 0.5f);
            y += LINE_HEIGHT;
        }

        if (showHitboxes) {
            drawText("Hitboxes: ON", x, y, 1, 0.5f, 0.5f);
            y += LINE_HEIGHT;
        }
    }

    private void renderRightSide(boolean vsyncEnabled) {
        float y = MARGIN;

        String javaVersion = System.getProperty("java.version");
        drawTextRight("Java: " + javaVersion, y, 1, 1, 1);
        y += LINE_HEIGHT;
        y += 2;

        if (systemTotalRAM > 0) {
            long systemUsedRAM = systemTotalRAM - systemFreeRAM;
            drawTextRight(String.format("System RAM: %d / %d MB",
                    systemUsedRAM, systemTotalRAM), y, 0.7f, 0.7f, 0.7f);
            y += LINE_HEIGHT;
        }

        drawTextRight(String.format("Java Mem: %d / %d MB (%d%%)",
                usedMemoryMB, allocatedMemoryMB, memoryPercent), y, 1, 1, 0);
        y += LINE_HEIGHT;

        drawTextRight(String.format("Java Max: %d MB", maxMemoryMB), y, 0.8f, 0.8f, 0);
        y += LINE_HEIGHT;

        drawMemoryBar(y);
        y += LINE_HEIGHT;
        y += 2;

        String glVersion = GL11.glGetString(GL11.GL_VERSION);
        drawTextRight("OpenGL: " + (glVersion != null ? glVersion : "Unknown"), y, 1, 1, 1);
        y += LINE_HEIGHT;

        String glRenderer = GL11.glGetString(GL11.GL_RENDERER);
        if (glRenderer != null && glRenderer.length() > 40) {
            glRenderer = glRenderer.substring(0, 37) + "...";
        }
        drawTextRight("GPU: " + (glRenderer != null ? glRenderer : "Unknown"), y, 1, 1, 1);
        y += LINE_HEIGHT;
        y += 2;

        drawTextRight(String.format("Display: %dx%d", windowWidth, windowHeight), y, 1, 1, 1);
        y += LINE_HEIGHT;

        drawTextRight("VSync: " + (vsyncEnabled ? "ON" : "OFF"), y, 1, 1, 1);
        y += LINE_HEIGHT;
        y += 2;

        int cores = Runtime.getRuntime().availableProcessors();
        drawTextRight(String.format("CPU: %d cores", cores), y, 1, 1, 1);
        y += LINE_HEIGHT;
        y += 2;

        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        drawTextRight(String.format("OS: %s (%s)", os, arch), y, 0.7f, 0.7f, 0.7f);
        y += LINE_HEIGHT;
        y += 2;

        float helpY = windowHeight - LINE_HEIGHT * 7;
        drawTextRight("F3 + Q = Help", helpY, 0.5f, 0.5f, 0.5f);
        helpY += LINE_HEIGHT;
        drawTextRight("F3 + A = Reload chunks", helpY, 0.5f, 0.5f, 0.5f);
        helpY += LINE_HEIGHT;
        drawTextRight("F3 + B = Hitboxes", helpY, 0.5f, 0.5f, 0.5f);
        helpY += LINE_HEIGHT;
        drawTextRight("F3 + G = Chunk borders", helpY, 0.5f, 0.5f, 0.5f);
        helpY += LINE_HEIGHT;
        drawTextRight("F3 + N = Gamemode", helpY, 0.5f, 0.5f, 0.5f);
    }

    private void drawMemoryBar(float y) {
        int barWidth = 200;
        int barHeight = 10;
        float x = windowWidth - barWidth - MARGIN;

        glDisable(GL_TEXTURE_2D);
        glColor4f(0.2f, 0.2f, 0.2f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + barWidth, y);
        glVertex2f(x + barWidth, y + barHeight);
        glVertex2f(x, y + barHeight);
        glEnd();

        float usedWidth = (barWidth * memoryPercent) / 100.0f;

        float r = 1.0f;
        float g = 1.0f;
        float b = 0.0f;

        if (memoryPercent > 80) {
            g = 0.3f;
        } else if (memoryPercent > 60) {
            g = 0.7f;
        }

        glColor4f(r, g, b, 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + usedWidth, y);
        glVertex2f(x + usedWidth, y + barHeight);
        glVertex2f(x, y + barHeight);
        glEnd();

        glColor4f(1, 1, 1, 1);
        glLineWidth(1);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + barWidth, y);
        glVertex2f(x + barWidth, y + barHeight);
        glVertex2f(x, y + barHeight);
        glEnd();
    }

    private void updateMemoryStats() {
        long now = System.currentTimeMillis();
        if (now - lastMemoryCheck < 500)
            return;

        lastMemoryCheck = now;

        Runtime runtime = Runtime.getRuntime();

        long totalMemoryBytes = runtime.totalMemory();
        long freeMemoryBytes = runtime.freeMemory();
        long maxMemoryBytes = runtime.maxMemory();
        long usedMemoryBytes = totalMemoryBytes - freeMemoryBytes;

        usedMemoryMB = usedMemoryBytes / (1024 * 1024);
        allocatedMemoryMB = totalMemoryBytes / (1024 * 1024);
        maxMemoryMB = maxMemoryBytes / (1024 * 1024);

        if (allocatedMemoryMB > 0) {
            memoryPercent = (int) ((usedMemoryMB * 100) / allocatedMemoryMB);
        } else {
            memoryPercent = 0;
        }

        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            systemFreeRAM = osBean.getFreeMemorySize() / (1024 * 1024);
        } catch (NoSuchMethodError e) {
            try {
                OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                @SuppressWarnings("deprecation")
                long freeRAM = osBean.getFreePhysicalMemorySize() / (1024 * 1024);
                systemFreeRAM = freeRAM;
            } catch (Exception ex) {
                // Ignore
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private String getCardinalDirection(float yaw) {
        yaw = yaw % 360;
        if (yaw < 0)
            yaw += 360;

        if (yaw >= 337.5 || yaw < 22.5)
            return "North (Z-)";
        if (yaw >= 22.5 && yaw < 67.5)
            return "North-East";
        if (yaw >= 67.5 && yaw < 112.5)
            return "East (X+)";
        if (yaw >= 112.5 && yaw < 157.5)
            return "South-East";
        if (yaw >= 157.5 && yaw < 202.5)
            return "South (Z+)";
        if (yaw >= 202.5 && yaw < 247.5)
            return "South-West";
        if (yaw >= 247.5 && yaw < 292.5)
            return "West (X-)";
        if (yaw >= 292.5 && yaw < 337.5)
            return "North-West";

        return "Unknown";
    }

    private void drawText(String text, float x, float y, float r, float g, float b) {
        font.drawStringWithShadow(text, x, y, r, g, b, 1.0f, FONT_SCALE);
    }

    private void drawTextRight(String text, float y, float r, float g, float b) {
        int textWidth = font.getStringWidth(text, FONT_SCALE);
        float x = windowWidth - textWidth - MARGIN;
        font.drawStringWithShadow(text, x, y, r, g, b, 1.0f, FONT_SCALE);
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean showChunkBorders() {
        return showChunkBorders;
    }

    public boolean showHitboxes() {
        return showHitboxes;
    }

    public void cleanup() {
        if (font != null) {
            font.cleanup();
        }
    }
}