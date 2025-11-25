// src/main/java/com/mineshaft/render/ChunkRenderer.java
package com.mineshaft.render;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.entity.Camera;
import com.mineshaft.world.Chunk;
import com.mineshaft.world.ChunkSection;
import com.mineshaft.world.World;
import com.mineshaft.world.lighting.LightingEngine;

import java.util.*;
import java.util.concurrent.*;

import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ OPTIMIZED ChunkRenderer v3.0 - Minecraft-Style Lighting System
 * 
 * Key Features:
 * - Mesh stores LIGHT VALUES (0-15), not final brightness
 * - Time-of-day brightness applied via glColor4f (no mesh rebuild!)
 * - Static directional shading (face brightness)
 * - Separated geometry rebuild vs lighting update
 * 
 * Performance:
 * - Time change (day→night): NO mesh rebuild, only glColor update
 * - Block place/remove: Only affected chunks rebuild
 * - Smooth lighting with vertex interpolation
 */
public class ChunkRenderer {

    // ========== TIME-OF-DAY LIGHTING (NEW!) ==========
    /**
     * ✅ MINECRAFT-STYLE: Time-of-day brightness multiplier
     * Applied globally to all chunks via glColor4f (no mesh rebuild needed)
     * Range: 0.0 (midnight) to 1.0 (noon)
     */
    private float timeOfDayBrightness = 1.0f;

    /**
     * ✅ Set time-of-day brightness without rebuilding meshes
     * Called by Game.java when TimeOfDay updates
     */
    public void setTimeOfDayBrightness(float brightness) {
        this.timeOfDayBrightness = Math.max(Settings.MIN_BRIGHTNESS, Math.min(1.0f, brightness));
    }

    public float getTimeOfDayBrightness() {
        return timeOfDayBrightness;
    }

    // ========== MESH STORAGE ==========
    private final Map<ChunkSection, ChunkMesh> solidMeshes = new ConcurrentHashMap<>();
    private final Map<ChunkSection, ChunkMesh> waterMeshes = new ConcurrentHashMap<>();
    private final Map<ChunkSection, ChunkMesh> translucentMeshes = new ConcurrentHashMap<>();

    // ========== REFERENCES ==========
    private World world;
    private LightingEngine lightingEngine;
    private TextureAtlas atlas;
    private Camera lastCamera;

    // ========== ASYNC MESH BUILDING ==========
    private final ExecutorService meshBuilder;
    private final Set<ChunkSection> buildingSections = ConcurrentHashMap.newKeySet();
    private final PriorityBlockingQueue<ChunkBuildTask> buildQueue = new PriorityBlockingQueue<>();
    private final Set<ChunkSection> queuedSections = ConcurrentHashMap.newKeySet();
    private final Queue<MeshDataResult> pendingVBOCreation = new ConcurrentLinkedQueue<>();

    // ========== STATISTICS ==========
    private int chunksRenderedLastFrame = 0;
    private int chunksCulledLastFrame = 0;
    private long lastStatsResetTime = 0;
    private long totalBuildTime = 0;
    private int totalBuilds = 0;
    private long slowestBuildTime = 0;

    // ========== FRUSTUM CULLING CACHE ==========
    private float[] frustumPlanes = new float[24];
    private boolean frustumDirty = true;
    private float lastCameraX, lastCameraY, lastCameraZ;
    private float lastCameraPitch, lastCameraYaw;

    // ========== INNER CLASSES ==========

    private static class ChunkBuildTask implements Comparable<ChunkBuildTask> {
        final ChunkSection section;
        final int chunkX;
        final int chunkZ;
        final int sectionY;
        final double distanceSquared;
        final long timestamp;
        final boolean isUrgent;

        ChunkBuildTask(ChunkSection section, double distSq, boolean urgent) {
            this.section = section;
            Chunk parent = section.getParentChunk();
            this.chunkX = parent != null ? parent.getChunkX() : 0;
            this.chunkZ = parent != null ? parent.getChunkZ() : 0;
            this.sectionY = section.getSectionY();
            this.distanceSquared = distSq;
            this.timestamp = System.nanoTime();
            this.isUrgent = urgent;
        }

