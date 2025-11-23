package com.mineshaft.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ Simple bitmap font renderer using ascii.png
 * 128x128 texture with 16x16 grid (8x8 per character)
 */
public class SimpleFont {
    
    private static final int CHAR_WIDTH = 8;
    private static final int CHAR_HEIGHT = 8;
    private static final int GRID_SIZE = 16; // 16x16 grid
    
    private int fontTexture = 0;
    private boolean textureLoaded = false;
    
    public SimpleFont() {
        System.out.println("[SimpleFont] Initializing...");
        loadFontTexture();
    }
    
    /**
     * ✅ Load ascii.png from resources
     */
    private void loadFontTexture() {
        String path = "assets/mineshaft/font/ascii.png";
        
        try {
            InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
            
            if (stream == null) {
                System.err.println("⚠️ [SimpleFont] ascii.png not found at: " + path);
                System.err.println("   Creating fallback texture");
                fontTexture = generateFallbackTexture();
                textureLoaded = false;
                return;
            }
            
            BufferedImage image = ImageIO.read(stream);
            stream.close();
            
            if (image == null) {
                System.err.println("❌ [SimpleFont] Failed to decode ascii.png");
                fontTexture = generateFallbackTexture();
                textureLoaded = false;
                return;
            }
            
            System.out.println("✅ [SimpleFont] Loaded ascii.png: " + image.getWidth() + "x" + image.getHeight());
            
            // Create OpenGL texture
            fontTexture = createTexture(image);
            textureLoaded = true;
            
            System.out.println("✅ [SimpleFont] Texture ID: " + fontTexture);
            
        } catch (IOException e) {
            System.err.println("❌ [SimpleFont] Exception loading ascii.png");
            e.printStackTrace();
            fontTexture = generateFallbackTexture();
            textureLoaded = false;
        }
    }
    
    /**
     * ✅ Create OpenGL texture from BufferedImage
     */
    private int createTexture(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Convert to RGBA byte array
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                buffer.put((byte) (pixel & 0xFF));         // B
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
            }
        }
        
        buffer.flip();
        
        // Create OpenGL texture
        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texID);
        
        // CRITICAL: Use NEAREST filter for pixel-perfect font
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
        
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        
        // Unbind
        glBindTexture(GL_TEXTURE_2D, 0);
        
        return texID;
    }
    
    /**
     * ✅ Generate fallback texture (128x128, 16x16 grid)
     */
    private int generateFallbackTexture() {
        System.out.println("⚠️ [SimpleFont] Generating 128x128 fallback texture");
        
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        
        // Draw 16x16 grid with visible characters
        for (int gridY = 0; gridY < 16; gridY++) {
            for (int gridX = 0; gridX < 16; gridX++) {
                int baseX = gridX * 8;
                int baseY = gridY * 8;
                
                // Draw each 8x8 cell
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        boolean border = (x == 0 || x == 7 || y == 0 || y == 7);
                        boolean inner = (x >= 2 && x <= 5 && y >= 2 && y <= 5);
                        
                        int color;
                        if (border) {
                            color = 0xFF606060; // Gray border
                        } else if (inner) {
                            color = 0xFFFFFFFF; // White center
                        } else {
                            color = 0x00000000; // Transparent
                        }
                        
                        image.setRGB(baseX + x, baseY + y, color);
                    }
                }
            }
        }
        
        return createTexture(image);
    }
    
    /**
     * ✅ Draw string with texture
     */
    public void drawString(String text, float x, float y, float r, float g, float b, float alpha) {
        if (text == null || text.isEmpty()) return;
        
        // Save OpenGL state
        glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT);
        
        // Enable texturing
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        
        // Enable blending
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Set color (multiplied with texture)
        glColor4f(r, g, b, alpha);
        
        // Disable depth test for 2D
        glDisable(GL_DEPTH_TEST);
        
        float curX = x;
        
        for (char c : text.toCharArray()) {
            if (c == ' ') {
                curX += CHAR_WIDTH / 2; // Space width
                continue;
            }
            
            drawChar(c, curX, y);
            curX += CHAR_WIDTH;
        }
        
        // Restore state
        glPopAttrib();
        glEnable(GL_DEPTH_TEST);
    }
    
    /**
     * ✅ Draw single character from texture
     */
    private void drawChar(char c, float x, float y) {
        int ascii = (int) c;
        if (ascii < 0 || ascii >= 256) return;
        
        // Calculate grid position (16x16 grid)
        int gridX = ascii % GRID_SIZE;
        int gridY = ascii / GRID_SIZE;
        
        // Calculate texture coordinates (0.0 to 1.0)
        float u1 = (float) gridX / GRID_SIZE;
        float v1 = (float) gridY / GRID_SIZE;
        float u2 = (float) (gridX + 1) / GRID_SIZE;
        float v2 = (float) (gridY + 1) / GRID_SIZE;
        
        // Draw textured quad
        glBegin(GL_QUADS);
        glTexCoord2f(u1, v1); glVertex2f(x, y);
        glTexCoord2f(u2, v1); glVertex2f(x + CHAR_WIDTH, y);
        glTexCoord2f(u2, v2); glVertex2f(x + CHAR_WIDTH, y + CHAR_HEIGHT);
        glTexCoord2f(u1, v2); glVertex2f(x, y + CHAR_HEIGHT);
        glEnd();
    }
    
    /**
     * ✅ Draw string with shadow
     */
    public void drawStringWithShadow(String text, float x, float y, float r, float g, float b, float alpha) {
        // Shadow (black, offset by 1px)
        drawString(text, x + 1, y + 1, 0, 0, 0, alpha * 0.5f);
        // Main text
        drawString(text, x, y, r, g, b, alpha);
    }
    
    /**
     * ✅ Get width of a string
     */
    public int getStringWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        
        int width = 0;
        for (char c : text.toCharArray()) {
            if (c == ' ') {
                width += CHAR_WIDTH / 2;
            } else {
                width += CHAR_WIDTH;
            }
        }
        return width;
    }
    
    /**
     * ✅ Draw centered string
     */
    public void drawCenteredString(String text, float centerX, float y, float r, float g, float b, float alpha) {
        int width = getStringWidth(text);
        float x = centerX - (width / 2.0f);
        drawString(text, x, y, r, g, b, alpha);
    }
    
    /**
     * ✅ Cleanup
     */
    public void cleanup() {
        if (fontTexture != 0) {
            glDeleteTextures(fontTexture);
            fontTexture = 0;
            System.out.println("[SimpleFont] Cleaned up texture");
        }
    }
    
    /**
     * ✅ Check if texture loaded successfully
     */
    public boolean isTextureLoaded() {
        return textureLoaded;
    }
    
    /**
     * ✅ Get texture ID (for debugging)
     */
    public int getTextureID() {
        return fontTexture;
    }
}