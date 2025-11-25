// src/main/java/com/mineshaft/world/WorldSaveManager.java
package com.mineshaft.world;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages world saves
 */
public class WorldSaveManager {

    private static final String SAVES_FOLDER = "saves";
    private static final String WORLD_INFO_FILE = "world.dat";

    static {
        // Ensure saves folder exists
        try {
            Files.createDirectories(Paths.get(SAVES_FOLDER));
        } catch (IOException e) {
            System.err.println("Failed to create saves folder: " + e.getMessage());
        }
    }

    /**
     * Get list of all saved worlds
     */
    public static List<WorldInfo> getWorldList() {
        List<WorldInfo> worlds = new ArrayList<>();

        File savesDir = new File(SAVES_FOLDER);
        File[] worldFolders = savesDir.listFiles(File::isDirectory);

        if (worldFolders != null) {
            for (File folder : worldFolders) {
                WorldInfo info = loadWorldInfo(folder.getName());
                if (info != null) {
                    worlds.add(info);
                }
            }
        }

        // Sort by last played (newest first)
        worlds.sort((a, b) -> Long.compare(b.getLastPlayedTime(), a.getLastPlayedTime()));

        return worlds;
    }

    /**
     * Load world info from folder
     */
    public static WorldInfo loadWorldInfo(String folderName) {
        File infoFile = new File(SAVES_FOLDER + "/" + folderName + "/" + WORLD_INFO_FILE);

        if (!infoFile.exists()) {
            // Create default info for legacy worlds
            return new WorldInfo(
                    folderName,
                    folderName,
                    0,
                    "Survival",
                    infoFile.getParentFile().lastModified());
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(infoFile))) {
            String name = dis.readUTF();
            long seed = dis.readLong();
            String gameMode = dis.readUTF();
            long created = dis.readLong();
            long lastPlayed = dis.readLong();

            WorldInfo info = new WorldInfo(name, folderName, seed, gameMode, created);
            info.setLastPlayedTime(lastPlayed);
            return info;

        } catch (IOException e) {
            System.err.println("Failed to load world info for " + folderName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Save world info
     */
    public static void saveWorldInfo(WorldInfo info) {
        File worldDir = new File(SAVES_FOLDER + "/" + info.getFolderName());
        worldDir.mkdirs();

        File infoFile = new File(worldDir, WORLD_INFO_FILE);

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(infoFile))) {
            dos.writeUTF(info.getName());
            dos.writeLong(info.getSeed());
            dos.writeUTF(info.getGameMode());
            dos.writeLong(info.getCreatedTime());
            dos.writeLong(info.getLastPlayedTime());

        } catch (IOException e) {
            System.err.println("Failed to save world info: " + e.getMessage());
        }
    }

    /**
     * Create new world
     */
    public static void createWorld(WorldInfo info) {
        File worldDir = new File(SAVES_FOLDER + "/" + info.getFolderName());
        worldDir.mkdirs();

        saveWorldInfo(info);

        System.out.println("[WorldSaveManager] Created world: " + info.getName() +
                " in " + info.getFolderName());
    }

    /**
     * Delete world
     */
    public static boolean deleteWorld(String folderName) {
        File worldDir = new File(SAVES_FOLDER + "/" + folderName);

        if (!worldDir.exists()) {
            return false;
        }

        try {
            deleteRecursive(worldDir);
            System.out.println("[WorldSaveManager] Deleted world: " + folderName);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to delete world: " + e.getMessage());
            return false;
        }
    }

    private static void deleteRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();
            if (contents != null) {
                for (File f : contents) {
                    deleteRecursive(f);
                }
            }
        }
        Files.delete(file.toPath());
    }

    /**
     * Generate unique folder name from world name
     */
    public static String generateFolderName(String worldName) {
        // Remove invalid characters
        String base = worldName.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
        if (base.isEmpty()) {
            base = "world";
        }

        // Ensure uniqueness
        String folderName = base;
        int counter = 1;

        while (new File(SAVES_FOLDER + "/" + folderName).exists()) {
            folderName = base + "_" + counter++;
        }

        return folderName;
    }

    /**
     * Check if world exists
     */
    public static boolean worldExists(String folderName) {
        return new File(SAVES_FOLDER + "/" + folderName).exists();
    }

    /**
     * Get saves folder path
     */
    public static String getSavesFolder() {
        return SAVES_FOLDER;
    }
}