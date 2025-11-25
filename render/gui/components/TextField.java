// src/main/java/com/mineshaft/render/gui/components/TextField.java
package com.mineshaft.render.gui.components;

import com.mineshaft.render.SimpleFont;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Text input field component
 */
public class TextField {
    private float x, y;
    private int width, height;
    private String text = "";
    private String placeholder = "";
    private int maxLength = 32;
    private boolean focused = false;
    private boolean enabled = true;
    private boolean visible = true;

    // Cursor
    private int cursorPosition = 0;
    private long cursorBlinkTime = 0;
    private boolean cursorVisible = true;

    // Colors
    private static final float[] BG_COLOR = { 0.0f, 0.0f, 0.0f, 0.7f };
    private static final float[] BG_FOCUSED = { 0.0f, 0.0f, 0.0f, 0.9f };
    private static final float[] BORDER_COLOR = { 0.5f, 0.5f, 0.5f, 1.0f };
    private static final float[] BORDER_FOCUSED = { 0.8f, 0.8f, 1.0f, 1.0f };

    public TextField(float x, float y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public TextField(float x, float y, int width, int height, String placeholder) {
        this(x, y, width, height);
        this.placeholder = placeholder;
    }

    public void render(SimpleFont font, float mouseX, float mouseY) {
        if (!visible)
            return;

        glDisable(GL_TEXTURE_2D);

        // Background
        float[] bgColor = focused ? BG_FOCUSED : BG_COLOR;
        glColor4f(bgColor[0], bgColor[1], bgColor[2], bgColor[3]);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        // Border
        float[] borderColor = focused ? BORDER_FOCUSED : BORDER_COLOR;
        glColor4f(borderColor[0], borderColor[1], borderColor[2], borderColor[3]);
        glLineWidth(focused ? 2.0f : 1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        glLineWidth(1.0f);

        glEnable(GL_TEXTURE_2D);

        // Text
        float textScale = 1.5f;
        float textX = x + 6;
        float textY = y + (height - 8 * textScale) / 2.0f;

        if (text.isEmpty() && !focused) {
            font.drawString(placeholder, textX, textY, 0.5f, 0.5f, 0.5f, 0.7f, textScale);
        } else {
            font.drawString(text, textX, textY, 1, 1, 1, 1, textScale);

            if (focused) {
                updateCursorBlink();
                if (cursorVisible) {
                    String textBeforeCursor = text.substring(0, Math.min(cursorPosition, text.length()));
                    float cursorX = textX + font.getStringWidth(textBeforeCursor, textScale);

                    glDisable(GL_TEXTURE_2D);
                    glColor4f(1, 1, 1, 1);
                    glLineWidth(2.0f);
                    glBegin(GL_LINES);
                    glVertex2f(cursorX, y + 4);
                    glVertex2f(cursorX, y + height - 4);
                    glEnd();
                    glLineWidth(1.0f);
                    glEnable(GL_TEXTURE_2D);
                }
            }
        }
    }

    private void updateCursorBlink() {
        long time = System.currentTimeMillis();
        if (time - cursorBlinkTime > 500) {
            cursorVisible = !cursorVisible;
            cursorBlinkTime = time;
        }
    }

    public boolean isMouseOver(float mouseX, float mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void mouseClicked(float mouseX, float mouseY) {
        if (isMouseOver(mouseX, mouseY)) {
            focused = true;
            cursorVisible = true;
            cursorBlinkTime = System.currentTimeMillis();
            cursorPosition = text.length();
        } else {
            focused = false;
        }
    }

    public void keyPressed(int key, int mods) {
        if (!focused || !enabled)
            return;

        boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;

        switch (key) {
            case GLFW_KEY_BACKSPACE:
                if (cursorPosition > 0) {
                    text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition);
                    cursorPosition--;
                }
                break;

            case GLFW_KEY_DELETE:
                if (cursorPosition < text.length()) {
                    text = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1);
                }
                break;

            case GLFW_KEY_LEFT:
                if (cursorPosition > 0) {
                    cursorPosition--;
                }
                break;

            case GLFW_KEY_RIGHT:
                if (cursorPosition < text.length()) {
                    cursorPosition++;
                }
                break;

            case GLFW_KEY_HOME:
                cursorPosition = 0;
                break;

            case GLFW_KEY_END:
                cursorPosition = text.length();
                break;

            case GLFW_KEY_V:
                if (ctrl) {
                    // Paste functionality can be added here if needed
                }
                break;
        }

        cursorVisible = true;
        cursorBlinkTime = System.currentTimeMillis();
    }

    public void charTyped(char c) {
        if (!focused || !enabled)
            return;

        if (c >= 32 && c < 127) {
            if (text.length() < maxLength) {
                text = text.substring(0, cursorPosition) + c + text.substring(cursorPosition);
                cursorPosition++;
                cursorVisible = true;
                cursorBlinkTime = System.currentTimeMillis();
            }
        }
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        this.cursorPosition = text.length();
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        if (focused) {
            cursorVisible = true;
            cursorBlinkTime = System.currentTimeMillis();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }
}