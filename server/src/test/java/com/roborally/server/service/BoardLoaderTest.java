package com.roborally.server.service;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.RotationDirection;
import com.roborally.server.model.Board;
import com.roborally.server.model.ConveyorBelt;
import com.roborally.server.model.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
    @DisplayName("Map1: legacy board only flips vertically, not rotate")
    void map1_legacyCoordinatesAreVerticallyFlippedOnly() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertTrue(board.getTile(0, 0).isPit());
        assertNotNull(board.getTile(0, 6).getConveyorBelt());
        assertEquals(Direction.EAST, board.getTile(0, 6).getConveyorBelt().getDirection());
        assertNotNull(board.getTile(5, 11).getConveyorBelt());
        assertEquals(Direction.SOUTH, board.getTile(5, 11).getConveyorBelt().getDirection());
    }

    @Test
    @DisplayName("Map1: legacy checkpoint replacement clears the underlying belt tile")
    void map1_checkpointReplacementClearsUnderlyingTile() {
        Board board = boardLoader.createDefaultBoard("map1");
        Tile checkpointTile = board.getTile(8, 7);

        assertNotNull(checkpointTile.getCheckpoint());
        assertEquals(2, checkpointTile.getCheckpoint().getNumber());
        assertNull(checkpointTile.getConveyorBelt());
        assertTrue(checkpointTile.hasWall(Direction.WEST));
        assertTrue(checkpointTile.hasWall(Direction.NORTH));
    }

    @Test
    @DisplayName("Map1: walls are mirrored on adjacent tiles")
    void map1_mirrorsLegacyWalls() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertTrue(board.getTile(10, 9).hasWall(Direction.SOUTH));
        assertTrue(board.getTile(10, 10).hasWall(Direction.NORTH));
    }

    @Test
    @DisplayName("Map1: curve and crossing metadata matches legacy semantics")
    void map1_conveyorMetadataMatchesLegacyBoard() {
        Board board = boardLoader.createDefaultBoard("map1");

        ConveyorBelt curve = board.getTile(10, 11).getConveyorBelt();
        assertNotNull(curve);
        assertEquals(Direction.NORTH, curve.getDirection());
        assertEquals(RotationDirection.CLOCKWISE, curve.getCurveRotation());

        ConveyorBelt crossing = board.getTile(1, 6).getConveyorBelt();
        assertNotNull(crossing);
        assertTrue(crossing.isCrossing());
        assertEquals("RIGHT", crossing.getCrossingType());
        assertEquals(Direction.EAST, crossing.getDirection());
    }

    @Test
    @DisplayName("Map2: gear rotation directions match the old board")
    void map2_hasLegacyGearDirections() {
        Board board = boardLoader.createDefaultBoard("map2");

        assertNotNull(board.getTile(3, 8).getGear());
        assertEquals(RotationDirection.COUNTERCLOCKWISE, board.getTile(3, 8).getGear().getRotation());
        assertNotNull(board.getTile(10, 1).getGear());
        assertEquals(RotationDirection.CLOCKWISE, board.getTile(10, 1).getGear().getRotation());
    }

    @Test
    @DisplayName("Map4: checkpoint replacement clears the old conveyor curve")
    void map4_checkpointReplacementClearsUnderlyingConveyor() {
        Board board = boardLoader.createDefaultBoard("map4");
        Tile checkpointTile = board.getTile(1, 10);

        assertNotNull(checkpointTile.getCheckpoint());
        assertEquals(1, checkpointTile.getCheckpoint().getNumber());
        assertNull(checkpointTile.getConveyorBelt());
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
        assertEquals(Direction.WEST, crossing.getDirection());
    }

    @Test
    @DisplayName("Map5: has express belt")
    void map5_hasExpressBelt() {
        Board board = boardLoader.createDefaultBoard("map5");
        Tile belt = board.getTile(11, 11);

        assertNotNull(belt.getConveyorBelt());
        assertTrue(belt.getConveyorBelt().isExpress());
        assertEquals(Direction.WEST, belt.getConveyorBelt().getDirection());
    }

    @Test
    @DisplayName("Default board: serializes to map")
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
