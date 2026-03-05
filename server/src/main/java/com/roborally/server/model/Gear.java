package com.roborally.server.model;

import com.roborally.common.enums.RotationDirection;
import java.util.Map;

/** Gear tile element — rotates robots 90° each step. */
public class Gear {
    private final RotationDirection rotation;

    public Gear(RotationDirection rotation) {
        this.rotation = rotation;
    }

    public RotationDirection getRotation() {
        return rotation;
    }

    public Map<String, Object> toMap() {
        return Map.of("rotation", rotation.name());
    }
}
