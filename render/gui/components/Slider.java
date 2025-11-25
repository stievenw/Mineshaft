// src/main/java/com/mineshaft/render/gui/components/Slider.java
package com.mineshaft.render.gui.components;

import com.mineshaft.render.SimpleFont;

import static org.lwjgl.opengl.GL11.*;

/**
 * Slider component for settings
 */
public class Slider {
    private float x, y;
    private int width, height;
    private String label;
    private float value; // 0.0 to 1.0
    private float minValue;
    private float maxValue;
    private int step; // For integer values
    private boolean dragging = false;
    private boolean enabled = true;
    private boolean visible = true;

    // Callback
    private ValueChangeListener listener;

    @FunctionalInterface
    public interface ValueChangeListener {
        void onValueChanged(float value);
    }

    public Slider(float x, float y, int width, int height, String label, float minValue, float maxValue,
            float currentValue) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.value = (currentValue - minValue) / (maxValue - minValue);
        this.step = 0; // Continuous by default
    }

    public void render(SimpleFont font, float mouseX, float mouseY) {
        if (!visible)
            return;

        boolean hovered = isMouseOver(mouseX, mouseY);

        glDisable(GL_TEXTURE_2D);

        // Background track
        glColor4f(0.0f, 0.0f, 0.0f, 0.7f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        // Filled portion
        float fillWidth = width * value;
        glColor4f(0.3f, 0.5f, 0.3f, 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + fillWidth, y);
        glVertex2f(x + fillWidth, y + height);
        glVertex2f(x, y + height);
        glEnd();

        // Border
        float borderBrightness = (hovered || dragging) ? 0.8f : 0.5f;
        glColor4f(borderBrightness, borderBrightness, borderBrightness, 1.0f);
        glLineWidth((hovered || dragging) ? 2.0f : 1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        glLineWidth(1.0f);

        // Slider handle
        float handleX = x + fillWidth - 3;
        float handleWidth = 6;
        glColor4f(0.9f, 0.9f, 0.9f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(handleX, y);
        glVertex2f(handleX + handleWidth, y);
        glVertex2f(handleX + handleWidth, y + height);
        glVertex2f(handleX, y + height);
        glEnd();

        glEnable(GL_TEXTURE_2D);

        // Label and value text
        float textScale = 1.5f;
        float actualValue = getActualValue();
        String displayText;

        if (step > 0) {
            displayText = label + ": " + (int) actualValue;
        } else {
            displayText = label + ": " + String.format("%.1f", actualValue);
        }

        int textWidth = font.getStringWidth(displayText, textScale);
        float textX = x + (width - textWidth) / 2.0f;
        float textY = y + (height - 8 * textScale) / 2.0f;

        font.drawStringWithShadow(displayText, textX, textY, 1, 1, 1, 1, textScale);
    }

    public boolean isMouseOver(float mouseX, float mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void mouseClicked(float mouseX, float mouseY) {
        if (isMouseOver(mouseX, mouseY) && enabled) {
            dragging = true;
            updateValue(mouseX);
        }
    }

    public void mouseReleased() {
        dragging = false;
    }

    public void mouseDragged(float mouseX, float mouseY) {
        if (dragging && enabled) {
            updateValue(mouseX);
        }
    }

    private void updateValue(float mouseX) {
        float newValue = (mouseX - x) / width;
        newValue = Math.max(0, Math.min(1, newValue));

        // Apply step if set
        if (step > 0) {
            float range = maxValue - minValue;
            float actualValue = minValue + newValue * range;
            actualValue = Math.round(actualValue / step) * step;
            newValue = (actualValue - minValue) / range;
        }

        if (newValue != value) {
            value = newValue;
            if (listener != null) {
                listener.onValueChanged(getActualValue());
            }
        }
    }

    public float getActualValue() {
        return minValue + value * (maxValue - minValue);
    }

    public void setActualValue(float actualValue) {
        this.value = (actualValue - minValue) / (maxValue - minValue);
        this.value = Math.max(0, Math.min(1, this.value));
    }

    public void setListener(ValueChangeListener listener) {
        this.listener = listener;
    }

    public void setStep(int step) {
        this.step = step;
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

    public boolean isDragging() {
        return dragging;
    }
}