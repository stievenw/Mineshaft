// src/main/java/com/mineshaft/world/ChunkGenerationManager.java
package com.mineshaft.world;

import java.util.*;
import java.util.concurrent.*;

/**
 * ⚡ Async chunk terrain generation system
 * - Generates terrain in background threads
 * - Prioritizes chunks closest to player
 * - Thread-safe and efficient
 */
public class ChunkGenerationManager {

    private final ExecutorService generatorThreadPool;
    private final Queue<ChunkGenTask> generationQueue = new ConcurrentLinkedQueue<>();
    private final Set<ChunkPos> generating = ConcurrentHashMap.newKeySet();
    private final Queue<Chunk> pendingLighting = new ConcurrentLinkedQueue<>();

    // ⚙️ Configuration
    private static final int GENERATOR_THREADS = 2; // 2 threads for generation
    private static final int MAX_GENERATIONS_PER_FRAME = 4; // Max new tasks per frame

    /**
     * Task for chunk generation with priority
     */
    private static class ChunkGenTask {
        Chunk chunk;
        double distanceSq;

        ChunkGenTask(Chunk chunk, double distSq) {
            this.chunk = chunk;
            this.distanceSq = distSq;
        }
    }

    public ChunkGenerationManager() {
        generatorThreadPool = Executors.newFixedThreadPool(GENERATOR_THREADS, r -> {
            Thread t = new Thread(r, "ChunkGenerator");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1); // Lower than render thread
            return t;
        });

        System.out.println("[ChunkGen] Manager initialized with " + GENERATOR_THREADS + " threads");
    }

    /**
     * Queue a chunk for generation
     * 
     * @param chunk            Chunk to generate
     * @param playerDistanceSq Distance from player (for prioritization)
     */
    public void queueGeneration(Chunk chunk, double playerDistanceSq) {
        ChunkPos pos = new ChunkPos(chunk.getChunkX(), chunk.getChunkZ());

        // Skip if already generated or generating
        if (chunk.getState() != ChunkState.EMPTY || generating.contains(pos)) {
            return;
        }

        generationQueue.offer(new ChunkGenTask(chunk, playerDistanceSq));
    }

    /**
     * Update generation system (call from main thread every frame)
     */
    public void update() {
        startPendingGenerations();
        processCompletedChunks();
    }

    /**
     * Start new generation tasks (closest chunks first)
     */
    private void startPendingGenerations() {
        // Collect and sort tasks by distance
        List<ChunkGenTask> tasks = new ArrayList<>();
        ChunkGenTask task;
        while ((task = generationQueue.poll()) != null) {
            tasks.add(task);
        }

        // Sort by distance (closest first)
        tasks.sort(Comparator.comparingDouble(t -> t.distanceSq));

        // Start generation (limited per frame to avoid thread spam)
        int started = 0;
        for (ChunkGenTask t : tasks) {
            if (started >= MAX_GENERATIONS_PER_FRAME) {
                generationQueue.offer(t); // Re-queue for next frame
                continue;
            }

            ChunkPos pos = new ChunkPos(t.chunk.getChunkX(), t.chunk.getChunkZ());

            // Start generation if chunk is still EMPTY and not already generating
            if (t.chunk.getState() == ChunkState.EMPTY && generating.add(pos)) {
                generatorThreadPool.submit(() -> generateChunkAsync(t.chunk, pos));
                started++;
            }
        }
    }

    /**
     * Generate chunk terrain in background thread
     */
    private void generateChunkAsync(Chunk chunk, ChunkPos pos) {
        try {
            // This runs in background thread - no OpenGL calls allowed!
            chunk.generate();

            // Queue for lighting calculation (must be done in main thread)
            pendingLighting.offer(chunk);

        } catch (Exception e) {
            System.err.println("[ChunkGen] Error generating chunk " + pos + ": " + e.getMessage());
            e.printStackTrace();
            chunk.setState(ChunkState.EMPTY); // Reset on error
        } finally {
            generating.remove(pos);
        }
    }

    /**
     * Process chunks that finished generation (move to lighting phase)
     */
    private void processCompletedChunks() {
        Chunk chunk;
        while ((chunk = pendingLighting.poll()) != null) {
            if (chunk.getState() == ChunkState.GENERATED) {
                chunk.setState(ChunkState.LIGHT_PENDING);
                // Lighting will be handled by LightingEngine in World.updateLighting()
            }
        }
    }

    /**
     * Check if a chunk is currently being generated
     */
    public boolean isGenerating(int chunkX, int chunkZ) {
        return generating.contains(new ChunkPos(chunkX, chunkZ));
    }

    /**
     * Get total pending work count
     */
    public int getPendingCount() {
        return generationQueue.size() + generating.size() + pendingLighting.size();
    }

    /**
     * Get active generation threads count
     */
    public int getActiveThreads() {
        return ((ThreadPoolExecutor) generatorThreadPool).getActiveCount();
    }

    /**
     * Shutdown generation system
     */
    public void shutdown() {
        System.out.println("[ChunkGen] Shutting down...");

        generatorThreadPool.shutdown();
        try {
            if (!generatorThreadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                generatorThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            generatorThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("[ChunkGen] Shutdown complete");
    }

    /**
     * Chunk position identifier
     */
    private static class ChunkPos {
        final int x, z;

        ChunkPos(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof ChunkPos))
                return false;
            ChunkPos that = (ChunkPos) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }

        @Override
        public String toString() {
            return "[" + x + ", " + z + "]";
        }
    }
}