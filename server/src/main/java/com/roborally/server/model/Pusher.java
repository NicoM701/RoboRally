package com.roborally.server.model;

import com.roborally.common.enums.Direction;
import java.util.Map;
import java.util.Set;

/** Pusher — pushes robots on specific steps. */
public class Pusher {
    private final Direction pushDirection;
    private final Set<Integer> activeSteps; // 1-5

    public Pusher(Direction pushDirection, Set<Integer> activeSteps) {
        this.pushDirection = pushDirection;
        this.activeSteps = activeSteps;
    }

    public Direction getPushDirection() {
        return pushDirection;
    }

    public Set<Integer> getActiveSteps() {
        return activeSteps;
    }

    public boolean isActiveOnStep(int step) {
        return activeSteps.contains(step);
    }

    public Map<String, Object> toMap() {
        return Map.of("direction", pushDirection.name(), "steps", activeSteps);
    }
}
