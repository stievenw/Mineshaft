// src/main/java/com/mineshaft/render/ChunkRenderer.java
package com.mineshaft.render;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.entity.Camera;
import com.mineshaft.world.Chunk;
import com.mineshaft.world.World;
import com.mineshaft.world.lighting.LightingEngine;
import com.mineshaft.world.lighting.SunLightCalculator;

import java.util.*;
import java.util.concurrent.*;

import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ OPTIMIZED ChunkRenderer v2.1
 * 
 * Mengikuti aturan Minecraft Java Edition:
 * - Render distance vs Simulation distance separation
 * - Frustum culling untuk skip chunk tidak terlihat
 * - Priority-based chunk loading (closer = higher priority)
 * - Batasi mesh builds per frame untuk menghindari lag spike
 * - Proper distance-based LOD support
 */
public class ChunkRenderer {

    // ========== MESH STORAGE ==========
    private final Map<Chunk, ChunkMesh> solidMeshes = new ConcurrentHashMap<>();
    private final Map<Chunk, ChunkMesh> waterMeshes = new ConcurrentHashMap<>();
    private final Map<Chunk, ChunkMesh> translucentMeshes = new ConcurrentHashMap<>();

    // ========== REFERENCES ==========
    private World world;
    private LightingEngine lightingEngine;
    private TextureAtlas atlas;
    private Camera lastCamera; // ✅ Store last camera for update() without params

    // ========== ASYNC MESH BUILDING ==========
    private final ExecutorService meshBuilder;
    private final Set<Chunk> buildingChunks = ConcurrentHashMap.newKeySet();
    private final Queue<ChunkBuildTask> buildQueue = new ConcurrentLinkedQueue<>();
    private final Queue<MeshDataResult> pendingVBOCreation = new ConcurrentLinkedQueue<>();

    // ========== STATISTICS ==========
    private int chunksRenderedLastFrame = 0;
    private int chunksCulledLastFrame = 0;
    private long lastStatsResetTime = 0;

    // ========== INNER CLASSES ==========

    /**
     * Task untuk async mesh building dengan priority
     */
    private static class ChunkBuildTask implements Comparable<ChunkBuildTask> {
        final Chunk chunk;
        final double distanceSquared;
        final long timestamp;

        ChunkBuildTask(Chunk chunk, double distSq) {
            this.chunk = chunk;
            this.distanceSquared = distSq;
            this.timestamp = System.nanoTime();
        }

        @Override
        public int compareTo(ChunkBuildTask other) {
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
            return chunk == other.chunk;
        }

        @Override
        public int hashCode() {
            return chunk.hashCode();
        }
    }

    /**
     * Result dari async mesh data building
     */
    private static class MeshDataResult {
        final Chunk chunk;
        final List<Float> solidVertices;
        final List<Float> waterVertices;
        final List<Float> translucentVertices;
        final long buildTime;

