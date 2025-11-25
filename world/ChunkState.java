// src/main/java/com/mineshaft/world/ChunkState.java
package com.mineshaft.world;

/**
 * ⚡ Chunk lifecycle states for async generation
 */
public enum ChunkState {
    EMPTY, // Chunk created but terrain not generated
    GENERATING, // Terrain generation in progress (background thread)
    GENERATED, // Terrain generated, waiting for lighting
    LIGHT_PENDING, // Lighting calculation queued
    READY // Fully ready to render
}