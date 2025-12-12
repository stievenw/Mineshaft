package com.mineshaft.render;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ Chat overlay - Message box lowered
 */
public class ChatOverlay {

    private final long window;
    private boolean chatOpen = false;
    private StringBuilder currentInput = new StringBuilder();
    private List<String> chatHistory = new ArrayList<>();
    private List<String> messageLog = new ArrayList<>();

    private static final int MAX_MESSAGES = 10;
    private static final int MAX_HISTORY = 100;
    private long lastMessageTime = 0;
    private static final long MESSAGE_FADE_TIME = 5000;

    private int screenWidth = 1280;
    private int screenHeight = 720;

    private String pendingMessage = null;
    private boolean ignoreNextChar = false;
    private int historyIndex = -1;

    // ✅ ADJUSTED POSITIONS
    private static final float TEXT_SCALE = 3.0f;
    private static final float LINE_HEIGHT = 30.0f;
    private static final float INPUT_HEIGHT = 36.0f;
    private static final float CHAT_Y_OFFSET = 150.0f; // ✅ Messages lowered (200 → 150)
    private static final float INPUT_Y_OFFSET = 80.0f; // Input box position
    private static final float CHAT_WIDTH = 700.0f;
    private static final float CHAT_PADDING = 6.0f;

    private SimpleFont font;

    public ChatOverlay(long window) {
        this.window = window;

        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        this.screenWidth = w[0];
        this.screenHeight = h[0];

        font = new SimpleFont();

        // ✅ FIX: Don't set up callbacks here - they will be handled by MenuManager
        // to avoid overwriting menu key handlers

        addMessage("§eChat system initialized!");
        addMessage("§7Press T to chat, / for commands");

        System.out.println("✅ [Chat] Initialized with bigger font");
    }

    // ✅ FIX: Removed setupCallbacks() - callbacks now handled by MenuManager
    // This method was overwriting MenuManager's key callback, breaking pause menu
    // ESC handler

    // ✅ Reference to MenuManager for pausing game
    private com.mineshaft.render.gui.MenuManager menuManager;

    /**
     * ✅ Set MenuManager reference for game pause functionality
     */
    public void setMenuManager(com.mineshaft.render.gui.MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    /**
     * ✅ Handle key press - called externally from game loop
     */
    public void keyPressed(int key, int scancode, int mods) {
        if (chatOpen) {
            handleChatKeyPress(key, GLFW_PRESS);
        } else {
            handleGameKeyPress(key);
        }
    }

    /**
     * ✅ Handle character input - called externally from game loop
     */
    public void charTyped(char codepoint) {
        if (!chatOpen)
            return;

        if (ignoreNextChar) {
            ignoreNextChar = false;
            return;
        }

        if (codepoint >= 32 && codepoint < 127) {
            if (currentInput.length() < 100) {
                currentInput.append(codepoint);
                historyIndex = -1;
            }
        }
    }

    private void handleChatKeyPress(int key, int action) {
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) {
            if (action == GLFW_PRESS) {
                sendMessage();
                closeChat();
            }
            return;
        }

        if (key == GLFW_KEY_ESCAPE) {
            if (action == GLFW_PRESS) {
                closeChat();
            }
            return;
        }

        if (key == GLFW_KEY_BACKSPACE) {
            if (currentInput.length() > 0) {
                currentInput.deleteCharAt(currentInput.length() - 1);
                historyIndex = -1;
            }
            return;
        }

        if (key == GLFW_KEY_V && action == GLFW_PRESS) {
            if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS ||
                    glfwGetKey(window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS) {
                pasteFromClipboard();
            }
            return;
        }

        if (key == GLFW_KEY_UP && action == GLFW_PRESS) {
            navigateHistory(1);
            return;
        }

        if (key == GLFW_KEY_DOWN && action == GLFW_PRESS) {
            navigateHistory(-1);
            return;
        }
    }