        MeshDataResult(Chunk chunk, List<Float> solid, List<Float> water, List<Float> translucent, long buildTime) {
            this.chunk = chunk;
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
            System.out
                    .println("[ChunkRenderer] Initialized with " + Settings.MESH_BUILD_THREADS + " mesh build threads");
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

    /**
     * ✅ Update tanpa parameter - untuk backward compatibility
     */
    public void update() {
        // Use last camera if available, otherwise just process pending uploads
        if (lastCamera != null) {
            update(lastCamera);
        } else {
            // Just upload pending meshes without camera-based processing
            uploadPendingMeshes();
        }
    }

    /**
     * ✅ Main update dengan camera
     */
    public void update(Camera camera) {
        this.lastCamera = camera; // Store for update() without params

        // Process mesh building pipeline
        startMeshDataBuilds(camera);
        uploadPendingMeshes();

        // Periodic stats logging
        if (Settings.DEBUG_MODE && Settings.LOG_PERFORMANCE) {
            logPerformanceStats();
        }
    }

    /**
     * ✅ Queue chunk untuk rebuild jika perlu
     */
    public void renderChunk(Chunk chunk, Camera camera) {
        if (chunk == null || !chunk.isReady()) {
            return;
        }

        this.lastCamera = camera; // Store camera reference

        if (chunk.needsRebuild() && !buildingChunks.contains(chunk)) {
            queueChunkRebuild(chunk, camera);
        }
    }

    /**
     * ✅ Queue chunk rebuild dengan priority berdasarkan jarak
     */
    private void queueChunkRebuild(Chunk chunk, Camera camera) {
        double distSq = getChunkDistanceSquared(chunk, camera);

        ChunkBuildTask newTask = new ChunkBuildTask(chunk, distSq);
        if (!buildQueue.contains(newTask)) {
            buildQueue.offer(newTask);
        }
    }

    // ========== ASYNC MESH BUILDING ==========

    /**
     * ✅ Start mesh builds dengan batasan per frame
     */
    private void startMeshDataBuilds(Camera camera) {
        List<ChunkBuildTask> sortedTasks = new ArrayList<>();
        ChunkBuildTask task;
        while ((task = buildQueue.poll()) != null) {
            sortedTasks.add(task);
        }
        sortedTasks.sort(Comparator.naturalOrder());

        int buildsStarted = 0;

        for (ChunkBuildTask t : sortedTasks) {
            if (buildsStarted >= Settings.MAX_MESH_BUILDS_PER_FRAME) {
                buildQueue.offer(t);
                continue;
            }

            if (!t.chunk.needsRebuild() || buildingChunks.contains(t.chunk)) {
                continue;
            }

            if (!isChunkInRenderDistance(t.chunk, camera)) {
                continue;
            }

            buildingChunks.add(t.chunk);
            meshBuilder.submit(() -> buildMeshDataAsync(t.chunk));
            buildsStarted++;
        }
    }

    /**
     * ✅ Async mesh data building (background thread)
     */
    private void buildMeshDataAsync(Chunk chunk) {
        long startTime = System.nanoTime();

        try {
            List<Float> solidVertices = new ArrayList<>(65536);
            List<Float> waterVertices = new ArrayList<>(8192);
            List<Float> translucentVertices = new ArrayList<>(8192);

            int offsetX = chunk.getChunkX() * Chunk.CHUNK_SIZE;
            int offsetZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE;

            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int index = 0; index < Chunk.CHUNK_HEIGHT; index++) {
                    for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                        int worldY = Settings.indexToWorldY(index);
                        GameBlock block = chunk.getBlockByIndex(x, index, z);

                        if (block != null && !block.isAir()) {
                            float worldX = offsetX + x;
                            float renderY = worldY;
                            float worldZ = offsetZ + z;

                            if (block == BlockRegistry.WATER) {
                                addWaterBlockToList(chunk, x, worldY, z, worldX, renderY, worldZ, waterVertices, block);
                            } else if (block == BlockRegistry.OAK_LEAVES) {
                                addBlockFacesToList(chunk, x, worldY, z, worldX, renderY, worldZ, translucentVertices,
                                        block, false, true, null);
                            } else if (block == BlockRegistry.GRASS_BLOCK) {
                                addBlockFacesToList(chunk, x, worldY, z, worldX, renderY, worldZ, solidVertices, block,
                                        false, false, translucentVertices);
                            } else {
                                addBlockFacesToList(chunk, x, worldY, z, worldX, renderY, worldZ, solidVertices, block,
                                        false, false, null);
                            }
                        }
                    }
                }
            }

