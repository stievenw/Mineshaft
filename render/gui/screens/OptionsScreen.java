// src/main/java/com/mineshaft/render/gui/screens/OptionsScreen.java
package com.mineshaft.render.gui.screens;

import com.mineshaft.core.Settings;
import com.mineshaft.render.SimpleFont;
import com.mineshaft.render.gui.GameState;
import com.mineshaft.render.gui.MenuManager;
import com.mineshaft.render.gui.Screen;
import com.mineshaft.render.gui.components.Button;
import com.mineshaft.render.gui.components.Slider;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Options/Settings screen
 */
public class OptionsScreen extends Screen {

    private List<Slider> sliders = new ArrayList<>();
    private Button vsyncButton;

    public OptionsScreen(long window, SimpleFont font, MenuManager menuManager) {
        super(window, font, menuManager);
        this.title = "Options";
    }

    @Override
    public void init() {
        sliders.clear();

        int sliderWidth = 400;
        int sliderHeight = 30;
        int buttonWidth = 400;
        int buttonHeight = 40;
        int centerX = screenWidth / 2;
        int startY = 100;
        int spacing = 50;

        Slider fovSlider = new Slider(
                centerX - sliderWidth / 2.0f,
                startY,
                sliderWidth,
                sliderHeight,
                "FOV",
                30, 110, Settings.FOV);
        fovSlider.setStep(1);
        fovSlider.setListener(value -> {
            System.out.println("FOV changed to: " + (int) value);
        });
        sliders.add(fovSlider);

        Slider renderSlider = new Slider(
                centerX - sliderWidth / 2.0f,
                startY + spacing,
                sliderWidth,
                sliderHeight,
                "Render Distance",
                2, 32, Settings.RENDER_DISTANCE);
        renderSlider.setStep(1);
        renderSlider.setListener(value -> Settings.setRenderDistance((int) value));
        sliders.add(renderSlider);

        Slider simSlider = new Slider(
                centerX - sliderWidth / 2.0f,
                startY + spacing * 2,
                sliderWidth,
                sliderHeight,
                "Simulation Distance",
                5, 32, Settings.SIMULATION_DISTANCE);
        simSlider.setStep(1);
        simSlider.setListener(value -> Settings.setSimulationDistance((int) value));
        sliders.add(simSlider);

        Slider sensSlider = new Slider(
                centerX - sliderWidth / 2.0f,
                startY + spacing * 3,
                sliderWidth,
                sliderHeight,
                "Sensitivity",
                0.05f, 1.0f, Settings.MOUSE_SENSITIVITY);
        sensSlider.setListener(value -> {
            System.out.println("Sensitivity changed to: " + value);
        });
        sliders.add(sensSlider);

        vsyncButton = addCenteredButton(startY + spacing * 4 + 20, buttonWidth, buttonHeight,
                "VSync: " + (Settings.VSYNC ? "ON" : "OFF"),
                this::toggleVSync);

        addCenteredButton(screenHeight - 60, buttonWidth, buttonHeight, "Done",
                this::saveAndClose);
    }

    private void toggleVSync() {
        Settings.VSYNC = !Settings.VSYNC;
        vsyncButton.setText("VSync: " + (Settings.VSYNC ? "ON" : "OFF"));
        menuManager.applyVSync();
    }

    private void saveAndClose() {
        menuManager.setGameState(GameState.MAIN_MENU);
    }

    @Override
    public void render(double mouseX, double mouseY) {
        renderBackground();
        renderTitle();

        for (Slider slider : sliders) {
            slider.render(font, (float) mouseX, (float) mouseY);
        }

        for (Button btn : buttons) {
            btn.render(font, (float) mouseX, (float) mouseY);
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);

        for (Slider slider : sliders) {
            slider.mouseClicked((float) mouseX, (float) mouseY);
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (Slider slider : sliders) {
            slider.mouseReleased();
        }
    }

    public void mouseDragged(double mouseX, double mouseY) {
        for (Slider slider : sliders) {
            slider.mouseDragged((float) mouseX, (float) mouseY);
        }
    }

    @Override
    public void keyPressed(int key, int scancode, int mods) {
        if (key == GLFW_KEY_ESCAPE) {
            saveAndClose();
        }
    }
}