package com.roborally.server.service;

import com.roborally.server.model.*;
import com.roborally.common.enums.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BoardLoader.
 */
class BoardLoaderTest {

    private BoardLoader boardLoader;

    @BeforeEach
    void setUp() {
        boardLoader = new BoardLoader();
    }

    @Test
    @DisplayName("Default board: 12×12")
    void defaultBoard_12x12() {
        Board board = boardLoader.createDefaultBoard("Test");

        assertEquals(12, board.getWidth());
        assertEquals(12, board.getHeight());
    }

    @Test
    @DisplayName("Default board: has start positions")
    void defaultBoard_hasStarts() {
        Board board = boardLoader.createDefaultBoard("Test");

        assertFalse(board.getStartPositions().isEmpty());
    }

    @Test
    @DisplayName("Default board: has checkpoints")
    void defaultBoard_hasCheckpoints() {
        Board board = boardLoader.createDefaultBoard("Test");

        assertEquals(3, board.getTotalCheckpoints());
    }

    @Test
    @DisplayName("Default board: has lasers")
    void defaultBoard_hasLasers() {
        Board board = boardLoader.createDefaultBoard("Test");

        assertFalse(board.getLasers().isEmpty());
    }

    @Test
    @DisplayName("Default board: has conveyor belts")
    void defaultBoard_hasConveyors() {
        Board board = boardLoader.createDefaultBoard("Test");
        Tile belt = board.getTile(0, 5);

        assertNotNull(belt.getConveyorBelt());
        assertEquals(Direction.NORTH, belt.getConveyorBelt().getDirection());
        assertFalse(belt.getConveyorBelt().isExpress());
    }

    @Test
    @DisplayName("Default board: has express belt")
    void defaultBoard_hasExpressBelt() {
        Board board = boardLoader.createDefaultBoard("Test");
        Tile belt = board.getTile(11, 5);

        assertNotNull(belt.getConveyorBelt());
        assertTrue(belt.getConveyorBelt().isExpress());
    }

    @Test
    @DisplayName("Default board: has gears")
    void defaultBoard_hasGears() {
        Board board = boardLoader.createDefaultBoard("Test");

        assertNotNull(board.getTile(4, 5).getGear());
        assertNotNull(board.getTile(7, 5).getGear());
    }

    @Test
    @DisplayName("Default board: has pits")
    void defaultBoard_hasPits() {
        Board board = boardLoader.createDefaultBoard("Test");

        assertTrue(board.getTile(5, 5).isPit());
    }

    @Test
    @DisplayName("Default board: has walls")
    void defaultBoard_hasWalls() {
        Board board = boardLoader.createDefaultBoard("Test");

        assertTrue(board.getTile(3, 3).hasWall(Direction.SOUTH));
        assertTrue(board.getTile(3, 4).hasWall(Direction.NORTH));
    }

    @Test
    @DisplayName("Default board: serializesToMap")
    void defaultBoard_toMap() {
        Board board = boardLoader.createDefaultBoard("Test");

        var map = board.toMap();

        assertEquals("Test", map.get("name"));
        assertEquals(12, map.get("width"));
        assertEquals(12, map.get("height"));
        assertNotNull(map.get("tiles"));
        assertNotNull(map.get("lasers"));
    }

    @Test
    @DisplayName("loadBoard: nonexistent falls back to default")
    void loadBoard_fallsBack() {
        Board board = boardLoader.loadBoard("Nonexistent");

        assertNotNull(board);
        assertEquals(12, board.getWidth());
    }
}
