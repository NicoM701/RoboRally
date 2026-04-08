package com.roborally.server.service;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.RotationDirection;
import com.roborally.server.model.Board;
import com.roborally.server.model.ConveyorBelt;
import com.roborally.server.model.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    @DisplayName("Default board: fallback start positions are real START tiles and serialize")
    @SuppressWarnings("unchecked")
    void defaultBoard_startsAreMarkedAndSerialized() {
        Board board = boardLoader.createDefaultBoard("Test");
        Map<String, Object> map = board.toMap();
        List<Map<String, Object>> tiles = (List<Map<String, Object>>) map.get("tiles");

        assertFalse(board.getStartPositions().isEmpty());
        for (int[] startPosition : board.getStartPositions()) {
            Tile tile = board.getTile(startPosition[0], startPosition[1]);
            assertNotNull(tile);
            assertTrue(tile.isStart(), "Expected START tile at (" + startPosition[0] + "," + startPosition[1] + ")");
            assertTrue(tiles.stream().anyMatch(serializedTile ->
                            startPosition[0] == ((Number) serializedTile.get("x")).intValue()
                                    && startPosition[1] == ((Number) serializedTile.get("y")).intValue()
                                    && "START".equals(serializedTile.get("type"))),
                    "Expected serialized START tile at (" + startPosition[0] + "," + startPosition[1] + ")");
        }
    }

    @Test
    @DisplayName("Default board: carries the single-board legacy checkpoint count")
    void defaultBoard_hasLegacyCheckpointCount() {
        Board board = boardLoader.createDefaultBoard("Test");

        assertEquals(2, board.getTotalCheckpoints());
    }

    @Test
    @DisplayName("Default board: map1 fallback still includes legacy lasers")
    void defaultBoard_hasLasers() {
        Board board = boardLoader.createDefaultBoard("Test");

        assertFalse(board.getLasers().isEmpty());
    }

    @Test
    @DisplayName("Map1: legacy coordinates land on the expected board squares")
    void map1_positionsMatchLegacyTransform() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertTrue(board.getTile(9, 3).isPit());

        ConveyorBelt firstBelt = board.getTile(0, 5).getConveyorBelt();
        assertNotNull(firstBelt);
        assertEquals(Direction.EAST, firstBelt.getDirection());

        ConveyorBelt topRightCurve = board.getTile(11, 10).getConveyorBelt();
        assertNotNull(topRightCurve);
        assertEquals(Direction.WEST, topRightCurve.getDirection());
        assertEquals(RotationDirection.CLOCKWISE, topRightCurve.getCurveRotation());
    }

    @Test
    @DisplayName("Map1: checkpoints match the legacy single-board layout")
    void map1_checkpointsMatchLegacyBoardPositions() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertNotNull(board.getTile(4, 1).getCheckpoint());
        assertEquals(1, board.getTile(4, 1).getCheckpoint().getNumber());
        assertNotNull(board.getTile(7, 8).getCheckpoint());
        assertEquals(2, board.getTile(7, 8).getCheckpoint().getNumber());
    }

    @Test
    @DisplayName("Map1: repair tiles keep the legacy wall layout")
    void map1_repairTilePreservesLegacyWalls() {
        Board board = boardLoader.createDefaultBoard("map1");
        Tile repairTile = board.getTile(1, 3);

        assertTrue(repairTile.isRepair());
        assertTrue(repairTile.hasWall(Direction.EAST));
        assertTrue(repairTile.hasWall(Direction.SOUTH));
    }

    @Test
    @DisplayName("Map1: walls are mirrored onto the adjacent tile once the board is assembled")
    void map1_mirrorsLegacyWalls() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertTrue(board.getTile(10, 3).hasWall(Direction.SOUTH));
        assertTrue(board.getTile(10, 4).hasWall(Direction.NORTH));
    }

    @Test
    @DisplayName("Map1: conveyor curve and crossing semantics survive the legacy adapter")
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
    @DisplayName("Map1: laser source coordinates and directions match the legacy board")
    void map1_lasersMatchLegacySources() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertTrue(board.getLasers().stream().anyMatch(laser ->
                laser.getX() == 8 && laser.getY() == 8 && laser.getDirection() == Direction.SOUTH && laser.getStrength() == 2));
        assertTrue(board.getLasers().stream().anyMatch(laser ->
                laser.getX() == 7 && laser.getY() == 3 && laser.getDirection() == Direction.EAST && laser.getStrength() == 1));
        assertEquals(4, board.getLasers().size());
    }

    @Test
    @DisplayName("Map2: checkpoints match the legacy single-board layout")
    void map2_checkpointsMatchLegacyBoardPositions() {
        Board board = boardLoader.createDefaultBoard("map2");

        assertNotNull(board.getTile(9, 2).getCheckpoint());
        assertEquals(1, board.getTile(9, 2).getCheckpoint().getNumber());
        assertNotNull(board.getTile(1, 9).getCheckpoint());
        assertEquals(2, board.getTile(1, 9).getCheckpoint().getNumber());
    }

    @Test
    @DisplayName("Map2: gear directions match the old board semantics")
    void map2_hasLegacyGearDirections() {
        Board board = boardLoader.createDefaultBoard("map2");

        assertNotNull(board.getTile(8, 3).getGear());
        assertEquals(RotationDirection.COUNTERCLOCKWISE, board.getTile(8, 3).getGear().getRotation());
        assertNotNull(board.getTile(1, 10).getGear());
        assertEquals(RotationDirection.CLOCKWISE, board.getTile(1, 10).getGear().getRotation());
    }

    @Test
    @DisplayName("Map2: express belts and laser source positions match the legacy board")
    void map2_expressBeltsAndLaserMatchLegacyBoard() {
        Board board = boardLoader.createDefaultBoard("map2");

        ConveyorBelt expressBelt = board.getTile(11, 5).getConveyorBelt();
        assertNotNull(expressBelt);
        assertTrue(expressBelt.isExpress());
        assertEquals(Direction.EAST, expressBelt.getDirection());

        assertTrue(board.getLasers().stream().anyMatch(laser ->
                laser.getX() == 2 && laser.getY() == 11 && laser.getDirection() == Direction.NORTH && laser.getStrength() == 1));
    }

    @Test
    @DisplayName("Default board: serializes to a board payload")
    void defaultBoard_toMap() {
        Board board = boardLoader.createDefaultBoard("Test");

        Map<String, Object> map = board.toMap();

        assertEquals("Test", map.get("name"));
        assertEquals(12, map.get("width"));
        assertEquals(12, map.get("height"));
        assertNotNull(map.get("tiles"));
        assertNotNull(map.get("lasers"));
    }

    @Test
    @DisplayName("loadBoard: unknown names fall back to the default legacy map")
    void loadBoard_fallsBack() {
        Board board = boardLoader.loadBoard("Nonexistent");

        assertNotNull(board);
        assertEquals(12, board.getWidth());
        assertEquals(12, board.getHeight());
    }
}
