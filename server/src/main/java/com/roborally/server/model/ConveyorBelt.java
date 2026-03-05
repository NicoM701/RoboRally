package com.roborally.server.model;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.RotationDirection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conveyor belt tile element.
 */
public class ConveyorBelt {
    private final Direction direction;
    private final boolean express; // true = express (2 steps), false = normal (1 step)
    private final Direction curveFrom; // null = straight, non-null = belt curves from this direction

    public ConveyorBelt(Direction direction, boolean express) {
        this(direction, express, null);
    }

    public ConveyorBelt(Direction direction, boolean express, Direction curveFrom) {
        this.direction = direction;
        this.express = express;
        this.curveFrom = curveFrom;
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isExpress() {
        return express;
    }

    public Direction getCurveFrom() {
        return curveFrom;
    }

    public boolean isCurve() {
        return curveFrom != null;
    }

    /**
     * Get the rotation applied when transported onto this curved belt.
     */
    public RotationDirection getCurveRotation() {
        if (curveFrom == null)
            return null;
        // If coming from curveFrom and belt points in direction,
        // determine if that's a CW or CCW turn
        if (curveFrom.rotateClockwise() == direction)
            return RotationDirection.CLOCKWISE;
        if (curveFrom.rotateCounterClockwise() == direction)
            return RotationDirection.COUNTERCLOCKWISE;
        return null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("direction", direction.name());
        m.put("express", express);
        if (curveFrom != null)
            m.put("curveFrom", curveFrom.name());
        return m;
    }
}
