package com.roborally.server.model;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.FieldType;

import java.util.*;

/**
 * A single tile on the game board.
 */
public class Tile {

    private final int x;
    private final int y;
    private FieldType fieldType;
    private final Set<Direction> walls = EnumSet.noneOf(Direction.class);

    // Optional elements (at most one per tile in most cases)
    private ConveyorBelt conveyorBelt;
    private Gear gear;
    private Pusher pusher;
    private Press press;
    private Checkpoint checkpoint;

    public Tile(int x, int y, FieldType fieldType) {
        this.x = x;
        this.y = y;
        this.fieldType = fieldType;
    }

    // ─── Position ───────────────────────────────────────

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public FieldType getFieldType() {
        return fieldType;
    }

    public void setFieldType(FieldType fieldType) {
        this.fieldType = fieldType;
    }

    // ─── Walls ──────────────────────────────────────────

    public Set<Direction> getWalls() {
        return Collections.unmodifiableSet(walls);
    }

    public boolean hasWall(Direction dir) {
        return walls.contains(dir);
    }

    public void addWall(Direction dir) {
        walls.add(dir);
    }

    // ─── Elements ───────────────────────────────────────

    public ConveyorBelt getConveyorBelt() {
        return conveyorBelt;
    }

    public void setConveyorBelt(ConveyorBelt cb) {
        this.conveyorBelt = cb;
    }

    public Gear getGear() {
        return gear;
    }

    public void setGear(Gear gear) {
        this.gear = gear;
    }

    public Pusher getPusher() {
        return pusher;
    }

    public void setPusher(Pusher pusher) {
        this.pusher = pusher;
    }

    public Press getPress() {
        return press;
    }

    public void setPress(Press press) {
        this.press = press;
    }

    public Checkpoint getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Checkpoint checkpoint) {
        this.checkpoint = checkpoint;
    }

    // ─── Queries ────────────────────────────────────────

    public boolean isPit() {
        return fieldType == FieldType.PIT;
    }

    public boolean isRepair() {
        return fieldType == FieldType.REPAIR_1 || fieldType == FieldType.REPAIR_2;
    }

    public boolean isStart() {
        return fieldType == FieldType.START;
    }

    /**
     * Serialize tile to map for client.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", x);
        map.put("y", y);
        map.put("type", fieldType.name());
        if (!walls.isEmpty()) {
            map.put("walls", walls.stream().map(Enum::name).toList());
        }
        if (conveyorBelt != null)
            map.put("conveyorBelt", conveyorBelt.toMap());
        if (gear != null)
            map.put("gear", gear.toMap());
        if (pusher != null)
            map.put("pusher", pusher.toMap());
        if (press != null)
            map.put("press", press.toMap());
        if (checkpoint != null)
            map.put("checkpoint", checkpoint.toMap());
        return map;
    }
}
