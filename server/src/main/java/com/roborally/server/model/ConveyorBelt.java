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
    private String crossingType; // LEFT, RIGHT, or LEFTRIGHT
    private Direction curveFrom; // Optional input flow direction

    public ConveyorBelt(Direction direction, boolean express) {
        this.direction = direction;
        this.express = express;
    }

    public ConveyorBelt(Direction direction, boolean express, Direction curveFrom) {
        this(direction, express);
        this.curveFrom = curveFrom;
    }

    public ConveyorBelt(Direction direction, boolean express, RotationDirection curveRotation, boolean crossing) {
        this(direction, express);
        this.curveRotation = curveRotation;
        setCrossing(crossing);
    }

    public ConveyorBelt(Direction direction, boolean express, RotationDirection curveRotation, String crossingType) {
        this(direction, express);
        this.curveRotation = curveRotation;
        setCrossingType(crossingType);
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isExpress() {
        return express;
    }

    public RotationDirection getCurveRotation() {
        if (curveRotation != null) {
            return curveRotation;
        }
        if (curveFrom == null) {
            return null;
        }
        if (curveFrom.rotateClockwise() == direction) {
            return RotationDirection.CLOCKWISE;
        }
        if (curveFrom.rotateCounterClockwise() == direction) {
            return RotationDirection.COUNTERCLOCKWISE;
        }
        return null;
    }

    public void setCurveRotation(RotationDirection curveRotation) {
        this.curveRotation = curveRotation;
    }

    public boolean isCrossing() {
        return crossing;
    }

    public void setCrossing(boolean crossing) {
        this.crossing = crossing;
        if (!crossing) {
            this.crossingType = null;
        } else if (this.crossingType == null) {
            this.crossingType = "LEFTRIGHT";
        }
    }

    public String getCrossingType() {
        return crossingType;
    }

    public void setCrossingType(String crossingType) {
        this.crossingType = crossingType;
        this.crossing = crossingType != null && !crossingType.isBlank();
    }

    public Direction getCurveFrom() {
        return curveFrom;
    }

    public void setCurveFrom(Direction curveFrom) {
        this.curveFrom = curveFrom;
    }

    public boolean isCurve() {
        return getCurveRotation() != null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("direction", direction.name());
        m.put("express", express);
        RotationDirection resolvedCurveRotation = getCurveRotation();
        if (resolvedCurveRotation != null) {
            // "LEFT" corresponds to COUNTERCLOCKWISE turn, "RIGHT" to CLOCKWISE
            m.put("curveRotation", resolvedCurveRotation == RotationDirection.CLOCKWISE ? "RIGHT" : "LEFT");
        }
        if (curveFrom != null) {
            m.put("curveFrom", curveFrom.name());
        }
        if (crossing) {
            m.put("crossing", true);
            m.put("crossingType", crossingType == null ? "LEFTRIGHT" : crossingType);
        }
        return m;
    }
}
