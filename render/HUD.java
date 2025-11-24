package com.mineshaft.render;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;

/**
 * HUD rendering using Minecraft GUI textures with stabilization
 * UV coordinates based on JSON grid configuration
 */
public class HUD {
    private final long window;
    private int screenWidth;
    private int screenHeight;
    private int widgetsTexture;

    // Texture atlas size (widgets.png is 256x256)
    private static final int ATLAS_SIZE = 256;

    // Crosshair configuration (from JSON)
    private static final int CROSSHAIR_GRID_X = 15;
    private static final int CROSSHAIR_GRID_Y = 0;
    private static final int CROSSHAIR_GRID_SIZE = 16;

    // Hotbar slots configuration (from JSON) - UPDATED
    private static final float SLOT_GRID_SIZE = 20.166f;
    private static final int SLOT_GRID_Y = 0;

    // Selected slot configuration (from JSON)
    private static final int SELECTED_SLOT_GRID_X = 0;
    private static final int SELECTED_SLOT_GRID_Y = 1;
    private static final int SELECTED_SLOT_GRID_SIZE = 22;

    // Stabilization cache
    private float cachedCenterX = 0;
    private float cachedCenterY = 0;
    private float cachedHotbarY = 0;

    public HUD(long window) {
        this.window = window;
        updateScreenSize();
        loadTextures();
    }

    private void loadTextures() {
        String path = "assets/mineshaft/textures/gui/widgets.png";

        try {
            InputStream stream = getClass().getClassLoader().getResourceAsStream(path);

            if (stream == null) {
                System.err.println("❌ [HUD] widgets.png not found at: " + path);
                widgetsTexture = -1;
                return;
            }

            BufferedImage image = ImageIO.read(stream);
            stream.close();

            if (image == null) {
                System.err.println("❌ [HUD] Failed to decode widgets.png");
                widgetsTexture = -1;
                return;
            }

            System.out.println("✅ [HUD] Loaded widgets.png: " + image.getWidth() + "x" + image.getHeight());

            widgetsTexture = createTexture(image);

            System.out.println("✅ [HUD] Texture ID: " + widgetsTexture);
            System.out.println("📐 [HUD] Crosshair UV: (" + (CROSSHAIR_GRID_X * CROSSHAIR_GRID_SIZE) + ", "
                    + (CROSSHAIR_GRID_Y * CROSSHAIR_GRID_SIZE) + ") size: " + CROSSHAIR_GRID_SIZE);
            System.out.println("📐 [HUD] Slot size: " + SLOT_GRID_SIZE + "x" + SLOT_GRID_SIZE + " (precise)");
            System.out.println("📐 [HUD] Selected slot UV: (" + (SELECTED_SLOT_GRID_X * SELECTED_SLOT_GRID_SIZE) + ", "
                    + (SELECTED_SLOT_GRID_Y * SELECTED_SLOT_GRID_SIZE) + ") size: " + SELECTED_SLOT_GRID_SIZE);

        } catch (Exception e) {
            System.err.println("❌ [HUD] Exception loading widgets.png: " + e.getMessage());
            e.printStackTrace();
            widgetsTexture = -1;
        }
    }

    private int createTexture(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];

                buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                buffer.put((byte) ((pixel >> 8) & 0xFF)); // G
                buffer.put((byte) (pixel & 0xFF)); // B
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
            }
        }

        buffer.flip();

        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texID);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        glBindTexture(GL_TEXTURE_2D, 0);

        return texID;
    }

    private void updateScreenSize() {
        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        this.screenWidth = w[0];
        this.screenHeight = h[0];

        // Update cached stable positions
        updateStablePositions();
    }

    /**
     * Calculate and cache stable pixel-aligned positions
     */
    private void updateStablePositions() {
        // Round to nearest pixel for stability
        cachedCenterX = snapToPixel(screenWidth / 2.0f);
        cachedCenterY = snapToPixel(screenHeight / 2.0f);
        cachedHotbarY = snapToPixel(screenHeight - 68.0f); // Fixed distance from bottom
    }

    /**
     * Snap coordinate to nearest pixel to prevent sub-pixel jittering
     */
    private float snapToPixel(float value) {
        return Math.round(value);
    }

    public void render(int selectedSlot) {
        updateScreenSize();

        // Save ALL GL state
        glPushAttrib(GL_ALL_ATTRIB_BITS);

        // Save matrices
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();

        // Use integer coordinates for stable orthographic projection
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        // Setup 2D rendering state
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_LIGHTING);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);

        // Clear depth to ensure HUD is always on top
        glClear(GL_DEPTH_BUFFER_BIT);

        // Disable any potential camera transforms
        glLoadIdentity();

        renderCrosshair();
        renderHotbar(selectedSlot);

        // Restore matrices
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        // Restore ALL GL state
        glPopAttrib();
    }

    private void renderCrosshair() {
        if (widgetsTexture <= 0) {
            // Fallback: simple white crosshair with pixel snapping
            glDisable(GL_TEXTURE_2D);

            float centerX = cachedCenterX;
            float centerY = cachedCenterY;

            glLineWidth(2.0f);
            glColor4f(1, 1, 1, 1);
            glBegin(GL_LINES);
            // Horizontal line
            glVertex2f(snapToPixel(centerX - 8), centerY);
            glVertex2f(snapToPixel(centerX + 8), centerY);
            // Vertical line
            glVertex2f(centerX, snapToPixel(centerY - 8));
            glVertex2f(centerX, snapToPixel(centerY + 8));
            glEnd();
            glLineWidth(1.0f);
            glEnable(GL_TEXTURE_2D);
            return;
        }

        float centerX = cachedCenterX;
        float centerY = cachedCenterY;

        // Crosshair texture size and scale
        float textureSize = CROSSHAIR_GRID_SIZE; // 16 pixels
        float scale = 2.0f; // Scale factor for rendering
        float renderSize = snapToPixel(textureSize * scale);

        glBindTexture(GL_TEXTURE_2D, widgetsTexture);
        glColor4f(1, 1, 1, 1);

        // Calculate UV coordinates based on grid position
        // Crosshair is at grid_x=15, grid_y=0, grid_size=16
        // Texture position: x = 15 * 16 = 240, y = 0 * 16 = 0
        float texX = CROSSHAIR_GRID_X * CROSSHAIR_GRID_SIZE; // 240
        float texY = CROSSHAIR_GRID_Y * CROSSHAIR_GRID_SIZE; // 0

        float u1 = texX / (float) ATLAS_SIZE; // 240/256
        float v1 = texY / (float) ATLAS_SIZE; // 0/256
        float u2 = (texX + CROSSHAIR_GRID_SIZE) / (float) ATLAS_SIZE; // 256/256
        float v2 = (texY + CROSSHAIR_GRID_SIZE) / (float) ATLAS_SIZE; // 16/256

        // Calculate and snap to pixel boundaries
        float halfSize = renderSize / 2;
        float x = snapToPixel(centerX - halfSize);
        float y = snapToPixel(centerY - halfSize);

        glBegin(GL_QUADS);
        glTexCoord2f(u1, v1);
        glVertex2f(x, y);
        glTexCoord2f(u2, v1);
        glVertex2f(x + renderSize, y);
        glTexCoord2f(u2, v2);
        glVertex2f(x + renderSize, y + renderSize);
        glTexCoord2f(u1, v2);
        glVertex2f(x, y + renderSize);
        glEnd();

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void renderHotbar(int selectedSlot) {
        if (widgetsTexture <= 0) {
            renderFallbackHotbar(selectedSlot);
            return;
        }

        glBindTexture(GL_TEXTURE_2D, widgetsTexture);

        // Hotbar slot dimensions with precise grid size
        float slotTextureSize = SLOT_GRID_SIZE; // 20.166 pixels in texture
        float scale = 3.0f; // Scale factor for rendering
        float scaledSlotSize = snapToPixel(slotTextureSize * scale); // ~60.5 pixels -> 61 pixels
        float totalWidth = snapToPixel(9 * scaledSlotSize); // Total width for 9 slots

        // Calculate stable positions
        float startX = snapToPixel((screenWidth - totalWidth) / 2.0f);
        float startY = cachedHotbarY;

        glColor4f(1, 1, 1, 1);

        // Draw each slot with pixel-perfect positioning
        for (int i = 0; i < 9; i++) {
            float slotX = snapToPixel(startX + i * scaledSlotSize);

            // Calculate UV coordinates for this slot using precise grid size
            // Slots are at grid_y=0, grid_x=0 to 8, grid_size=20.166
            // Slot 0: texture position (0, 0)
            // Slot 1: texture position (20.166, 0)
            // Slot i: texture position (i*20.166, 0)
            float texX = i * SLOT_GRID_SIZE;
            float texY = SLOT_GRID_Y * SLOT_GRID_SIZE;

            float u1 = texX / (float) ATLAS_SIZE;
            float v1 = texY / (float) ATLAS_SIZE;
            float u2 = (texX + SLOT_GRID_SIZE) / (float) ATLAS_SIZE;
            float v2 = (texY + SLOT_GRID_SIZE) / (float) ATLAS_SIZE;

            glBegin(GL_QUADS);
            glTexCoord2f(u1, v1);
            glVertex2f(slotX, startY);
            glTexCoord2f(u2, v1);
            glVertex2f(slotX + scaledSlotSize, startY);
            glTexCoord2f(u2, v2);
            glVertex2f(slotX + scaledSlotSize, startY + scaledSlotSize);
            glTexCoord2f(u1, v2);
            glVertex2f(slotX, startY + scaledSlotSize);
            glEnd();
        }

        // Draw selection highlight using the selected slot texture
        float selectionX = snapToPixel(startX + selectedSlot * scaledSlotSize);

        // Selected slot texture dimensions
        float selectedTextureSize = SELECTED_SLOT_GRID_SIZE; // 22x22 pixels
        float selectedRenderSize = snapToPixel(selectedTextureSize * scale); // 66 pixels

        // Calculate UV coordinates for selected slot
        // Selected slot is at grid_x=0, grid_y=1, grid_size=22
        // Texture position: x = 0 * 22 = 0, y = 1 * 22 = 22
        float selTexX = SELECTED_SLOT_GRID_X * SELECTED_SLOT_GRID_SIZE; // 0
        float selTexY = SELECTED_SLOT_GRID_Y * SELECTED_SLOT_GRID_SIZE; // 22

        float selU1 = selTexX / (float) ATLAS_SIZE; // 0/256
        float selV1 = selTexY / (float) ATLAS_SIZE; // 22/256
        float selU2 = (selTexX + SELECTED_SLOT_GRID_SIZE) / (float) ATLAS_SIZE; // 22/256
        float selV2 = (selTexY + SELECTED_SLOT_GRID_SIZE) / (float) ATLAS_SIZE; // 44/256

        // Center the selection highlight (22x22 @ scale 3 = 66px) over the slot
        // (20.166x20.166 @ scale 3 = ~61px)
        // Offset = (66 - 61) / 2 = 2.5 pixels -> snap to 3 or 2
        float offset = snapToPixel((selectedRenderSize - scaledSlotSize) / 2.0f);
        float selX = snapToPixel(selectionX - offset);
        float selY = snapToPixel(startY - offset);

        glBegin(GL_QUADS);
        glTexCoord2f(selU1, selV1);
        glVertex2f(selX, selY);
        glTexCoord2f(selU2, selV1);
        glVertex2f(selX + selectedRenderSize, selY);
        glTexCoord2f(selU2, selV2);
        glVertex2f(selX + selectedRenderSize, selY + selectedRenderSize);
        glTexCoord2f(selU1, selV2);
        glVertex2f(selX, selY + selectedRenderSize);
        glEnd();

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void renderFallbackHotbar(int selectedSlot) {
        glDisable(GL_TEXTURE_2D);

        float slotSize = 40.0f;
        float padding = 4.0f;
        float totalWidth = snapToPixel(9 * (slotSize + padding) + padding);

        float startX = snapToPixel((screenWidth - totalWidth) / 2.0f);
        float startY = snapToPixel(screenHeight - slotSize - padding * 2 - 20);

        // Background
        glColor4f(0, 0, 0, 0.6f);
        glBegin(GL_QUADS);
        glVertex2f(startX, startY);
        glVertex2f(startX + totalWidth, startY);
        glVertex2f(startX + totalWidth, startY + slotSize + padding * 2);
        glVertex2f(startX, startY + slotSize + padding * 2);
        glEnd();

        // Slots
        for (int i = 0; i < 9; i++) {
            float slotX = snapToPixel(startX + padding + i * (slotSize + padding));
            float slotY = snapToPixel(startY + padding);

            // Selection highlight
            if (i == selectedSlot) {
                glColor4f(1, 1, 1, 0.9f);
                glLineWidth(3.0f);
                glBegin(GL_LINE_LOOP);
                glVertex2f(slotX - 3, slotY - 3);
                glVertex2f(slotX + slotSize + 3, slotY - 3);
                glVertex2f(slotX + slotSize + 3, slotY + slotSize + 3);
                glVertex2f(slotX - 3, slotY + slotSize + 3);
                glEnd();
                glLineWidth(1.0f);
            }

            // Slot background
            glColor4f(0.2f, 0.2f, 0.2f, 0.9f);
            glBegin(GL_QUADS);
            glVertex2f(slotX, slotY);
            glVertex2f(slotX + slotSize, slotY);
            glVertex2f(slotX + slotSize, slotY + slotSize);
            glVertex2f(slotX, slotY + slotSize);
            glEnd();
        }

        glEnable(GL_TEXTURE_2D);
    }

    public void updateSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        updateStablePositions();
    }

    public void cleanup() {
        if (widgetsTexture > 0) {
            glDeleteTextures(widgetsTexture);
        }
    }
}