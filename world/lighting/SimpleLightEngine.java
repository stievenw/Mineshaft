package com.mineshaft.world.lighting;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.world.Chunk;
import com.mineshaft.world.World;

import java.util.*;

/**
 * ⚡ SimpleLightEngine - Straightforward Minecraft-style lighting
 * 
 * Design goals:
 * - SIMPLE: Easy to understand and debug
 * - CORRECT: Light propagates properly into caves
 * - FAST: Minimal overhead, no complex queuing
 * 
 * Algorithm:
 * 1. When block broken: Recalculate light from neighbors (flood fill)
 * 2. When block placed: Block light and recalculate shadows
 * 3. Full BFS for proper cave lighting
 */
public class SimpleLightEngine {

    private final World world;

    // Direction vectors for 6-way spread
    private static final int[][] DIRS = {
            { 1, 0, 0 }, { -1, 0, 0 },
            { 0, 1, 0 }, { 0, -1, 0 },
            { 0, 0, 1 }, { 0, 0, -1 }
    };

    public SimpleLightEngine(World world) {
        this.world = world;
        System.out.println("[SimpleLightEngine] Initialized");
    }

    // ========== CHUNK INITIALIZATION ==========

    /**
     * ✅ Initialize lighting for a newly generated chunk
     * Step 1: Vertical skylight propagation
     * Step 2: Full BFS spread into caves
     */
    public void initializeChunk(Chunk chunk) {
        if (chunk == null)
            return;

        // Step 1: Vertical skylight (fast column-based)
        initializeVerticalSkylight(chunk);

        // Step 2: Spread light into caves using BFS
        spreadLightInChunk(chunk);

        // Step 3: Initialize block lights (torches, etc.)
        initializeBlockLights(chunk);

        chunk.setLightInitialized(true);
    }

