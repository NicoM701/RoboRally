package com.roborally.common.enums;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Direction enum and its helper methods.
 */
class DirectionTest {

    @Test
    @DisplayName("opposite: all directions correct")
    void opposite_allDirections() {
        assertEquals(Direction.SOUTH, Direction.NORTH.opposite());
        assertEquals(Direction.NORTH, Direction.SOUTH.opposite());
        assertEquals(Direction.WEST, Direction.EAST.opposite());
        assertEquals(Direction.EAST, Direction.WEST.opposite());
    }

    @Test
    @DisplayName("rotateClockwise: N→E→S→W→N")
    void rotateClockwise_fullCircle() {
        assertEquals(Direction.EAST, Direction.NORTH.rotateClockwise());
        assertEquals(Direction.SOUTH, Direction.EAST.rotateClockwise());
        assertEquals(Direction.WEST, Direction.SOUTH.rotateClockwise());
        assertEquals(Direction.NORTH, Direction.WEST.rotateClockwise());
    }

    @Test
    @DisplayName("rotateCounterClockwise: N→W→S→E→N")
    void rotateCounterClockwise_fullCircle() {
        assertEquals(Direction.WEST, Direction.NORTH.rotateCounterClockwise());
        assertEquals(Direction.SOUTH, Direction.WEST.rotateCounterClockwise());
        assertEquals(Direction.EAST, Direction.SOUTH.rotateCounterClockwise());
        assertEquals(Direction.NORTH, Direction.EAST.rotateCounterClockwise());
    }

    @Test
    @DisplayName("rotate180 == opposite")
    void rotate180_equalsOpposite() {
        for (Direction d : Direction.values()) {
            assertEquals(d.opposite(), d.rotate180());
        }
    }

    @Test
    @DisplayName("dx: EAST=+1, WEST=-1, others=0")
    void dx_correctValues() {
        assertEquals(1, Direction.EAST.dx());
        assertEquals(-1, Direction.WEST.dx());
        assertEquals(0, Direction.NORTH.dx());
        assertEquals(0, Direction.SOUTH.dx());
    }

    @Test
    @DisplayName("dy: SOUTH=+1, NORTH=-1, others=0")
    void dy_correctValues() {
        assertEquals(1, Direction.SOUTH.dy());
        assertEquals(-1, Direction.NORTH.dy());
        assertEquals(0, Direction.EAST.dy());
        assertEquals(0, Direction.WEST.dy());
    }

    @Test
    @DisplayName("4 clockwise rotations return to original")
    void fourRotations_returnToOriginal() {
        for (Direction d : Direction.values()) {
            Direction result = d.rotateClockwise()
                    .rotateClockwise()
                    .rotateClockwise()
                    .rotateClockwise();
            assertEquals(d, result, d + " should return to itself after 4 CW rotations");
        }
    }

    @Test
    @DisplayName("CW then CCW is identity")
    void cwThenCcw_identity() {
        for (Direction d : Direction.values()) {
            assertEquals(d, d.rotateClockwise().rotateCounterClockwise());
        }
    }
}