    private void handleGameKeyPress(int key) {
        // ✅ ESC key opens pause menu
        if (key == GLFW_KEY_ESCAPE) {
            if (menuManager != null) {
                menuManager.setGameState(com.mineshaft.render.gui.GameState.PAUSED);
            }
            return;
        }

        if (key == GLFW_KEY_T) {
            openChat();
            ignoreNextChar = true;
            return;
        }

        if (key == GLFW_KEY_SLASH) {
            openChatWithCommand();
            ignoreNextChar = true;
            return;
        }
    }

    private void navigateHistory(int direction) {
        if (chatHistory.isEmpty())
            return;

        historyIndex += direction;

        if (historyIndex < -1) {
            historyIndex = -1;
        } else if (historyIndex >= chatHistory.size()) {
            historyIndex = chatHistory.size() - 1;
        }

        currentInput.setLength(0);
        if (historyIndex >= 0) {
            currentInput.append(chatHistory.get(chatHistory.size() - 1 - historyIndex));
        }
    }

    public void openChat() {
        chatOpen = true;
        currentInput.setLength(0);
        historyIndex = -1;
        System.out.println("[Chat] Opened");
    }

    public void openChatWithCommand() {
        chatOpen = true;
        currentInput.setLength(0);
        currentInput.append("/");
        historyIndex = -1;
        System.out.println("[Chat] Opened with command");
    }

    private void sendMessage() {
        String message = currentInput.toString().trim();

        if (!message.isEmpty()) {
            chatHistory.add(message);
            if (chatHistory.size() > MAX_HISTORY) {
                chatHistory.remove(0);
            }

            pendingMessage = message;

            System.out.println("[Chat] Sent: " + message);
        }

        currentInput.setLength(0);
        historyIndex = -1;
    }

    private void closeChat() {
        chatOpen = false;
        currentInput.setLength(0);
        ignoreNextChar = false;
        historyIndex = -1;
        System.out.println("[Chat] Closed");
    }

    private void pasteFromClipboard() {
        String clipboard = glfwGetClipboardString(window);
        if (clipboard != null && !clipboard.isEmpty()) {
            for (char c : clipboard.toCharArray()) {
                if (c >= 32 && c < 127 && currentInput.length() < 100) {
                    currentInput.append(c);
                }
            }
            historyIndex = -1;
        }
    }

    public String getPendingMessage() {
        String msg = pendingMessage;
        pendingMessage = null;
        return msg;
    }

    public void addMessage(String message) {
        messageLog.add(message);
        if (messageLog.size() > MAX_MESSAGES) {
            messageLog.remove(0);
        }
        lastMessageTime = System.currentTimeMillis();
        System.out.println("[Chat] " + message);
    }

    /**
     * ✅ Message box LOWERED position
     */
    public void render() {
        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        screenWidth = w[0];
        screenHeight = h[0];

        glPushAttrib(GL_ALL_ATTRIB_BITS);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_LIGHTING);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // ✅ Messages position - LOWERED
        float chatY = screenHeight - CHAT_Y_OFFSET;

        // ✅ Render message log
        if (chatOpen || (System.currentTimeMillis() - lastMessageTime < MESSAGE_FADE_TIME)) {
            float alpha = chatOpen ? 1.0f
                    : Math.max(0, 1.0f - (System.currentTimeMillis() - lastMessageTime) / (float) MESSAGE_FADE_TIME);

            for (int i = 0; i < messageLog.size(); i++) {
                String message = messageLog.get(messageLog.size() - 1 - i);
                float msgY = chatY - (i * LINE_HEIGHT);

                // Background
                glColor4f(0, 0, 0, 0.6f * alpha);
                drawRect(5, msgY - CHAT_PADDING, CHAT_WIDTH, LINE_HEIGHT);

                // Text with color support
                renderColoredText(message, 10, msgY, TEXT_SCALE, alpha);
            }
        }

