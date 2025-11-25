// src/main/java/com/mineshaft/render/gui/screens/MainMenuScreen.java
package com.mineshaft.render.gui.screens;

import com.mineshaft.core.Settings;
import com.mineshaft.render.SimpleFont;
import com.mineshaft.render.gui.GameState;
import com.mineshaft.render.gui.MenuManager;
import com.mineshaft.render.gui.Screen;

import static org.lwjgl.opengl.GL11.*;

/**
 * Main menu screen - Minecraft style
 */
public class MainMenuScreen extends Screen {

    private String splashText = "Now with menus!";
    private float splashScale = 1.0f;
    private float splashPulse = 0;

    private float titleY = -100;
    private boolean titleAnimating = true;

    private static final String[] SPLASH_TEXTS = {
            "Now with menus!",
            "100% voxel!",
            "Written in Java!",
            "OpenGL powered!",
            "Block by block!",
            "Mine and craft!",
            "Infinite worlds!",
            "16x16x16 chunks!",
            "LWJGL inside!",
            "Procedurally generated!"
    };

    public MainMenuScreen(long window, SimpleFont font, MenuManager menuManager) {
        super(window, font, menuManager);
        this.title = "";

        splashText = SPLASH_TEXTS[(int) (Math.random() * SPLASH_TEXTS.length)];
    }

    @Override
    public void init() {
        int buttonWidth = 400;
        int buttonHeight = 40;
        int centerY = screenHeight / 2;

        addCenteredButton(centerY - 30, buttonWidth, buttonHeight, "Singleplayer",
                () -> menuManager.setGameState(GameState.SINGLEPLAYER));

        addCenteredButton(centerY + 30, buttonWidth, buttonHeight, "Multiplayer",
                () -> menuManager.setGameState(GameState.MULTIPLAYER));

        int halfWidth = (buttonWidth - 10) / 2;
        float leftX = (screenWidth - buttonWidth) / 2.0f;
        float rightX = leftX + halfWidth + 10;

        addButton(leftX, centerY + 100, halfWidth, buttonHeight, "Options",
                () -> menuManager.setGameState(GameState.OPTIONS));

        addButton(rightX, centerY + 100, halfWidth, buttonHeight, "Quit Game",
                () -> menuManager.quitGame());
    }

    @Override
    public void onShow() {
        titleY = -100;
        titleAnimating = true;
    }

    @Override
    public void update() {
        if (titleAnimating) {
            float targetY = 60;
            titleY += (targetY - titleY) * 0.1f;
            if (Math.abs(titleY - targetY) < 0.5f) {
                titleY = targetY;
                titleAnimating = false;
            }
        }

        splashPulse += 0.1f;
        splashScale = 1.0f + (float) Math.sin(splashPulse) * 0.1f;
    }

    @Override
    public void render(double mouseX, double mouseY) {
        renderBackground();
        renderGameTitle();
        renderSplashText();

        for (var btn : buttons) {
            btn.render(font, (float) mouseX, (float) mouseY);
        }

        renderFooter();
    }

    private void renderGameTitle() {
        String titleText = "MINESHAFT";
        float titleScale = 4.0f;
        int titleWidth = font.getStringWidth(titleText, titleScale);
        float titleX = (screenWidth - titleWidth) / 2.0f;

        font.drawString(titleText, titleX + 4, titleY + 4, 0.1f, 0.1f, 0.1f, 0.8f, titleScale);
        font.drawString(titleText, titleX, titleY, 1.0f, 0.9f, 0.2f, 1.0f, titleScale);

        String subtitle = "Voxel Engine";
        float subScale = 1.5f;
        int subWidth = font.getStringWidth(subtitle, subScale);
        float subX = (screenWidth - subWidth) / 2.0f;
        font.drawStringWithShadow(subtitle, subX, titleY + 40, 0.7f, 0.7f, 0.7f, 1.0f, subScale);
    }

    private void renderSplashText() {
        if (titleAnimating)
            return;

        float splashX = screenWidth / 2.0f + 150;
        float splashY = titleY + 60;

        glPushMatrix();
        glTranslatef(splashX, splashY, 0);
        glRotatef(-20, 0, 0, 1);
        glScalef(splashScale, splashScale, 1);

        float scale = 1.5f;
        int textWidth = font.getStringWidth(splashText, scale);
        font.drawStringWithShadow(splashText, -textWidth / 2.0f, 0, 1.0f, 1.0f, 0.0f, 1.0f, scale);

        glPopMatrix();
    }

    private void renderFooter() {
        String version = Settings.VERSION;
        font.drawStringWithShadow(version, 5, screenHeight - 15, 0.7f, 0.7f, 0.7f, 1.0f, 1.0f);

        String copyright = "Not affiliated with Mojang";
        int copyrightWidth = font.getStringWidth(copyright, 1.0f);
        font.drawStringWithShadow(copyright, screenWidth - copyrightWidth - 5, screenHeight - 15,
                0.7f, 0.7f, 0.7f, 1.0f, 1.0f);
    }

    @Override
    protected void renderBackground() {
        glDisable(GL_TEXTURE_2D);

        float time = (System.currentTimeMillis() % 10000) / 10000.0f;
        float offset = (float) Math.sin(time * Math.PI * 2) * 0.05f;

        glBegin(GL_QUADS);
        glColor4f(0.05f + offset, 0.02f, 0.15f + offset, 1.0f);
        glVertex2f(0, 0);
        glVertex2f(screenWidth, 0);
        glColor4f(0.02f, 0.02f, 0.05f, 1.0f);
        glVertex2f(screenWidth, screenHeight);
        glVertex2f(0, screenHeight);
        glEnd();

        renderStars();

        glEnable(GL_TEXTURE_2D);
    }

    private void renderStars() {
        glPointSize(2.0f);
        glBegin(GL_POINTS);

        java.util.Random rand = new java.util.Random(12345);
        for (int i = 0; i < 100; i++) {
            float sx = rand.nextFloat() * screenWidth;
            float sy = rand.nextFloat() * (screenHeight * 0.6f);
            float brightness = 0.3f + rand.nextFloat() * 0.5f;

            float twinkle = (float) Math.sin((System.currentTimeMillis() / 500.0) + i) * 0.2f;
            brightness += twinkle;

            glColor4f(brightness, brightness, brightness, brightness);
            glVertex2f(sx, sy);
        }

        glEnd();
        glPointSize(1.0f);
    }
}