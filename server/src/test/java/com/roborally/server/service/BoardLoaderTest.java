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
    @DisplayName("Default board: start positions are marked as START tiles")
    void defaultBoard_startPositionsAreMarkedAsStartTiles() {
        Board board = boardLoader.createDefaultBoard("Test");

        for (int[] startPosition : board.getStartPositions()) {
            Tile tile = board.getTile(startPosition[0], startPosition[1]);
            assertNotNull(tile);
            assertTrue(tile.isStart(), "Expected start tile at (" + startPosition[0] + "," + startPosition[1] + ")");
        }
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
    @DisplayName("Map1: legacy board is transposed after the vertical flip")
    void map1_legacyCoordinatesAreTransposedAfterVerticalFlip() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertTrue(board.getTile(9, 3).isPit());
        assertNotNull(board.getTile(0, 5).getConveyorBelt());
        assertEquals(Direction.EAST, board.getTile(0, 5).getConveyorBelt().getDirection());
        assertNotNull(board.getTile(11, 10).getConveyorBelt());
        assertEquals(Direction.WEST, board.getTile(11, 10).getConveyorBelt().getDirection());
    }

    @Test
    @DisplayName("Map1: transformed checkpoints match the legacy single-board layout")
    void map1_checkpointsMatchLegacyBoardPositions() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertNotNull(board.getTile(4, 1).getCheckpoint());
        assertEquals(1, board.getTile(4, 1).getCheckpoint().getNumber());
        assertNotNull(board.getTile(7, 8).getCheckpoint());
        assertEquals(2, board.getTile(7, 8).getCheckpoint().getNumber());
    }

    @Test
    @DisplayName("Map1: repair tiles keep walls from the legacy layout")
    void map1_repairTilePreservesLegacyWalls() {
        Board board = boardLoader.createDefaultBoard("map1");
        Tile repairTile = board.getTile(1, 3);

        assertTrue(repairTile.isRepair());
        assertTrue(repairTile.hasWall(Direction.EAST));
        assertTrue(repairTile.hasWall(Direction.SOUTH));
    }

    @Test
    @DisplayName("Map1: walls are mirrored on adjacent tiles")
    void map1_mirrorsLegacyWalls() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertTrue(board.getTile(10, 3).hasWall(Direction.SOUTH));
        assertTrue(board.getTile(10, 4).hasWall(Direction.NORTH));
    }

    @Test
    @DisplayName("Map1: curve and crossing metadata matches legacy semantics")
    void map1_conveyorMetadataMatchesLegacyBoard() {
        Board board = boardLoader.createDefaultBoard("map1");

        ConveyorBelt curve = board.getTile(11, 10).getConveyorBelt();
        assertNotNull(curve);
        assertEquals(Direction.WEST, curve.getDirection());
        assertEquals(RotationDirection.CLOCKWISE, curve.getCurveRotation());

        ConveyorBelt crossing = board.getTile(6, 1).getConveyorBelt();
        assertNotNull(crossing);
        assertTrue(crossing.isCrossing());
        assertEquals("RIGHT", crossing.getCrossingType());
        assertEquals(Direction.SOUTH, crossing.getDirection());
    }

    @Test
    @DisplayName("Map2: transformed checkpoints match the legacy layout")
    void map2_checkpointsMatchLegacyBoardPositions() {
        Board board = boardLoader.createDefaultBoard("map2");

        assertNotNull(board.getTile(9, 2).getCheckpoint());
        assertEquals(1, board.getTile(9, 2).getCheckpoint().getNumber());
        assertNotNull(board.getTile(1, 9).getCheckpoint());
        assertEquals(2, board.getTile(1, 9).getCheckpoint().getNumber());
    }

    @Test
    @DisplayName("Map2: gear rotation directions match the old board")
    void map2_hasLegacyGearDirections() {
        Board board = boardLoader.createDefaultBoard("map2");

        assertNotNull(board.getTile(8, 3).getGear());
        assertEquals(RotationDirection.COUNTERCLOCKWISE, board.getTile(8, 3).getGear().getRotation());
        assertNotNull(board.getTile(1, 10).getGear());
        assertEquals(RotationDirection.CLOCKWISE, board.getTile(1, 10).getGear().getRotation());
    }

    @Test
    @DisplayName("Map4: checkpoints keep the underlying legacy conveyor metadata")
    void map4_checkpointPreservesUnderlyingConveyor() {
        Board board = boardLoader.createDefaultBoard("map4");
        Tile checkpointTile = board.getTile(10, 1);

        assertNotNull(checkpointTile.getCheckpoint());
        assertEquals(1, checkpointTile.getCheckpoint().getNumber());
        assertNotNull(checkpointTile.getConveyorBelt());
        assertEquals(Direction.SOUTH, checkpointTile.getConveyorBelt().getDirection());
        assertEquals(RotationDirection.COUNTERCLOCKWISE, checkpointTile.getConveyorBelt().getCurveRotation());
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
    @DisplayName("Map5: has express belt")
    void map5_hasExpressBelt() {
        Board board = boardLoader.createDefaultBoard("map5");
        Tile belt = board.getTile(11, 11);

        assertNotNull(belt.getConveyorBelt());
        assertTrue(belt.getConveyorBelt().isExpress());
        assertEquals(Direction.NORTH, belt.getConveyorBelt().getDirection());
    }

    @Test
    @DisplayName("Map5: laser source tiles keep walls and still mirror them to neighbors")
    void map5_laserSourcePreservesWallsAndMirroring() {
        Board board = boardLoader.createDefaultBoard("map5");
        Tile sourceTile = board.getTile(0, 9);

        assertTrue(sourceTile.hasWall(Direction.WEST));
        assertTrue(sourceTile.hasWall(Direction.EAST));
        assertTrue(board.getTile(1, 9).hasWall(Direction.WEST));
        assertTrue(board.getLasers().stream().anyMatch(laser ->
                laser.getX() == 0 && laser.getY() == 9
                        && laser.getDirection() == Direction.WEST
                        && laser.getStrength() == 3));
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
    @DisplayName("Default board: serialized tiles include START fields for start positions")
    @SuppressWarnings("unchecked")
    void defaultBoard_toMapIncludesStartTiles() {
        Board board = boardLoader.createDefaultBoard("Test");

        var map = board.toMap();
        var tiles = (java.util.List<java.util.Map<String, Object>>) map.get("tiles");

        for (int[] startPosition : board.getStartPositions()) {
            assertTrue(tiles.stream().anyMatch(tile ->
                            startPosition[0] == ((Number) tile.get("x")).intValue()
                                    && startPosition[1] == ((Number) tile.get("y")).intValue()
                                    && "START".equals(tile.get("type"))),
                    "Expected serialized START tile at (" + startPosition[0] + "," + startPosition[1] + ")");
        }
    }

    @Test
    @DisplayName("loadBoard: nonexistent falls back to default")
    void loadBoard_fallsBack() {
        Board board = boardLoader.loadBoard("Nonexistent");

        assertNotNull(board);
        assertEquals(12, board.getWidth());
    }
}
