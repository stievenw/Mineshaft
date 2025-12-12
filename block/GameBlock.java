package com.mineshaft.block;

import com.mineshaft.block.properties.Material;
import com.mineshaft.resource.BlockData;
import com.mineshaft.resource.ResourceManager;

/**
 * Runtime representation of a block, loaded from JSON data.
 */
public class GameBlock {
    private final BlockData data;

    public GameBlock(BlockData data) {
        this.data = data;
    }

    public String getId() {
        return data.id;
    }

    public float getHardness() {
        return data.properties.hardness;
    }

    public float getResistance() {
        return data.properties.resistance;
    }

    public boolean requiresCorrectTool() {
        return data.properties.requiresTool;
    }

    public String getToolType() {
        return data.properties.toolType;
    }

    public int getLightLevel() {
        return data.properties.lightLevel;
    }

    public int getLightOpacity() {
        return data.properties.lightOpacity;
    }

    public boolean isSolid() {
        return data.properties.isSolid;
    }

    public boolean isLiquid() {
        return data.properties.isLiquid;
    }

    public boolean isReplaceable() {
        return data.properties.isReplaceable;
    }

    public boolean isAir() {
        return "mineshaft:air".equals(data.id);
    }

    public Material getMaterial() {
        if (isAir())
            return Material.AIR;
        if (isLiquid())
            return Material.WATER;
        if (!isSolid())
            return Material.PLANT;
        return Material.STONE;
    }

    /**
     * Get texture path for a specific face.
     * 
     * @param face Face name (top, bottom, side, all)
     * @return Texture resource path
     */
    public String getTexture(String face) {
        if (data.textures.containsKey(face)) {
            return ResourceManager.getTexturePath(data.textures.get(face));
        }
        if (data.textures.containsKey("all")) {
            return ResourceManager.getTexturePath(data.textures.get("all"));
        }

        return "assets/mineshaft/textures/missing.png";
    }

    // ✅ NEW: Check if block has overlay texture
    /**
     * Check if this block has an overlay texture for a specific face.
     * Used for grass_block side overlay rendering.
     * 
     * @param face Face name (side_overlay, etc.)
     * @return true if overlay exists
     */
    public boolean hasOverlay(String face) {
        return data.textures.containsKey(face);
    }

    // ✅ NEW: Get overlay texture path
    /**
     * Get overlay texture path for a specific face.
     * 
     * @param face Face name with "_overlay" suffix (e.g., "side_overlay")
     * @return Texture resource path, or null if no overlay
     */
    public String getOverlayTexture(String face) {
        if (data.textures.containsKey(face)) {
            return ResourceManager.getTexturePath(data.textures.get(face));
        }
        return null;
    }

    // ✅ NEW: Check if block uses biome coloring
    /**
     * Check if this block should use biome-based color tinting.
     * Currently used for grass_block and oak_leaves.
     * 
     * @return true if block uses biome colors
     */
    public boolean usesBiomeColor() {
        // For now, hardcode grass_block and oak_leaves
        // Later this can be moved to JSON properties
        return "mineshaft:grass_block".equals(data.id) ||
                "mineshaft:oak_leaves".equals(data.id);
    }

    // ✅ NEW: Get biome color for this block
    /**
     * Get the biome color tint for this block.
     * Returns default green for now, will be biome-dependent later.
     * 
     * @return RGB color array [r, g, b] in range 0.0-1.0
     */
    public float[] getBiomeColor() {
        // Default grass green color (will be replaced with biome colormap later)
        if ("mineshaft:grass_block".equals(data.id)) {
            return new float[] { 0.5f, 1.0f, 0.4f }; // Brighter grass green
        }
        if ("mineshaft:oak_leaves".equals(data.id)) {
            return new float[] { 0.3f, 0.7f, 0.3f }; // Leaf green
        }
        return new float[] { 1.0f, 1.0f, 1.0f }; // White (no tint)
    }
}