package com.mineshaft.render;

import com.mineshaft.block.Block;
import com.mineshaft.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * ✅ Block Texture Registry - FIXED
 */
public class BlockTextures {
    
    private static TextureAtlas atlas;
    private static Map<Block, BlockTextureCoords> textureMap = new HashMap<>();
    
    private static final String TEXTURE_PATH = "assets/mineshaft/textures/blocks/";
    
    /**
     * Block texture coordinates (can have different textures per face)
     */
    public static class BlockTextureCoords {
        public int[] top;
        public int[] bottom;
        public int[] north;
        public int[] south;
        public int[] east;
        public int[] west;
        
        // Constructor: All faces same
        public BlockTextureCoords(int[] all) {
            this.top = all;
            this.bottom = all;
            this.north = all;
            this.south = all;
            this.east = all;
            this.west = all;
        }
        
        // Constructor: Top, sides, bottom
        public BlockTextureCoords(int[] top, int[] sides, int[] bottom) {
            this.top = top;
            this.bottom = bottom;
            this.north = sides;
            this.south = sides;
            this.east = sides;
            this.west = sides;
        }
        
        // Constructor: All faces different
        public BlockTextureCoords(int[] top, int[] bottom, int[] north, int[] south, int[] east, int[] west) {
            this.top = top;
            this.bottom = bottom;
            this.north = north;
            this.south = south;
            this.east = east;
            this.west = west;
        }
    }
    
    /**
     * Initialize texture atlas
     */
    public static void init() {
        System.out.println("===============================================");
        System.out.println("Building Block Texture Atlas...");
        
        // Create atlas (16x16 texture grid, 256 slots total)
        atlas = new TextureAtlas(16, 16, 16);
        
        // ===== LOAD TEXTURES INTO ATLAS =====
        
        // Grass block
        int[] grassTop = atlas.addTexture(TEXTURE_PATH + "grass_top.png");
        int[] grassSide = atlas.addTexture(TEXTURE_PATH + "grass_side.png");
        int[] dirt = atlas.addTexture(TEXTURE_PATH + "dirt.png");
        
        // Stone
        int[] stone = atlas.addTexture(TEXTURE_PATH + "stone.png");
        
        // Cobblestone
        int[] cobblestone = atlas.addTexture(TEXTURE_PATH + "cobblestone.png");
        
        // Bedrock
        int[] bedrock = atlas.addTexture(TEXTURE_PATH + "bedrock.png");
        
        // Sand
        int[] sand = atlas.addTexture(TEXTURE_PATH + "sand.png");
        
        // Gravel
        int[] gravel = atlas.addTexture(TEXTURE_PATH + "gravel.png");
        
        // Wood (Oak Planks)
        int[] planks = atlas.addTexture(TEXTURE_PATH + "planks_oak.png");
        
        // Log (Oak)
        int[] logTop = atlas.addTexture(TEXTURE_PATH + "log_oak_top.png");
        int[] logSide = atlas.addTexture(TEXTURE_PATH + "log_oak.png");
        
        // Leaves (Oak)
        int[] leaves = atlas.addTexture(TEXTURE_PATH + "leaves_oak.png");
        
        // Water
        int[] water = atlas.addTexture(TEXTURE_PATH + "water_still.png");
        
        // Ores
        int[] coalOre = atlas.addTexture(TEXTURE_PATH + "coal_ore.png");
        int[] ironOre = atlas.addTexture(TEXTURE_PATH + "iron_ore.png");
        int[] goldOre = atlas.addTexture(TEXTURE_PATH + "gold_ore.png");
        int[] diamondOre = atlas.addTexture(TEXTURE_PATH + "diamond_ore.png");
        
        // Light blocks
        int[] torch = atlas.addTexture(TEXTURE_PATH + "torch_on.png");
        int[] glowstone = atlas.addTexture(TEXTURE_PATH + "glowstone.png");
        int[] lava = atlas.addTexture(TEXTURE_PATH + "lava_still.png");
        
        // Build atlas (upload to GPU)
        atlas.build();
        
        // ===== REGISTER BLOCK TEXTURES =====
        
        // ✅ Grass: top = grass_top, sides = grass_side, bottom = dirt
        textureMap.put(Blocks.GRASS, new BlockTextureCoords(grassTop, grassSide, dirt));
        
        // ✅ Dirt: all faces same
        textureMap.put(Blocks.DIRT, new BlockTextureCoords(dirt));
        
        // ✅ Stone: all faces same
        textureMap.put(Blocks.STONE, new BlockTextureCoords(stone));
        
        // ✅ Cobblestone: all faces same
        textureMap.put(Blocks.COBBLESTONE, new BlockTextureCoords(cobblestone));
        
        // ✅ Bedrock: all faces same
        textureMap.put(Blocks.BEDROCK, new BlockTextureCoords(bedrock));
        
        // ✅ Sand: all faces same
        textureMap.put(Blocks.SAND, new BlockTextureCoords(sand));
        
        // ✅ Gravel: all faces same
        textureMap.put(Blocks.GRAVEL, new BlockTextureCoords(gravel));
        
        // ✅ Planks: all faces same
        textureMap.put(Blocks.WOOD, new BlockTextureCoords(planks));
        
        // ✅ Log: top/bottom = log_top, sides = log_side
        textureMap.put(Blocks.LOG, new BlockTextureCoords(logTop, logSide, logTop));
        
        // ✅ Leaves: all faces same
        textureMap.put(Blocks.LEAVES, new BlockTextureCoords(leaves));
        
        // ✅ Water: all faces same
        textureMap.put(Blocks.WATER, new BlockTextureCoords(water));
        
        // ✅ Ores: all faces same
        textureMap.put(Blocks.COAL_ORE, new BlockTextureCoords(coalOre));
        textureMap.put(Blocks.IRON_ORE, new BlockTextureCoords(ironOre));
        textureMap.put(Blocks.GOLD_ORE, new BlockTextureCoords(goldOre));
        textureMap.put(Blocks.DIAMOND_ORE, new BlockTextureCoords(diamondOre));
        
        // ✅ Light blocks: all faces same
        textureMap.put(Blocks.TORCH, new BlockTextureCoords(torch));
        textureMap.put(Blocks.GLOWSTONE, new BlockTextureCoords(glowstone));
        textureMap.put(Blocks.LAVA, new BlockTextureCoords(lava));
        
        System.out.println("✅ Texture atlas built with " + textureMap.size() + " blocks");
        System.out.println("===============================================");
    }
    
    /**
     * Get texture coordinates for block face
     */
    public static int[] getTextureCoords(Block block, String face) {
        BlockTextureCoords coords = textureMap.get(block);
        if (coords == null) {
            return new int[]{0, 0}; // Missing texture (grass_top as fallback)
        }
        
        switch (face) {
            case "top": return coords.top;
            case "bottom": return coords.bottom;
            case "north": return coords.north;
            case "south": return coords.south;
            case "east": return coords.east;
            case "west": return coords.west;
            default: return coords.north;
        }
    }
    
    /**
     * Get UV coordinates for block face
     */
    public static float[] getUV(Block block, String face) {
        int[] texCoords = getTextureCoords(block, face);
        return atlas.getUV(texCoords[0], texCoords[1]);
    }
    
    /**
     * Bind texture atlas
     */
    public static void bind() {
        if (atlas != null) {
            atlas.bind();
        }
    }
    
    public static TextureAtlas getAtlas() {
        return atlas;
    }
    
    public static void cleanup() {
        textureMap.clear();
    }
}