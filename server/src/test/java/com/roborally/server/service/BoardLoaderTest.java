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
import java.util.Set;

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
    @DisplayName("Map1: legacy coordinates are flipped vertically, not rotated")
    void map1_positionsMatchLegacyRendererCoordinates() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertTrue(board.getTile(3, 9).isPit());
        assertTrue(board.getTile(10, 10).isRepair());

        ConveyorBelt belt = board.getTile(5, 11).getConveyorBelt();
        assertNotNull(belt);
        assertEquals(Direction.SOUTH, belt.getDirection());

        ConveyorBelt curve = board.getTile(11, 11).getConveyorBelt();
        assertNotNull(curve);
        assertEquals(Direction.EAST, curve.getDirection());
        assertEquals(RotationDirection.COUNTERCLOCKWISE, curve.getCurveRotation());
    }

    @Test
    @DisplayName("Map1: checkpoints match the legacy single-board layout")
    void map1_checkpointsMatchLegacyBoardPositions() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertNotNull(board.getTile(1, 4).getCheckpoint());
        assertEquals(1, board.getTile(1, 4).getCheckpoint().getNumber());
        assertNull(board.getTile(1, 4).getConveyorBelt(), "Checkpoint tiles replace the old field semantics");

        assertNotNull(board.getTile(8, 7).getCheckpoint());
        assertEquals(2, board.getTile(8, 7).getCheckpoint().getNumber());
    }

    @Test
    @DisplayName("Map1: walls are mirrored onto the adjacent tile once the board is assembled")
    void map1_mirrorsLegacyWalls() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertTrue(board.getTile(2, 11).hasWall(Direction.SOUTH));
        assertTrue(board.getTile(2, 10).hasWall(Direction.NORTH));
    }

    @Test
    @DisplayName("Map1: conveyor curve and crossing semantics match the legacy board")
    void map1_conveyorMetadataMatchesLegacyBoard() {
        Board board = boardLoader.createDefaultBoard("map1");

        ConveyorBelt curve = board.getTile(11, 11).getConveyorBelt();
        assertNotNull(curve);
        assertEquals(Direction.EAST, curve.getDirection());
        assertEquals(RotationDirection.COUNTERCLOCKWISE, curve.getCurveRotation());

        ConveyorBelt crossing = board.getTile(1, 6).getConveyorBelt();
        assertNotNull(crossing);
        assertTrue(crossing.isCrossing());
        assertEquals("RIGHT", crossing.getCrossingType());
        assertEquals(Direction.EAST, crossing.getDirection());
    }

    @Test
    @DisplayName("Map1: laser source coordinates and directions match the legacy board")
    void map1_lasersMatchLegacySources() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertTrue(board.getLasers().stream().anyMatch(laser ->
                laser.getX() == 8 && laser.getY() == 8 && laser.getDirection() == Direction.EAST && laser.getStrength() == 2));
        assertTrue(board.getLasers().stream().anyMatch(laser ->
                laser.getX() == 3 && laser.getY() == 7 && laser.getDirection() == Direction.SOUTH && laser.getStrength() == 1));
        assertEquals(4, board.getLasers().size());
    }

    @Test
    @DisplayName("Map1: category counts match the legacy BoardTest expectations")
    void map1_countsMatchLegacyBoardTest() {
        Board board = boardLoader.createDefaultBoard("map1");

        assertEquals(64, countPlainFloorTiles(board));
        assertEquals(40, countBelts(board, false, false, false));
        assertEquals(18, countBelts(board, false, true, false));
        assertEquals(2, countBelts(board, false, false, true));
        assertEquals(2, countCheckpoints(board));
        assertEquals(3, countRepairs(board));
        assertEquals(4, board.getLasers().size());
        assertEquals(11, countPits(board));
        assertEquals(0, countBelts(board, true, false, false));
        assertEquals(0, countBelts(board, true, true, false));
        assertEquals(0, countBelts(board, true, false, true));
        assertEquals(0, countGears(board));
        assertEquals(0, countPresses(board));
        assertEquals(0, countPushers(board));
    }

    @Test
    @DisplayName("Map2: checkpoints match the legacy single-board layout")
    void map2_checkpointsMatchLegacyBoardPositions() {
        Board board = boardLoader.createDefaultBoard("map2");

        assertNotNull(board.getTile(2, 9).getCheckpoint());
        assertEquals(1, board.getTile(2, 9).getCheckpoint().getNumber());
        assertNotNull(board.getTile(9, 1).getCheckpoint());
        assertEquals(2, board.getTile(9, 1).getCheckpoint().getNumber());
    }

    @Test
    @DisplayName("Map2: gear directions match the old board semantics")
    void map2_hasLegacyGearDirections() {
        Board board = boardLoader.createDefaultBoard("map2");

        assertNotNull(board.getTile(3, 8).getGear());
        assertEquals(RotationDirection.CLOCKWISE, board.getTile(3, 8).getGear().getRotation());
        assertNotNull(board.getTile(10, 1).getGear());
        assertEquals(RotationDirection.COUNTERCLOCKWISE, board.getTile(10, 1).getGear().getRotation());
    }

    @Test
    @DisplayName("Map2: express belts and laser source positions match the legacy board")
    void map2_expressBeltsAndLaserMatchLegacyBoard() {
        Board board = boardLoader.createDefaultBoard("map2");

        ConveyorBelt expressBelt = board.getTile(5, 11).getConveyorBelt();
        assertNotNull(expressBelt);
        assertTrue(expressBelt.isExpress());
        assertEquals(Direction.SOUTH, expressBelt.getDirection());

        assertTrue(board.getLasers().stream().anyMatch(laser ->
                laser.getX() == 11 && laser.getY() == 2 && laser.getDirection() == Direction.WEST && laser.getStrength() == 1));
    }

    @Test
    @DisplayName("Map2: category counts match the legacy BoardTest expectations")
    void map2_countsMatchLegacyBoardTest() {
        Board board = boardLoader.createDefaultBoard("map2");

        assertEquals(64, countPlainFloorTiles(board));
        assertEquals(55, countBelts(board, false, false, false));
        assertEquals(0, countBelts(board, false, true, false));
        assertEquals(0, countBelts(board, false, false, true));
        assertEquals(2, countCheckpoints(board));
        assertEquals(3, countRepairs(board));
        assertEquals(1, board.getLasers().size());
        assertEquals(2, countPits(board));
        assertEquals(13, countBelts(board, true, false, false));
        assertEquals(0, countBelts(board, true, true, false));
        assertEquals(0, countBelts(board, true, false, true));
        assertEquals(4, countGears(board));
        assertEquals(0, countPresses(board));
        assertEquals(0, countPushers(board));
    }

    @Test
    @DisplayName("Map4: legacy pusher round patterns are preserved")
    void map4_pushersKeepLegacyRoundPatterns() {
        Board board = boardLoader.createDefaultBoard("map4");

        assertEquals(Set.of(2, 4), board.getTile(2, 11).getPusher().getActiveSteps());
        assertEquals(Set.of(1, 3, 5), board.getTile(4, 11).getPusher().getActiveSteps());
    }

    @Test
    @DisplayName("Map5: legacy press round patterns are preserved")
    void map5_pressesKeepLegacyRoundPatterns() {
        Board board = boardLoader.createDefaultBoard("map5");

        assertEquals(Set.of(1, 5), board.getTile(6, 10).getPress().getActiveSteps());
        assertEquals(Set.of(2, 4), board.getTile(4, 7).getPress().getActiveSteps());
    }

    @Test
    @DisplayName("Map6: overwritten legacy wall does not survive the translated curve tile")
    void map6_overwrittenLegacyWallDoesNotSurviveTranslation() {
        Board board = boardLoader.createDefaultBoard("map6");
        Tile curveTile = board.getTile(3, 6);

        assertNotNull(curveTile.getConveyorBelt());
        assertEquals(Direction.SOUTH, curveTile.getConveyorBelt().getDirection());
        assertTrue(curveTile.hasWall(Direction.WEST));
        assertFalse(curveTile.hasWall(Direction.EAST));
        assertFalse(board.getTile(4, 6).hasWall(Direction.WEST));
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

    private int countPlainFloorTiles(Board board) {
        Set<String> laserSources = board.getLasers().stream()
                .map(laser -> laser.getX() + "," + laser.getY())
                .collect(java.util.stream.Collectors.toSet());

        int count = 0;
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                Tile tile = board.getTile(x, y);
                boolean emptyFloor = tile.getFieldType() == com.roborally.common.enums.FieldType.FLOOR
                        && tile.getConveyorBelt() == null
                        && tile.getGear() == null
                        && tile.getPusher() == null
                        && tile.getPress() == null
                        && tile.getCheckpoint() == null
                        && !tile.isStart()
                        && !laserSources.contains(tile.getX() + "," + tile.getY());
                if (emptyFloor) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countBelts(Board board, boolean express, boolean curve, boolean crossing) {
        int count = 0;
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                Tile tile = board.getTile(x, y);
                ConveyorBelt belt = tile.getConveyorBelt();
                if (belt == null) {
                    continue;
                }
                boolean matches = belt.isExpress() == express
                        && belt.isCrossing() == crossing
                        && (belt.getCurveRotation() != null) == curve
                        && !(curve && crossing);
                if (matches) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countCheckpoints(Board board) {
        int count = 0;
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                if (board.getTile(x, y).getCheckpoint() != null) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countRepairs(Board board) {
        int count = 0;
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                if (board.getTile(x, y).isRepair()) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countPits(Board board) {
        int count = 0;
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                if (board.getTile(x, y).isPit()) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countGears(Board board) {
        int count = 0;
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                if (board.getTile(x, y).getGear() != null) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countPresses(Board board) {
        int count = 0;
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                if (board.getTile(x, y).getPress() != null) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countPushers(Board board) {
        int count = 0;
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                if (board.getTile(x, y).getPusher() != null) {
                    count++;
                }
            }
        }
        return count;
    }
}
