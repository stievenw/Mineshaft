package com.mineshaft.world.lighting;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.TimeOfDay;
import com.mineshaft.world.Chunk;

import java.util.*;
import java.util.concurrent.*;

/**
 * ⚡ OPTIMIZED Lighting Engine - Performance Fixed
 */
public class LightingEngine {

    private SunLightCalculator sunLight;
    private static final float[] BRIGHTNESS_TABLE = new float[16];

    // ⚡ PERFORMANCE OPTIMIZATIONS
    private final ExecutorService lightingExecutor;
    private final Queue<Chunk> pendingLightUpdates = new ConcurrentLinkedQueue<>();
    private final Set<Chunk> processingChunks = ConcurrentHashMap.newKeySet();

    // Throttling controls
    private static final int MAX_CHUNKS_PER_FRAME = 4;
    private static final int MAX_LIGHT_OPERATIONS_PER_CHUNK = 1000;

    // Smooth transition
    private int targetSkylightLevel = 15;
    private int currentSkylightLevel = 15;
    private long lastLightLevelChange = 0;
    private static final long LIGHT_TRANSITION_DELAY = 50; // ms

    // Cache
    private final Map<ChunkPosition, Integer> skylightCache = new ConcurrentHashMap<>();

    static {
        for (int i = 0; i < 16; i++) {
            if (i <= 0) {
                BRIGHTNESS_TABLE[i] = 0.4f;
            } else if (i >= 15) {
                BRIGHTNESS_TABLE[i] = 1.0f;
            } else {
                float normalized = i / 15.0f;
                BRIGHTNESS_TABLE[i] = 0.4f + (normalized * 0.6f);
            }
        }
    }

    private static class LightNode {
        int x, y, z;
        int lightLevel;