            long buildTime = System.nanoTime() - startTime;
            pendingVBOCreation
                    .offer(new MeshDataResult(chunk, solidVertices, waterVertices, translucentVertices, buildTime));

        } catch (Exception e) {
            System.err.println("[ChunkRenderer] Error building mesh for chunk [" + chunk.getChunkX() + ", "
                    + chunk.getChunkZ() + "]: " + e.getMessage());
            if (Settings.DEBUG_MODE) {
                e.printStackTrace();
            }
        } finally {
            buildingChunks.remove(chunk);
        }
    }

    /**
     * ✅ Upload pending meshes ke GPU (main thread only)
     */
    private void uploadPendingMeshes() {
        int uploaded = 0;

        while (uploaded < Settings.MAX_VBO_UPLOADS_PER_FRAME && !pendingVBOCreation.isEmpty()) {
            MeshDataResult result = pendingVBOCreation.poll();

            if (result != null && result.chunk != null) {
                ChunkMesh solidMesh = createMeshFromVertices(result.solidVertices);
                ChunkMesh waterMesh = createMeshFromVertices(result.waterVertices);
                ChunkMesh translucentMesh = createMeshFromVertices(result.translucentVertices);

                swapMeshes(result.chunk, solidMesh, waterMesh, translucentMesh);

                result.chunk.setNeedsRebuild(false);
                uploaded++;

                if (Settings.DEBUG_CHUNK_LOADING) {
                    System.out.println("[ChunkRenderer] Uploaded mesh for chunk [" + result.chunk.getChunkX() + ", "
                            + result.chunk.getChunkZ() + "] in " + (result.buildTime / 1_000_000) + "ms");
                }
            }
        }
    }

    /**
     * ✅ Create ChunkMesh from vertex list
     */
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

    /**
     * ✅ Swap meshes atomically and cleanup old ones
     */
    private void swapMeshes(Chunk chunk, ChunkMesh newSolid, ChunkMesh newWater, ChunkMesh newTranslucent) {
        ChunkMesh oldSolid = solidMeshes.put(chunk, newSolid);
        if (oldSolid != null)
            oldSolid.destroy();

        ChunkMesh oldWater = waterMeshes.put(chunk, newWater);
        if (oldWater != null)
            oldWater.destroy();

        ChunkMesh oldTranslucent = translucentMeshes.put(chunk, newTranslucent);
        if (oldTranslucent != null)
            oldTranslucent.destroy();
    }

    // ========== RENDER PASSES ==========

    /**
     * ✅ Render solid blocks (opaque pass) dengan camera
     */
    public void renderSolidPass(Collection<Chunk> chunks, Camera camera) {
        BlockTextures.bind();

        chunksRenderedLastFrame = 0;
        chunksCulledLastFrame = 0;

        List<Chunk> sortedChunks = new ArrayList<>(chunks);
        sortedChunks.sort((c1, c2) -> {
            float dist1 = getChunkDistanceSquared(c1, camera);
            float dist2 = getChunkDistanceSquared(c2, camera);
            return Float.compare(dist1, dist2);
        });

        for (Chunk chunk : sortedChunks) {
            if (Settings.FRUSTUM_CULLING && !isChunkVisibleInFrustum(chunk, camera)) {
                chunksCulledLastFrame++;
                continue;
            }

            if (!isChunkInRenderDistance(chunk, camera)) {
                chunksCulledLastFrame++;
                continue;
            }

            ChunkMesh solidMesh = solidMeshes.get(chunk);
            if (solidMesh != null && solidMesh.getVertexCount() > 0) {
                solidMesh.render();
                chunksRenderedLastFrame++;
            }
        }
    }

    /**
     * ✅ Render solid blocks (simple version without camera - backward
     * compatibility)
     */
    public void renderSolidPass(Collection<Chunk> chunks) {
        BlockTextures.bind();

        for (Chunk chunk : chunks) {
            ChunkMesh solidMesh = solidMeshes.get(chunk);
            if (solidMesh != null && solidMesh.getVertexCount() > 0) {
                solidMesh.render();
            }
        }
    }

    /**
     * ✅ Render translucent blocks (leaves, grass overlay, etc)
     */
    public void renderTranslucentPass(Collection<Chunk> chunks, Camera camera) {
        List<Chunk> sortedChunks = new ArrayList<>(chunks);
        sortedChunks.sort((c1, c2) -> {
            float dist1 = getChunkDistanceSquared(c1, camera);
            float dist2 = getChunkDistanceSquared(c2, camera);
            return Float.compare(dist2, dist1);
        });

        BlockTextures.bind();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glAlphaFunc(GL_GREATER, 0.1f);
        glEnable(GL_ALPHA_TEST);

        for (Chunk chunk : sortedChunks) {
            if (Settings.FRUSTUM_CULLING && !isChunkVisibleInFrustum(chunk, camera)) {
                continue;
            }

            if (!isChunkInRenderDistance(chunk, camera)) {
                continue;
            }

            ChunkMesh translucentMesh = translucentMeshes.get(chunk);
            if (translucentMesh != null && translucentMesh.getVertexCount() > 0) {
                translucentMesh.render();
            }
        }

        glDisable(GL_ALPHA_TEST);
        glDisable(GL_BLEND);
    }

    /**
     * ✅ Render water pass
     */
    public void renderWaterPass(Collection<Chunk> chunks, Camera camera) {
        List<Chunk> sortedChunks = new ArrayList<>(chunks);
        sortedChunks.sort((c1, c2) -> {
            float dist1 = getChunkDistanceSquared(c1, camera);
            float dist2 = getChunkDistanceSquared(c2, camera);
            return Float.compare(dist2, dist1);
        });

        BlockTextures.bind();

        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);

        for (Chunk chunk : sortedChunks) {
            if (Settings.FRUSTUM_CULLING && !isChunkVisibleInFrustum(chunk, camera)) {
                continue;
            }

            if (!isChunkInRenderDistance(chunk, camera)) {
                continue;
            }

            ChunkMesh waterMesh = waterMeshes.get(chunk);
            if (waterMesh != null && waterMesh.getVertexCount() > 0) {
                waterMesh.render();
            }
        }

        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
    }

    // ========== FRUSTUM CULLING ==========

    /**
     * ✅ Check if chunk is visible in frustum
     */
    private boolean isChunkVisibleInFrustum(Chunk chunk, Camera camera) {
        if (!Settings.FRUSTUM_CULLING) {
            return true;
        }

        float minX = chunk.getChunkX() * Chunk.CHUNK_SIZE;
        float maxX = minX + Chunk.CHUNK_SIZE;
        float minZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE;
        float maxZ = minZ + Chunk.CHUNK_SIZE;

        float dx = (minX + maxX) / 2 - camera.getX();
        float dz = (minZ + maxZ) / 2 - camera.getZ();

        float yaw = (float) Math.toRadians(camera.getYaw());
        float forwardX = (float) -Math.sin(yaw);
        float forwardZ = (float) -Math.cos(yaw);

        float dot = dx * forwardX + dz * forwardZ;

        float chunkRadius = Chunk.CHUNK_SIZE * 1.5f;
        if (dot < -chunkRadius) {
            return false;
        }

        float distSq = dx * dx + dz * dz;
        float dist = (float) Math.sqrt(distSq);

        if (dist > 0) {
            float cosAngle = dot / dist;
            float fovCos = (float) Math.cos(Math.toRadians(Settings.FOV * 0.7));

            if (cosAngle < fovCos) {
                float maxVisibleDistance = Chunk.CHUNK_SIZE * 3;
                if (dist > maxVisibleDistance) {
                    return false;
                }
            }
        }

        return true;
    }

    // ========== DISTANCE CALCULATIONS ==========

    /**
     * ✅ Check if chunk is within render distance
     */
    private boolean isChunkInRenderDistance(Chunk chunk, Camera camera) {
        int playerChunkX = (int) Math.floor(camera.getX() / Chunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(camera.getZ() / Chunk.CHUNK_SIZE);

        return Settings.isInRenderDistance(chunk.getChunkX(), chunk.getChunkZ(), playerChunkX, playerChunkZ);
    }

    /**
     * ✅ Get squared distance from camera to chunk center
     */
    private float getChunkDistanceSquared(Chunk chunk, Camera camera) {
        float chunkCenterX = chunk.getChunkX() * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2.0f;
        float chunkCenterZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2.0f;

        float dx = chunkCenterX - camera.getX();
        float dz = chunkCenterZ - camera.getZ();

        return dx * dx + dz * dz;
    }

    // ========== BLOCK FACE BUILDING ==========

    /**
     * ✅ Add water block faces to vertex list
     */
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

        SunLightCalculator sunLight = (lightingEngine != null) ? lightingEngine.getSunLight() : null;
        float[] uv = BlockTextures.getUV(block, "top");

        if (top != BlockRegistry.WATER) {
            float brightness = getSunBrightness(sunLight, 0, 1, 0);
            addWaterFaceToList(vertices,
                    worldX, topY, worldZ,
                    worldX, topY, worldZ + 1,
                    worldX + 1, topY, worldZ + 1,
                    worldX + 1, topY, worldZ,
                    baseColor, alpha, brightness, 0, 1, 0, uv);
        }

        if (bottom != null && bottom.isAir()) {
            float brightness = getSunBrightness(sunLight, 0, -1, 0);
            addWaterFaceToList(vertices,
                    worldX, worldY, worldZ,
                    worldX + 1, worldY, worldZ,
                    worldX + 1, worldY, worldZ + 1,
                    worldX, worldY, worldZ + 1,
                    baseColor, alpha, brightness, 0, -1, 0, uv);
        }

        if (north != BlockRegistry.WATER && (north == null || north.isAir() || !north.isSolid())) {
            float brightness = getSunBrightness(sunLight, 0, 0, -1);
            addWaterFaceToList(vertices,
                    worldX, worldY, worldZ,
                    worldX, topY, worldZ,
                    worldX + 1, topY, worldZ,
                    worldX + 1, worldY, worldZ,
                    baseColor, alpha, brightness, 0, 0, -1, uv);
        }

        if (south != BlockRegistry.WATER && (south == null || south.isAir() || !south.isSolid())) {
            float brightness = getSunBrightness(sunLight, 0, 0, 1);
            addWaterFaceToList(vertices,
                    worldX, worldY, worldZ + 1,
                    worldX + 1, worldY, worldZ + 1,
                    worldX + 1, topY, worldZ + 1,
                    worldX, topY, worldZ + 1,
                    baseColor, alpha, brightness, 0, 0, 1, uv);
        }

        if (east != BlockRegistry.WATER && (east == null || east.isAir() || !east.isSolid())) {
            float brightness = getSunBrightness(sunLight, 1, 0, 0);
            addWaterFaceToList(vertices,
                    worldX + 1, worldY, worldZ,
                    worldX + 1, topY, worldZ,
                    worldX + 1, topY, worldZ + 1,
                    worldX + 1, worldY, worldZ + 1,
                    baseColor, alpha, brightness, 1, 0, 0, uv);
        }

        if (west != BlockRegistry.WATER && (west == null || west.isAir() || !west.isSolid())) {
            float brightness = getSunBrightness(sunLight, -1, 0, 0);
            addWaterFaceToList(vertices,
                    worldX, worldY, worldZ,
                    worldX, worldY, worldZ + 1,
                    worldX, topY, worldZ + 1,
                    worldX, topY, worldZ,
                    baseColor, alpha, brightness, -1, 0, 0, uv);
        }
    }

    /**
     * ✅ Add water face to vertex list
     */
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

    /**
     * ✅ Add block faces to vertex list
     */
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
     * ✅ Add top face
     */
    private void addTopFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
            float worldX, float worldY, float worldZ,
            float[] color, float alpha, GameBlock block) {
        SunLightCalculator sunLight = (lightingEngine != null) ? lightingEngine.getSunLight() : null;
        float sunBrightness = getSunBrightness(sunLight, 0, 1, 0);

        float light1 = getLightBrightness(chunk, x, y + 1, z) * sunBrightness;
        float light2 = getLightBrightness(chunk, x, y + 1, z + 1) * sunBrightness;
        float light3 = getLightBrightness(chunk, x + 1, y + 1, z + 1) * sunBrightness;
        float light4 = getLightBrightness(chunk, x + 1, y + 1, z) * sunBrightness;

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
     * ✅ Add bottom face
     */
    private void addBottomFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
            float worldX, float worldY, float worldZ,
            float[] color, float alpha, GameBlock block) {
        SunLightCalculator sunLight = (lightingEngine != null) ? lightingEngine.getSunLight() : null;
        float sunBrightness = getSunBrightness(sunLight, 0, -1, 0);

        float light1 = getLightBrightness(chunk, x, y - 1, z) * sunBrightness;
        float light2 = getLightBrightness(chunk, x + 1, y - 1, z) * sunBrightness;
        float light3 = getLightBrightness(chunk, x + 1, y - 1, z + 1) * sunBrightness;
        float light4 = getLightBrightness(chunk, x, y - 1, z + 1) * sunBrightness;

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
     * ✅ Add side face with overlay support
     */
    private void addSideFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
            float worldX, float worldY, float worldZ,
            float[] color, float alpha, GameBlock block,
            float nx, float ny, float nz,
            List<Float> overlayVertices) {

        SunLightCalculator sunLight = (lightingEngine != null) ? lightingEngine.getSunLight() : null;
        float sunBrightness = getSunBrightness(sunLight, nx, ny, nz);

        int neighborX = x + (int) nx;
        int neighborY = y + (int) ny;
        int neighborZ = z + (int) nz;

        float light1 = getLightBrightness(chunk, neighborX, neighborY, neighborZ) * sunBrightness;
        float light2 = getLightBrightness(chunk, neighborX, neighborY + 1, neighborZ) * sunBrightness;
        float light3 = light2;
        float light4 = light1;

        float[] uv = BlockTextures.getUV(block, "side");
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];

        if (nz == -1) {
            addVertexToList(vertices, worldX, worldY, worldZ,
                    color[0] * light1, color[1] * light1, color[2] * light1, alpha, nx, ny, nz, u1, v2);
            addVertexToList(vertices, worldX, worldY + 1, worldZ,
                    color[0] * light2, color[1] * light2, color[2] * light2, alpha, nx, ny, nz, u1, v1);
            addVertexToList(vertices, worldX + 1, worldY + 1, worldZ,
                    color[0] * light3, color[1] * light3, color[2] * light3, alpha, nx, ny, nz, u2, v1);
            addVertexToList(vertices, worldX + 1, worldY, worldZ,
                    color[0] * light4, color[1] * light4, color[2] * light4, alpha, nx, ny, nz, u2, v2);
        } else if (nz == 1) {
            addVertexToList(vertices, worldX, worldY, worldZ + 1,
                    color[0] * light1, color[1] * light1, color[2] * light1, alpha, nx, ny, nz, u2, v2);
            addVertexToList(vertices, worldX + 1, worldY, worldZ + 1,
                    color[0] * light2, color[1] * light2, color[2] * light2, alpha, nx, ny, nz, u1, v2);
            addVertexToList(vertices, worldX + 1, worldY + 1, worldZ + 1,
                    color[0] * light3, color[1] * light3, color[2] * light3, alpha, nx, ny, nz, u1, v1);
            addVertexToList(vertices, worldX, worldY + 1, worldZ + 1,
                    color[0] * light4, color[1] * light4, color[2] * light4, alpha, nx, ny, nz, u2, v1);
        } else if (nx == 1) {
            addVertexToList(vertices, worldX + 1, worldY, worldZ,
                    color[0] * light1, color[1] * light1, color[2] * light1, alpha, nx, ny, nz, u2, v2);
            addVertexToList(vertices, worldX + 1, worldY + 1, worldZ,
                    color[0] * light2, color[1] * light2, color[2] * light2, alpha, nx, ny, nz, u2, v1);
            addVertexToList(vertices, worldX + 1, worldY + 1, worldZ + 1,
                    color[0] * light3, color[1] * light3, color[2] * light3, alpha, nx, ny, nz, u1, v1);
            addVertexToList(vertices, worldX + 1, worldY, worldZ + 1,
                    color[0] * light4, color[1] * light4, color[2] * light4, alpha, nx, ny, nz, u1, v2);
        } else if (nx == -1) {
            addVertexToList(vertices, worldX, worldY, worldZ,
                    color[0] * light1, color[1] * light1, color[2] * light1, alpha, nx, ny, nz, u1, v2);
            addVertexToList(vertices, worldX, worldY, worldZ + 1,
                    color[0] * light2, color[1] * light2, color[2] * light2, alpha, nx, ny, nz, u2, v2);
            addVertexToList(vertices, worldX, worldY + 1, worldZ + 1,
                    color[0] * light3, color[1] * light3, color[2] * light3, alpha, nx, ny, nz, u2, v1);
            addVertexToList(vertices, worldX, worldY + 1, worldZ,
                    color[0] * light4, color[1] * light4, color[2] * light4, alpha, nx, ny, nz, u1, v1);
        }

        if (block.hasOverlay("side_overlay") && overlayVertices != null) {
            addOverlayFace(overlayVertices, worldX, worldY, worldZ, nx, ny, nz,
                    block, light1, light2, light3, light4, alpha);
        }
    }

    /**
     * ✅ Add overlay face (for grass side overlay)
     */
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

    /**
     * ✅ Add single vertex to list
     */
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

    // ========== LIGHTING HELPERS ==========

    /**
     * ✅ Get sun brightness for face normal
     */
    private float getSunBrightness(SunLightCalculator sunLight, float nx, float ny, float nz) {
        float brightness;

        if (sunLight == null) {
            if (ny > 0)
                brightness = 1.0f;
            else if (ny < 0)
                brightness = 0.5f;
            else if (nz != 0)
                brightness = 0.8f;
            else
                brightness = 0.6f;
        } else {
            brightness = sunLight.calculateFaceBrightness(nx, ny, nz);
        }

        brightness = (float) Math.pow(brightness, 1.0f / Settings.GAMMA);
        brightness += Settings.BRIGHTNESS_BOOST;

        return Math.max(Settings.MIN_BRIGHTNESS, Math.min(1.0f, brightness));
    }

    /**
     * ✅ Get light brightness at position
     */
    private float getLightBrightness(Chunk chunk, int x, int y, int z) {
        int light = getLightSafe(chunk, x, y, z);
        float brightness = LightingEngine.getBrightness(light);
        brightness = (float) Math.pow(brightness, 1.0f / Settings.GAMMA);
        return Math.max(Settings.MIN_BRIGHTNESS, brightness);
    }

    /**
     * ✅ Get light level safely (handles cross-chunk lookups)
     */
    private int getLightSafe(Chunk chunk, int x, int worldY, int z) {
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

        return 15;
    }

    // ========== BLOCK ACCESS HELPERS ==========

    /**
     * ✅ Get block safely (handles cross-chunk lookups)
     */
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

    /**
     * ✅ Check if face should be rendered
     */
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

    /**
     * ✅ Remove chunk and cleanup resources
     */
    public void removeChunk(Chunk chunk) {
        buildQueue.removeIf(task -> task.chunk == chunk);
        pendingVBOCreation.removeIf(result -> result.chunk == chunk);
        buildingChunks.remove(chunk);

        ChunkMesh solidMesh = solidMeshes.remove(chunk);
        if (solidMesh != null)
            solidMesh.destroy();

        ChunkMesh waterMesh = waterMeshes.remove(chunk);
        if (waterMesh != null)
            waterMesh.destroy();

        ChunkMesh translucentMesh = translucentMeshes.remove(chunk);
        if (translucentMesh != null)
            translucentMesh.destroy();

        if (Settings.DEBUG_CHUNK_LOADING) {
            System.out.println("[ChunkRenderer] Removed chunk [" + chunk.getChunkX() + ", " + chunk.getChunkZ() + "]");
        }
    }

    /**
     * ✅ Cleanup all resources
     */
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
        pendingVBOCreation.clear();
        buildingChunks.clear();

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

    /**
     * ✅ Get pending builds count
     */
    public int getPendingBuilds() {
        return buildQueue.size() + buildingChunks.size() + pendingVBOCreation.size();
    }

    /**
     * ✅ Get chunks rendered last frame
     */
    public int getChunksRenderedLastFrame() {
        return chunksRenderedLastFrame;
    }

    /**
     * ✅ Get chunks culled last frame
     */
    public int getChunksCulledLastFrame() {
        return chunksCulledLastFrame;
    }

    /**
     * ✅ Get total loaded meshes
     */
    public int getTotalLoadedMeshes() {
        return solidMeshes.size();
    }

    /**
     * ✅ Log performance stats
     */
    private void logPerformanceStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatsResetTime >= 5000) {
            System.out.println("[ChunkRenderer] Stats - " +
                    "Rendered: " + chunksRenderedLastFrame + ", " +
                    "Culled: " + chunksCulledLastFrame + ", " +
                    "Pending: " + getPendingBuilds() + ", " +
                    "Meshes: " + getTotalLoadedMeshes());
            lastStatsResetTime = now;
        }
    }

    /**
     * ✅ Force rebuild all chunks
     */
    public void forceRebuildAll() {
        for (Chunk chunk : solidMeshes.keySet()) {
            chunk.setNeedsRebuild(true);
        }
        System.out.println("[ChunkRenderer] Forced rebuild for " + solidMeshes.size() + " chunks");
    }

    /**
     * ✅ Get memory usage estimate
     */
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