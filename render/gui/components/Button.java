// src/main/java/com/mineshaft/render/gui/components/Button.java
package com.mineshaft.render.gui.components;

import com.mineshaft.render.SimpleFont;

import static org.lwjgl.opengl.GL11.*;

/**
 * Minecraft-style button component
 */
public class Button {
    private float x, y;
    private int width, height;
    private String text;
    private Runnable action;
    private boolean enabled = true;
    private boolean visible = true;

    // Button states
    private boolean hovered = false;

    // Colors
    private static final float[] COLOR_NORMAL = { 0.2f, 0.2f, 0.2f, 0.8f };
    private static final float[] COLOR_HOVERED = { 0.3f, 0.4f, 0.5f, 0.9f };
    private static final float[] COLOR_DISABLED = { 0.1f, 0.1f, 0.1f, 0.5f };
    private static final float[] BORDER_COLOR = { 0.0f, 0.0f, 0.0f, 1.0f };
    private static final float[] BORDER_HIGHLIGHT = { 0.5f, 0.6f, 0.7f, 1.0f };

    public Button(float x, float y, int width, int height, String text, Runnable action) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
        this.action = action;
    }

    public void render(SimpleFont font, float mouseX, float mouseY) {
        if (!visible)
            return;

        hovered = isMouseOver(mouseX, mouseY) && enabled;

        glDisable(GL_TEXTURE_2D);

        // Select color based on state
        float[] bgColor;
        if (!enabled) {
            bgColor = COLOR_DISABLED;
        } else if (hovered) {
            bgColor = COLOR_HOVERED;
        } else {
            bgColor = COLOR_NORMAL;
        }

        // Draw button background with gradient
        glBegin(GL_QUADS);
        // Top - slightly lighter
        glColor4f(bgColor[0] + 0.1f, bgColor[1] + 0.1f, bgColor[2] + 0.1f, bgColor[3]);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        // Bottom - base color
        glColor4f(bgColor[0], bgColor[1], bgColor[2], bgColor[3]);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        // Draw border
        float[] borderColor = hovered ? BORDER_HIGHLIGHT : BORDER_COLOR;
        glColor4f(borderColor[0], borderColor[1], borderColor[2], borderColor[3]);
        glLineWidth(hovered ? 2.0f : 1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        glLineWidth(1.0f);

        // Draw inner highlight (top-left)
        if (enabled && !hovered) {
            glColor4f(1, 1, 1, 0.1f);
            glBegin(GL_LINES);
            glVertex2f(x + 1, y + 1);
            glVertex2f(x + width - 1, y + 1);
            glVertex2f(x + 1, y + 1);
            glVertex2f(x + 1, y + height - 1);
            glEnd();
        }

        glEnable(GL_TEXTURE_2D);

        // Draw text
        float textScale = 1.5f;
        int textWidth = font.getStringWidth(text, textScale);
        float textX = x + (width - textWidth) / 2.0f;
        float textY = y + (height - 8 * textScale) / 2.0f;

        if (enabled) {
            float textBrightness = hovered ? 1.0f : 0.9f;
            font.drawStringWithShadow(text, textX, textY, textBrightness, textBrightness, textBrightness, 1, textScale);
        } else {
            font.drawStringWithShadow(text, textX, textY, 0.5f, 0.5f, 0.5f, 1, textScale);
        }
    }

    public boolean isMouseOver(float mouseX, float mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void onClick() {
        if (enabled && action != null) {
            action.run();
        }
    }

    // Getters and setters
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setAction(Runnable action) {
        this.action = action;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}