        @Override
        public int compareTo(ChunkBuildTask other) {
            if (this.isUrgent != other.isUrgent) {
                return this.isUrgent ? -1 : 1;
            }
            int distCompare = Double.compare(this.distanceSquared, other.distanceSquared);
            if (distCompare != 0)
                return distCompare;
            return Long.compare(this.timestamp, other.timestamp);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            ChunkBuildTask other = (ChunkBuildTask) obj;
            return chunkX == other.chunkX &&
                    chunkZ == other.chunkZ &&
                    sectionY == other.sectionY;
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkX, chunkZ, sectionY);
        }
    }

    private static class MeshDataResult {
        final ChunkSection section;
        final int chunkX, chunkZ, sectionY;
        final List<Float> solidVertices;
        final List<Float> waterVertices;
        final List<Float> translucentVertices;
        final long buildTime;

        MeshDataResult(ChunkSection section, List<Float> solid, List<Float> water, List<Float> translucent,
                long buildTime) {
            this.section = section;
            Chunk parent = section.getParentChunk();
            this.chunkX = parent != null ? parent.getChunkX() : 0;
            this.chunkZ = parent != null ? parent.getChunkZ() : 0;
            this.sectionY = section.getSectionY();
            this.solidVertices = solid;
            this.waterVertices = water;
            this.translucentVertices = translucent;
            this.buildTime = buildTime;
        }
    }

    // ========== CONSTRUCTORS ==========

    public ChunkRenderer(TextureAtlas atlas) {
        this.atlas = atlas;
        this.meshBuilder = Executors.newFixedThreadPool(Settings.MESH_BUILD_THREADS, r -> {
            Thread t = new Thread(r, "MeshBuilder");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        if (Settings.DEBUG_MODE) {
            System.out.println("[ChunkRenderer] Initialized with Minecraft-style lighting (no rebuild on time change)");
        }
    }

    public ChunkRenderer() {
        this(BlockTextures.getAtlas());
    }

    // ========== SETTERS ==========

    public void setWorld(World world) {
        this.world = world;
    }

    public void setLightingEngine(LightingEngine lightingEngine) {
        this.lightingEngine = lightingEngine;
    }

    // ========== MAIN UPDATE METHODS ==========

    public void update() {
        if (lastCamera != null) {
            update(lastCamera);
        } else {
            uploadPendingMeshes();
        }
    }

    public void update(Camera camera) {
        this.lastCamera = camera;
        updateFrustumIfNeeded(camera);
        startMeshDataBuilds(camera);
        uploadPendingMeshes();
        cleanupStaleQueueEntries();

        if (Settings.DEBUG_MODE && Settings.LOG_PERFORMANCE) {
            logPerformanceStats();
        }
    }

    public void renderChunk(Chunk chunk, Camera camera) {
        if (chunk == null || !chunk.isReady()) {
            return;
        }

        this.lastCamera = camera;

        for (int i = 0; i < Chunk.SECTION_COUNT; i++) {
            ChunkSection section = chunk.getSection(i);
            if (section != null && !section.isEmpty()) {
                if (section.needsMeshRebuild() && !buildingSections.contains(section)) {
                    queueSectionRebuild(section, camera);
                }
            }
        }
    }

    private void queueSectionRebuild(ChunkSection section, Camera camera) {
        Chunk parentChunk = section.getParentChunk();
        if (parentChunk == null) {
            return;
        }

        if (queuedSections.contains(section)) {
            return;
        }

        double centerX = (double) parentChunk.getChunkX() * Chunk.CHUNK_SIZE + 8.0;
        double centerY = (double) section.getMinWorldY() + 8.0;
        double centerZ = (double) parentChunk.getChunkZ() * Chunk.CHUNK_SIZE + 8.0;

        double dx = centerX - camera.getX();
        double dy = centerY - camera.getY();
        double dz = centerZ - camera.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        boolean isVisible = isSectionVisibleFast(section, camera);
        boolean isClose = distSq < (Settings.RENDER_DISTANCE * 16) * (Settings.RENDER_DISTANCE * 16);
        boolean isUrgent = isVisible && isClose;

        ChunkBuildTask newTask = new ChunkBuildTask(section, distSq, isUrgent);

        if (queuedSections.add(section)) {
            buildQueue.offer(newTask);
        }
    }

    // ========== FRUSTUM UPDATE ==========

    private void updateFrustumIfNeeded(Camera camera) {
        float dx = Math.abs(camera.getX() - lastCameraX);
        float dy = Math.abs(camera.getY() - lastCameraY);
        float dz = Math.abs(camera.getZ() - lastCameraZ);
        float dPitch = Math.abs(camera.getPitch() - lastCameraPitch);
        float dYaw = Math.abs(camera.getYaw() - lastCameraYaw);

        if (dx > 1 || dy > 1 || dz > 1 || dPitch > 5 || dYaw > 5) {
            frustumDirty = true;
            lastCameraX = camera.getX();
            lastCameraY = camera.getY();
            lastCameraZ = camera.getZ();
            lastCameraPitch = camera.getPitch();
            lastCameraYaw = camera.getYaw();
        }
    }

    // ========== ASYNC MESH BUILDING ==========

    private void startMeshDataBuilds(Camera camera) {
        int buildsStarted = 0;
        int maxBuilds = Settings.MAX_MESH_BUILDS_PER_FRAME;

        if (buildQueue.size() > 100) {
            maxBuilds = Math.min(maxBuilds * 2, Settings.MESH_BUILD_THREADS * 2);
        }

        List<ChunkBuildTask> toRequeue = new ArrayList<>();

        while (buildsStarted < maxBuilds) {
            ChunkBuildTask task = buildQueue.poll();
            if (task == null)
                break;

            queuedSections.remove(task.section);

            if (task.section == null || task.section.getParentChunk() == null) {
                continue;
            }

            if (!task.section.needsMeshRebuild()) {
                continue;
            }

            if (buildingSections.contains(task.section)) {
                continue;
            }

            if (!areNeighborChunksReady(task.section)) {
                toRequeue.add(new ChunkBuildTask(task.section, task.distanceSquared * 1.5, false));
                continue;
            }

            buildingSections.add(task.section);

            final ChunkSection sectionCopy = task.section;
            meshBuilder.submit(() -> buildMeshDataAsync(sectionCopy));
            buildsStarted++;
        }

        for (ChunkBuildTask task : toRequeue) {
            if (queuedSections.add(task.section)) {
                buildQueue.offer(task);
            }
        }
    }

    private boolean areNeighborChunksReady(ChunkSection section) {
        if (world == null)
            return true;

        Chunk parent = section.getParentChunk();
        if (parent == null)
            return false;

        int cx = parent.getChunkX();
        int cz = parent.getChunkZ();

        int[][] neighbors = { { cx - 1, cz }, { cx + 1, cz }, { cx, cz - 1 }, { cx, cz + 1 } };

        for (int[] neighbor : neighbors) {
            Chunk neighborChunk = world.getChunk(neighbor[0], neighbor[1]);
            if (neighborChunk == null || !neighborChunk.isReady()) {
                return false;
            }
        }

        return true;
    }

    private void cleanupStaleQueueEntries() {
        buildQueue.removeIf(task -> {
            if (task.section == null || task.section.getParentChunk() == null) {
                queuedSections.remove(task.section);
                return true;
            }
            if (!task.section.needsMeshRebuild()) {
                queuedSections.remove(task.section);
                return true;
            }
            return false;
        });

        while (buildQueue.size() > 500) {
            ChunkBuildTask removed = buildQueue.poll();
            if (removed != null) {
                queuedSections.remove(removed.section);
            }
        }
    }

    private void buildMeshDataAsync(ChunkSection section) {
        long startTime = System.nanoTime();

        try {
            Chunk parentChunk = section.getParentChunk();
            if (parentChunk == null || !parentChunk.isReady()) {
                buildingSections.remove(section);
                return;
            }

            // ✅ FIX: Handle lighting-only updates without full rebuild
            if (!section.needsGeometryRebuild() && section.needsLightingUpdate()) {
                section.setNeedsLightingUpdate(false);
                buildingSections.remove(section);
                return;
            }

            List<Float> solidVertices = new ArrayList<>(4096);
            List<Float> waterVertices = new ArrayList<>(1024);
            List<Float> translucentVertices = new ArrayList<>(1024);

            long chunkOffsetX = (long) parentChunk.getChunkX() * Chunk.CHUNK_SIZE;
            long chunkOffsetZ = (long) parentChunk.getChunkZ() * Chunk.CHUNK_SIZE;
            int sectionMinY = section.getMinWorldY();

            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int y = 0; y < ChunkSection.SECTION_SIZE; y++) {
                    for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                        int worldY = sectionMinY + y;
                        GameBlock block = section.getBlock(x, y, z);

                        if (block != null && !block.isAir()) {
                            float worldX = (float) (chunkOffsetX + x);
                            float renderY = worldY;
                            float worldZ = (float) (chunkOffsetZ + z);

                            if (block == BlockRegistry.WATER) {
                                addWaterBlockToList(parentChunk, x, worldY, z, worldX, renderY, worldZ, waterVertices,
                                        block);
                            } else if (block == BlockRegistry.OAK_LEAVES) {
                                addBlockFacesToList(parentChunk, x, worldY, z, worldX, renderY, worldZ,
                                        translucentVertices, block, false, true, null);
                            } else if (block == BlockRegistry.GRASS_BLOCK) {
                                addBlockFacesToList(parentChunk, x, worldY, z, worldX, renderY, worldZ, solidVertices,
                                        block, false, false, translucentVertices);
                            } else {
                                addBlockFacesToList(parentChunk, x, worldY, z, worldX, renderY, worldZ, solidVertices,
                                        block, false, false, null);
                            }
                        }
                    }
                }
            }

            long buildTime = System.nanoTime() - startTime;

            if (Settings.DEBUG_MODE && Settings.LOG_PERFORMANCE && buildTime > 10_000_000) {
                System.out.printf("[ChunkRenderer] Slow mesh build: %.2fms for section Y=%d at [%d, %d]%n",
                        buildTime / 1_000_000.0, section.getMinWorldY(),
                        parentChunk.getChunkX(), parentChunk.getChunkZ());
            }

            pendingVBOCreation
                    .offer(new MeshDataResult(section, solidVertices, waterVertices, translucentVertices, buildTime));

        } catch (Exception e) {
            Chunk parent = section.getParentChunk();
            String coords = parent != null ? "[" + parent.getChunkX() + ", " + parent.getChunkZ() + "]" : "unknown";
            System.err.println("[ChunkRenderer] Error building mesh for section " + section.getMinWorldY()
                    + " in chunk " + coords + ": " + e.getMessage());
            if (Settings.DEBUG_MODE) {
                e.printStackTrace();
            }
            buildingSections.remove(section);
        }
    }

    private void uploadPendingMeshes() {
        int uploaded = 0;

        while (uploaded < Settings.MAX_VBO_UPLOADS_PER_FRAME && !pendingVBOCreation.isEmpty()) {
            MeshDataResult result = pendingVBOCreation.poll();

            if (result != null && result.section != null) {
                Chunk parent = result.section.getParentChunk();
                if (parent == null || parent.getChunkX() != result.chunkX || parent.getChunkZ() != result.chunkZ) {
                    buildingSections.remove(result.section);
                    continue;
                }

                ChunkMesh solidMesh = createMeshFromVertices(result.solidVertices);
                ChunkMesh waterMesh = createMeshFromVertices(result.waterVertices);
                ChunkMesh translucentMesh = createMeshFromVertices(result.translucentVertices);

                swapMeshes(result.section, solidMesh, waterMesh, translucentMesh);

                result.section.setNeedsGeometryRebuild(false);
                result.section.setNeedsLightingUpdate(false);
                buildingSections.remove(result.section);

                totalBuildTime += result.buildTime;
                totalBuilds++;
                if (result.buildTime > slowestBuildTime) {
                    slowestBuildTime = result.buildTime;
                }

                uploaded++;

                if (Settings.DEBUG_CHUNK_LOADING) {
                    System.out.printf(
                            "[ChunkRenderer] Uploaded mesh for section Y=%d at [%d, %d] (build time: %.2fms)%n",
                            result.section.getSectionY(), result.chunkX, result.chunkZ, result.buildTime / 1_000_000.0);
                }
            }
        }
    }

    private ChunkMesh createMeshFromVertices(List<Float> vertices) {
        ChunkMesh mesh = new ChunkMesh(atlas);

        for (int i = 0; i + 11 < vertices.size(); i += 12) {
            mesh.addVertex(
                    vertices.get(i), vertices.get(i + 1), vertices.get(i + 2),
                    vertices.get(i + 3), vertices.get(i + 4), vertices.get(i + 5),
                    vertices.get(i + 6),
                    vertices.get(i + 7), vertices.get(i + 8), vertices.get(i + 9),
                    vertices.get(i + 10), vertices.get(i + 11));
        }

        mesh.build();
        return mesh;
    }

    private void swapMeshes(ChunkSection section, ChunkMesh newSolid, ChunkMesh newWater, ChunkMesh newTranslucent) {
        ChunkMesh oldSolid = solidMeshes.put(section, newSolid);
        if (oldSolid != null)
            oldSolid.destroy();

        ChunkMesh oldWater = waterMeshes.put(section, newWater);
        if (oldWater != null)
            oldWater.destroy();

        ChunkMesh oldTranslucent = translucentMeshes.put(section, newTranslucent);
        if (oldTranslucent != null)
            oldTranslucent.destroy();
    }

    // ========== TIME-OF-DAY BRIGHTNESS APPLICATION ==========

    /**
     * ✅ NEW: Apply time-of-day brightness multiplier before rendering
     * Uses glColor4f to modulate all vertex colors globally
     * This is the Minecraft-style approach - no mesh rebuild needed!
     */
    private void applyTimeOfDayBrightness() {
        glColor4f(timeOfDayBrightness, timeOfDayBrightness, timeOfDayBrightness, 1.0f);
    }

    /**
     * ✅ NEW: Reset color modulation after rendering
     */
    private void resetColorModulation() {
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // ========== RENDER PASSES ==========

    public void render(Camera camera) {
        chunksRenderedLastFrame = 0;
        chunksCulledLastFrame = 0;

        if (System.currentTimeMillis() - lastStatsResetTime > 1000) {
            lastStatsResetTime = System.currentTimeMillis();
        }

        // ✅ Apply time-of-day brightness
        applyTimeOfDayBrightness();

        for (Map.Entry<ChunkSection, ChunkMesh> entry : solidMeshes.entrySet()) {
            ChunkSection section = entry.getKey();
            ChunkMesh mesh = entry.getValue();

            if (Settings.FRUSTUM_CULLING && !isSectionVisible(section, camera)) {
                chunksCulledLastFrame++;
                continue;
            }

            if (mesh != null && mesh.getVertexCount() > 0) {
                mesh.render();
                chunksRenderedLastFrame++;
            }
        }

        for (ChunkMesh mesh : waterMeshes.values()) {
            if (mesh != null && mesh.getVertexCount() > 0)
                mesh.render();
        }

        for (ChunkMesh mesh : translucentMeshes.values()) {
            if (mesh != null && mesh.getVertexCount() > 0)
                mesh.render();
        }

        // ✅ Reset color modulation
        resetColorModulation();
    }

    /**
     * ✅ REVISED: Apply time-of-day brightness in solid pass
     */
    public void renderSolidPass(Collection<Chunk> chunks) {
        BlockTextures.bind();

        // ✅ Apply time-of-day brightness (no mesh rebuild!)
        applyTimeOfDayBrightness();

        for (Chunk chunk : chunks) {
            if (chunk == null || !chunk.isReady())
                continue;

            for (int i = 0; i < Chunk.SECTION_COUNT; i++) {
                ChunkSection section = chunk.getSection(i);
                if (section != null && !section.isEmpty()) {
                    ChunkMesh solidMesh = solidMeshes.get(section);
                    if (solidMesh != null && solidMesh.getVertexCount() > 0) {
                        solidMesh.render();
                    }
                }
            }
        }

        // ✅ Reset color modulation
        resetColorModulation();
    }

    /**
     * ✅ REVISED: Apply time-of-day brightness in translucent pass
     */
    public void renderTranslucentPass(Collection<Chunk> chunks, Camera camera) {
        List<ChunkSection> sortedSections = new ArrayList<>(translucentMeshes.keySet());
        sortedSections.sort((s1, s2) -> {
            double dist1 = getSectionDistanceSquared(s1, camera);
            double dist2 = getSectionDistanceSquared(s2, camera);
            return Double.compare(dist2, dist1);
        });

        BlockTextures.bind();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glAlphaFunc(GL_GREATER, 0.1f);
        glEnable(GL_ALPHA_TEST);

        // ✅ Apply time-of-day brightness
        applyTimeOfDayBrightness();

        for (ChunkSection section : sortedSections) {
            if (Settings.FRUSTUM_CULLING && !isSectionVisible(section, camera))
                continue;

            ChunkMesh translucentMesh = translucentMeshes.get(section);
            if (translucentMesh != null && translucentMesh.getVertexCount() > 0) {
                translucentMesh.render();
            }
        }

        // ✅ Reset color modulation
        resetColorModulation();

        glDisable(GL_ALPHA_TEST);
        glDisable(GL_BLEND);
    }

    /**
     * ✅ REVISED: Apply time-of-day brightness in water pass
     */
    public void renderWaterPass(Collection<Chunk> chunks, Camera camera) {
        BlockTextures.bind();

        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);

        // ✅ Apply time-of-day brightness (slightly brighter for water reflections)
        float waterBrightness = Math.min(1.0f, timeOfDayBrightness * 1.1f);
        glColor4f(waterBrightness, waterBrightness, waterBrightness, 1.0f);

        for (Map.Entry<ChunkSection, ChunkMesh> entry : waterMeshes.entrySet()) {
            ChunkSection section = entry.getKey();
            ChunkMesh mesh = entry.getValue();

            if (Settings.FRUSTUM_CULLING && !isSectionVisible(section, camera)) {
                continue;
            }

            if (mesh != null && mesh.getVertexCount() > 0) {
                mesh.render();
            }
        }

        // ✅ Reset color modulation
        resetColorModulation();

        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
    }

    // ========== FRUSTUM CULLING ==========

    private boolean isSectionVisibleFast(ChunkSection section, Camera camera) {
        if (section == null || section.getParentChunk() == null)
            return false;

        Chunk chunk = section.getParentChunk();

        double centerX = (double) chunk.getChunkX() * Chunk.CHUNK_SIZE + 8.0;
        double centerY = (double) section.getMinWorldY() + 8.0;
        double centerZ = (double) chunk.getChunkZ() * Chunk.CHUNK_SIZE + 8.0;

        double dx = centerX - camera.getX();
        double dy = centerY - camera.getY();
        double dz = centerZ - camera.getZ();

        float[] forward = camera.getForwardVector();
        double dot = dx * forward[0] + dy * forward[1] + dz * forward[2];

        double radius = Chunk.CHUNK_SIZE * 1.5;
        return dot >= -radius;
    }

    private boolean isSectionVisible(ChunkSection section, Camera camera) {
        if (!Settings.FRUSTUM_CULLING)
            return true;
        if (section == null || section.getParentChunk() == null)
            return false;

        Chunk chunk = section.getParentChunk();

        double minX = (double) chunk.getChunkX() * Chunk.CHUNK_SIZE;
        double maxX = minX + Chunk.CHUNK_SIZE;
        double minY = section.getMinWorldY();
        double maxY = minY + ChunkSection.SECTION_SIZE;
        double minZ = (double) chunk.getChunkZ() * Chunk.CHUNK_SIZE;
        double maxZ = minZ + Chunk.CHUNK_SIZE;

        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;
        double centerZ = (minZ + maxZ) / 2;

        double radius = Chunk.CHUNK_SIZE * Math.sqrt(3) / 2.0 + 1.0;

        double dx = centerX - camera.getX();
        double dy = centerY - camera.getY();
        double dz = centerZ - camera.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        double maxDist = (Settings.RENDER_DISTANCE + 2) * Chunk.CHUNK_SIZE;
        if (distSq > maxDist * maxDist) {
            return false;
        }

        float[] forward = camera.getForwardVector();
        double forwardX = forward[0];
        double forwardY = forward[1];
        double forwardZ = forward[2];

        double dot = dx * forwardX + dy * forwardY + dz * forwardZ;

        if (distSq < radius * radius * 4) {
            return true;
        }

        double fovRadians = Math.toRadians(Settings.FOV + 30);
        double halfFov = fovRadians / 2;

        double dist = Math.sqrt(distSq);
        if (dist < 0.001)
            return true;

        double normalizedDot = dot / dist;

        double angleMargin = Math.asin(Math.min(1.0, radius / dist));
        double effectiveCos = Math.cos(halfFov + angleMargin);

        return normalizedDot >= effectiveCos - 0.1;
    }

    // ========== DISTANCE CALCULATIONS ==========

    private double getSectionDistanceSquared(ChunkSection section, Camera camera) {
        if (section == null || section.getParentChunk() == null)
            return Double.MAX_VALUE;

        Chunk chunk = section.getParentChunk();

        double centerX = (double) chunk.getChunkX() * Chunk.CHUNK_SIZE + 8.0;
        double centerY = (double) section.getMinWorldY() + 8.0;
        double centerZ = (double) chunk.getChunkZ() * Chunk.CHUNK_SIZE + 8.0;

        double dx = centerX - camera.getX();
        double dy = centerY - camera.getY();
        double dz = centerZ - camera.getZ();

        return dx * dx + dy * dy + dz * dz;
    }

    // ========== BLOCK FACE BUILDING (MINECRAFT-STYLE LIGHTING) ==========

    private void addWaterBlockToList(Chunk chunk, int x, int y, int z,
            float worldX, float worldY, float worldZ,
            List<Float> vertices, GameBlock block) {
        float[] baseColor = new float[] { 0.5f, 0.7f, 1.0f };
        float alpha = 0.7f;
        float topY = worldY + 0.875f;

        GameBlock top = getBlockSafe(chunk, x, y + 1, z);
        GameBlock bottom = getBlockSafe(chunk, x, y - 1, z);
        GameBlock north = getBlockSafe(chunk, x, y, z - 1);
        GameBlock south = getBlockSafe(chunk, x, y, z + 1);
        GameBlock east = getBlockSafe(chunk, x + 1, y, z);
        GameBlock west = getBlockSafe(chunk, x - 1, y, z);

        float[] uv = BlockTextures.getUV(block, "top");

        // ✅ REVISED: Use static face brightness + block light, NO sun brightness baked
        // in
        if (top != BlockRegistry.WATER) {
            float faceBrightness = getStaticFaceBrightness(0, 1, 0);
            float lightBrightness = getLightBrightnessAt(chunk, x, y + 1, z);
            float brightness = faceBrightness * lightBrightness;
            addWaterFaceToList(vertices,
                    worldX, topY, worldZ,
                    worldX, topY, worldZ + 1,
                    worldX + 1, topY, worldZ + 1,
                    worldX + 1, topY, worldZ,
                    baseColor, alpha, brightness, 0, 1, 0, uv);
        }

        if (bottom != null && bottom.isAir()) {
            float faceBrightness = getStaticFaceBrightness(0, -1, 0);
            float lightBrightness = getLightBrightnessAt(chunk, x, y - 1, z);
            float brightness = faceBrightness * lightBrightness;
            addWaterFaceToList(vertices,
                    worldX, worldY, worldZ,
                    worldX + 1, worldY, worldZ,
                    worldX + 1, worldY, worldZ + 1,
                    worldX, worldY, worldZ + 1,
                    baseColor, alpha, brightness, 0, -1, 0, uv);
        }

        if (north != BlockRegistry.WATER && (north == null || north.isAir() || !north.isSolid())) {
            float faceBrightness = getStaticFaceBrightness(0, 0, -1);
            float lightBrightness = getLightBrightnessAt(chunk, x, y, z - 1);
            float brightness = faceBrightness * lightBrightness;
            addWaterFaceToList(vertices,
                    worldX, worldY, worldZ,
                    worldX, topY, worldZ,
                    worldX + 1, topY, worldZ,
                    worldX + 1, worldY, worldZ,
                    baseColor, alpha, brightness, 0, 0, -1, uv);
        }

        if (south != BlockRegistry.WATER && (south == null || south.isAir() || !south.isSolid())) {
            float faceBrightness = getStaticFaceBrightness(0, 0, 1);
            float lightBrightness = getLightBrightnessAt(chunk, x, y, z + 1);
            float brightness = faceBrightness * lightBrightness;
            addWaterFaceToList(vertices,
                    worldX, worldY, worldZ + 1,
                    worldX + 1, worldY, worldZ + 1,
                    worldX + 1, topY, worldZ + 1,
                    worldX, topY, worldZ + 1,
                    baseColor, alpha, brightness, 0, 0, 1, uv);
        }

        if (east != BlockRegistry.WATER && (east == null || east.isAir() || !east.isSolid())) {
            float faceBrightness = getStaticFaceBrightness(1, 0, 0);
            float lightBrightness = getLightBrightnessAt(chunk, x + 1, y, z);
            float brightness = faceBrightness * lightBrightness;
            addWaterFaceToList(vertices,
                    worldX + 1, worldY, worldZ,
                    worldX + 1, topY, worldZ,
                    worldX + 1, topY, worldZ + 1,
                    worldX + 1, worldY, worldZ + 1,
                    baseColor, alpha, brightness, 1, 0, 0, uv);
        }

        if (west != BlockRegistry.WATER && (west == null || west.isAir() || !west.isSolid())) {
            float faceBrightness = getStaticFaceBrightness(-1, 0, 0);
            float lightBrightness = getLightBrightnessAt(chunk, x - 1, y, z);
            float brightness = faceBrightness * lightBrightness;
            addWaterFaceToList(vertices,
                    worldX, worldY, worldZ,
                    worldX, worldY, worldZ + 1,
                    worldX, topY, worldZ + 1,
                    worldX, topY, worldZ,
                    baseColor, alpha, brightness, -1, 0, 0, uv);
        }
    }

    private void addWaterFaceToList(List<Float> vertices,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float[] color, float alpha, float brightness,
            float nx, float ny, float nz, float[] uv) {
        float r = color[0] * brightness;
        float g = color[1] * brightness;
        float b = color[2] * brightness;

        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];

        addVertexToList(vertices, x1, y1, z1, r, g, b, alpha, nx, ny, nz, u1, v1);
        addVertexToList(vertices, x2, y2, z2, r, g, b, alpha, nx, ny, nz, u1, v2);
        addVertexToList(vertices, x3, y3, z3, r, g, b, alpha, nx, ny, nz, u2, v2);
        addVertexToList(vertices, x4, y4, z4, r, g, b, alpha, nx, ny, nz, u2, v1);
    }

    private void addBlockFacesToList(Chunk chunk, int x, int y, int z,
            float worldX, float worldY, float worldZ,
            List<Float> vertices, GameBlock block,
            boolean isWater, boolean isTranslucent,
            List<Float> overlayVertices) {
        float[] color = new float[] { 1.0f, 1.0f, 1.0f };
        float alpha = isTranslucent ? 0.9f : 1.0f;

        if (shouldRenderFace(chunk, x, y + 1, z, block)) {
            addTopFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }

        if (shouldRenderFace(chunk, x, y - 1, z, block)) {
            addBottomFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }

        if (shouldRenderFace(chunk, x, y, z - 1, block)) {
            addSideFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block, 0, 0, -1,
                    overlayVertices);
        }

        if (shouldRenderFace(chunk, x, y, z + 1, block)) {
            addSideFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block, 0, 0, 1,
                    overlayVertices);
        }

        if (shouldRenderFace(chunk, x + 1, y, z, block)) {
            addSideFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block, 1, 0, 0,
                    overlayVertices);
        }

        if (shouldRenderFace(chunk, x - 1, y, z, block)) {
            addSideFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block, -1, 0, 0,
                    overlayVertices);
        }
    }

    /**
     * ✅ REVISED: Top face - uses static face brightness + block light
     * NO sun brightness baked in - that's applied via glColor at render time
     */
    private void addTopFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
            float worldX, float worldY, float worldZ,
            float[] color, float alpha, GameBlock block) {

        // ✅ Get static face brightness (directional shading, not time-dependent)
        float faceBrightness = getStaticFaceBrightness(0, 1, 0);

        // ✅ Get block light values and convert to brightness
        float light1 = getLightBrightnessAt(chunk, x, y + 1, z) * faceBrightness;
        float light2 = getLightBrightnessAt(chunk, x, y + 1, z + 1) * faceBrightness;
        float light3 = getLightBrightnessAt(chunk, x + 1, y + 1, z + 1) * faceBrightness;
        float light4 = getLightBrightnessAt(chunk, x + 1, y + 1, z) * faceBrightness;

        float[] uv = BlockTextures.getUV(block, "top");
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];

        float[] tint = { 1.0f, 1.0f, 1.0f };
        if (block == BlockRegistry.GRASS_BLOCK) {
            tint = block.getBiomeColor();
        }

        addVertexToList(vertices, worldX, worldY + 1, worldZ,
                color[0] * tint[0] * light1, color[1] * tint[1] * light1, color[2] * tint[2] * light1, alpha, 0, 1, 0,
                u1, v1);
        addVertexToList(vertices, worldX, worldY + 1, worldZ + 1,
                color[0] * tint[0] * light2, color[1] * tint[1] * light2, color[2] * tint[2] * light2, alpha, 0, 1, 0,
                u1, v2);
        addVertexToList(vertices, worldX + 1, worldY + 1, worldZ + 1,
                color[0] * tint[0] * light3, color[1] * tint[1] * light3, color[2] * tint[2] * light3, alpha, 0, 1, 0,
                u2, v2);
        addVertexToList(vertices, worldX + 1, worldY + 1, worldZ,
                color[0] * tint[0] * light4, color[1] * tint[1] * light4, color[2] * tint[2] * light4, alpha, 0, 1, 0,
                u2, v1);
    }

    /**
     * ✅ REVISED: Bottom face - uses static face brightness + block light
     */
    private void addBottomFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
            float worldX, float worldY, float worldZ,
            float[] color, float alpha, GameBlock block) {

        // ✅ Static face brightness (bottom is darker)
        float faceBrightness = getStaticFaceBrightness(0, -1, 0);

        float light1 = getLightBrightnessAt(chunk, x, y - 1, z) * faceBrightness;
        float light2 = getLightBrightnessAt(chunk, x + 1, y - 1, z) * faceBrightness;
        float light3 = getLightBrightnessAt(chunk, x + 1, y - 1, z + 1) * faceBrightness;
        float light4 = getLightBrightnessAt(chunk, x, y - 1, z + 1) * faceBrightness;

        float[] uv = BlockTextures.getUV(block, "bottom");
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];

        addVertexToList(vertices, worldX, worldY, worldZ,
                color[0] * light1, color[1] * light1, color[2] * light1, alpha, 0, -1, 0, u1, v1);
        addVertexToList(vertices, worldX + 1, worldY, worldZ,
                color[0] * light2, color[1] * light2, color[2] * light2, alpha, 0, -1, 0, u2, v1);
        addVertexToList(vertices, worldX + 1, worldY, worldZ + 1,
                color[0] * light3, color[1] * light3, color[2] * light3, alpha, 0, -1, 0, u2, v2);
        addVertexToList(vertices, worldX, worldY, worldZ + 1,
                color[0] * light4, color[1] * light4, color[2] * light4, alpha, 0, -1, 0, u1, v2);
    }

    /**
     * ✅ REVISED: Side faces - uses static face brightness + block light
     */
    private void addSideFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
            float worldX, float worldY, float worldZ,
            float[] color, float alpha, GameBlock block,
            float nx, float ny, float nz,
            List<Float> overlayVertices) {

        // ✅ Static face brightness (directional shading)
        float faceBrightness = getStaticFaceBrightness(nx, ny, nz);

        int neighborX = x + (int) nx;
        int neighborY = y + (int) ny;
        int neighborZ = z + (int) nz;

        float light1 = getLightBrightnessAt(chunk, neighborX, neighborY, neighborZ) * faceBrightness;
        float light2 = getLightBrightnessAt(chunk, neighborX, neighborY + 1, neighborZ) * faceBrightness;
        float light3 = light2;
        float light4 = light1;

        float[] uv = BlockTextures.getUV(block, "side");
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];

        if (nz == -1) { // North face
            addVertexToList(vertices, worldX, worldY, worldZ,
                    color[0] * light1, color[1] * light1, color[2] * light1, alpha, nx, ny, nz, u1, v2);
            addVertexToList(vertices, worldX, worldY + 1, worldZ,
                    color[0] * light2, color[1] * light2, color[2] * light2, alpha, nx, ny, nz, u1, v1);
            addVertexToList(vertices, worldX + 1, worldY + 1, worldZ,
                    color[0] * light3, color[1] * light3, color[2] * light3, alpha, nx, ny, nz, u2, v1);
            addVertexToList(vertices, worldX + 1, worldY, worldZ,
                    color[0] * light4, color[1] * light4, color[2] * light4, alpha, nx, ny, nz, u2, v2);
        } else if (nz == 1) { // South face
            addVertexToList(vertices, worldX, worldY, worldZ + 1,
                    color[0] * light1, color[1] * light1, color[2] * light1, alpha, nx, ny, nz, u2, v2);
            addVertexToList(vertices, worldX + 1, worldY, worldZ + 1,
                    color[0] * light2, color[1] * light2, color[2] * light2, alpha, nx, ny, nz, u1, v2);
            addVertexToList(vertices, worldX + 1, worldY + 1, worldZ + 1,
                    color[0] * light3, color[1] * light3, color[2] * light3, alpha, nx, ny, nz, u1, v1);
            addVertexToList(vertices, worldX, worldY + 1, worldZ + 1,
                    color[0] * light4, color[1] * light4, color[2] * light4, alpha, nx, ny, nz, u2, v1);
        } else if (nx == 1) { // East face
            addVertexToList(vertices, worldX + 1, worldY, worldZ,
                    color[0] * light1, color[1] * light1, color[2] * light1, alpha, nx, ny, nz, u2, v2);
            addVertexToList(vertices, worldX + 1, worldY + 1, worldZ,
                    color[0] * light2, color[1] * light2, color[2] * light2, alpha, nx, ny, nz, u2, v1);
            addVertexToList(vertices, worldX + 1, worldY + 1, worldZ + 1,
                    color[0] * light3, color[1] * light3, color[2] * light3, alpha, nx, ny, nz, u1, v1);
            addVertexToList(vertices, worldX + 1, worldY, worldZ + 1,
                    color[0] * light4, color[1] * light4, color[2] * light4, alpha, nx, ny, nz, u1, v2);
        } else if (nx == -1) { // West face
            addVertexToList(vertices, worldX, worldY, worldZ,
                    color[0] * light1, color[1] * light1, color[2] * light1, alpha, nx, ny, nz, u1, v2);
            addVertexToList(vertices, worldX, worldY, worldZ + 1,
                    color[0] * light2, color[1] * light2, color[2] * light2, alpha, nx, ny, nz, u2, v2);
            addVertexToList(vertices, worldX, worldY + 1, worldZ + 1,
                    color[0] * light3, color[1] * light3, color[2] * light3, alpha, nx, ny, nz, u2, v1);
            addVertexToList(vertices, worldX, worldY + 1, worldZ,
                    color[0] * light4, color[1] * light4, color[2] * light4, alpha, nx, ny, nz, u1, v1);
        }

        // Handle grass overlay
        if (block.hasOverlay("side_overlay") && overlayVertices != null) {
            addOverlayFace(overlayVertices, worldX, worldY, worldZ, nx, ny, nz,
                    block, light1, light2, light3, light4, alpha);
        }
    }

    private void addOverlayFace(List<Float> overlayVertices, float worldX, float worldY, float worldZ,
            float nx, float ny, float nz, GameBlock block,
            float light1, float light2, float light3, float light4, float alpha) {
        String overlayTexture = block.getOverlayTexture("side_overlay");
        float[] overlayUv = BlockTextures.getUV(overlayTexture);
        float[] tint = block.getBiomeColor();

        float ou1 = overlayUv[0], ov1 = overlayUv[1], ou2 = overlayUv[2], ov2 = overlayUv[3];
        float offset = 0.001f;

        if (nz == -1) {
            addVertexToList(overlayVertices, worldX, worldY, worldZ - offset,
                    tint[0] * light1, tint[1] * light1, tint[2] * light1, alpha, nx, ny, nz, ou1, ov2);
            addVertexToList(overlayVertices, worldX, worldY + 1, worldZ - offset,
                    tint[0] * light2, tint[1] * light2, tint[2] * light2, alpha, nx, ny, nz, ou1, ov1);
            addVertexToList(overlayVertices, worldX + 1, worldY + 1, worldZ - offset,
                    tint[0] * light3, tint[1] * light3, tint[2] * light3, alpha, nx, ny, nz, ou2, ov1);
            addVertexToList(overlayVertices, worldX + 1, worldY, worldZ - offset,
                    tint[0] * light4, tint[1] * light4, tint[2] * light4, alpha, nx, ny, nz, ou2, ov2);
        } else if (nz == 1) {
            addVertexToList(overlayVertices, worldX, worldY, worldZ + 1 + offset,
                    tint[0] * light1, tint[1] * light1, tint[2] * light1, alpha, nx, ny, nz, ou2, ov2);
            addVertexToList(overlayVertices, worldX + 1, worldY, worldZ + 1 + offset,
                    tint[0] * light2, tint[1] * light2, tint[2] * light2, alpha, nx, ny, nz, ou1, ov2);
            addVertexToList(overlayVertices, worldX + 1, worldY + 1, worldZ + 1 + offset,
                    tint[0] * light3, tint[1] * light3, tint[2] * light3, alpha, nx, ny, nz, ou1, ov1);
            addVertexToList(overlayVertices, worldX, worldY + 1, worldZ + 1 + offset,
                    tint[0] * light4, tint[1] * light4, tint[2] * light4, alpha, nx, ny, nz, ou2, ov1);
        } else if (nx == 1) {
            addVertexToList(overlayVertices, worldX + 1 + offset, worldY, worldZ,
                    tint[0] * light1, tint[1] * light1, tint[2] * light1, alpha, nx, ny, nz, ou2, ov2);
            addVertexToList(overlayVertices, worldX + 1 + offset, worldY + 1, worldZ,
                    tint[0] * light2, tint[1] * light2, tint[2] * light2, alpha, nx, ny, nz, ou2, ov1);
            addVertexToList(overlayVertices, worldX + 1 + offset, worldY + 1, worldZ + 1,
                    tint[0] * light3, tint[1] * light3, tint[2] * light3, alpha, nx, ny, nz, ou1, ov1);
            addVertexToList(overlayVertices, worldX + 1 + offset, worldY, worldZ + 1,
                    tint[0] * light4, tint[1] * light4, tint[2] * light4, alpha, nx, ny, nz, ou1, ov2);
        } else if (nx == -1) {
            addVertexToList(overlayVertices, worldX - offset, worldY, worldZ,
                    tint[0] * light1, tint[1] * light1, tint[2] * light1, alpha, nx, ny, nz, ou1, ov2);
            addVertexToList(overlayVertices, worldX - offset, worldY, worldZ + 1,
                    tint[0] * light2, tint[1] * light2, tint[2] * light2, alpha, nx, ny, nz, ou2, ov2);
            addVertexToList(overlayVertices, worldX - offset, worldY + 1, worldZ + 1,
                    tint[0] * light3, tint[1] * light3, tint[2] * light3, alpha, nx, ny, nz, ou2, ov1);
            addVertexToList(overlayVertices, worldX - offset, worldY + 1, worldZ,
                    tint[0] * light4, tint[1] * light4, tint[2] * light4, alpha, nx, ny, nz, ou1, ov1);
        }
    }

    private void addVertexToList(List<Float> vertices,
            float x, float y, float z,
            float r, float g, float b, float a,
            float nx, float ny, float nz,
            float u, float v) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(r);
        vertices.add(g);
        vertices.add(b);
        vertices.add(a);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
        vertices.add(u);
        vertices.add(v);
    }

    // ========== LIGHTING HELPERS (MINECRAFT-STYLE) ==========

    /**
     * ✅ NEW: Static face brightness (AO-style directional shading)
     * This NEVER changes with time of day - it's purely directional shading
     * 
     * Values match Minecraft's face shading:
     * - Top (Y+): 1.0 (brightest)
     * - Bottom (Y-): 0.5 (darkest)
     * - North/South (Z): 0.8
     * - East/West (X): 0.6
     */
    private float getStaticFaceBrightness(float nx, float ny, float nz) {
        if (ny > 0)
            return 1.0f; // Top - brightest
        if (ny < 0)
            return 0.5f; // Bottom - darkest
        if (nz != 0)
            return 0.8f; // North/South
        return 0.6f; // East/West
    }

    /**
     * ✅ NEW: Get light brightness at position (combines skylight + blocklight)
     * Returns brightness from block's light value (0-15), NOT time-dependent
     */
    private float getLightBrightnessAt(Chunk chunk, int x, int y, int z) {
        int lightValue = getLightValue(chunk, x, y, z);
        return getLightBrightness(lightValue);
    }

    /**
     * ✅ NEW: Get combined light value (0-15) without brightness conversion
     * This is the RAW light value that doesn't change with time of day
     */
    private int getLightValue(Chunk chunk, int x, int worldY, int z) {
        if (!Settings.isValidWorldY(worldY)) {
            return worldY > Settings.WORLD_MAX_Y ? 15 : 0;
        }

        if (x >= 0 && x < Chunk.CHUNK_SIZE && z >= 0 && z < Chunk.CHUNK_SIZE) {
            return LightingEngine.getCombinedLight(chunk, x, worldY, z);
        }

        if (world != null) {
            int worldX = chunk.getChunkX() * Chunk.CHUNK_SIZE + x;
            int worldZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE + z;

            int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
            int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

            Chunk neighborChunk = world.getChunk(chunkX, chunkZ);
            if (neighborChunk != null && neighborChunk.isReady()) {
                int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
                int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);
                return LightingEngine.getCombinedLight(neighborChunk, localX, worldY, localZ);
            }
        }

        return 15; // Default to full brightness
    }

    /**
     * ✅ NEW: Convert light value (0-15) to brightness (0.0-1.0)
     * This is BLOCK lighting only, not time-of-day lighting
     * Applies gamma correction
     */
    private float getLightBrightness(int lightValue) {
        float brightness = LightingEngine.getBrightness(lightValue);
        brightness = (float) Math.pow(brightness, 1.0f / Settings.GAMMA);
        return Math.max(Settings.MIN_BRIGHTNESS, brightness);
    }

    // ========== BLOCK ACCESS HELPERS ==========

    private GameBlock getBlockSafe(Chunk chunk, int x, int worldY, int z) {
        if (!Settings.isValidWorldY(worldY)) {
            return BlockRegistry.AIR;
        }

        if (x >= 0 && x < Chunk.CHUNK_SIZE && z >= 0 && z < Chunk.CHUNK_SIZE) {
            GameBlock block = chunk.getBlock(x, worldY, z);
            return (block != null) ? block : BlockRegistry.AIR;
        }

        if (world != null) {
            int worldX = chunk.getChunkX() * Chunk.CHUNK_SIZE + x;
            int worldZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE + z;
            GameBlock block = world.getBlock(worldX, worldY, worldZ);
            return (block != null) ? block : BlockRegistry.AIR;
        }

        return BlockRegistry.AIR;
    }

    private boolean shouldRenderFace(Chunk chunk, int x, int worldY, int z, GameBlock currentBlock) {
        if (!Settings.isValidWorldY(worldY)) {
            return worldY > Settings.WORLD_MAX_Y;
        }

        GameBlock neighbor = getBlockSafe(chunk, x, worldY, z);

        if (neighbor.isAir())
            return true;
        if (neighbor == currentBlock)
            return false;
        if (neighbor == BlockRegistry.WATER)
            return currentBlock != BlockRegistry.WATER;
        if (neighbor == BlockRegistry.OAK_LEAVES)
            return true;

        return !neighbor.isSolid();
    }

    // ========== CHUNK MANAGEMENT ==========

    public void removeChunk(Chunk chunk) {
        for (int i = 0; i < Chunk.SECTION_COUNT; i++) {
            ChunkSection section = chunk.getSection(i);
            if (section != null) {
                queuedSections.remove(section);
                buildQueue.removeIf(task -> task.section == section);
                pendingVBOCreation.removeIf(result -> result.section == section);
                buildingSections.remove(section);

                ChunkMesh solidMesh = solidMeshes.remove(section);
                if (solidMesh != null)
                    solidMesh.destroy();

                ChunkMesh waterMesh = waterMeshes.remove(section);
                if (waterMesh != null)
                    waterMesh.destroy();

                ChunkMesh translucentMesh = translucentMeshes.remove(section);
                if (translucentMesh != null)
                    translucentMesh.destroy();
            }
        }

        if (Settings.DEBUG_CHUNK_LOADING) {
            System.out.println("[ChunkRenderer] Removed chunk [" + chunk.getChunkX() + ", " + chunk.getChunkZ() + "]");
        }
    }

    public void cleanup() {
        meshBuilder.shutdown();
        try {
            if (!meshBuilder.awaitTermination(3, TimeUnit.SECONDS)) {
                meshBuilder.shutdownNow();
            }
        } catch (InterruptedException e) {
            meshBuilder.shutdownNow();
            Thread.currentThread().interrupt();
        }

        buildQueue.clear();
        queuedSections.clear();
        pendingVBOCreation.clear();
        buildingSections.clear();

        for (ChunkMesh mesh : solidMeshes.values()) {
            mesh.destroy();
        }
        solidMeshes.clear();

        for (ChunkMesh mesh : waterMeshes.values()) {
            mesh.destroy();
        }
        waterMeshes.clear();

        for (ChunkMesh mesh : translucentMeshes.values()) {
            mesh.destroy();
        }
        translucentMeshes.clear();

        System.out.println("[ChunkRenderer] Cleanup complete");
    }

    // ========== STATISTICS ==========

    public int getPendingBuilds() {
        return buildQueue.size() + buildingSections.size() + pendingVBOCreation.size();
    }

    public int getChunksRenderedLastFrame() {
        return chunksRenderedLastFrame;
    }

    public int getChunksCulledLastFrame() {
        return chunksCulledLastFrame;
    }

    public int getTotalLoadedMeshes() {
        return solidMeshes.size();
    }

    private void logPerformanceStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatsResetTime >= 5000) {
            double avgBuildTime = totalBuilds > 0 ? (totalBuildTime / totalBuilds / 1_000_000.0) : 0;

            System.out.println("[ChunkRenderer] Stats - " +
                    "Rendered: " + chunksRenderedLastFrame + ", " +
                    "Culled: " + chunksCulledLastFrame + ", " +
                    "Pending: " + getPendingBuilds() + ", " +
                    "Queued: " + queuedSections.size() + ", " +
                    "Meshes: " + getTotalLoadedMeshes() + ", " +
                    "Avg Build: " + String.format("%.2fms", avgBuildTime) + ", " +
                    "Slowest: " + String.format("%.2fms", slowestBuildTime / 1_000_000.0) + ", " +
                    "TimeOfDay: " + String.format("%.2f", timeOfDayBrightness));

            lastStatsResetTime = now;
            totalBuildTime = 0;
            totalBuilds = 0;
            slowestBuildTime = 0;
        }
    }

    public void forceRebuildAll() {
        for (ChunkSection section : solidMeshes.keySet()) {
            section.setNeedsGeometryRebuild(true);
        }
        System.out.println("[ChunkRenderer] Forced rebuild for " + solidMeshes.size() + " sections");
    }

    public void forceRebuildAround(float x, float z, int radiusChunks) {
        if (world == null)
            return;

        int centerCX = (int) Math.floor(x / Chunk.CHUNK_SIZE);
        int centerCZ = (int) Math.floor(z / Chunk.CHUNK_SIZE);

        int count = 0;
        for (int cx = centerCX - radiusChunks; cx <= centerCX + radiusChunks; cx++) {
            for (int cz = centerCZ - radiusChunks; cz <= centerCZ + radiusChunks; cz++) {
                Chunk chunk = world.getChunk(cx, cz);
                if (chunk != null && chunk.isReady()) {
                    for (int i = 0; i < Chunk.SECTION_COUNT; i++) {
                        ChunkSection section = chunk.getSection(i);
                        if (section != null && !section.isEmpty()) {
                            section.setNeedsGeometryRebuild(true);
                            count++;
                        }
                    }
                }
            }
        }

        if (Settings.DEBUG_MODE) {
            System.out.println(
                    "[ChunkRenderer] Force rebuild " + count + " sections around [" + centerCX + ", " + centerCZ + "]");
        }
    }

    public long getEstimatedMemoryUsage() {
        long total = 0;
        for (ChunkMesh mesh : solidMeshes.values()) {
            total += mesh.getVertexCount() * 12 * 4;
        }
        for (ChunkMesh mesh : waterMeshes.values()) {
            total += mesh.getVertexCount() * 12 * 4;
        }
        for (ChunkMesh mesh : translucentMeshes.values()) {
            total += mesh.getVertexCount() * 12 * 4;
        }
        return total;
    }
}