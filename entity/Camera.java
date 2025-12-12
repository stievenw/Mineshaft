package com.mineshaft.entity;

import com.mineshaft.core.Settings;
import com.mineshaft.player.Player;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ FIXED - Mouse processed every frame via callback
 */
public class Camera {
    private final Player player;
    private final long window;

    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private boolean firstMouse = true;

    private static final float MOUSE_SENSITIVITY = Settings.MOUSE_SENSITIVITY;

    public Camera(Player player, long window) {
        this.player = player;
        this.window = window;
        // ✅ FIX: Don't set up callback here - will be handled by game loop
        // to avoid overwriting MenuManager's cursor callback
    }

    // ✅ FIX: Removed setupMouseCallback() - cursorcallback now handled by game loop
    // This method was overwriting MenuManager's cursor callback

    /**
     * ✅ Process mouse movement - called externally from game loop when playing
     */
    public void processMouse(double xpos, double ypos) {
        if (firstMouse) {
            lastMouseX = xpos;
            lastMouseY = ypos;
            firstMouse = false;
            return;
        }

        float xoffset = (float) (xpos - lastMouseX);
        float yoffset = (float) (lastMouseY - ypos);

        lastMouseX = xpos;
        lastMouseY = ypos;

        processMouseDelta(xoffset, yoffset);
    }

    /**
     * Reset first mouse flag when camera is re-enabled
     */
    public void resetMouse() {
        firstMouse = true;
    }

    private void processMouseDelta(float xoffset, float yoffset) {
        xoffset *= MOUSE_SENSITIVITY;
        yoffset *= MOUSE_SENSITIVITY;

        float newYaw = player.getYaw() + xoffset;
        float newPitch = player.getPitch() - yoffset;

        if (newPitch > 89.0f)
            newPitch = 89.0f;
        if (newPitch < -89.0f)
            newPitch = -89.0f;

        while (newYaw > 360.0f)
            newYaw -= 360.0f;
        while (newYaw < 0.0f)
            newYaw += 360.0f;

        player.setRotation(newYaw, newPitch);
    }

    public void applyTranslations(float partialTicks) {
        // Standard OpenGL camera: Pitch (X-axis), then Yaw (Y-axis)
        // Order must match third-person camera for consistency
        glRotatef(player.getPitch(), 1, 0, 0);
        glRotatef(player.getYaw(), 0, 1, 0);

        float renderX = player.getRenderX(partialTicks);
        float renderY = player.getRenderY(partialTicks) + player.getCurrentEyeHeight();
        float renderZ = player.getRenderZ(partialTicks);

        glTranslatef(-renderX, -renderY, -renderZ);
    }

    /**
     * ✅ REVISED v2: Apply visual effects (fog, atmosphere) for both underwater and
     * land
     * 
     * - Uses isHeadInWater() for correct POV when partially submerged
     * - Land fog is BRUTAL like Minecraft Alpha for horror-like atmosphere
     */
    public void applyUnderwaterEffect() {
        applyUnderwaterEffect(null);
    }

    public void applyUnderwaterEffect(float[] skyColor) {
        glEnable(GL_FOG);
        glHint(GL_FOG_HINT, GL_NICEST);

        FloatBuffer fogColorBuffer = BufferUtils.createFloatBuffer(4);

        // ✅ FIX: Use HEAD position for underwater view, not body
        if (player.isHeadInWater()) {
            // ========== UNDERWATER EFFECT ==========
            glFogi(GL_FOG_MODE, GL_LINEAR);
            glFogf(GL_FOG_START, 1.0f);
            glFogf(GL_FOG_END, 20.0f);

            // Blue underwater fog
            fogColorBuffer.put(0.1f);
            fogColorBuffer.put(0.3f);
            fogColorBuffer.put(0.6f);
            fogColorBuffer.put(1.0f);
            fogColorBuffer.flip();
            glFogfv(GL_FOG_COLOR, fogColorBuffer);

            // Blue tint overlay
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            glMatrixMode(GL_PROJECTION);
            glPushMatrix();
            glLoadIdentity();
            glOrtho(0, 1, 1, 0, -1, 1);

            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            glLoadIdentity();

            glDisable(GL_DEPTH_TEST);
            glDisable(GL_TEXTURE_2D);
            glColor4f(0.0f, 0.15f, 0.4f, 0.25f);
            glBegin(GL_QUADS);
            glVertex2f(0, 0);
            glVertex2f(1, 0);
            glVertex2f(1, 1);
            glVertex2f(0, 1);
            glEnd();
            glEnable(GL_TEXTURE_2D);
            glEnable(GL_DEPTH_TEST);

            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
            glPopMatrix();

            glColor3f(1, 1, 1);
            glDisable(GL_BLEND);

        } else if (Settings.ENABLE_FOG) {
            // ========== BRUTAL MINECRAFT ALPHA FOG ==========
            // Very close, very dense - horror-like atmosphere!
            glFogi(GL_FOG_MODE, GL_LINEAR);

            // ✅ BRUTAL FOG: Starts VERY close, ends at moderate distance
            // This creates the classic Minecraft Alpha horror feel
            float fogStart = 8.0f; // Fog starts just 8 blocks away!
            float fogEnd = Settings.RENDER_DISTANCE * 8.0f; // Dense fog

            glFogf(GL_FOG_START, fogStart);
            glFogf(GL_FOG_END, fogEnd);

            // Use sky color for fog (slightly darker for atmosphere)
            if (skyColor != null) {
                // Darken sky color slightly for more atmosphere
                fogColorBuffer.put(skyColor[0] * 0.9f);
                fogColorBuffer.put(skyColor[1] * 0.9f);
                fogColorBuffer.put(skyColor[2] * 0.9f);
            } else {
                // Default gloomy fog color
                fogColorBuffer.put(0.6f);
                fogColorBuffer.put(0.7f);
                fogColorBuffer.put(0.8f);
            }
            fogColorBuffer.put(1.0f);
            fogColorBuffer.flip();
            glFogfv(GL_FOG_COLOR, fogColorBuffer);
        } else {
            glDisable(GL_FOG);
        }
    }

    public float[] getForwardVector() {
        float yawRad = (float) Math.toRadians(player.getYaw());
        float pitchRad = (float) Math.toRadians(player.getPitch());

        // OpenGL coordinate system:
        // Yaw 0° = -Z (North), 90° = +X (East), 180° = +Z (South), 270° = -X (West)
        float x = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
        float y = (float) (-Math.sin(pitchRad));
        float z = (float) (-Math.cos(yawRad) * Math.cos(pitchRad));

        return new float[] { x, y, z };
    }

    public float getX(float partialTicks) {
        return player.getRenderX(partialTicks);
    }

    public float getY(float partialTicks) {
        return player.getRenderY(partialTicks);
    }

    public float getZ(float partialTicks) {
        return player.getRenderZ(partialTicks);
    }

    public float getX() {
        return player.getX();
    }

    public float getY() {
        return player.getY();
    }

    public float getZ() {
        return player.getZ();
    }

    public float getYaw() {
        return player.getYaw();
    }

    public float getPitch() {
        return player.getPitch();
    }

    public void setPosition(float x, float y, float z) {
        player.setPosition(x, y, z);
    }

    public Player getPlayer() {
        return player;
    }
}