        // ✅ Render input box
        if (chatOpen) {
            float inputY = screenHeight - INPUT_Y_OFFSET;

            // Background
            glColor4f(0, 0, 0, 0.9f);
            drawRect(5, inputY - CHAT_PADDING, screenWidth - 10, INPUT_HEIGHT);

            // Border
            glColor4f(1, 1, 1, 1);
            drawRectOutline(5, inputY - CHAT_PADDING, screenWidth - 10, INPUT_HEIGHT);

            // Input text with cursor
            String displayText = currentInput.toString();
            boolean showCursor = (System.currentTimeMillis() / 500) % 2 == 0;
            String textWithCursor = displayText + (showCursor ? "_" : " ");

            // Draw text (Yellow)
            font.drawString(textWithCursor, 10, inputY, 1.0f, 1.0f, 0.0f, 1.0f, TEXT_SCALE);
        }

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        glPopAttrib();
    }

    /**
     * ✅ Render text with Minecraft-style color codes (§)
     */
    private void renderColoredText(String text, float x, float y, float scale, float alpha) {
        float currentX = x;
        float r = 1.0f, g = 1.0f, b = 1.0f;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Check for color code
            if (c == '§' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                float[] color = getColorFromCode(code);
                if (color != null) {
                    r = color[0];
                    g = color[1];
                    b = color[2];
                    i++; // Skip the color code character
                    continue;
                }
            }

            // Draw character
            String charStr = String.valueOf(c);
            font.drawString(charStr, currentX, y, r, g, b, alpha, scale);

            // Move X position
            if (c == ' ') {
                currentX += (8 / 2) * scale;
            } else {
                currentX += 8 * scale;
            }
        }
    }

    /**
     * ✅ Minecraft color codes
     */
    private float[] getColorFromCode(char code) {
        switch (code) {
            case '0':
                return new float[] { 0.0f, 0.0f, 0.0f }; // Black
            case '1':
                return new float[] { 0.0f, 0.0f, 0.67f }; // Dark Blue
            case '2':
                return new float[] { 0.0f, 0.67f, 0.0f }; // Dark Green
            case '3':
                return new float[] { 0.0f, 0.67f, 0.67f }; // Dark Aqua
            case '4':
                return new float[] { 0.67f, 0.0f, 0.0f }; // Dark Red
            case '5':
                return new float[] { 0.67f, 0.0f, 0.67f }; // Dark Purple
            case '6':
                return new float[] { 1.0f, 0.67f, 0.0f }; // Gold
            case '7':
                return new float[] { 0.67f, 0.67f, 0.67f }; // Gray
            case '8':
                return new float[] { 0.33f, 0.33f, 0.33f }; // Dark Gray
            case '9':
                return new float[] { 0.33f, 0.33f, 1.0f }; // Blue
            case 'a':
                return new float[] { 0.33f, 1.0f, 0.33f }; // Green
            case 'b':
                return new float[] { 0.33f, 1.0f, 1.0f }; // Aqua
            case 'c':
                return new float[] { 1.0f, 0.33f, 0.33f }; // Red
            case 'd':
                return new float[] { 1.0f, 0.33f, 1.0f }; // Light Purple
            case 'e':
                return new float[] { 1.0f, 1.0f, 0.33f }; // Yellow
            case 'f':
                return new float[] { 1.0f, 1.0f, 1.0f }; // White
            default:
                return null;
        }
    }

    private void drawRect(float x, float y, float width, float height) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
    }

    private void drawRectOutline(float x, float y, float width, float height) {
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
    }

    public boolean isChatOpen() {
        return chatOpen;
    }

    public void updateSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    public String getCurrentInput() {
        return currentInput.toString();
    }

    public List<String> getMessageLog() {
        return new ArrayList<>(messageLog);
    }

    public List<String> getChatHistory() {
        return new ArrayList<>(chatHistory);
    }

    public void cleanup() {
        // ✅ FIX: No longer setting callbacks, so no need to clear them

        if (font != null) {
            font.cleanup();
        }
    }
}