// src/main/java/com/mineshaft/render/gui/screens/SingleplayerScreen.java
package com.mineshaft.render.gui.screens;

import com.mineshaft.render.SimpleFont;
import com.mineshaft.render.gui.GameState;
import com.mineshaft.render.gui.MenuManager;
import com.mineshaft.render.gui.Screen;
import com.mineshaft.render.gui.components.Button;
import com.mineshaft.world.WorldInfo;
import com.mineshaft.world.WorldSaveManager;

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * World selection screen
 */
public class SingleplayerScreen extends Screen {

    private List<WorldInfo> worldList;
    private int selectedWorldIndex = -1;
    private float scrollOffset = 0;
    private float maxScroll = 0;

    private float listX, listY, listWidth, listHeight;
    private static final int WORLD_ENTRY_HEIGHT = 60;
    private static final int WORLD_ENTRY_PADDING = 4;

    private Button playButton;
    private Button editButton;
    private Button deleteButton;

    public SingleplayerScreen(long window, SimpleFont font, MenuManager menuManager) {
        super(window, font, menuManager);
        this.title = "Select World";
    }

    @Override
    public void init() {
        listWidth = 600;
        listHeight = screenHeight - 200;
        listX = (screenWidth - listWidth) / 2.0f;
        listY = 80;

        // PERBAIKAN: Inisialisasi button dulu sebelum refreshWorldList()
        int buttonWidth = 150;
        int buttonHeight = 40;
        int bottomY = screenHeight - 60;
        int spacing = 10;

        float totalButtonWidth = buttonWidth * 5 + spacing * 4;
        float startX = (screenWidth - totalButtonWidth) / 2.0f;

        playButton = addButton(startX, bottomY, buttonWidth, buttonHeight, "Play Selected World",
                this::playSelectedWorld);
        playButton.setEnabled(false);

        addButton(startX + buttonWidth + spacing, bottomY, buttonWidth, buttonHeight, "Create New World",
                () -> menuManager.setGameState(GameState.CREATE_WORLD));

        editButton = addButton(startX + (buttonWidth + spacing) * 2, bottomY, buttonWidth, buttonHeight, "Edit",
                this::editSelectedWorld);
        editButton.setEnabled(false);

        deleteButton = addButton(startX + (buttonWidth + spacing) * 3, bottomY, buttonWidth, buttonHeight, "Delete",
                this::deleteSelectedWorld);
        deleteButton.setEnabled(false);

        addButton(startX + (buttonWidth + spacing) * 4, bottomY, buttonWidth, buttonHeight, "Cancel",
                () -> menuManager.setGameState(GameState.MAIN_MENU));

        // Sekarang baru panggil refreshWorldList() setelah semua button diinisialisasi
        refreshWorldList();
    }

