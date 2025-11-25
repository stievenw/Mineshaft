// src/main/java/com/mineshaft/render/gui/MenuManager.java
package com.mineshaft.render.gui;

import com.mineshaft.core.Settings;
import com.mineshaft.render.SimpleFont;
import com.mineshaft.render.gui.screens.*;
import com.mineshaft.world.WorldInfo;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Manages all game screens and state transitions
 */
public class MenuManager {

    private long window;
    private SimpleFont font;
    private GameState currentState = GameState.MAIN_MENU;
    private Map<GameState, Screen> screens = new HashMap<>();

    private int screenWidth;
    private int screenHeight;

    private double mouseX, mouseY;

    private Runnable onQuitGame;
    private WorldLoadCallback onWorldLoad;

    @FunctionalInterface
    public interface WorldLoadCallback {
        void onLoad(WorldInfo worldInfo);
    }

    public MenuManager(long window) {
        this.window = window;

        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        this.screenWidth = w[0];
        this.screenHeight = h[0];

        this.font = new SimpleFont();

        initScreens();
        setupCallbacks();
    }

    private void initScreens() {
        screens.put(GameState.MAIN_MENU, new MainMenuScreen(window, font, this));
        screens.put(GameState.SINGLEPLAYER, new SingleplayerScreen(window, font, this));
        screens.put(GameState.CREATE_WORLD, new CreateWorldScreen(window, font, this));
        screens.put(GameState.MULTIPLAYER, new MultiplayerScreen(window, font, this));
        screens.put(GameState.OPTIONS, new OptionsScreen(window, font, this));

        for (Screen screen : screens.values()) {
            screen.screenWidth = screenWidth;
            screen.screenHeight = screenHeight;
            screen.init();
        }
    }

    private void setupCallbacks() {
        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;

            Screen current = screens.get(currentState);
            if (current instanceof OptionsScreen) {
                ((OptionsScreen) current).mouseDragged(mouseX, mouseY);
            }
        });

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button >= 0 && button < 3) {
                if (action == GLFW_PRESS) {
                    Screen current = screens.get(currentState);
                    if (current != null) {
                        current.mouseClicked(mouseX, mouseY, button);
                    }
                } else if (action == GLFW_RELEASE) {
                    Screen current = screens.get(currentState);
                    if (current != null) {
                        current.mouseReleased(mouseX, mouseY, button);
                    }
                }
            }
        });

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                Screen current = screens.get(currentState);
                if (current != null) {
                    current.keyPressed(key, scancode, mods);
                }
            }
        });

        glfwSetCharCallback(window, (w, codepoint) -> {
            Screen current = screens.get(currentState);
            if (current != null) {
                current.charTyped((char) codepoint);
            }
        });

        glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            Screen current = screens.get(currentState);
            if (current instanceof SingleplayerScreen) {
                ((SingleplayerScreen) current).scroll(yoffset);
            }
        });

        glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            resize(width, height);
        });
    }

    public void resize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;

        for (Screen screen : screens.values()) {
            screen.resize(width, height);
        }
    }

    public void setGameState(GameState newState) {
        if (currentState != newState) {
            Screen oldScreen = screens.get(currentState);
            if (oldScreen != null) {
                oldScreen.onHide();
            }

            currentState = newState;

            Screen newScreen = screens.get(currentState);
            if (newScreen != null) {
                newScreen.onShow();
            }

            if (currentState == GameState.PLAYING) {
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            } else {
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            }

            System.out.println("[MenuManager] State changed to: " + newState);
        }
    }

    public GameState getGameState() {
        return currentState;
    }

    public boolean isInGame() {
        return currentState == GameState.PLAYING || currentState == GameState.PAUSED;
    }

    public boolean isMenuActive() {
        return currentState != GameState.PLAYING;
    }

    // ✅ PERBAIKAN: Tambah method getCurrentScreen
    public Screen getCurrentScreen() {
        return screens.get(currentState);
    }

    public void update() {
        Screen current = screens.get(currentState);
        if (current != null) {
            current.update();
        }
    }

    public void render() {
        if (currentState == GameState.PLAYING) {
            return;
        }

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_LIGHTING);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);

        Screen current = screens.get(currentState);
        if (current != null) {
            current.render(mouseX, mouseY);
        }

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    public void quitGame() {
        if (onQuitGame != null) {
            onQuitGame.run();
        } else {
            glfwSetWindowShouldClose(window, true);
        }
    }

    public void loadWorld(WorldInfo worldInfo) {
        System.out.println("[MenuManager] Loading world: " + worldInfo.getName());

        if (onWorldLoad != null) {
            onWorldLoad.onLoad(worldInfo);
        }

        setGameState(GameState.PLAYING);
    }

    public void applyVSync() {
        glfwSwapInterval(Settings.VSYNC ? 1 : 0);
    }

    public void setOnQuitGame(Runnable callback) {
        this.onQuitGame = callback;
    }

    public void setOnWorldLoad(WorldLoadCallback callback) {
        this.onWorldLoad = callback;
    }

    public void cleanup() {
        if (font != null) {
            font.cleanup();
        }
    }

    public SimpleFont getFont() {
        return font;
    }
}