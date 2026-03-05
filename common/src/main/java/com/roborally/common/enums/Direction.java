package com.roborally.common.enums;

public enum Direction {
    NORTH, EAST, SOUTH, WEST;

    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

    public Direction rotateClockwise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
        };
    }

    public Direction rotateCounterClockwise() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
        };
    }

    public Direction rotate180() {
        return opposite();
    }

    public int dx() {
        return switch (this) {
            case EAST -> 1;
            case WEST -> -1;
            default -> 0;
        };
    }

    public int dy() {
        return switch (this) {
            case SOUTH -> 1;
            case NORTH -> -1;
            default -> 0;
        };
    }
}
