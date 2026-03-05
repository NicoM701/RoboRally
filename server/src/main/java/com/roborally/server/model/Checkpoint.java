package com.roborally.server.model;

import java.util.Map;

/** Board checkpoint / flag. */
public class Checkpoint {
    private final int number; // 1-based checkpoint order

    public Checkpoint(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public Map<String, Object> toMap() {
        return Map.of("number", number);
    }
}
