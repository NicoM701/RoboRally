package com.roborally.server.service;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.FieldType;
import com.roborally.common.enums.RotationDirection;
import com.roborally.server.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BoardLoader {

    private static final Logger log = LoggerFactory.getLogger(BoardLoader.class);

    public Board loadBoard(String boardName) {
        return createDefaultBoard(boardName);
    }

    public List<Map<String, Object>> getAvailableBoards() {
        List<Map<String, Object>> list = new ArrayList<>();
        List<String> names = List.of("map1", "map2", "map3", "map4", "map5", "map6");
        for (String name : names) {
            Board board = loadBoard(name);
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("id", name);
            bm.put("name", name);
            bm.put("maxPlayers", board.getStartPositions().size());
            bm.put("boardData", board.toMap());
            list.add(bm);
        }
        return list;
    }

    public Board createDefaultBoard(String name) {
        Board board = new Board(name, 12, 12);
        String normalizedName = name == null ? "map1" : name.toLowerCase(Locale.ROOT);

        switch (normalizedName) {
            case "map2" -> generateMap2(board, 1, true);
            case "map3" -> generateMap3(board, 1, true);
            case "map4" -> generateMap4(board, 1, true);
            case "map5" -> generateMap5(board, 1, true);
            case "map6" -> generateMap6(board, 1, true);
            default -> generateMap1(board, 1, true);
        }

        if (board.getStartPositions().isEmpty()) {
            int maxSpawns = 8;
            if (normalizedName.equals("map1") || normalizedName.equals("map2")) maxSpawns = 2;
            else if (normalizedName.equals("map3") || normalizedName.equals("map4")) maxSpawns = 4;

            for (int x = 1; x <= maxSpawns; x++) {
                board.addStartPosition(x, 11);
            }
        }

        board.setTotalCheckpoints(2);
        applyLegacyConveyorMetadata(board, normalizedName);
        calculateConveyorCurves(board);
        return board;
    }

    private void calculateConveyorCurves(Board board) {
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                Tile tile = board.getTile(x, y);
                if (tile == null || tile.getConveyorBelt() == null) continue;

                ConveyorBelt cb = tile.getConveyorBelt();
                if (cb.isCrossing() || cb.getCurveRotation() != null || cb.getCurveFrom() != null) {
                    continue;
                }

                Direction outDir = cb.getDirection();
                List<Direction> inputDirs = new ArrayList<>();

                for (Direction d : Direction.values()) {
                    int nx = x + d.dx();
                    int ny = y + d.dy();
                    Tile neighbor = board.getTile(nx, ny);
                    if (neighbor != null && neighbor.getConveyorBelt() != null
                            && neighbor.getConveyorBelt().getDirection() == d.opposite()) {
                        inputDirs.add(d);
                    }
                }

                if (inputDirs.isEmpty()) {
                    continue;
                }

                List<Direction> turningInputs = inputDirs.stream()
                        .filter(inDir -> inDir.opposite() != outDir)
                        .toList();

                boolean hasStraightInput = inputDirs.stream()
                        .anyMatch(inDir -> inDir.opposite() == outDir);

                if (hasStraightInput || turningInputs.size() != 1) {
                    continue;
                }

                Direction curveFrom = turningInputs.get(0);
                RotationDirection rot = determineCurveRotation(curveFrom, outDir);
                if (rot != null) {
                    cb.setCurveFrom(curveFrom);
                    cb.setCurveRotation(rot);
                }
            }
        }
    }

    private RotationDirection determineCurveRotation(Direction curveFrom, Direction outDir) {
        if (curveFrom.rotateClockwise() == outDir) {
            return RotationDirection.CLOCKWISE;
        }
        if (curveFrom.rotateCounterClockwise() == outDir) {
            return RotationDirection.COUNTERCLOCKWISE;
        }
        return null;
    }

    private enum LegacyOrientation {
        LEFT, RIGHT, TOP, BOTTOM
    }

    private Tile legacyTile(Board board, int legacyX, int legacyY) {
        return board.getTile(board.getWidth() - 1 - legacyY, legacyX);
    }

    private Direction legacyDirection(LegacyOrientation orientation) {
        return switch (orientation) {
            case LEFT -> Direction.NORTH;
            case RIGHT -> Direction.SOUTH;
            case TOP -> Direction.WEST;
            case BOTTOM -> Direction.EAST;
        };
    }

    private RotationDirection legacyCurveRotation(int legacyCurve) {
        return legacyCurve == 2 ? RotationDirection.CLOCKWISE : RotationDirection.COUNTERCLOCKWISE;
    }

    private Direction curveFromFor(Direction outDir, RotationDirection rotation) {
        return rotation == RotationDirection.CLOCKWISE ? outDir.rotateClockwise() : outDir.rotateCounterClockwise();
    }

    private String legacyCrossingType(int legacyCrossing) {
        return switch (legacyCrossing) {
            case 2 -> "RIGHT";
            case 3 -> "LEFTRIGHT";
            default -> "LEFT";
        };
    }

    private ConveyorBelt requireLegacyConveyor(Board board, int legacyX, int legacyY, LegacyOrientation orientation, boolean express) {
        Tile tile = legacyTile(board, legacyX, legacyY);
        if (tile == null || tile.getConveyorBelt() == null) {
            throw new IllegalStateException("Missing conveyor at legacy tile (" + legacyX + ", " + legacyY + ")");
        }

        ConveyorBelt belt = tile.getConveyorBelt();
        Direction expectedDirection = legacyDirection(orientation);
        if (belt.getDirection() != expectedDirection || belt.isExpress() != express) {
            throw new IllegalStateException(
                    "Legacy conveyor mismatch at (" + legacyX + ", " + legacyY + "): expected "
                            + expectedDirection + " express=" + express + " but found "
                            + belt.getDirection() + " express=" + belt.isExpress());
        }
        return belt;
    }

    private void annotateLegacyCurve(Board board, int legacyX, int legacyY, LegacyOrientation orientation, int legacyCurve, boolean express) {
        ConveyorBelt belt = requireLegacyConveyor(board, legacyX, legacyY, orientation, express);
        RotationDirection rotation = legacyCurveRotation(legacyCurve);
        belt.setCrossing(false);
        belt.setCurveRotation(rotation);
        belt.setCurveFrom(curveFromFor(belt.getDirection(), rotation));
    }

    private void annotateLegacyCrossing(Board board, int legacyX, int legacyY, LegacyOrientation orientation, int legacyCrossing, boolean express) {
        ConveyorBelt belt = requireLegacyConveyor(board, legacyX, legacyY, orientation, express);
        belt.setCurveRotation(null);
        belt.setCurveFrom(null);
        belt.setCrossingType(legacyCrossingType(legacyCrossing));
    }

    private void applyLegacyConveyorMetadata(Board board, String boardName) {
        switch (boardName) {
            case "map1" -> applyMap1ConveyorMetadata(board);
            case "map3" -> applyMap3ConveyorMetadata(board);
            case "map4" -> applyMap4ConveyorMetadata(board);
            case "map5" -> applyMap5ConveyorMetadata(board);
            case "map6" -> applyMap6ConveyorMetadata(board);
            default -> {
            }
        }
    }

    private void applyMap1ConveyorMetadata(Board board) {
        annotateLegacyCurve(board, 10, 0, LegacyOrientation.TOP, 2, false);
        annotateLegacyCurve(board, 11, 0, LegacyOrientation.RIGHT, 1, false);
        annotateLegacyCurve(board, 1, 1, LegacyOrientation.RIGHT, 1, false);
        annotateLegacyCurve(board, 11, 1, LegacyOrientation.TOP, 2, false);
        annotateLegacyCurve(board, 6, 4, LegacyOrientation.TOP, 2, false);
        annotateLegacyCurve(board, 7, 4, LegacyOrientation.RIGHT, 1, false);
        annotateLegacyCrossing(board, 1, 5, LegacyOrientation.RIGHT, 2, false);
        annotateLegacyCurve(board, 5, 5, LegacyOrientation.RIGHT, 2, false);
        annotateLegacyCurve(board, 7, 5, LegacyOrientation.TOP, 2, false);
        annotateLegacyCurve(board, 4, 6, LegacyOrientation.BOTTOM, 2, false);
        annotateLegacyCurve(board, 8, 6, LegacyOrientation.LEFT, 2, false);
        annotateLegacyCrossing(board, 10, 6, LegacyOrientation.LEFT, 2, false);
        annotateLegacyCurve(board, 4, 7, LegacyOrientation.LEFT, 1, false);
        annotateLegacyCurve(board, 5, 7, LegacyOrientation.BOTTOM, 2, false);
        annotateLegacyCurve(board, 7, 7, LegacyOrientation.LEFT, 2, false);
        annotateLegacyCurve(board, 8, 7, LegacyOrientation.TOP, 1, false);
        annotateLegacyCurve(board, 6, 8, LegacyOrientation.LEFT, 2, false);
        annotateLegacyCurve(board, 7, 8, LegacyOrientation.TOP, 1, false);
        annotateLegacyCurve(board, 1, 10, LegacyOrientation.BOTTOM, 2, false);
        annotateLegacyCurve(board, 10, 10, LegacyOrientation.LEFT, 1, false);
    }

    private void applyMap3ConveyorMetadata(Board board) {
        annotateLegacyCrossing(board, 5, 3, LegacyOrientation.RIGHT, 1, false);
        annotateLegacyCrossing(board, 6, 8, LegacyOrientation.LEFT, 1, false);
    }

    private void applyMap4ConveyorMetadata(Board board) {
        annotateLegacyCrossing(board, 1, 5, LegacyOrientation.TOP, 1, false);
        annotateLegacyCrossing(board, 10, 6, LegacyOrientation.BOTTOM, 1, true);
        annotateLegacyCrossing(board, 6, 1, LegacyOrientation.LEFT, 1, true);
        annotateLegacyCrossing(board, 5, 10, LegacyOrientation.RIGHT, 1, false);
        annotateLegacyCurve(board, 1, 1, LegacyOrientation.RIGHT, 1, false);
        annotateLegacyCurve(board, 2, 1, LegacyOrientation.LEFT, 2, true);
        annotateLegacyCrossing(board, 10, 1, LegacyOrientation.LEFT, 3, true);
        annotateLegacyCurve(board, 3, 2, LegacyOrientation.LEFT, 2, false);
        annotateLegacyCurve(board, 9, 2, LegacyOrientation.BOTTOM, 2, false);
        annotateLegacyCurve(board, 4, 3, LegacyOrientation.LEFT, 2, true);
        annotateLegacyCurve(board, 8, 3, LegacyOrientation.BOTTOM, 2, true);
        annotateLegacyCurve(board, 5, 4, LegacyOrientation.LEFT, 2, false);
        annotateLegacyCurve(board, 7, 4, LegacyOrientation.BOTTOM, 2, false);
        annotateLegacyCurve(board, 4, 7, LegacyOrientation.TOP, 2, true);
        annotateLegacyCurve(board, 6, 7, LegacyOrientation.RIGHT, 2, true);
        annotateLegacyCurve(board, 3, 8, LegacyOrientation.TOP, 2, false);
        annotateLegacyCurve(board, 7, 8, LegacyOrientation.RIGHT, 2, false);
        annotateLegacyCurve(board, 2, 9, LegacyOrientation.TOP, 2, true);
        annotateLegacyCurve(board, 8, 9, LegacyOrientation.RIGHT, 2, true);
        annotateLegacyCrossing(board, 1, 10, LegacyOrientation.RIGHT, 3, false);
        annotateLegacyCurve(board, 9, 10, LegacyOrientation.RIGHT, 2, false);
        annotateLegacyCurve(board, 10, 10, LegacyOrientation.LEFT, 1, true);
    }

    private void applyMap5ConveyorMetadata(Board board) {
        annotateLegacyCurve(board, 4, 0, LegacyOrientation.BOTTOM, 1, false);
        annotateLegacyCurve(board, 5, 0, LegacyOrientation.RIGHT, 2, false);
        annotateLegacyCrossing(board, 6, 0, LegacyOrientation.TOP, 2, false);
        annotateLegacyCrossing(board, 10, 0, LegacyOrientation.LEFT, 1, true);
        annotateLegacyCrossing(board, 0, 1, LegacyOrientation.TOP, 1, true);
        annotateLegacyCrossing(board, 11, 3, LegacyOrientation.TOP, 2, false);
        annotateLegacyCrossing(board, 0, 5, LegacyOrientation.RIGHT, 2, true);
        annotateLegacyCrossing(board, 4, 5, LegacyOrientation.BOTTOM, 2, false);
        annotateLegacyCurve(board, 9, 5, LegacyOrientation.RIGHT, 2, true);
        annotateLegacyCurve(board, 11, 5, LegacyOrientation.TOP, 2, false);
        annotateLegacyCrossing(board, 6, 6, LegacyOrientation.TOP, 2, false);
        annotateLegacyCrossing(board, 11, 6, LegacyOrientation.LEFT, 2, true);
        annotateLegacyCurve(board, 5, 10, LegacyOrientation.LEFT, 1, false);
        annotateLegacyCurve(board, 6, 10, LegacyOrientation.TOP, 1, false);
        annotateLegacyCrossing(board, 11, 10, LegacyOrientation.BOTTOM, 1, true);
        annotateLegacyCrossing(board, 1, 11, LegacyOrientation.RIGHT, 1, true);
        annotateLegacyCrossing(board, 4, 11, LegacyOrientation.BOTTOM, 3, false);
        annotateLegacyCurve(board, 5, 11, LegacyOrientation.BOTTOM, 2, false);
    }

    private void applyMap6ConveyorMetadata(Board board) {
        annotateLegacyCurve(board, 4, 0, LegacyOrientation.BOTTOM, 1, false);
        annotateLegacyCurve(board, 5, 0, LegacyOrientation.RIGHT, 2, false);
        annotateLegacyCrossing(board, 1, 1, LegacyOrientation.BOTTOM, 2, false);
        annotateLegacyCurve(board, 1, 2, LegacyOrientation.LEFT, 1, false);
        annotateLegacyCurve(board, 2, 2, LegacyOrientation.BOTTOM, 2, false);
        annotateLegacyCurve(board, 6, 2, LegacyOrientation.TOP, 2, false);
        annotateLegacyCurve(board, 7, 2, LegacyOrientation.RIGHT, 1, false);
        annotateLegacyCurve(board, 1, 4, LegacyOrientation.BOTTOM, 1, false);
        annotateLegacyCurve(board, 2, 4, LegacyOrientation.RIGHT, 2, false);
        annotateLegacyCurve(board, 7, 4, LegacyOrientation.TOP, 2, false);
        annotateLegacyCurve(board, 8, 4, LegacyOrientation.RIGHT, 1, false);
        annotateLegacyCurve(board, 1, 5, LegacyOrientation.RIGHT, 2, false);
        annotateLegacyCurve(board, 3, 5, LegacyOrientation.BOTTOM, 1, false);
        annotateLegacyCurve(board, 4, 5, LegacyOrientation.RIGHT, 2, false);
        annotateLegacyCurve(board, 8, 5, LegacyOrientation.TOP, 2, false);
        annotateLegacyCurve(board, 2, 7, LegacyOrientation.BOTTOM, 1, false);
        annotateLegacyCurve(board, 3, 7, LegacyOrientation.RIGHT, 2, false);
        annotateLegacyCurve(board, 5, 9, LegacyOrientation.BOTTOM, 1, false);
        annotateLegacyCurve(board, 7, 9, LegacyOrientation.RIGHT, 1, false);
        annotateLegacyCurve(board, 1, 10, LegacyOrientation.BOTTOM, 1, false);
        annotateLegacyCurve(board, 2, 10, LegacyOrientation.RIGHT, 2, false);
        annotateLegacyCurve(board, 10, 10, LegacyOrientation.LEFT, 2, false);
        annotateLegacyCurve(board, 6, 11, LegacyOrientation.LEFT, 2, false);
        annotateLegacyCurve(board, 7, 11, LegacyOrientation.TOP, 1, false);
    }

    private void setLaserField(Board board, int x, int y, Direction dir, int strength) {
        board.addLaser(new Laser(x, y, dir, strength));
    }

    private void generateMap1(Board board, int numberCheckpoint, boolean only1Map) {

board.getTile(11, 2).addWall(Direction.EAST);
board.getTile(11, 4).addWall(Direction.EAST);
board.getTile(11, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(11, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(11, 7).addWall(Direction.EAST);
board.getTile(11, 9).addWall(Direction.EAST);
board.getTile(11, 10).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(11, 11).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(10, 0).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(10, 1).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(10, 3).addWall(Direction.SOUTH);
board.getTile(10, 4).addWall(Direction.NORTH);
board.getTile(10, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(10, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(10, 7).addWall(Direction.SOUTH);
board.getTile(10, 8).addWall(Direction.NORTH);
board.getTile(10, 10).setFieldType(FieldType.REPAIR_1);
board.getTile(10, 10).addWall(Direction.WEST);
board.getTile(10, 11).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(9, 0).addWall(Direction.NORTH);
board.getTile(9, 1).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(9, 3).setFieldType(FieldType.PIT);
board.getTile(9, 3).addWall(Direction.WEST);
board.getTile(9, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(9, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(9, 6).addWall(Direction.SOUTH);
board.getTile(9, 7).addWall(Direction.NORTH);
board.getTile(9, 9).setFieldType(FieldType.PIT);
board.getTile(9, 10).addWall(Direction.SOUTH);
board.getTile(9, 10).addWall(Direction.EAST);
board.getTile(9, 11).addWall(Direction.NORTH);
board.getTile(9, 11).addWall(Direction.SOUTH);
board.getTile(8, 0).addWall(Direction.WEST);
board.getTile(8, 1).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(8, 2).addWall(Direction.WEST);
board.getTile(8, 3).addWall(Direction.EAST);
board.getTile(8, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(8, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(8, 7).addWall(Direction.SOUTH);
board.getTile(8, 8).addWall(Direction.NORTH);
board.getTile(8, 10).addWall(Direction.SOUTH);
board.getTile(8, 11).addWall(Direction.NORTH);
board.getTile(7, 0).addWall(Direction.NORTH);
board.getTile(7, 0).addWall(Direction.EAST);
board.getTile(7, 1).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(7, 2).addWall(Direction.EAST);
board.getTile(7, 3).addWall(Direction.WEST);
board.getTile(7, 4).addWall(Direction.WEST);
board.getTile(7, 4).addWall(Direction.SOUTH);
board.getTile(7, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(7, 5).addWall(Direction.NORTH);
board.getTile(7, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(7, 7).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(7, 7).addWall(Direction.SOUTH);
board.getTile(7, 8).addWall(Direction.NORTH);
board.getTile(7, 8).addWall(Direction.WEST);
board.getTile(7, 9).addWall(Direction.WEST);
board.getTile(7, 11).addWall(Direction.SOUTH);
board.getTile(6, 0).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 1).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, true));
board.getTile(6, 2).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 3).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 3).addWall(Direction.EAST);
board.getTile(6, 4).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 4).addWall(Direction.EAST);
board.getTile(6, 5).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 6).setFieldType(FieldType.PIT);
board.getTile(6, 7).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(6, 8).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 8).addWall(Direction.EAST);
board.getTile(6, 9).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 9).addWall(Direction.EAST);
board.getTile(6, 10).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 11).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(5, 0).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 1).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 2).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 3).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(5, 5).setFieldType(FieldType.PIT);
board.getTile(5, 6).setFieldType(FieldType.PIT);
board.getTile(5, 7).setFieldType(FieldType.PIT);
board.getTile(5, 8).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 9).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 10).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, true));
board.getTile(5, 11).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(4, 0).addWall(Direction.NORTH);
board.getTile(4, 3).addWall(Direction.WEST);
board.getTile(4, 3).addWall(Direction.SOUTH);
board.getTile(4, 4).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(4, 4).addWall(Direction.NORTH);
board.getTile(4, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(4, 6).setFieldType(FieldType.PIT);
board.getTile(4, 7).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(4, 8).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(4, 8).addWall(Direction.WEST);
board.getTile(4, 8).addWall(Direction.SOUTH);
board.getTile(4, 9).addWall(Direction.NORTH);
board.getTile(4, 10).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(4, 11).addWall(Direction.SOUTH);
board.getTile(3, 3).addWall(Direction.EAST);
board.getTile(3, 3).addWall(Direction.SOUTH);
board.getTile(3, 4).addWall(Direction.NORTH);
board.getTile(3, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(3, 6).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(3, 7).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(3, 7).addWall(Direction.WEST);
board.getTile(3, 7).addWall(Direction.SOUTH);
board.getTile(3, 8).addWall(Direction.NORTH);
board.getTile(3, 8).addWall(Direction.EAST);
board.getTile(3, 10).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(2, 0).addWall(Direction.NORTH);
board.getTile(2, 1).setFieldType(FieldType.PIT);
board.getTile(2, 1).addWall(Direction.SOUTH);
board.getTile(2, 2).addWall(Direction.NORTH);
board.getTile(2, 3).addWall(Direction.WEST);
board.getTile(2, 4).addWall(Direction.SOUTH);
board.getTile(2, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(2, 5).addWall(Direction.NORTH);
board.getTile(2, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(2, 7).setFieldType(FieldType.PIT);
board.getTile(2, 7).addWall(Direction.EAST);
board.getTile(2, 8).addWall(Direction.WEST);
board.getTile(2, 9).setFieldType(FieldType.REPAIR_2);
board.getTile(2, 10).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(2, 11).addWall(Direction.SOUTH);
board.getTile(1, 0).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(1, 1).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(1, 3).addWall(Direction.SOUTH);
board.getTile(1, 3).addWall(Direction.EAST);
board.getTile(1, 4).addWall(Direction.NORTH);
board.getTile(1, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(1, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(1, 7).setFieldType(FieldType.PIT);
board.getTile(1, 7).addWall(Direction.SOUTH);
board.getTile(1, 8).addWall(Direction.NORTH);
board.getTile(1, 8).addWall(Direction.EAST);
board.getTile(1, 10).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(1, 11).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(0, 0).setFieldType(FieldType.PIT);
board.getTile(0, 1).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(1, 3).setFieldType(FieldType.REPAIR_1);
board.getTile(0, 2).addWall(Direction.WEST);
board.getTile(0, 4).addWall(Direction.WEST);
board.getTile(0, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(0, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(0, 7).addWall(Direction.WEST);
board.getTile(0, 9).addWall(Direction.WEST);
board.getTile(4, 1).setCheckpoint(new Checkpoint(1));
if (numberCheckpoint == 1) {
    if (only1Map) {
        board.getTile(7, 8).setCheckpoint(new Checkpoint(2));
    }
}
setLaserField(board, 8, 8, Direction.SOUTH, 2);
setLaserField(board, 7, 3, Direction.EAST, 1);
setLaserField(board, 4, 9, Direction.SOUTH, 1);
setLaserField(board, 2, 3, Direction.EAST, 1);
    }

    private void generateMap2(Board board, int numberCheckpoint, boolean only1Map) {

board.getTile(11, 1).addWall(Direction.WEST);
board.getTile(11, 2).addWall(Direction.EAST);
board.getTile(11, 3).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(11, 4).addWall(Direction.EAST);
board.getTile(11, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
board.getTile(11, 7).addWall(Direction.EAST);
board.getTile(11, 8).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(11, 9).addWall(Direction.EAST);
board.getTile(11, 10).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(10, 0).setFieldType(FieldType.PIT);
board.getTile(10, 1).addWall(Direction.EAST);
board.getTile(10, 2).addWall(Direction.SOUTH);
board.getTile(10, 3).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(10, 3).addWall(Direction.NORTH);
board.getTile(10, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
board.getTile(10, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(10, 8).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(10, 10).setFieldType(FieldType.REPAIR_1);
board.getTile(10, 11).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(9, 0).addWall(Direction.NORTH);
board.getTile(9, 3).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(9, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
board.getTile(9, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(9, 8).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(9, 10).addWall(Direction.WEST);
board.getTile(9, 11).addWall(Direction.SOUTH);
board.getTile(8, 0).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(8, 1).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(8, 2).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(8, 3).setGear(new Gear(RotationDirection.COUNTERCLOCKWISE));
board.getTile(8, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
board.getTile(8, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(8, 8).setGear(new Gear(RotationDirection.COUNTERCLOCKWISE));
board.getTile(8, 9).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(8, 10).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(8, 10).addWall(Direction.EAST);
board.getTile(8, 11).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(7, 0).addWall(Direction.NORTH);
board.getTile(7, 4).addWall(Direction.WEST);
board.getTile(7, 4).addWall(Direction.SOUTH);
board.getTile(7, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
board.getTile(7, 5).addWall(Direction.NORTH);
board.getTile(7, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(7, 6).addWall(Direction.SOUTH);
board.getTile(7, 7).setFieldType(FieldType.REPAIR_2);
board.getTile(7, 7).addWall(Direction.WEST);
board.getTile(7, 7).addWall(Direction.NORTH);
board.getTile(7, 11).addWall(Direction.SOUTH);
board.getTile(6, 1).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 2).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 3).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 4).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 4).addWall(Direction.EAST);
board.getTile(6, 7).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(6, 7).addWall(Direction.EAST);
board.getTile(6, 8).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(6, 9).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(6, 10).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(6, 11).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(5, 0).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 1).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 2).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 3).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 4).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 4).addWall(Direction.WEST);
board.getTile(5, 7).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 7).addWall(Direction.WEST);
board.getTile(5, 8).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 9).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 10).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 11).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(4, 0).addWall(Direction.NORTH);
board.getTile(4, 4).addWall(Direction.EAST);
board.getTile(4, 4).addWall(Direction.SOUTH);
board.getTile(4, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(4, 5).addWall(Direction.NORTH);
board.getTile(4, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(4, 6).addWall(Direction.SOUTH);
board.getTile(4, 7).addWall(Direction.EAST);
board.getTile(4, 7).addWall(Direction.NORTH);
board.getTile(4, 11).addWall(Direction.SOUTH);
board.getTile(3, 0).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(3, 1).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(3, 2).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(3, 3).setGear(new Gear(RotationDirection.COUNTERCLOCKWISE));
board.getTile(3, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(3, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(3, 8).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(3, 9).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(3, 10).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(3, 11).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(2, 0).addWall(Direction.NORTH);
board.getTile(2, 3).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(2, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(2, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(2, 8).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(2, 8).addWall(Direction.SOUTH);
board.getTile(2, 9).addWall(Direction.NORTH);
board.getTile(2, 11).addWall(Direction.SOUTH);
board.getTile(1, 0).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(1, 1).setFieldType(FieldType.REPAIR_1);
board.getTile(1, 2).setFieldType(FieldType.PIT);
board.getTile(1, 3).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(1, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(1, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(1, 8).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(1, 10).setGear(new Gear(RotationDirection.CLOCKWISE));
board.getTile(1, 11).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(0, 2).addWall(Direction.WEST);
board.getTile(0, 3).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(0, 4).addWall(Direction.WEST);
board.getTile(0, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(0, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(0, 7).addWall(Direction.WEST);
board.getTile(0, 8).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(0, 9).addWall(Direction.WEST);
board.getTile(0, 10).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
if (only1Map) {
    board.getTile(9, 2).setCheckpoint(new Checkpoint(1));
    board.getTile(1, 9).setCheckpoint(new Checkpoint(2));
    } else if (numberCheckpoint == 1) {
        board.getTile(5, 6).setCheckpoint(new Checkpoint(numberCheckpoint));
        } else {
            board.getTile(5, 6).setCheckpoint(new Checkpoint(numberCheckpoint));
        }
        setLaserField(board, 2, 11, Direction.NORTH, 1);
    }

    private void generateMap3(Board board, int numberCheckpoint, boolean only1Map) {

board.getTile(11, 2).addWall(Direction.EAST);
board.getTile(11, 4).addWall(Direction.EAST);
board.getTile(11, 7).addWall(Direction.EAST);
board.getTile(11, 9).addWall(Direction.EAST);
board.getTile(10, 1).setFieldType(FieldType.PIT);
board.getTile(10, 2).setFieldType(FieldType.PIT);
board.getTile(10, 9).setFieldType(FieldType.PIT);
board.getTile(10, 10).setFieldType(FieldType.PIT);
board.getTile(9, 0).addWall(Direction.NORTH);
board.getTile(9, 1).setFieldType(FieldType.PIT);
board.getTile(9, 2).setGear(new Gear(RotationDirection.CLOCKWISE));
board.getTile(9, 3).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(9, 4).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(9, 5).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(9, 5).addWall(Direction.WEST);
board.getTile(9, 6).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(9, 7).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(9, 8).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(9, 9).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(9, 10).setFieldType(FieldType.PIT);
board.getTile(9, 11).addWall(Direction.SOUTH);
board.getTile(8, 2).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(8, 3).setGear(new Gear(RotationDirection.COUNTERCLOCKWISE));
board.getTile(8, 4).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(8, 5).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, true));
board.getTile(8, 6).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(8, 7).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(8, 8).setGear(new Gear(RotationDirection.COUNTERCLOCKWISE));
board.getTile(8, 9).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(7, 0).addWall(Direction.NORTH);
board.getTile(7, 2).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(7, 3).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(7, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(7, 6).setFieldType(FieldType.PIT);
board.getTile(7, 7).setFieldType(FieldType.PIT);
board.getTile(7, 8).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(7, 9).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(7, 10).setFieldType(FieldType.REPAIR_1);
board.getTile(7, 11).addWall(Direction.SOUTH);
board.getTile(6, 2).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(6, 2).addWall(Direction.SOUTH);
board.getTile(6, 3).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(6, 3).addWall(Direction.NORTH);
board.getTile(6, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(6, 7).setFieldType(FieldType.PIT);
board.getTile(6, 8).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(6, 8).addWall(Direction.SOUTH);
board.getTile(6, 9).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(6, 9).addWall(Direction.NORTH);
board.getTile(5, 2).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(5, 3).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(5, 4).setFieldType(FieldType.PIT);
board.getTile(5, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(5, 8).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(5, 9).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(4, 0).addWall(Direction.NORTH);
board.getTile(4, 1).setFieldType(FieldType.REPAIR_1);
board.getTile(4, 2).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(4, 3).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(4, 4).setFieldType(FieldType.PIT);
board.getTile(4, 5).setFieldType(FieldType.PIT);
board.getTile(4, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(4, 7).setFieldType(FieldType.REPAIR_2);
board.getTile(4, 8).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(4, 9).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(4, 11).addWall(Direction.SOUTH);
board.getTile(3, 2).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(3, 3).setGear(new Gear(RotationDirection.COUNTERCLOCKWISE));
board.getTile(3, 4).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(3, 5).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(3, 6).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, true));
board.getTile(3, 6).addWall(Direction.WEST);
board.getTile(3, 7).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(3, 8).setGear(new Gear(RotationDirection.COUNTERCLOCKWISE));
board.getTile(3, 9).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(2, 0).addWall(Direction.NORTH);
board.getTile(2, 1).setFieldType(FieldType.PIT);
board.getTile(2, 2).setGear(new Gear(RotationDirection.CLOCKWISE));
board.getTile(2, 3).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(2, 4).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(2, 5).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(2, 6).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(2, 6).addWall(Direction.EAST);
board.getTile(2, 7).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(2, 8).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(2, 9).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(2, 10).setFieldType(FieldType.PIT);
board.getTile(2, 11).addWall(Direction.SOUTH);
board.getTile(1, 1).setFieldType(FieldType.PIT);
board.getTile(1, 2).setFieldType(FieldType.PIT);
board.getTile(1, 9).setFieldType(FieldType.PIT);
board.getTile(1, 10).setFieldType(FieldType.PIT);
board.getTile(0, 2).addWall(Direction.WEST);
board.getTile(0, 4).addWall(Direction.WEST);
board.getTile(0, 7).addWall(Direction.WEST);
board.getTile(0, 9).addWall(Direction.WEST);
if (only1Map) {
    board.getTile(10, 4).setCheckpoint(new Checkpoint(1));
    board.getTile(1, 8).setCheckpoint(new Checkpoint(2));
    } else if (numberCheckpoint == 1) {
        board.getTile(10, 4).setCheckpoint(new Checkpoint(numberCheckpoint));
        } else {
            board.getTile(7, 4).setCheckpoint(new Checkpoint(numberCheckpoint));
        }
    }

    private void generateMap4(Board board, int numberCheckpoint, boolean only1Map) {

for (int i = 2; i < 10; i++) {
    board.getTile(11 - (i), 1).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
}
board.getTile(6, 1).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, true));
for (int i = 2; i < 9; i++) {
    board.getTile(11 - (i), 2).setConveyorBelt(new ConveyorBelt(Direction.WEST, true, null, false));
}
board.getTile(6, 2).addWall(Direction.SOUTH);
for (int i = 3; i < 8; i++) {
    board.getTile(11 - (i), 3).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
}
board.getTile(6, 3).addWall(Direction.NORTH);
board.getTile(5, 3).addWall(Direction.SOUTH);
for (int i = 4; i < 7; i++) {
    board.getTile(11 - (i), 4).setConveyorBelt(new ConveyorBelt(Direction.WEST, true, null, false));
}
board.getTile(5, 4).addWall(Direction.NORTH);
for (int i = 2; i < 10; i++) {
    board.getTile(11 - (i), 10).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
}
board.getTile(5, 10).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, true));
for (int i = 3; i < 10; i++) {
    board.getTile(11 - (i), 9).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
}
board.getTile(5, 9).addWall(Direction.NORTH);
for (int i = 4; i < 9; i++) {
    board.getTile(11 - (i), 8).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
}
board.getTile(5, 8).addWall(Direction.SOUTH);
for (int i = 5; i < 8; i++) {
    board.getTile(11 - (i), 7).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
}
board.getTile(6, 7).addWall(Direction.SOUTH);
board.getTile(6, 8).addWall(Direction.NORTH);
for (int j = 3; j < 10; j++) {
    board.getTile(10, j).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
}
board.getTile(10, 6).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, true));
for (int j = 4; j < 9; j++) {
    board.getTile(9, j).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
}
board.getTile(9, 6).addWall(Direction.WEST);
board.getTile(8, 6).addWall(Direction.EAST);
for (int j = 5; j < 8; j++) {
    board.getTile(8, j).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
}
for (int j = 2; j < 9; j++) {
    board.getTile(1, j).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
}
board.getTile(1, 5).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, true));
for (int j = 3; j < 8; j++) {
    board.getTile(2, j).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
}
for (int j = 4; j < 7; j++) {
    board.getTile(3, j).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
}
board.getTile(11, 2).setPusher(new Pusher(Direction.EAST, Set.of(4)));
board.getTile(11, 4).setPusher(new Pusher(Direction.EAST, Set.of(0)));
board.getTile(11, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(11, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, true, null, false));
board.getTile(11, 7).setPusher(new Pusher(Direction.EAST, Set.of(0)));
board.getTile(11, 9).setPusher(new Pusher(Direction.EAST, Set.of(4)));
board.getTile(11, 10).setConveyorBelt(new ConveyorBelt(Direction.WEST, true, null, false));
board.getTile(10, 0).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(10, 1).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(10, 2).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(10, 10).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, true));
board.getTile(9, 0).setPusher(new Pusher(Direction.NORTH, Set.of(4)));
board.getTile(9, 3).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(9, 9).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(9, 11).setPusher(new Pusher(Direction.SOUTH, Set.of(4)));
board.getTile(8, 4).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(8, 8).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
board.getTile(7, 0).setPusher(new Pusher(Direction.NORTH, Set.of(0)));
board.getTile(7, 5).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(7, 6).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(7, 7).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(7, 11).setPusher(new Pusher(Direction.SOUTH, Set.of(0)));
board.getTile(6, 0).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 5).setFieldType(FieldType.PIT);
board.getTile(6, 6).setFieldType(FieldType.PIT);
board.getTile(5, 0).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 5).setFieldType(FieldType.PIT);
board.getTile(5, 6).setFieldType(FieldType.PIT);
board.getTile(5, 11).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(4, 0).setPusher(new Pusher(Direction.NORTH, Set.of(0)));
board.getTile(4, 4).setConveyorBelt(new ConveyorBelt(Direction.WEST, true, null, false));
board.getTile(4, 5).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(4, 6).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(4, 11).setPusher(new Pusher(Direction.SOUTH, Set.of(0)));
board.getTile(3, 3).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(3, 7).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(2, 0).setPusher(new Pusher(Direction.NORTH, Set.of(4)));
board.getTile(2, 2).setConveyorBelt(new ConveyorBelt(Direction.WEST, true, null, false));
board.getTile(2, 8).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(2, 11).setPusher(new Pusher(Direction.SOUTH, Set.of(4)));
board.getTile(1, 1).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, true));
board.getTile(1, 9).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(1, 10).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(1, 11).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(0, 1).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(0, 2).setPusher(new Pusher(Direction.WEST, Set.of(4)));
board.getTile(0, 4).setPusher(new Pusher(Direction.WEST, Set.of(0)));
board.getTile(0, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(0, 7).setPusher(new Pusher(Direction.WEST, Set.of(0)));
board.getTile(0, 9).setPusher(new Pusher(Direction.WEST, Set.of(4)));
if (only1Map) {
    board.getTile(10, 1).setCheckpoint(new Checkpoint(1));
    board.getTile(3, 8).setCheckpoint(new Checkpoint(2));
    } else if (numberCheckpoint == 1) {
        board.getTile(10, 1).setCheckpoint(new Checkpoint(numberCheckpoint));
        } else {
            board.getTile(3, 8).setCheckpoint(new Checkpoint(numberCheckpoint));
        }
        setLaserField(board, 6, 7, Direction.NORTH, 1);
        setLaserField(board, 5, 8, Direction.NORTH, 1);
    }

    private void generateMap5(Board board, int numberCheckpoint, boolean only1Map) {

board.getTile(11, 0).setConveyorBelt(new ConveyorBelt(Direction.WEST, true, null, false));
board.getTile(11, 2).addWall(Direction.EAST);
board.getTile(11, 2).addWall(Direction.WEST);
board.getTile(11, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(11, 4).addWall(Direction.EAST);
board.getTile(11, 5).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(11, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, true));
board.getTile(11, 7).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(11, 7).addWall(Direction.EAST);
board.getTile(11, 8).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(11, 8).addWall(Direction.WEST);
board.getTile(11, 9).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(11, 9).addWall(Direction.EAST);
board.getTile(11, 10).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, true));
board.getTile(11, 10).addWall(Direction.WEST);
board.getTile(11, 11).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(10, 0).setConveyorBelt(new ConveyorBelt(Direction.WEST, true, null, true));
board.getTile(10, 1).setFieldType(FieldType.REPAIR_1);
board.getTile(10, 2).addWall(Direction.EAST);
board.getTile(10, 3).addWall(Direction.SOUTH);
board.getTile(10, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(10, 4).addWall(Direction.NORTH);
board.getTile(10, 4).addWall(Direction.SOUTH);
board.getTile(10, 5).setPusher(new Pusher(Direction.NORTH, Set.of(3)));
board.getTile(10, 5).addWall(Direction.NORTH);
board.getTile(10, 6).setPress(new Press(Set.of(1)));
board.getTile(10, 8).addWall(Direction.EAST);
board.getTile(10, 9).setFieldType(FieldType.REPAIR_2);
board.getTile(10, 9).addWall(Direction.WEST);
board.getTile(10, 9).addWall(Direction.SOUTH);
board.getTile(10, 10).addWall(Direction.NORTH);
board.getTile(10, 10).addWall(Direction.EAST);
board.getTile(10, 11).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(9, 0).setConveyorBelt(new ConveyorBelt(Direction.WEST, true, null, false));
board.getTile(9, 0).addWall(Direction.NORTH);
board.getTile(9, 1).addWall(Direction.WEST);
board.getTile(9, 1).addWall(Direction.SOUTH);
board.getTile(9, 2).addWall(Direction.NORTH);
board.getTile(9, 3).addWall(Direction.EAST);
board.getTile(9, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(9, 5).addWall(Direction.WEST);
board.getTile(9, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(9, 7).addWall(Direction.WEST);
board.getTile(9, 7).addWall(Direction.SOUTH);
board.getTile(9, 8).setPusher(new Pusher(Direction.NORTH, Set.of(2)));
board.getTile(9, 9).addWall(Direction.WEST);
board.getTile(9, 9).addWall(Direction.EAST);
board.getTile(9, 10).setPusher(new Pusher(Direction.SOUTH, Set.of(1)));
board.getTile(9, 10).addWall(Direction.SOUTH);
board.getTile(9, 11).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(9, 11).addWall(Direction.SOUTH);
board.getTile(8, 0).setConveyorBelt(new ConveyorBelt(Direction.WEST, true, null, false));
board.getTile(8, 1).addWall(Direction.EAST);
board.getTile(8, 2).addWall(Direction.SOUTH);
board.getTile(8, 2).addWall(Direction.WEST);
board.getTile(8, 3).addWall(Direction.NORTH);
board.getTile(8, 3).addWall(Direction.SOUTH);
board.getTile(8, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(8, 4).addWall(Direction.NORTH);
board.getTile(8, 5).setFieldType(FieldType.REPAIR_2);
board.getTile(8, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(8, 7).addWall(Direction.EAST);
board.getTile(8, 9).setFieldType(FieldType.PIT);
board.getTile(8, 9).addWall(Direction.EAST);
board.getTile(8, 10).addWall(Direction.WEST);
board.getTile(8, 11).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, true));
board.getTile(7, 0).setConveyorBelt(new ConveyorBelt(Direction.WEST, true, null, false));
board.getTile(7, 0).addWall(Direction.SOUTH);
board.getTile(7, 1).addWall(Direction.NORTH);
board.getTile(7, 1).addWall(Direction.WEST);
board.getTile(7, 2).addWall(Direction.EAST);
board.getTile(7, 3).addWall(Direction.WEST);
board.getTile(7, 4).setPress(new Press(Set.of(2)));
board.getTile(7, 5).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(7, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(7, 9).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
board.getTile(7, 10).addWall(Direction.EAST);
board.getTile(7, 11).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(6, 0).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, true));
board.getTile(6, 1).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(6, 1).addWall(Direction.EAST);
board.getTile(6, 2).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(6, 2).addWall(Direction.WEST);
board.getTile(6, 3).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(6, 3).addWall(Direction.EAST);
board.getTile(6, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, true));
board.getTile(6, 5).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 6).setPress(new Press(Set.of(3)));
board.getTile(6, 8).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(6, 9).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(6, 11).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(5, 1).addWall(Direction.WEST);
board.getTile(5, 2).addWall(Direction.EAST);
board.getTile(5, 5).setFieldType(FieldType.REPAIR_2);
board.getTile(5, 3).addWall(Direction.WEST);
board.getTile(5, 4).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(5, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, true));
board.getTile(5, 7).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(5, 8).setPusher(new Pusher(Direction.WEST, Set.of(2)));
board.getTile(5, 9).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(5, 10).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, false));
board.getTile(5, 11).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true, null, true));
board.getTile(4, 2).addWall(Direction.WEST);
board.getTile(4, 3).addWall(Direction.EAST);
board.getTile(4, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(4, 5).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(4, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(4, 8).addWall(Direction.WEST);
board.getTile(4, 8).addWall(Direction.EAST);
board.getTile(4, 9).addWall(Direction.EAST);
board.getTile(4, 10).addWall(Direction.WEST);
board.getTile(4, 10).addWall(Direction.SOUTH);
board.getTile(4, 11).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
board.getTile(4, 11).addWall(Direction.SOUTH);
board.getTile(3, 0).addWall(Direction.SOUTH);
board.getTile(3, 1).addWall(Direction.NORTH);
board.getTile(3, 2).addWall(Direction.EAST);
board.getTile(3, 3).addWall(Direction.SOUTH);
board.getTile(3, 4).setPress(new Press(Set.of(2)));
board.getTile(3, 4).addWall(Direction.NORTH);
board.getTile(3, 5).addWall(Direction.EAST);
board.getTile(3, 5).addWall(Direction.WEST);
board.getTile(3, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(3, 8).addWall(Direction.EAST);
board.getTile(3, 9).addWall(Direction.WEST);
board.getTile(3, 10).addWall(Direction.EAST);
board.getTile(3, 11).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
board.getTile(2, 0).addWall(Direction.NORTH);
board.getTile(2, 1).addWall(Direction.WEST);
board.getTile(2, 1).addWall(Direction.SOUTH);
board.getTile(2, 2).addWall(Direction.NORTH);
board.getTile(2, 2).addWall(Direction.SOUTH);
board.getTile(2, 3).addWall(Direction.NORTH);
board.getTile(2, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(2, 5).setFieldType(FieldType.PIT);
board.getTile(2, 5).addWall(Direction.EAST);
board.getTile(2, 6).setPress(new Press(Set.of(1)));
board.getTile(2, 7).addWall(Direction.SOUTH);
board.getTile(2, 8).addWall(Direction.NORTH);
board.getTile(2, 8).addWall(Direction.SOUTH);
board.getTile(2, 9).addWall(Direction.NORTH);
board.getTile(2, 9).addWall(Direction.EAST);
board.getTile(2, 11).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
board.getTile(2, 11).addWall(Direction.SOUTH);
board.getTile(1, 1).addWall(Direction.EAST);
board.getTile(1, 2).addWall(Direction.WEST);
board.getTile(1, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(1, 5).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(1, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(1, 9).addWall(Direction.WEST);
board.getTile(1, 9).addWall(Direction.SOUTH);
board.getTile(1, 10).setFieldType(FieldType.REPAIR_1);
board.getTile(1, 11).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, true));
board.getTile(0, 0).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(0, 1).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, true));
board.getTile(0, 2).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(0, 2).addWall(Direction.WEST);
board.getTile(0, 2).addWall(Direction.EAST);
board.getTile(0, 3).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, true, null, false));
board.getTile(0, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, true));
board.getTile(0, 4).addWall(Direction.WEST);
board.getTile(0, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(0, 6).setFieldType(FieldType.PIT);
board.getTile(0, 7).addWall(Direction.WEST);
board.getTile(0, 9).addWall(Direction.WEST);
board.getTile(0, 9).addWall(Direction.EAST);
board.getTile(0, 10).addWall(Direction.SOUTH);
board.getTile(0, 11).setConveyorBelt(new ConveyorBelt(Direction.EAST, true, null, false));
if (only1Map) {
    board.getTile(10, 2).setCheckpoint(new Checkpoint(1));
    board.getTile(1, 9).setCheckpoint(new Checkpoint(2));
    } else if (numberCheckpoint == 1) {
        board.getTile(10, 2).setCheckpoint(new Checkpoint(numberCheckpoint));
        } else {
            board.getTile(5, 5).setCheckpoint(new Checkpoint(numberCheckpoint));
        }
        setLaserField(board, 9, 9, Direction.WEST, 2);
        setLaserField(board, 8, 3, Direction.SOUTH, 1);
        setLaserField(board, 5, 1, Direction.EAST, 1);
        setLaserField(board, 4, 2, Direction.EAST, 1);
        setLaserField(board, 3, 5, Direction.WEST, 1);
        setLaserField(board, 3, 9, Direction.EAST, 1);
        setLaserField(board, 2, 2, Direction.NORTH, 2);
        setLaserField(board, 2, 8, Direction.SOUTH, 2);
        setLaserField(board, 0, 9, Direction.WEST, 3);
    }

    private void generateMap6(Board board, int numberCheckpoint, boolean only1Map) {

board.getTile(11, 0).setFieldType(FieldType.PIT);
board.getTile(11, 1).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(11, 2).addWall(Direction.WEST);
board.getTile(11, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(11, 5).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(11, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(11, 11).setFieldType(FieldType.PIT);
board.getTile(10, 0).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(10, 1).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, true));
board.getTile(10, 2).setFieldType(FieldType.PIT);
board.getTile(10, 2).addWall(Direction.EAST);
board.getTile(10, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(10, 5).setFieldType(FieldType.PIT);
board.getTile(10, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(10, 8).setFieldType(FieldType.PIT);
board.getTile(10, 9).addWall(Direction.SOUTH);
board.getTile(10, 10).addWall(Direction.NORTH);
board.getTile(9, 0).addWall(Direction.NORTH);
board.getTile(9, 1).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(9, 2).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(9, 3).addWall(Direction.WEST);
board.getTile(9, 3).addWall(Direction.SOUTH);
board.getTile(9, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(9, 4).addWall(Direction.NORTH);
board.getTile(9, 5).addWall(Direction.WEST);
board.getTile(9, 6).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(9, 7).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(9, 8).addWall(Direction.WEST);
board.getTile(9, 10).setFieldType(FieldType.REPAIR_1);
board.getTile(9, 11).addWall(Direction.SOUTH);
board.getTile(8, 0).addWall(Direction.SOUTH);
board.getTile(8, 1).setFieldType(FieldType.PIT);
board.getTile(8, 1).addWall(Direction.NORTH);
board.getTile(8, 2).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(8, 3).setFieldType(FieldType.REPAIR_2);
board.getTile(8, 3).addWall(Direction.EAST);
board.getTile(8, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(8, 5).addWall(Direction.EAST);
board.getTile(8, 6).setFieldType(FieldType.PIT);
board.getTile(8, 7).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(8, 8).setFieldType(FieldType.PIT);
board.getTile(8, 8).addWall(Direction.SOUTH);
board.getTile(8, 8).addWall(Direction.EAST);
board.getTile(8, 9).addWall(Direction.NORTH);
board.getTile(8, 10).setFieldType(FieldType.PIT);
board.getTile(8, 10).addWall(Direction.SOUTH);
board.getTile(8, 11).addWall(Direction.NORTH);
board.getTile(7, 0).addWall(Direction.NORTH);
board.getTile(7, 1).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(7, 2).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(7, 2).addWall(Direction.SOUTH);
board.getTile(7, 3).setFieldType(FieldType.PIT);
board.getTile(7, 3).addWall(Direction.NORTH);
board.getTile(7, 4).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(7, 6).addWall(Direction.WEST);
board.getTile(7, 7).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(7, 8).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(7, 11).addWall(Direction.SOUTH);
board.getTile(6, 0).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 1).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 2).setFieldType(FieldType.PIT);
board.getTile(6, 3).addWall(Direction.SOUTH);
board.getTile(6, 3).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(6, 3).addWall(Direction.NORTH);
board.getTile(6, 4).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 6).addWall(Direction.EAST);
board.getTile(6, 6).addWall(Direction.SOUTH);
board.getTile(6, 7).setFieldType(FieldType.PIT);
board.getTile(6, 7).addWall(Direction.NORTH);
board.getTile(6, 8).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(6, 9).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 10).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(6, 11).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(5, 0).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(5, 2).setFieldType(FieldType.PIT);
board.getTile(5, 3).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(5, 4).setFieldType(FieldType.PIT);
board.getTile(5, 4).addWall(Direction.WEST);
board.getTile(5, 5).setFieldType(FieldType.PIT);
board.getTile(5, 5).addWall(Direction.SOUTH);
board.getTile(5, 6).addWall(Direction.NORTH);
board.getTile(5, 6).addWall(Direction.WEST);
board.getTile(5, 7).setFieldType(FieldType.REPAIR_2);
board.getTile(5, 8).addWall(Direction.WEST);
board.getTile(5, 9).setFieldType(FieldType.PIT);
board.getTile(5, 10).addWall(Direction.WEST);
board.getTile(4, 0).addWall(Direction.NORTH);
board.getTile(4, 2).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(4, 2).addWall(Direction.EAST);
board.getTile(4, 3).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(4, 4).addWall(Direction.EAST);
board.getTile(4, 6).addWall(Direction.EAST);
board.getTile(4, 8).addWall(Direction.EAST);
board.getTile(4, 10).addWall(Direction.EAST);
board.getTile(4, 10).addWall(Direction.SOUTH);
board.getTile(4, 11).addWall(Direction.SOUTH);
board.getTile(3, 1).setFieldType(FieldType.PIT);
board.getTile(3, 2).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(3, 3).setFieldType(FieldType.PIT);
board.getTile(3, 5).setFieldType(FieldType.PIT);
board.getTile(3, 5).addWall(Direction.WEST);
board.getTile(3, 10).setFieldType(FieldType.REPAIR_2);
board.getTile(3, 7).setFieldType(FieldType.PIT);
board.getTile(3, 7).addWall(Direction.WEST);
board.getTile(3, 9).setFieldType(FieldType.PIT);
board.getTile(3, 9).addWall(Direction.WEST);
board.getTile(3, 10).addWall(Direction.WEST);
board.getTile(2, 0).addWall(Direction.NORTH);
board.getTile(2, 1).setFieldType(FieldType.REPAIR_1);
board.getTile(2, 2).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(2, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(2, 5).addWall(Direction.EAST);
board.getTile(2, 6).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(2, 7).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(2, 7).addWall(Direction.EAST);
board.getTile(2, 9).addWall(Direction.EAST);
board.getTile(2, 10).addWall(Direction.EAST);
board.getTile(2, 10).addWall(Direction.SOUTH);
board.getTile(2, 11).addWall(Direction.NORTH);
board.getTile(2, 11).addWall(Direction.SOUTH);
board.getTile(1, 1).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(1, 2).setConveyorBelt(new ConveyorBelt(Direction.SOUTH, false, null, false));
board.getTile(1, 2).addWall(Direction.WEST);
board.getTile(1, 3).addWall(Direction.SOUTH);
board.getTile(1, 4).setFieldType(FieldType.PIT);
board.getTile(1, 4).addWall(Direction.NORTH);
board.getTile(1, 4).addWall(Direction.SOUTH);
board.getTile(1, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(1, 5).addWall(Direction.NORTH);
board.getTile(1, 6).setFieldType(FieldType.PIT);
board.getTile(1, 7).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(1, 8).setFieldType(FieldType.PIT);
board.getTile(1, 9).setFieldType(FieldType.PIT);
board.getTile(1, 10).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(1, 11).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(0, 1).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(0, 2).addWall(Direction.WEST);
board.getTile(0, 2).addWall(Direction.EAST);
board.getTile(0, 4).addWall(Direction.WEST);
board.getTile(0, 5).setConveyorBelt(new ConveyorBelt(Direction.EAST, false, null, false));
board.getTile(0, 6).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false, null, false));
board.getTile(0, 7).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(0, 7).addWall(Direction.WEST);
board.getTile(0, 8).addWall(Direction.EAST);
board.getTile(0, 9).addWall(Direction.WEST);
board.getTile(0, 10).setConveyorBelt(new ConveyorBelt(Direction.WEST, false, null, false));
board.getTile(0, 11).setFieldType(FieldType.PIT);
if (only1Map) {
    board.getTile(10, 2).setCheckpoint(new Checkpoint(1));
    board.getTile(0, 9).setCheckpoint(new Checkpoint(2));
    } else if (numberCheckpoint == 1) {
        board.getTile(10, 2).setCheckpoint(new Checkpoint(numberCheckpoint));
        } else {
            board.getTile(6, 7).setCheckpoint(new Checkpoint(numberCheckpoint));
        }
        setLaserField(board, 8, 5, Direction.WEST, 1);
    }

}
