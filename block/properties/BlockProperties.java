package com.mineshaft.block.properties;

/**
 * Properties for blocks (FIXED version)
 */
public class BlockProperties {
    private Material material = Material.STONE;
    private float hardness = 1.0f;
    private float resistance = 1.0f;
    private boolean requiresCorrectTool = false;
    private String toolType = "hand";
    private int lightLevel = 0;
    private int lightOpacity = 0; // 0 = transparent, 15 = fully opaque
    private float[] color = { 1.0f, 1.0f, 1.0f };

    private BlockProperties() {
    }

    public static BlockProperties of(Material material) {
        BlockProperties properties = new BlockProperties();
        properties.material = material;
        return properties;
    }

    public static BlockProperties copy(com.mineshaft.block.GameBlock block) {
        BlockProperties properties = new BlockProperties();
        properties.material = block.getMaterial();
        properties.hardness = block.getHardness();
        properties.resistance = block.getResistance();
        return properties;
    }

    public BlockProperties strength(float hardness) {
        this.hardness = hardness;
        this.resistance = hardness;
        return this;
    }

    public BlockProperties strength(float hardness, float resistance) {
        this.hardness = hardness;
        this.resistance = resistance;
        return this;
    }

    public BlockProperties unbreakable() {
        this.hardness = -1.0f;
        this.resistance = 3600000.0f;
        return this;
    }

    /**
     * ✅ FIXED: Renamed to avoid conflict with getter
     */
    public BlockProperties requiresCorrectToolForDrops() {
        this.requiresCorrectTool = true;
        return this;
    }

    public BlockProperties tool(String toolType) {
        this.toolType = toolType;
        return this;
    }

    public BlockProperties lightLevel(int level) {
        this.lightLevel = Math.max(0, Math.min(15, level));
        return this;
    }

    public BlockProperties lightOpacity(int opacity) {
        this.lightOpacity = Math.max(0, Math.min(15, opacity));
        return this;
    }

    public BlockProperties color(float r, float g, float b) {
        this.color = new float[] { r, g, b };
        return this;
    }

    // Getters
    public Material getMaterial() {
        return material;
    }

    public float getHardness() {
        return hardness;
    }

    public float getResistance() {
        return resistance;
    }

    /**
     * ✅ FIXED: Proper getter name
     */
    public boolean isRequiresCorrectTool() {
        return requiresCorrectTool;
    }

    public String getToolType() {
        return toolType;
    }

    public int getLightLevel() {
        return lightLevel;
    }

    public int getLightOpacity() {
        return lightOpacity;
    }

    public float[] getColor() {
        return color;
    }
}