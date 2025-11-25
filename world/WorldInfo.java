// src/main/java/com/mineshaft/world/WorldInfo.java
package com.mineshaft.world;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * World metadata
 */
public class WorldInfo {
    private String name;
    private String folderName;
    private long seed;
    private String gameMode;
    private long lastPlayedTime;
    private long createdTime;

    public WorldInfo(String name, String folderName, long seed, String gameMode, long createdTime) {
        this.name = name;
        this.folderName = folderName;
        this.seed = seed;
        this.gameMode = gameMode;
        this.createdTime = createdTime;
        this.lastPlayedTime = createdTime;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getFolderName() {
        return folderName;
    }

    public long getSeed() {
        return seed;
    }

    public String getGameMode() {
        return gameMode;
    }

    public long getLastPlayedTime() {
        return lastPlayedTime;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setLastPlayedTime(long time) {
        this.lastPlayedTime = time;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public String getLastPlayed() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(new Date(lastPlayedTime));
    }

    public String getCreated() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(new Date(createdTime));
    }
}