        LightNode(int x, int y, int z, int lightLevel) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lightLevel = lightLevel;
        }
    }

    private static class ChunkPosition {
        int x, z;

        ChunkPosition(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof ChunkPosition))
                return false;
            ChunkPosition that = (ChunkPosition) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }

    public LightingEngine(com.mineshaft.world.World world, TimeOfDay timeOfDay) {
        this.sunLight = new SunLightCalculator(timeOfDay);

        // Create optimized thread pool
        this.lightingExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "LightingWorker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    /**
     * ⚡ Update sun light and return true if changed
     */
    public boolean updateSunLight() {
        boolean directionChanged = sunLight.updateSunDirection();

        // ✅ FIX: Get skylight level from SunLightCalculator
        targetSkylightLevel = sunLight.getSkylightLevel();

        return directionChanged;
    }

    /**
     * ⚡ Call every frame for smooth transitions
     */
    public void update() {
        // Smooth light level transition
        long currentTime = System.currentTimeMillis();
        if (currentSkylightLevel != targetSkylightLevel &&
                currentTime - lastLightLevelChange >= LIGHT_TRANSITION_DELAY) {

            if (currentSkylightLevel < targetSkylightLevel) {
                currentSkylightLevel++;
            } else {
                currentSkylightLevel--;
            }
            lastLightLevelChange = currentTime;
        }

        // Process queued chunks
        processQueuedChunks();
    }

    /**
     * ⚡ Process limited chunks per frame
     */
    private void processQueuedChunks() {
        int chunksProcessed = 0;

        while (chunksProcessed < MAX_CHUNKS_PER_FRAME && !pendingLightUpdates.isEmpty()) {
            Chunk chunk = pendingLightUpdates.poll();

            if (chunk == null || processingChunks.contains(chunk)) {
                continue;
            }

            processingChunks.add(chunk);

            // Process async
            lightingExecutor.submit(() -> {
                try {
                    updateChunkSkylightAsync(chunk, currentSkylightLevel);
                } finally {
                    processingChunks.remove(chunk);
                }
            });

            chunksProcessed++;
        }
    }

    /**
     * ⚡ Async chunk skylight update - ✅ PERFORMANCE OPTIMIZED
     */
    private void updateChunkSkylightAsync(Chunk chunk, int skylightLevel) {
        if (chunk == null)
            return;

        ChunkPosition pos = new ChunkPosition(chunk.getChunkX(), chunk.getChunkZ());

        // ✅ PERFORMANCE: Check cache to avoid unnecessary updates
        Integer cachedLevel = skylightCache.get(pos);
        if (cachedLevel != null && cachedLevel == skylightLevel) {
            return; // No change needed
        }

        // Update skylight
        boolean changed = false;
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                if (updateColumnSkylight(chunk, x, z, skylightLevel)) {
                    changed = true;
                }
            }
        }

        // ✅ PERFORMANCE: Only rebuild if lighting actually changed
        if (changed) {
            skylightCache.put(pos, skylightLevel);
            chunk.setNeedsRebuild(true);
        }
    }

    /**
     * ⚡ Update single column - ✅ PERFORMANCE OPTIMIZED with array indices
     */
    private boolean updateColumnSkylight(Chunk chunk, int x, int z, int skylightLevel) {
        int currentLight = skylightLevel;
        boolean changed = false;

        // ✅ PERFORMANCE: Use array index loop (top to bottom)
        for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
            GameBlock block = chunk.getBlockByIndex(x, index, z);
            int newLight;

            if (block == null || block.isAir() || !block.isSolid()) {
                newLight = currentLight;
            } else {
                newLight = 0;
                currentLight = 0;
            }

            // Convert index to world Y for getSkyLight/setSkyLight calls
            int worldY = com.mineshaft.core.Settings.indexToWorldY(index);
            int oldLight = chunk.getSkyLight(x, worldY, z);
            if (oldLight != newLight) {
                chunk.setSkyLight(x, worldY, z, newLight);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * ⚡ Queue chunk for update
     */
    public void queueChunkForLightUpdate(Chunk chunk) {
        if (chunk != null && !processingChunks.contains(chunk)) {
            pendingLightUpdates.offer(chunk);
        }
    }

    /**
     * ⚡ Batch queue chunks
     */
    public void queueChunksForLightUpdate(Collection<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            queueChunkForLightUpdate(chunk);
        }
    }

    public SunLightCalculator getSunLight() {
        return sunLight;
    }

    public int getCurrentSkylightLevel() {
        return currentSkylightLevel;
    }

    /**
     * ⚡ Initial skylight setup
     */
    public void initializeSkylightForChunk(Chunk chunk, int skylightLevel) {
        lightingExecutor.submit(() -> {
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                    updateColumnSkylight(chunk, x, z, skylightLevel);
                }
            }
        });
    }

    /**
     * ⚡ Blocklight initialization - ✅ PERFORMANCE OPTIMIZED with array indices
     */
    public void initializeBlocklightForChunk(Chunk chunk) {
        lightingExecutor.submit(() -> {
            Queue<LightNode> lightQueue = new LinkedList<>();

            // ✅ PERFORMANCE: Use array index loops
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int index = 0; index < Chunk.CHUNK_HEIGHT; index++) {
                    for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                        GameBlock block = chunk.getBlockByIndex(x, index, z);
                        int lightLevel = (block != null) ? block.getLightLevel() : 0;

                        if (lightLevel > 0) {
                            // Convert to world Y for light storage
                            int worldY = com.mineshaft.core.Settings.indexToWorldY(index);
                            chunk.setBlockLight(x, worldY, z, lightLevel);
                            lightQueue.add(new LightNode(x, worldY, z, lightLevel));
                        }
                    }
                }
            }

            propagateLightOptimized(chunk, lightQueue, false);
        });
    }

    /**
     * ⚡ OPTIMIZED: Light propagation with operation limit - ✅ Using world Y
     * validation
     */
    private void propagateLightOptimized(Chunk chunk, Queue<LightNode> queue, boolean isSkylight) {
        int[][] directions = {
                { 1, 0, 0 }, { -1, 0, 0 },
                { 0, 1, 0 }, { 0, -1, 0 },
                { 0, 0, 1 }, { 0, 0, -1 }
        };

        Set<Long> visited = new HashSet<>();
        int operations = 0;

        while (!queue.isEmpty() && operations < MAX_LIGHT_OPERATIONS_PER_CHUNK) {
            LightNode node = queue.poll();
            long key = ((long) node.x << 16) | ((long) node.y << 8) | node.z;

            if (visited.contains(key))
                continue;
            visited.add(key);
            operations++;

            int newLight = node.lightLevel - 1;
            if (newLight <= 0)
                continue;

            for (int[] dir : directions) {
                int nx = node.x + dir[0];
                int ny = node.y + dir[1];
                int nz = node.z + dir[2];

                // ✅ Check chunk bounds for X and Z, world Y bounds for Y
                if (nx < 0 || nx >= Chunk.CHUNK_SIZE ||
                        !com.mineshaft.core.Settings.isValidWorldY(ny) ||
                        nz < 0 || nz >= Chunk.CHUNK_SIZE) {
                    continue;
                }

                long neighborKey = ((long) nx << 16) | ((long) ny << 8) | nz;
                if (visited.contains(neighborKey))
                    continue;

                GameBlock neighbor = chunk.getBlock(nx, ny, nz);

                if (neighbor != null && neighbor.isSolid() && neighbor != BlockRegistry.OAK_LEAVES) {
                    continue;
                }

                int currentLight = isSkylight ? chunk.getSkyLight(nx, ny, nz) : chunk.getBlockLight(nx, ny, nz);

                if (newLight > currentLight) {
                    if (isSkylight) {
                        chunk.setSkyLight(nx, ny, nz, newLight);
                    } else {
                        chunk.setBlockLight(nx, ny, nz, newLight);
                    }
                    queue.add(new LightNode(nx, ny, nz, newLight));
                }
            }
        }
    }

    /**
     * ⚡ Block placement
     */
    public void onBlockPlaced(Chunk chunk, int x, int y, int z, GameBlock block) {
        lightingExecutor.submit(() -> {
            if (block.isSolid()) {
                chunk.setSkyLight(x, y, z, 0);
                propagateDarknessDownOptimized(chunk, x, y, z);
            }

            int lightLevel = block.getLightLevel();
            if (lightLevel > 0) {
                chunk.setBlockLight(x, y, z, lightLevel);
                Queue<LightNode> queue = new LinkedList<>();
                queue.add(new LightNode(x, y, z, lightLevel));
                propagateLightOptimized(chunk, queue, false);
            }

            chunk.setNeedsRebuild(true);
        });
    }

    /**
     * ⚡ Block removal
     */
    public void onBlockRemoved(Chunk chunk, int x, int y, int z) {
        lightingExecutor.submit(() -> {
            updateColumnSkylight(chunk, x, z, currentSkylightLevel);
            chunk.setBlockLight(x, y, z, 0);
            recalculateBlocklightAroundOptimized(chunk, x, y, z);
            chunk.setNeedsRebuild(true);
        });
    }

    private void propagateDarknessDownOptimized(Chunk chunk, int x, int startY, int z) {
        for (int y = startY - 1; y >= 0; y--) {
            GameBlock block = chunk.getBlock(x, y, z);
            if (block != null && block.isSolid())
                break;

            int currentLight = chunk.getSkyLight(x, y, z);
            if (currentLight == 0)
                break;

            chunk.setSkyLight(x, y, z, 0);
        }
    }

    private void recalculateBlocklightAroundOptimized(Chunk chunk, int x, int y, int z) {
        Queue<LightNode> queue = new LinkedList<>();

        int[][] directions = {
                { 1, 0, 0 }, { -1, 0, 0 },
                { 0, 1, 0 }, { 0, -1, 0 },
                { 0, 0, 1 }, { 0, 0, -1 }
        };

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            int nz = z + dir[2];

            // ✅ Check chunk bounds for X and Z, world Y bounds for Y
            if (nx < 0 || nx >= Chunk.CHUNK_SIZE ||
                    !com.mineshaft.core.Settings.isValidWorldY(ny) ||
                    nz < 0 || nz >= Chunk.CHUNK_SIZE) {
                continue;
            }

            int light = chunk.getBlockLight(nx, ny, nz);
            if (light > 1) {
                queue.add(new LightNode(nx, ny, nz, light));
            }
        }

        propagateLightOptimized(chunk, queue, false);
    }

    public static int getCombinedLight(Chunk chunk, int x, int y, int z) {
        if (chunk == null)
            return 15;

        int skyLight = chunk.getSkyLight(x, y, z);
        int blockLight = chunk.getBlockLight(x, y, z);

        return Math.max(skyLight, blockLight);
    }

    public static float getBrightness(int lightLevel) {
        if (lightLevel < 0)
            return BRIGHTNESS_TABLE[0];
        if (lightLevel > 15)
            return BRIGHTNESS_TABLE[15];
        return BRIGHTNESS_TABLE[lightLevel];
    }

    public static float getBrightnessWithGamma(int lightLevel, float gamma) {
        float brightness = getBrightness(lightLevel);
        return (float) Math.pow(brightness, 1.0f / gamma);
    }

    public void cancelChunkUpdates(Chunk chunk) {
        pendingLightUpdates.remove(chunk);
        processingChunks.remove(chunk);
        ChunkPosition pos = new ChunkPosition(chunk.getChunkX(), chunk.getChunkZ());
        skylightCache.remove(pos);
    }

    public int getPendingUpdatesCount() {
        return pendingLightUpdates.size() + processingChunks.size();
    }

    public void flush() {
        while (!pendingLightUpdates.isEmpty()) {
            processQueuedChunks();
        }
    }

    public void shutdown() {
        lightingExecutor.shutdown();
        try {
            if (!lightingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                lightingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            lightingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        pendingLightUpdates.clear();
        processingChunks.clear();
        skylightCache.clear();
    }
}