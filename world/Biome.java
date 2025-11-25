// src/main/java/com/mineshaft/world/Biome.java
package com.mineshaft.world;

/**
 * ✅ Minecraft-style biome system
 * Determines terrain features, vegetation, and block types
 */
public enum Biome {
    DEEP_OCEAN("Deep Ocean", -60, -30, 0.5, 0.5, false),
    OCEAN("Ocean", -40, 40, 0.5, 0.5, false),
    BEACH("Beach", 62, 66, 0.8, 0.4, false),
    PLAINS("Plains", 64, 70, 0.8, 0.4, true),
    FOREST("Forest", 64, 75, 0.7, 0.8, true),
    TAIGA("Taiga", 64, 75, 0.25, 0.8, true),
    HILLS("Hills", 75, 95, 0.7, 0.6, true),
    MOUNTAINS("Mountains", 100, 180, 0.2, 0.3, false),
    SNOWY_PEAKS("Snowy Peaks", 140, 220, -0.5, 0.3, false),
    DESERT("Desert", 64, 70, 2.0, 0.0, false);

    private final String name;
    private final int minHeight;
    private final int maxHeight;
    private final double temperature;
    private final double humidity;
    private final boolean canHaveTrees;

    Biome(String name, int minHeight, int maxHeight, double temperature, double humidity, boolean canHaveTrees) {
        this.name = name;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.temperature = temperature;
        this.humidity = humidity;
        this.canHaveTrees = canHaveTrees;
    }

    public String getName() {
        return name;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getHumidity() {
        return humidity;
    }

    public boolean canHaveTrees() {
        return canHaveTrees;
    }

    /**
     * Get tree spawn chance for this biome
     */
    public double getTreeDensity() {
        switch (this) {
            case FOREST:
                return 0.08; // 8% - dense forest
            case TAIGA:
                return 0.06; // 6% - moderate forest
            case PLAINS:
                return 0.01; // 1% - sparse trees
            default:
                return 0.0; // No trees
        }
    }

    /**
     * Determine biome from continent noise, temperature, and humidity
     */
    public static Biome getBiome(double continentNoise, double temperature, double humidity, int height) {
        // Deep ocean
        if (continentNoise < -0.4) {
            return DEEP_OCEAN;
        }

        // Ocean
        if (continentNoise < -0.1) {
            return OCEAN;
        }

        // Beach (coastal transition)
        if (continentNoise < 0.05 && height >= 62 && height <= 66) {
            return BEACH;
        }

        // High elevation = mountains
        if (height > 140) {
            return SNOWY_PEAKS;
        }
        if (height > 95) {
            return MOUNTAINS;
        }
        if (height > 75) {
            return HILLS;
        }

        // Land biomes based on temperature and humidity
        if (temperature > 1.5) {
            return DESERT; // Hot and dry
        }

        if (temperature < 0.4) {
            if (humidity > 0.6) {
                return TAIGA; // Cold and wet
            }
            return PLAINS; // Cold and dry
        }

        if (humidity > 0.6) {
            return FOREST; // Temperate and wet
        }

        return PLAINS; // Default temperate
    }
}
