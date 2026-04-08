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

        assertEquals(2, board.getTotalCheckpoints());
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
        assertEquals(Direction.EAST, belt.getConveyorBelt().getDirection());
        assertFalse(belt.getConveyorBelt().isExpress());
    }

    @Test
    @DisplayName("Map5: has express belt")
    void map5_hasExpressBelt() {
        Board board = boardLoader.createDefaultBoard("map5");
        Tile belt = board.getTile(11, 0);

        assertNotNull(belt.getConveyorBelt());
        assertTrue(belt.getConveyorBelt().isExpress());
    }

    @Test
    @DisplayName("Map2: has gears")
    void map2_hasGears() {
        Board board = boardLoader.createDefaultBoard("map2");

        assertNotNull(board.getTile(8, 3).getGear());
        assertNotNull(board.getTile(8, 8).getGear());
    }

    @Test
    @DisplayName("Default board: has pits")
    void defaultBoard_hasPits() {
        Board board = boardLoader.createDefaultBoard("Test");

        assertTrue(board.getTile(9, 3).isPit());
    }

    @Test
    @DisplayName("Default board: has walls")
    void defaultBoard_hasWalls() {
        Board board = boardLoader.createDefaultBoard("Test");

        assertTrue(board.getTile(10, 3).hasWall(Direction.SOUTH));
        assertTrue(board.getTile(10, 4).hasWall(Direction.NORTH));
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
    @DisplayName("Map1: preserves legacy checkpoint layout for single-board mode")
    void map1_preservesLegacyCheckpoints() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertEquals(2, board.getTotalCheckpoints());
        assertEquals(1, board.getTile(4, 1).getCheckpoint().getNumber());
        assertEquals(2, board.getTile(7, 8).getCheckpoint().getNumber());
    }

    @Test
    @DisplayName("Map1: synthetic start positions stay invisible")
    void map1_syntheticStartsDoNotPaintTiles() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertEquals(2, board.getStartPositions().size());
        assertFalse(board.getTile(1, 11).isStart());
        assertFalse(board.getTile(2, 11).isStart());
    }

    @Test
    @DisplayName("Map1: curve and crossing metadata matches legacy board")
    void map1_conveyorMetadataMatchesLegacyBoard() {
        Board board = boardLoader.createDefaultBoard("map1");

        ConveyorBelt curve = board.getTile(11, 10).getConveyorBelt();
        assertNotNull(curve);
        assertEquals(Direction.WEST, curve.getDirection());
        assertEquals(RotationDirection.CLOCKWISE, curve.getCurveRotation());
        assertEquals(Direction.NORTH, curve.getCurveFrom());

        ConveyorBelt crossing = board.getTile(6, 1).getConveyorBelt();
        assertNotNull(crossing);
        assertTrue(crossing.isCrossing());
        assertEquals("RIGHT", crossing.getCrossingType());
        assertEquals(Direction.SOUTH, crossing.getDirection());
    }

    @Test
    @DisplayName("Map4: express crossings retain their legacy subtype")
    void map4_expressCrossingKeepsSubtype() {
        Board board = boardLoader.createDefaultBoard("map4");

        ConveyorBelt crossing = board.getTile(10, 10).getConveyorBelt();
        assertNotNull(crossing);
        assertTrue(crossing.isExpress());
        assertTrue(crossing.isCrossing());
        assertEquals("LEFTRIGHT", crossing.getCrossingType());
        assertEquals(Direction.NORTH, crossing.getDirection());
    }

    @Test
    @DisplayName("loadBoard: nonexistent falls back to default")
    void loadBoard_fallsBack() {
        Board board = boardLoader.loadBoard("Nonexistent");

        assertNotNull(board);
        assertEquals(12, board.getWidth());
    }
}
