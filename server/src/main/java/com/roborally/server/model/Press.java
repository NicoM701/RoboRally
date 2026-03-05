package com.roborally.server.model;

import java.util.Map;
import java.util.Set;

/** Press — destroys robots on specific steps. */
public class Press {
    private final Set<Integer> activeSteps; // 1-5

    public Press(Set<Integer> activeSteps) {
        this.activeSteps = activeSteps;
    }

    public Set<Integer> getActiveSteps() {
        return activeSteps;
    }

    public boolean isActiveOnStep(int step) {
        return activeSteps.contains(step);
    }

    public Map<String, Object> toMap() {
        return Map.of("steps", activeSteps);
    }
}
