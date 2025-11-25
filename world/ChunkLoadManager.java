// src/main/java/com/mineshaft/world/ChunkLoadManager.java
package com.mineshaft.world;

import com.mineshaft.core.Settings;
import com.mineshaft.entity.Camera;

import java.util.*;
import java.util.concurrent.*;

/**
 * ✅ Chunk Load Manager - Mengatur loading chunk berdasarkan prioritas
 * Mengikuti aturan MC Java Edition untuk mengurangi lag
 */
public class ChunkLoadManager {

    private final World world;
    private final Set<Long> loadedChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> simulationChunks = ConcurrentHashMap.newKeySet();
    private final Queue<ChunkLoadTask> loadQueue = new PriorityBlockingQueue<>();

    private static final int MAX_CHUNKS_LOAD_PER_FRAME = 2;
    private static final int MAX_CHUNKS_UNLOAD_PER_FRAME = 4;

    public ChunkLoadManager(World world) {
        this.world = world;
        Settings.validateDistances();
    }

    /**
     * ✅ Update chunks berdasarkan posisi player
     */
    public void update(Camera camera) {
        int playerChunkX = (int) Math.floor(camera.getX() / Chunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(camera.getZ() / Chunk.CHUNK_SIZE);

        queueChunksToLoad(playerChunkX, playerChunkZ);
        updateSimulationChunks(playerChunkX, playerChunkZ);
        unloadDistantChunks(playerChunkX, playerChunkZ);
        processLoadQueue();
    }

    private void queueChunksToLoad(int playerChunkX, int playerChunkZ) {
        int renderDist = Settings.RENDER_DISTANCE;

        for (int dx = -renderDist; dx <= renderDist; dx++) {
            for (int dz = -renderDist; dz <= renderDist; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                long key = chunkKey(chunkX, chunkZ);

                if (!loadedChunks.contains(key)) {
                    double distSq = dx * dx + dz * dz;
                    loadQueue.offer(new ChunkLoadTask(chunkX, chunkZ, distSq));
                }
            }
        }
    }

    private void updateSimulationChunks(int playerChunkX, int playerChunkZ) {
        simulationChunks.clear();
        int simDist = Settings.SIMULATION_DISTANCE;

        for (int dx = -simDist; dx <= simDist; dx++) {
            for (int dz = -simDist; dz <= simDist; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                long key = chunkKey(chunkX, chunkZ);

                if (loadedChunks.contains(key)) {
                    simulationChunks.add(key);
                }
            }
        }
    }

    private void unloadDistantChunks(int playerChunkX, int playerChunkZ) {
        int unloaded = 0;
        Iterator<Long> iterator = loadedChunks.iterator();

        while (iterator.hasNext() && unloaded < MAX_CHUNKS_UNLOAD_PER_FRAME) {
            long key = iterator.next();
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;

            int dx = Math.abs(chunkX - playerChunkX);
            int dz = Math.abs(chunkZ - playerChunkZ);

            if (dx > Settings.RENDER_DISTANCE + 2 || dz > Settings.RENDER_DISTANCE + 2) {
                iterator.remove();
                simulationChunks.remove(key);
                world.unloadChunk(chunkX, chunkZ);
                unloaded++;
            }
        }
    }

    private void processLoadQueue() {
        int loaded = 0;

        while (!loadQueue.isEmpty() && loaded < MAX_CHUNKS_LOAD_PER_FRAME) {
            ChunkLoadTask task = loadQueue.poll();
            if (task != null) {
                long key = chunkKey(task.chunkX, task.chunkZ);

                if (!loadedChunks.contains(key)) {
                    world.loadChunk(task.chunkX, task.chunkZ);
                    loadedChunks.add(key);
                    loaded++;
                }
            }
        }
    }

    /**
     * ✅ Cek apakah chunk dalam simulation distance
     */
    public boolean isSimulationChunk(int chunkX, int chunkZ) {
        return simulationChunks.contains(chunkKey(chunkX, chunkZ));
    }

    /**
     * ✅ Get chunks yang perlu di-render
     */
    public Collection<Chunk> getChunksToRender() {
        List<Chunk> chunks = new ArrayList<>();
        for (long key : loadedChunks) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            Chunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk != null && chunk.isReady()) {
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    private long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static class ChunkLoadTask implements Comparable<ChunkLoadTask> {
        int chunkX, chunkZ;
        double distanceSquared;

        ChunkLoadTask(int x, int z, double distSq) {
            this.chunkX = x;
            this.chunkZ = z;
            this.distanceSquared = distSq;
        }

        @Override
        public int compareTo(ChunkLoadTask other) {
            return Double.compare(this.distanceSquared, other.distanceSquared);
        }
    }
}