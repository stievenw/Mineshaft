// src/main/java/com/mineshaft/render/gui/screens/CreateWorldScreen.java
package com.mineshaft.render.gui.screens;

import com.mineshaft.render.SimpleFont;
import com.mineshaft.render.gui.GameState;
import com.mineshaft.render.gui.MenuManager;
import com.mineshaft.render.gui.Screen;
import com.mineshaft.render.gui.components.Button;
import com.mineshaft.render.gui.components.TextField;
import com.mineshaft.world.WorldInfo;
import com.mineshaft.world.WorldSaveManager;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Create new world screen
 */
public class CreateWorldScreen extends Screen {

    private TextField worldNameField;
    private TextField seedField;

    private Button gameModeButton;

    private String[] gameModes = { "Survival", "Creative", "Hardcore" };
    private int currentGameMode = 1;

    public CreateWorldScreen(long window, SimpleFont font, MenuManager menuManager) {
        super(window, font, menuManager);
        this.title = "Create New World";
    }

    @Override
    public void init() {
        int fieldWidth = 400;
        int fieldHeight = 30;
        int centerX = screenWidth / 2;
        int startY = 120;
        int spacing = 60;

        worldNameField = new TextField(
                centerX - fieldWidth / 2.0f,
                startY,
                fieldWidth,
                fieldHeight,
                "World Name");
        worldNameField.setText("New World");
        worldNameField.setMaxLength(32);

        seedField = new TextField(
                centerX - fieldWidth / 2.0f,
                startY + spacing,
                fieldWidth,
                fieldHeight,
                "Seed (leave blank for random)");
        seedField.setMaxLength(20);

        int buttonWidth = 400;
        int buttonHeight = 40;

        gameModeButton = addCenteredButton(startY + spacing * 2, buttonWidth, buttonHeight,
                "Game Mode: " + gameModes[currentGameMode],
                this::cycleGameMode);

        addCenteredButton(startY + spacing * 3, buttonWidth, buttonHeight,
                "More World Options...",
                () -> System.out.println("More options not implemented yet"));

        int bottomY = screenHeight - 60;
        int halfWidth = (buttonWidth - 10) / 2;
        float leftX = (screenWidth - buttonWidth) / 2.0f;
        float rightX = leftX + halfWidth + 10;

        addButton(leftX, bottomY, halfWidth, buttonHeight, "Create New World",
                this::createWorld);

        addButton(rightX, bottomY, halfWidth, buttonHeight, "Cancel",
                () -> menuManager.setGameState(GameState.SINGLEPLAYER));
    }

    @Override
    public void onShow() {
        int worldCount = WorldSaveManager.getWorldList().size();
        worldNameField.setText("New World" + (worldCount > 0 ? " " + (worldCount + 1) : ""));
        seedField.setText("");
        worldNameField.setFocused(true);
    }

    private void cycleGameMode() {
        currentGameMode = (currentGameMode + 1) % gameModes.length;
        gameModeButton.setText("Game Mode: " + gameModes[currentGameMode]);
    }

    private void createWorld() {
        String worldName = worldNameField.getText().trim();
        if (worldName.isEmpty()) {
            worldName = "New World";
        }

        long seed;
        String seedText = seedField.getText().trim();
        if (seedText.isEmpty()) {
            seed = System.currentTimeMillis();
        } else {
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException e) {
                seed = seedText.hashCode();
            }
        }

        WorldInfo worldInfo = new WorldInfo(
                worldName,
                WorldSaveManager.generateFolderName(worldName),
                seed,
                gameModes[currentGameMode],
                System.currentTimeMillis());

        WorldSaveManager.createWorld(worldInfo);
        menuManager.loadWorld(worldInfo);
    }

    @Override
    public void render(double mouseX, double mouseY) {
        renderBackground();
        renderTitle();

        float labelX = (screenWidth - 400) / 2.0f;
        font.drawStringWithShadow("World Name:", labelX, 100, 0.8f, 0.8f, 0.8f, 1, 1.2f);
        font.drawStringWithShadow("Seed:", labelX, 160, 0.8f, 0.8f, 0.8f, 1, 1.2f);

        worldNameField.render(font, (float) mouseX, (float) mouseY);
        seedField.render(font, (float) mouseX, (float) mouseY);

        for (Button btn : buttons) {
            btn.render(font, (float) mouseX, (float) mouseY);
        }

        drawCenteredString("Will be saved in: saves/" + WorldSaveManager.generateFolderName(worldNameField.getText()),
                screenHeight - 100, 0.5f, 0.5f, 0.5f, 1.0f);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);

        worldNameField.mouseClicked((float) mouseX, (float) mouseY);
        seedField.mouseClicked((float) mouseX, (float) mouseY);
    }

    @Override
    public void keyPressed(int key, int scancode, int mods) {
        if (key == GLFW_KEY_ESCAPE) {
            menuManager.setGameState(GameState.SINGLEPLAYER);
            return;
        }

        if (key == GLFW_KEY_TAB) {
            if (worldNameField.isFocused()) {
                worldNameField.setFocused(false);
                seedField.setFocused(true);
            } else {
                seedField.setFocused(false);
                worldNameField.setFocused(true);
            }
            return;
        }

        if (key == GLFW_KEY_ENTER) {
            createWorld();
            return;
        }

        worldNameField.keyPressed(key, mods);
        seedField.keyPressed(key, mods);
    }

    @Override
    public void charTyped(char c) {
        worldNameField.charTyped(c);
        seedField.charTyped(c);
    }
}