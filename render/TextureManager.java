package com.mineshaft.render;

import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ Texture Manager - Load and manage block textures
 */
public class TextureManager {
    
    private static final Map<String, Integer> loadedTextures = new HashMap<>();
    private static final Map<String, BufferedImage> imageCache = new HashMap<>();
    
    /**
     * Load texture from resources
     * @param path Path relative to resources/ (e.g. "assets/mineshaft/textures/blocks/grass_top.png")
     * @return OpenGL texture ID
     */
    public static int loadTexture(String path) {
        // Check cache
        if (loadedTextures.containsKey(path)) {
            return loadedTextures.get(path);
        }
        
        try {
            // Load image from resources
            InputStream stream = TextureManager.class.getClassLoader().getResourceAsStream(path);
            if (stream == null) {
                System.err.println("❌ Texture not found: " + path);
                return createMissingTexture();
            }
            
            BufferedImage image = ImageIO.read(stream);
            stream.close();
            
            int textureId = createTexture(image);
            loadedTextures.put(path, textureId);
            
            System.out.println("✅ Loaded texture: " + path + " (" + image.getWidth() + "x" + image.getHeight() + ")");
            
            return textureId;
            
        } catch (IOException e) {
            System.err.println("❌ Failed to load texture: " + path);
            e.printStackTrace();
            return createMissingTexture();
        }
    }
    
    /**
     * Load image without creating OpenGL texture (for atlas building)
     */
    public static BufferedImage loadImage(String path) {
        if (imageCache.containsKey(path)) {
            return imageCache.get(path);
        }
        
        try {
            InputStream stream = TextureManager.class.getClassLoader().getResourceAsStream(path);
            if (stream == null) {
                System.err.println("❌ Image not found: " + path);
                return createMissingImage();
            }
            
            BufferedImage image = ImageIO.read(stream);
            stream.close();
            
            imageCache.put(path, image);
            return image;
            
        } catch (IOException e) {
            System.err.println("❌ Failed to load image: " + path);
            e.printStackTrace();
            return createMissingImage();
        }
    }
    
    /**
     * Create OpenGL texture from BufferedImage
     */
    public static int createTexture(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Convert image to RGBA byte buffer
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
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        
        // Set texture parameters (pixelated look like Minecraft)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        
        // Upload texture data
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        
        return textureId;
    }
    
    /**
     * Create missing texture (pink/black checkerboard)
     */
    private static int createMissingTexture() {
        BufferedImage image = createMissingImage();
        return createTexture(image);
    }
    
    private static BufferedImage createMissingImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean checker = ((x / 8) + (y / 8)) % 2 == 0;
                image.setRGB(x, y, checker ? 0xFFFF00FF : 0xFF000000); // Pink/Black
            }
        }
        return image;
    }
    
    /**
     * Bind texture for rendering
     */
    public static void bindTexture(int textureId) {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }
    
    /**
     * Cleanup all textures
     */
    public static void cleanup() {
        for (int textureId : loadedTextures.values()) {
            glDeleteTextures(textureId);
        }
        loadedTextures.clear();
        imageCache.clear();
        System.out.println("✅ Cleaned up " + loadedTextures.size() + " textures");
    }
}