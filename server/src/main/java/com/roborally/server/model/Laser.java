package com.roborally.server.model;

import com.roborally.common.enums.Direction;
import java.util.Map;

/** Board laser emitter. */
public class Laser {
    private final int x;
    private final int y;
    private final Direction direction; // Direction laser fires toward
    private final int strength; // 1, 2, or 3

    public Laser(int x, int y, Direction direction, int strength) {
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.strength = strength;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getStrength() {
        return strength;
    }

    public Map<String, Object> toMap() {
        return Map.of("x", x, "y", y, "direction", direction.name(), "strength", strength);
    }
}
