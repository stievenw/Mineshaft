// src/main/java/com/mineshaft/render/gui/Screen.java
package com.mineshaft.render.gui;

import com.mineshaft.render.SimpleFont;
import com.mineshaft.render.gui.components.Button;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Base class for all menu screens
 */
public abstract class Screen {
    protected long window;
    protected int screenWidth;
    protected int screenHeight;
    protected SimpleFont font;
    protected MenuManager menuManager;
    protected List<Button> buttons = new ArrayList<>();

    protected String title = "";

    public Screen(long window, SimpleFont font, MenuManager menuManager) {
        this.window = window;
        this.font = font;
        this.menuManager = menuManager;
    }

    /**
     * Initialize screen components
     */
    public abstract void init();

    /**
     * Called when screen is shown
     */
    public void onShow() {
        // Override if needed
    }

    /**
     * Called when screen is hidden
     */
    public void onHide() {
        // Override if needed
    }

    /**
     * Update screen size
     */
    public void resize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;

        // Re-initialize buttons with new positions
        buttons.clear();
        init();
    }

    /**
     * Handle mouse click
     */
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            for (Button btn : buttons) {
                if (btn.isMouseOver((float) mouseX, (float) mouseY)) {
                    btn.onClick();
                    break;
                }
            }
        }
    }

    /**
     * Handle mouse release
     */
    public void mouseReleased(double mouseX, double mouseY, int button) {
        // Override if needed
    }

    /**
     * Handle key press
     */
    public void keyPressed(int key, int scancode, int mods) {
        // Override if needed
    }

    /**
     * Handle character input
     */
    public void charTyped(char c) {
        // Override if needed
    }

    /**
     * Update logic
     */
    public void update() {
        // Override if needed
    }

    /**
     * Render the screen
     */
    public void render(double mouseX, double mouseY) {
        // Render background
        renderBackground();

        // Render title
        if (title != null && !title.isEmpty()) {
            renderTitle();
        }

        // Render buttons
        for (Button btn : buttons) {
            btn.render(font, (float) mouseX, (float) mouseY);
        }
    }

    /**
     * Render dark gradient background
     */
    protected void renderBackground() {
        glDisable(GL_TEXTURE_2D);

        // Dark gradient background
        glBegin(GL_QUADS);
        // Top - darker
        glColor4f(0.05f, 0.05f, 0.1f, 1.0f);
        glVertex2f(0, 0);
        glVertex2f(screenWidth, 0);
        // Bottom - slightly lighter
        glColor4f(0.1f, 0.1f, 0.15f, 1.0f);
        glVertex2f(screenWidth, screenHeight);
        glVertex2f(0, screenHeight);
        glEnd();

        glEnable(GL_TEXTURE_2D);
    }

    /**
     * Render dirt-style tiled background (Minecraft style)
     */
    protected void renderDirtBackground() {
        // For now, use gradient. Can add texture later
        renderBackground();
    }

    /**
     * Render screen title
     */
    protected void renderTitle() {
        float scale = 2.0f;
        int titleWidth = font.getStringWidth(title, scale);
        float titleX = (screenWidth - titleWidth) / 2.0f;
        float titleY = 40;

        font.drawStringWithShadow(title, titleX, titleY, 1, 1, 1, 1, scale);
    }

    /**
     * Add a centered button
     */
    protected Button addCenteredButton(int y, int width, int height, String text, Runnable action) {
        float x = (screenWidth - width) / 2.0f;
        Button btn = new Button(x, y, width, height, text, action);
        buttons.add(btn);
        return btn;
    }

    /**
     * Add a button at specific position
     */
    protected Button addButton(float x, float y, int width, int height, String text, Runnable action) {
        Button btn = new Button(x, y, width, height, text, action);
        buttons.add(btn);
        return btn;
    }

    /**
     * Draw centered string
     */
    protected void drawCenteredString(String text, float y, float r, float g, float b, float scale) {
        int width = font.getStringWidth(text, scale);
        float x = (screenWidth - width) / 2.0f;
        font.drawStringWithShadow(text, x, y, r, g, b, 1, scale);
    }
}