    /**
     * Simple vertical skylight - marks sky-visible blocks
     */
    private void initializeVerticalSkylight(Chunk chunk) {
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                int light = 15;

                for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
                    int y = Settings.indexToWorldY(index);
                    GameBlock block = chunk.getBlock(x, y, z);

                    if (block == null || block.isAir()) {
                        chunk.setSkyLight(x, y, z, light);
                    } else if (isTransparent(block)) {
                        light = Math.max(0, light - 1);
                        chunk.setSkyLight(x, y, z, light);
                    } else {
                        // Solid - light stops but DON'T reset to 0 yet
                        // We'll handle cave spreading in next step
                        chunk.setSkyLight(x, y, z, 0);
                        light = 0;
                    }
                }
            }
        }
    }

    /**
     * ✅ BFS to spread light horizontally into caves
     */
    private void spreadLightInChunk(Chunk chunk) {
        Deque<int[]> queue = new ArrayDeque<>();

        // Queue all blocks that have light > 0 for spreading
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                for (int index = 0; index < Chunk.CHUNK_HEIGHT; index++) {
                    int y = Settings.indexToWorldY(index);
                    int light = chunk.getSkyLight(x, y, z);

                    if (light > 1) {
                        queue.add(new int[] { x, y, z, light });
                    }
                }
            }
        }

        // BFS spread
        while (!queue.isEmpty()) {
            int[] node = queue.poll();
            int x = node[0], y = node[1], z = node[2], light = node[3];

            for (int[] dir : DIRS) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                // Stay within chunk for now
                if (nx < 0 || nx >= Chunk.CHUNK_SIZE)
                    continue;
                if (nz < 0 || nz >= Chunk.CHUNK_SIZE)
                    continue;
                if (!Settings.isValidWorldY(ny))
                    continue;

                GameBlock neighbor = chunk.getBlock(nx, ny, nz);

                // Skip solid blocks
                if (neighbor != null && neighbor.isSolid() && !isTransparent(neighbor)) {
                    continue;
                }

                int newLight = light - 1;
                if (newLight <= 0)
                    continue;

                int currentLight = chunk.getSkyLight(nx, ny, nz);
                if (newLight > currentLight) {
                    chunk.setSkyLight(nx, ny, nz, newLight);
                    queue.add(new int[] { nx, ny, nz, newLight });
                }
            }
        }
    }

    /**
     * Initialize block lights (torches, glowstone, etc.)
     */
    private void initializeBlockLights(Chunk chunk) {
        Deque<int[]> queue = new ArrayDeque<>();

        // Find all light sources
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                for (int index = 0; index < Chunk.CHUNK_HEIGHT; index++) {
                    int y = Settings.indexToWorldY(index);
                    GameBlock block = chunk.getBlock(x, y, z);

                    if (block != null && block.getLightLevel() > 0) {
                        int light = block.getLightLevel();
                        chunk.setBlockLight(x, y, z, light);
                        queue.add(new int[] { x, y, z, light });
                    }
                }
            }
        }

        // BFS spread block light
        while (!queue.isEmpty()) {
            int[] node = queue.poll();
            int x = node[0], y = node[1], z = node[2], light = node[3];

            for (int[] dir : DIRS) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                if (nx < 0 || nx >= Chunk.CHUNK_SIZE)
                    continue;
                if (nz < 0 || nz >= Chunk.CHUNK_SIZE)
                    continue;
                if (!Settings.isValidWorldY(ny))
                    continue;

                GameBlock neighbor = chunk.getBlock(nx, ny, nz);
                if (neighbor != null && neighbor.isSolid() && !isTransparent(neighbor)) {
                    continue;
                }

                int newLight = light - 1;
                if (newLight <= 0)
                    continue;

                int currentLight = chunk.getBlockLight(nx, ny, nz);
                if (newLight > currentLight) {
                    chunk.setBlockLight(nx, ny, nz, newLight);
                    queue.add(new int[] { nx, ny, nz, newLight });
                }
            }
        }
    }

    // ========== BLOCK CHANGE HANDLING ==========

    /**
     * ✅ Called when a block is BROKEN - light floods in
     */
    public void onBlockBroken(int worldX, int worldY, int worldZ) {
        // Find the brightest neighbor and flood light from there
        int maxSkyLight = 0;
        int maxBlockLight = 0;

        // Check all 6 neighbors
        for (int[] dir : DIRS) {
            int nx = worldX + dir[0];
            int ny = worldY + dir[1];
            int nz = worldZ + dir[2];

            if (!Settings.isValidWorldY(ny))
                continue;

            int sky = getWorldSkyLight(nx, ny, nz);
            int block = getWorldBlockLight(nx, ny, nz);

            maxSkyLight = Math.max(maxSkyLight, sky);
            maxBlockLight = Math.max(maxBlockLight, block);
        }

        // Check directly above for direct skylight
        if (worldY < Settings.WORLD_MAX_Y) {
            int aboveSky = getWorldSkyLight(worldX, worldY + 1, worldZ);
            if (aboveSky == 15) {
                maxSkyLight = 15;
            }
        }

        // Set light at broken position
        int newSkyLight = Math.max(0, maxSkyLight - 1);
        int newBlockLight = Math.max(0, maxBlockLight - 1);

        // Direct skylight column check
        boolean hasDirectSky = checkDirectSkylight(worldX, worldY, worldZ);
        if (hasDirectSky) {
            newSkyLight = 15;

            // ✅ FIX: Propagate 15 downwards immediately (Minecraft logic: Sky falls
            // instantly)
            // This ensures shadows disappear when blocking block is removed
            setWorldSkyLight(worldX, worldY, worldZ, 15);
            propagateVerticalSkyLightOnly(worldX, worldY - 1, worldZ);

            // Allow horizontal flood from this new column of light
            floodLight(worldX, worldY, worldZ, 15, true);
        } else {
            setWorldSkyLight(worldX, worldY, worldZ, newSkyLight);
            if (newSkyLight > 1) {
                floodLight(worldX, worldY, worldZ, newSkyLight, true);
            }
        }

        setWorldBlockLight(worldX, worldY, worldZ, newBlockLight);
        if (newBlockLight > 1) {
            floodLight(worldX, worldY, worldZ, newBlockLight, false);
        }
    }

    /**
     * ✅ Called when a block is PLACED - updates lighting based on opacity
     * 
     * NOW SUPPORTS LIGHT OPACITY for semi-transparent blocks like leaves
     */
    public void onBlockPlaced(int worldX, int worldY, int worldZ, GameBlock block) {
        if (block == null)
            return;

        int opacity = block.getLightOpacity();

        // Handle light blocking based on opacity
        if (opacity > 0 || (block.isSolid() && !isTransparent(block))) {
            // Remember old light values
            int oldSkyLight = getWorldSkyLight(worldX, worldY, worldZ);
            int oldBlockLight = getWorldBlockLight(worldX, worldY, worldZ);

            // Calculate new light based on opacity
            int newSkyLight, newBlockLight;

            if (opacity > 0) {
                // Semi-transparent: reduce by opacity amount
                newSkyLight = Math.max(0, oldSkyLight - opacity);
                newBlockLight = Math.max(0, oldBlockLight - opacity);
            } else {
                // Fully opaque: block completely
                newSkyLight = 0;
                newBlockLight = 0;
            }

            // Apply new light values
            setWorldSkyLight(worldX, worldY, worldZ, newSkyLight);
            setWorldBlockLight(worldX, worldY, worldZ, newBlockLight);

            // Propagate darkness if light was blocked
            if (oldSkyLight > newSkyLight) {
                if (oldSkyLight == 15 && newSkyLight < 15) {
                    // Blocked vertical beam - darken column below
                    propagateVerticalShadow(worldX, worldY - 1, worldZ);
                    removeLightFrom(worldX, worldY, worldZ, oldSkyLight, true);
                } else if (oldSkyLight > 1) {
                    removeLightFrom(worldX, worldY, worldZ, oldSkyLight, true);
                }
            }

            if (oldBlockLight > newBlockLight && oldBlockLight > 1) {
                removeLightFrom(worldX, worldY, worldZ, oldBlockLight, false);
            }
        }

        // If block emits light, spread it
        if (block.getLightLevel() > 0) {
            int light = block.getLightLevel();
            setWorldBlockLight(worldX, worldY, worldZ, light);
            floodLight(worldX, worldY, worldZ, light, false);
        }
    }

    /**
     * ✅ REWRITTEN: Remove light using Minecraft-style algorithm
     * 
     * Algorithm:
     * 1. BFS from source, removing light that was <= source light
     * 2. Track edge nodes where we find higher light (alternative sources)
     * 3. Refill from those alternative sources
     */
    private void removeLightFrom(int sourceX, int sourceY, int sourceZ, int sourceLight, boolean isSkyLight) {
        Deque<int[]> removeQueue = new ArrayDeque<>();
        List<int[]> refillSources = new ArrayList<>();
        Set<Long> removed = new HashSet<>();

        // Start BFS from the source itself
        removeQueue.add(new int[] { sourceX, sourceY, sourceZ, sourceLight });

        // Remove light at source (it's already 0 externally, but we mark it here)
        if (isSkyLight)
            setWorldSkyLight(sourceX, sourceY, sourceZ, 0);
        else
            setWorldBlockLight(sourceX, sourceY, sourceZ, 0);

        long sourceKey = ((long) sourceX << 40) | ((long) (sourceY & 0xFFFF) << 24) | (sourceZ & 0xFFFFFF);
        removed.add(sourceKey);

        int maxNodes = 5000;
        int processed = 0;

        while (!removeQueue.isEmpty() && processed < maxNodes) {
            int[] node = removeQueue.poll();
            int x = node[0], y = node[1], z = node[2], val = node[3];
            processed++;

            for (int[] dir : DIRS) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                if (!Settings.isValidWorldY(ny))
                    continue;

                // neighbor check
                int neighborLight = isSkyLight ? getWorldSkyLight(nx, ny, nz) : getWorldBlockLight(nx, ny, nz);
                if (neighborLight == 0)
                    continue;

                GameBlock neighbor = getWorldBlock(nx, ny, nz);
                // If solid opaque, implied light is 0 (or we don't traverse)
                if (neighbor != null && neighbor.isSolid() && !isTransparent(neighbor))
                    continue;

                // CRITICAL: Standard MC Logic
                // If neighbor is strictly less than 'val', it likely got light from us (val ->
                // val-1).
                // If neighbor is >= val, it is an independent source (or parallel path).
                if (neighborLight < val) {
                    // It was lit by us. Darken it and propagate removal.
                    if (isSkyLight)
                        setWorldSkyLight(nx, ny, nz, 0);
                    else
                        setWorldBlockLight(nx, ny, nz, 0);

                    long k = ((long) nx << 40) | ((long) (ny & 0xFFFF) << 24) | (nz & 0xFFFFFF);
                    removed.add(k);

                    removeQueue.add(new int[] { nx, ny, nz, neighborLight });
                } else {
                    // It has >= light. It is a boundary source that needs to reflood back into the
                    // dark area.
                    refillSources.add(new int[] { nx, ny, nz, neighborLight });
                }
            }
        }

        // Phase 2: Refill from alternative sources
        for (int[] source : refillSources) {
            int light = isSkyLight ? getWorldSkyLight(source[0], source[1], source[2])
                    : getWorldBlockLight(source[0], source[1], source[2]);
            if (light > 0) {
                floodLight(source[0], source[1], source[2], light, isSkyLight);
            }
        }
    }

    /**
     * BFS flood light from a source position
     */
    private void floodLight(int startX, int startY, int startZ, int startLight, boolean isSkyLight) {
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] { startX, startY, startZ, startLight });

        Set<Long> visited = new HashSet<>();
        int maxNodes = 2000; // Limit to prevent lag
        int processed = 0;

        while (!queue.isEmpty() && processed < maxNodes) {
            int[] node = queue.poll();
            int x = node[0], y = node[1], z = node[2], light = node[3];

            long key = ((long) x << 40) | ((long) (y & 0xFFFF) << 24) | (z & 0xFFFFFF);
            if (visited.contains(key))
                continue;
            visited.add(key);
            processed++;

            for (int[] dir : DIRS) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                if (!Settings.isValidWorldY(ny))
                    continue;

                GameBlock neighbor = getWorldBlock(nx, ny, nz);
                if (neighbor != null && neighbor.isSolid() && !isTransparent(neighbor)) {
                    continue;
                }

                int newLight = light - 1;
                if (newLight <= 0)
                    continue;

                int currentLight = isSkyLight ? getWorldSkyLight(nx, ny, nz) : getWorldBlockLight(nx, ny, nz);

                if (newLight > currentLight) {
                    if (isSkyLight) {
                        setWorldSkyLight(nx, ny, nz, newLight);
                    } else {
                        setWorldBlockLight(nx, ny, nz, newLight);
                    }

                    if (newLight > 1) {
                        queue.add(new int[] { nx, ny, nz, newLight });
                    }
                }
            }
        }
    }

    /**
     * Check if position has direct line to sky
     */
    private boolean checkDirectSkylight(int worldX, int worldY, int worldZ) {
        for (int y = worldY + 1; y <= Settings.WORLD_MAX_Y; y++) {
            GameBlock block = getWorldBlock(worldX, y, worldZ);
            if (block != null && block.isSolid() && !isTransparent(block)) {
                return false;
            }
        }
        return true;
    }

    /**
     * ✅ NEW: Propagate skylight vertically downwards (for restoring shadows)
     */
    private void propagateVerticalSkyLightOnly(int x, int startY, int z) {
        int currentLight = 15;

        for (int y = startY; y >= Settings.WORLD_MIN_Y; y--) {
            GameBlock block = getWorldBlock(x, y, z);

            // If solid, stop
            if (block != null && block.isSolid() && !isTransparent(block)) {
                break;
            }

            // If air, keep 15. If transparent, decay.
            if (block != null && (block == BlockRegistry.WATER || block == BlockRegistry.OAK_LEAVES)) {
                currentLight = Math.max(0, currentLight - 1);
            }
            // else currentLight stays 15 (Air)

            int existing = getWorldSkyLight(x, y, z);
            if (existing != currentLight) {
                setWorldSkyLight(x, y, z, currentLight);
                // Also trigger horizontal flood at this level if we increased light
                if (currentLight > existing) {
                    floodLight(x, y, z, currentLight, true);
                }
            } else {
                // Optimization: If light didn't change, and it's full sky,
                // we might be able to stop... but safe to continue to be sure
                if (currentLight == 15)
                    continue;
            }

            if (currentLight <= 0)
                break;
        }
    }

    /**
     * ✅ NEW: Propagate shadow vertically downwards
     */
    private void propagateVerticalShadow(int x, int startY, int z) {
        for (int y = startY; y >= Settings.WORLD_MIN_Y; y--) {
            int current = getWorldSkyLight(x, y, z);
            if (current == 0)
                break; // Already dark, stop

            GameBlock block = getWorldBlock(x, y, z);
            if (block != null && block.isSolid() && !isTransparent(block))
                break; // Hit ground

            // Set to 0 (Darken)
            setWorldSkyLight(x, y, z, 0);

            // Queue removal for horizontal neighbors of this level
            // because they might have been lit by this vertical beam
            removeLightFrom(x, y, z, current, true);
        }
    }

    // ========== WORLD ACCESS HELPERS ==========

    private GameBlock getWorldBlock(int x, int y, int z) {
        return world.getBlock(x, y, z);
    }

    private int getWorldSkyLight(int x, int y, int z) {
        return world.getSkyLight(x, y, z);
    }

    private int getWorldBlockLight(int x, int y, int z) {
        return world.getBlockLight(x, y, z);
    }

    private void setWorldSkyLight(int x, int y, int z, int light) {
        int chunkX = Math.floorDiv(x, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(z, Chunk.CHUNK_SIZE);
        int localX = Math.floorMod(x, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(z, Chunk.CHUNK_SIZE);

        Chunk chunk = world.getChunk(chunkX, chunkZ);
        if (chunk != null) {
            chunk.setSkyLight(localX, y, localZ, light);
        }
    }

    private void setWorldBlockLight(int x, int y, int z, int light) {
        int chunkX = Math.floorDiv(x, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(z, Chunk.CHUNK_SIZE);
        int localX = Math.floorMod(x, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(z, Chunk.CHUNK_SIZE);

        Chunk chunk = world.getChunk(chunkX, chunkZ);
        if (chunk != null) {
            chunk.setBlockLight(localX, y, localZ, light);
        }
    }

    private boolean isTransparent(GameBlock block) {
        if (block == null || block.isAir())
            return true;
        if (block == BlockRegistry.WATER)
            return true;
        if (block == BlockRegistry.OAK_LEAVES)
            return true;
        return !block.isSolid();
    }
}
