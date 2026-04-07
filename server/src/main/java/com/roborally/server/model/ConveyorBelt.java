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
    private RotationDirection curveRotation; // CLOCKWISE or COUNTERCLOCKWISE
    private boolean crossing;
    private Direction curveFrom; // Optional input flow direction

    public ConveyorBelt(Direction direction, boolean express) {
        this.direction = direction;
        this.express = express;
    }

    public ConveyorBelt(Direction direction, boolean express, RotationDirection curveRotation, boolean crossing) {
        this.direction = direction;
        this.express = express;
        this.curveRotation = curveRotation;
        this.crossing = crossing;
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isExpress() {
        return express;
    }

    public RotationDirection getCurveRotation() {
        return curveRotation;
    }

    public void setCurveRotation(RotationDirection curveRotation) {
        this.curveRotation = curveRotation;
    }

    public boolean isCrossing() {
        return crossing;
    }

    public void setCrossing(boolean crossing) {
        this.crossing = crossing;
    }

    public Direction getCurveFrom() {
        return curveFrom;
    }

    public void setCurveFrom(Direction curveFrom) {
        this.curveFrom = curveFrom;
    }

    public boolean isCurve() {
        return curveRotation != null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("direction", direction.name());
        m.put("express", express);
        if (curveRotation != null) {
            // "LEFT" corresponds to COUNTERCLOCKWISE turn, "RIGHT" to CLOCKWISE
            m.put("curveRotation", curveRotation == RotationDirection.CLOCKWISE ? "RIGHT" : "LEFT");
        }
        if (crossing) {
            m.put("crossing", true);
        }
        return m;
    }
}
