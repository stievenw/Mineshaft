package com.mineshaft.render;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ Chat overlay with SIMPLE font rendering
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
    
    // ✅ Use SIMPLE font (no texture needed)
    private SimpleFont font;
    
    public ChatOverlay(long window) {
        this.window = window;
        
        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        this.screenWidth = w[0];
        this.screenHeight = h[0];
        
        // ✅ Initialize simple font
        font = new SimpleFont();
        
        setupCallbacks();
        
        // ✅ Test message
        addMessage("Chat system initialized!");
        
        System.out.println("✅ [Chat] Initialized with SimpleFont renderer");
    }
    
    private void setupCallbacks() {
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFW_RELEASE) return;
            
            if (chatOpen) {
                handleChatKeyPress(key, action);
            } else {
                handleGameKeyPress(key);
            }
        });
        
        glfwSetCharCallback(window, (w, codepoint) -> {
            if (!chatOpen) return;
            
            if (ignoreNextChar) {
                ignoreNextChar = false;
                return;
            }
            
            if (codepoint >= 32 && codepoint < 127) {
                if (currentInput.length() < 100) {
                    currentInput.append((char) codepoint);
                    historyIndex = -1;
                }
            }
        });
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
        if (chatHistory.isEmpty()) return;
        
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
    
    private void openChat() {
        chatOpen = true;
        currentInput.setLength(0);
        historyIndex = -1;
        System.out.println("[Chat] Opened");
    }
    
    private void openChatWithCommand() {
        chatOpen = true;
        currentInput.setLength(0);
        currentInput.append("/");
        historyIndex = -1;
        System.out.println("[Chat] Opened with command");
    }
    
    private void sendMessage() {
        String message = currentInput.toString().trim();
        
        if (!message.isEmpty()) {
            addMessage("> " + message);
            
            chatHistory.add(message);
            if (chatHistory.size() > MAX_HISTORY) {
                chatHistory.remove(0);
            }
            
            pendingMessage = message;
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
     * ✅ FIXED: Render with simple font (GUARANTEED VISIBLE)
     */
    public void render() {
        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        screenWidth = w[0];
        screenHeight = h[0];
        
        // ✅ Save current OpenGL state
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        
        // ✅ Setup 2D rendering
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // ✅ Disable stuff that might interfere
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_LIGHTING);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        float chatY = screenHeight - 60;
        
        // ✅ Render message log
        if (chatOpen || (System.currentTimeMillis() - lastMessageTime < MESSAGE_FADE_TIME)) {
            float alpha = chatOpen ? 1.0f : Math.max(0, 1.0f - (System.currentTimeMillis() - lastMessageTime) / (float) MESSAGE_FADE_TIME);
            
            for (int i = 0; i < messageLog.size(); i++) {
                String message = messageLog.get(messageLog.size() - 1 - i);
                float msgY = chatY - (i * 14);
                
                // Background
                glColor4f(0, 0, 0, 0.6f * alpha);
                drawRect(5, msgY - 2, 500, 14);
                
                // Text
                font.drawString(message, 10, msgY, 1.0f, 1.0f, 1.0f, alpha);
            }
        }
        
        // ✅ Render input box
        if (chatOpen) {
            float inputY = screenHeight - 35;
            
            // Background
            glColor4f(0, 0, 0, 0.9f);
            drawRect(5, inputY - 2, screenWidth - 10, 18);
            
            // Border
            glColor4f(1, 1, 1, 1);
            drawRectOutline(5, inputY - 2, screenWidth - 10, 18);
            
            // Input text with cursor
            String displayText = currentInput.toString();
            boolean showCursor = (System.currentTimeMillis() / 500) % 2 == 0;
            String textWithCursor = displayText + (showCursor ? "_" : " ");
            
            // Draw text
            font.drawString(textWithCursor, 10, inputY, 1.0f, 1.0f, 0.0f, 1.0f); // Yellow text
        }
        
        // ✅ Restore OpenGL state
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        
        glPopAttrib();
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
        glfwSetCharCallback(window, null);
        glfwSetKeyCallback(window, null);
        
        if (font != null) {
            font.cleanup();
        }
    }
}