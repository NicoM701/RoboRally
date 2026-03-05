package com.roborally.server.model;

import com.roborally.common.enums.FieldType;
import java.util.*;

/**
 * Game board: 12×12 grid of tiles with lasers and checkpoints.
 */
public class Board {

    private final String name;
    private final int width;
    private final int height;
    private final Tile[][] tiles;
    private final List<Laser> lasers = new ArrayList<>();
    private final List<int[]> startPositions = new ArrayList<>();
    private int totalCheckpoints = 0;

    public Board(String name, int width, int height) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.tiles = new Tile[height][width];
        // Initialize with floor tiles
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = new Tile(x, y, FieldType.FLOOR);
            }
        }
    }

    public String getName() {
        return name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Tile getTile(int x, int y) {
        if (!isInBounds(x, y))
            return null;
        return tiles[y][x];
    }

    public void setTile(int x, int y, Tile tile) {
        if (isInBounds(x, y))
            tiles[y][x] = tile;
    }

    public boolean isInBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public List<Laser> getLasers() {
        return lasers;
    }

    public void addLaser(Laser laser) {
        lasers.add(laser);
    }

    public List<int[]> getStartPositions() {
        return startPositions;
    }

    public void addStartPosition(int x, int y) {
        startPositions.add(new int[] { x, y });
    }

    public int getTotalCheckpoints() {
        return totalCheckpoints;
    }

    public void setTotalCheckpoints(int n) {
        this.totalCheckpoints = n;
    }

    /**
     * Serialize board state for client.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("width", width);
        map.put("height", height);
        map.put("totalCheckpoints", totalCheckpoints);

        List<Map<String, Object>> tileList = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Tile t = tiles[y][x];
                // Only send non-default tiles to reduce payload
                if (t.getFieldType() != FieldType.FLOOR || !t.getWalls().isEmpty()
                        || t.getConveyorBelt() != null || t.getGear() != null
                        || t.getPusher() != null || t.getPress() != null
                        || t.getCheckpoint() != null) {
                    tileList.add(t.toMap());
                }
            }
        }
        map.put("tiles", tileList);
        map.put("lasers", lasers.stream().map(Laser::toMap).toList());
        return map;
    }
}
