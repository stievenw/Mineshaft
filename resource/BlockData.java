package com.mineshaft.resource;

import java.util.Map;

/**
 * POJO for loading block data from JSON.
 */
public class BlockData {
    public String id;
    public String type; // e.g., "solid", "liquid", "air"
    public BlockPropertiesData properties;
    public Map<String, String> textures;

    public static class BlockPropertiesData {
        public float hardness = 0.0f;
        public float resistance = 0.0f;
        public int lightLevel = 0;
        public int lightOpacity = 0; // 0 = transparent, 15 = fully opaque
        public String toolType = "none";
        public boolean requiresTool = false;
        public boolean isSolid = true;
        public boolean isLiquid = false;
        public boolean isReplaceable = false;
    }
}
