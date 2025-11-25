// src/main/java/com/mineshaft/render/gui/screens/MultiplayerScreen.java
package com.mineshaft.render.gui.screens;

import com.mineshaft.render.SimpleFont;
import com.mineshaft.render.gui.GameState;
import com.mineshaft.render.gui.MenuManager;
import com.mineshaft.render.gui.Screen;
import com.mineshaft.render.gui.components.Button;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Multiplayer server selection screen (placeholder)
 */
public class MultiplayerScreen extends Screen {

    public MultiplayerScreen(long window, SimpleFont font, MenuManager menuManager) {
        super(window, font, menuManager);
        this.title = "Play Multiplayer";
    }

    @Override
    public void init() {
        int buttonWidth = 300;
        int buttonHeight = 40;
        int centerY = screenHeight / 2;

        addCenteredButton(centerY - 30, buttonWidth, buttonHeight, "Direct Connect",
                () -> System.out.println("Direct Connect not implemented"));

        addCenteredButton(centerY + 30, buttonWidth, buttonHeight, "Add Server",
                () -> System.out.println("Add Server not implemented"));

        addCenteredButton(centerY + 100, buttonWidth, buttonHeight, "Cancel",
                () -> menuManager.setGameState(GameState.MAIN_MENU));
    }

    @Override
    public void render(double mouseX, double mouseY) {
        renderBackground();
        renderTitle();

        drawCenteredString("Multiplayer is not yet implemented", 200, 0.8f, 0.8f, 0.8f, 1.5f);
        drawCenteredString("Check back in a future update!", 230, 0.6f, 0.6f, 0.6f, 1.2f);

        for (Button btn : buttons) {
            btn.render(font, (float) mouseX, (float) mouseY);
        }
    }

    @Override
    public void keyPressed(int key, int scancode, int mods) {
        if (key == GLFW_KEY_ESCAPE) {
            menuManager.setGameState(GameState.MAIN_MENU);
        }
    }
}