    private void refreshWorldList() {
        worldList = WorldSaveManager.getWorldList();
        selectedWorldIndex = -1;
        updateButtonStates();

        int totalHeight = worldList.size() * (WORLD_ENTRY_HEIGHT + WORLD_ENTRY_PADDING);
        maxScroll = Math.max(0, totalHeight - listHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private void updateButtonStates() {
        // Tambahan null check untuk safety
        if (playButton == null || editButton == null || deleteButton == null) {
            return;
        }

        boolean hasSelection = selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size();
        playButton.setEnabled(hasSelection);
        editButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
    }

    @Override
    public void render(double mouseX, double mouseY) {
        renderBackground();
        renderTitle();

        renderWorldList((float) mouseX, (float) mouseY);

        for (Button btn : buttons) {
            btn.render(font, (float) mouseX, (float) mouseY);
        }
    }

    private void renderWorldList(float mouseX, float mouseY) {
        glDisable(GL_TEXTURE_2D);

        glColor4f(0.0f, 0.0f, 0.0f, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(listX, listY);
        glVertex2f(listX + listWidth, listY);
        glVertex2f(listX + listWidth, listY + listHeight);
        glVertex2f(listX, listY + listHeight);
        glEnd();

        glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
        glLineWidth(1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(listX, listY);
        glVertex2f(listX + listWidth, listY);
        glVertex2f(listX + listWidth, listY + listHeight);
        glVertex2f(listX, listY + listHeight);
        glEnd();

        glEnable(GL_TEXTURE_2D);

        glEnable(GL_SCISSOR_TEST);

        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        int scissorY = viewport[3] - (int) (listY + listHeight);
        glScissor((int) listX, scissorY, (int) listWidth, (int) listHeight);

        float entryY = listY + WORLD_ENTRY_PADDING - scrollOffset;

        if (worldList.isEmpty()) {
            String noWorlds = "No worlds found!";
            float scale = 1.5f;
            int textWidth = font.getStringWidth(noWorlds, scale);
            font.drawStringWithShadow(noWorlds,
                    listX + (listWidth - textWidth) / 2,
                    listY + listHeight / 2 - 8,
                    0.7f, 0.7f, 0.7f, 1.0f, scale);
        }

        for (int i = 0; i < worldList.size(); i++) {
            WorldInfo world = worldList.get(i);
            float entryEndY = entryY + WORLD_ENTRY_HEIGHT;

            if (entryEndY > listY && entryY < listY + listHeight) {
                boolean hovered = mouseX >= listX && mouseX <= listX + listWidth
                        && mouseY >= entryY && mouseY <= entryEndY
                        && mouseY >= listY && mouseY <= listY + listHeight;
                boolean selected = (i == selectedWorldIndex);

                renderWorldEntry(world, listX + 4, entryY, listWidth - 8, WORLD_ENTRY_HEIGHT,
                        hovered, selected);
            }

            entryY += WORLD_ENTRY_HEIGHT + WORLD_ENTRY_PADDING;
        }

        glDisable(GL_SCISSOR_TEST);

        if (maxScroll > 0) {
            renderScrollbar();
        }
    }

    private void renderWorldEntry(WorldInfo world, float x, float y, float width, float height,
            boolean hovered, boolean selected) {
        glDisable(GL_TEXTURE_2D);

        if (selected) {
            glColor4f(0.2f, 0.3f, 0.5f, 0.9f);
        } else if (hovered) {
            glColor4f(0.2f, 0.2f, 0.3f, 0.7f);
        } else {
            glColor4f(0.1f, 0.1f, 0.15f, 0.6f);
        }

        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        if (selected) {
            glColor4f(0.5f, 0.7f, 1.0f, 1.0f);
            glLineWidth(2.0f);
        } else {
            glColor4f(0.3f, 0.3f, 0.3f, 1.0f);
            glLineWidth(1.0f);
        }
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        glLineWidth(1.0f);

        glEnable(GL_TEXTURE_2D);

        float iconSize = height - 8;
        float iconX = x + 4;
        float iconY = y + 4;

        glDisable(GL_TEXTURE_2D);
        glColor4f(0.3f, 0.6f, 0.3f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(iconX, iconY);
        glVertex2f(iconX + iconSize, iconY);
        glVertex2f(iconX + iconSize, iconY + iconSize);
        glVertex2f(iconX, iconY + iconSize);
        glEnd();

        glColor4f(0.2f, 0.4f, 0.2f, 1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(iconX, iconY);
        glVertex2f(iconX + iconSize, iconY);
        glVertex2f(iconX + iconSize, iconY + iconSize);
        glVertex2f(iconX, iconY + iconSize);
        glEnd();
        glEnable(GL_TEXTURE_2D);

        float textX = iconX + iconSize + 10;
        font.drawStringWithShadow(world.getName(), textX, y + 10, 1, 1, 1, 1, 1.5f);

        String info = world.getGameMode() + " | " + world.getLastPlayed();
        font.drawStringWithShadow(info, textX, y + 30, 0.6f, 0.6f, 0.6f, 1, 1.0f);

        String seedInfo = "Seed: " + world.getSeed();
        font.drawStringWithShadow(seedInfo, textX, y + 42, 0.5f, 0.5f, 0.5f, 1, 1.0f);
    }

    private void renderScrollbar() {
        float scrollbarWidth = 8;
        float scrollbarX = listX + listWidth - scrollbarWidth - 2;
        float scrollbarY = listY + 2;
        float scrollbarHeight = listHeight - 4;

        glDisable(GL_TEXTURE_2D);
        glColor4f(0.1f, 0.1f, 0.1f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(scrollbarX, scrollbarY);
        glVertex2f(scrollbarX + scrollbarWidth, scrollbarY);
        glVertex2f(scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight);
        glVertex2f(scrollbarX, scrollbarY + scrollbarHeight);
        glEnd();

        float thumbHeight = Math.max(30, scrollbarHeight * (listHeight / (listHeight + maxScroll)));
        float thumbY = scrollbarY + (scrollbarHeight - thumbHeight) * (scrollOffset / maxScroll);

        glColor4f(0.5f, 0.5f, 0.5f, 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(scrollbarX, thumbY);
        glVertex2f(scrollbarX + scrollbarWidth, thumbY);
        glVertex2f(scrollbarX + scrollbarWidth, thumbY + thumbHeight);
        glVertex2f(scrollbarX, thumbY + thumbHeight);
        glEnd();
        glEnable(GL_TEXTURE_2D);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);

        if (button == 0 && mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= listY && mouseY <= listY + listHeight) {

            float entryY = listY + WORLD_ENTRY_PADDING - scrollOffset;

            for (int i = 0; i < worldList.size(); i++) {
                float entryEndY = entryY + WORLD_ENTRY_HEIGHT;

                if (mouseY >= entryY && mouseY <= entryEndY
                        && entryY < listY + listHeight && entryEndY > listY) {

                    if (selectedWorldIndex == i) {
                        playSelectedWorld();
                    } else {
                        selectedWorldIndex = i;
                        updateButtonStates();
                    }
                    break;
                }

                entryY += WORLD_ENTRY_HEIGHT + WORLD_ENTRY_PADDING;
            }
        }
    }

    @Override
    public void keyPressed(int key, int scancode, int mods) {
        switch (key) {
            case GLFW_KEY_ESCAPE:
                menuManager.setGameState(GameState.MAIN_MENU);
                break;
            case GLFW_KEY_UP:
                if (selectedWorldIndex > 0) {
                    selectedWorldIndex--;
                    updateButtonStates();
                }
                break;
            case GLFW_KEY_DOWN:
                if (selectedWorldIndex < worldList.size() - 1) {
                    selectedWorldIndex++;
                    updateButtonStates();
                }
                break;
            case GLFW_KEY_ENTER:
                if (selectedWorldIndex >= 0) {
                    playSelectedWorld();
                }
                break;
        }
    }

    public void scroll(double amount) {
        scrollOffset -= amount * 30;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
    }

    private void playSelectedWorld() {
        if (selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size()) {
            WorldInfo world = worldList.get(selectedWorldIndex);
            menuManager.loadWorld(world);
        }
    }

    private void editSelectedWorld() {
        if (selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size()) {
            WorldInfo world = worldList.get(selectedWorldIndex);
            System.out.println("Edit world: " + world.getName());
            // Future: Open edit world dialog
        }
    }

    private void deleteSelectedWorld() {
        if (selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size()) {
            WorldInfo world = worldList.get(selectedWorldIndex);
            WorldSaveManager.deleteWorld(world.getFolderName());
            refreshWorldList();
        }
    }
}