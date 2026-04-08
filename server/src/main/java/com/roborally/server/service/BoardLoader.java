package com.roborally.server.service;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.FieldType;
import com.roborally.common.enums.RotationDirection;
import com.roborally.server.model.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BoardLoader {

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
        return board;
    }

    private enum LegacyOrientation {
        LEFT, RIGHT, TOP, BOTTOM
    }

    private int legacyScreenY(Board board, int legacyY) {
        return board.getHeight() - 1 - legacyY;
    }

    private Tile legacyTile(Board board, int legacyX, int legacyY) {
        return board.getTile(legacyX, legacyScreenY(board, legacyY));
    }

    private Tile replaceLegacyTile(Board board, int legacyX, int legacyY, FieldType type) {
        int screenY = legacyScreenY(board, legacyY);
        Tile tile = new Tile(legacyX, screenY, type);
        board.setTile(legacyX, screenY, tile);
        return tile;
    }

    private Direction legacyDirection(LegacyOrientation orientation) {
        return switch (orientation) {
            case LEFT -> Direction.WEST;
            case RIGHT -> Direction.EAST;
            case TOP -> Direction.NORTH;
            case BOTTOM -> Direction.SOUTH;
        };
    }

    private RotationDirection legacyGearRotation(LegacyOrientation orientation) {
        return switch (orientation) {
            case LEFT -> RotationDirection.COUNTERCLOCKWISE;
            case RIGHT -> RotationDirection.CLOCKWISE;
            default -> throw new IllegalArgumentException("Unsupported gear orientation: " + orientation);
        };
    }

    private RotationDirection legacyCurveRotation(int legacyCurve) {
        return legacyCurve == 2 ? RotationDirection.CLOCKWISE : RotationDirection.COUNTERCLOCKWISE;
    }

    private String legacyCrossingType(int legacyCrossing) {
        return switch (legacyCrossing) {
            case 2 -> "RIGHT";
            case 3 -> "LEFTRIGHT";
            default -> "LEFT";
        };
    }

    private void legacyPit(Board board, int legacyX, int legacyY) {
        replaceLegacyTile(board, legacyX, legacyY, FieldType.PIT);
    }

    private void legacyRepair(Board board, int legacyX, int legacyY, int level) {
        replaceLegacyTile(board, legacyX, legacyY, level == 2 ? FieldType.REPAIR_2 : FieldType.REPAIR_1);
    }

    private void legacyCheckpoint(Board board, int legacyX, int legacyY, int number) {
        replaceLegacyTile(board, legacyX, legacyY, FieldType.FLOOR).setCheckpoint(new Checkpoint(number));
    }

    private void legacyConveyor(Board board, int legacyX, int legacyY, LegacyOrientation orientation, boolean express) {
        replaceLegacyTile(board, legacyX, legacyY, FieldType.FLOOR)
                .setConveyorBelt(new ConveyorBelt(legacyDirection(orientation), express));
    }

    private void legacyCurve(Board board, int legacyX, int legacyY, LegacyOrientation orientation, int legacyCurve, boolean express) {
        ConveyorBelt belt = new ConveyorBelt(legacyDirection(orientation), express);
        belt.setCurveRotation(legacyCurveRotation(legacyCurve));
        replaceLegacyTile(board, legacyX, legacyY, FieldType.FLOOR).setConveyorBelt(belt);
    }

    private void legacyCrossing(Board board, int legacyX, int legacyY, LegacyOrientation orientation, int legacyCrossing, boolean express) {
        ConveyorBelt belt = new ConveyorBelt(legacyDirection(orientation), express);
        belt.setCrossingType(legacyCrossingType(legacyCrossing));
        replaceLegacyTile(board, legacyX, legacyY, FieldType.FLOOR).setConveyorBelt(belt);
    }

    private void legacyGear(Board board, int legacyX, int legacyY, LegacyOrientation orientation) {
        replaceLegacyTile(board, legacyX, legacyY, FieldType.FLOOR)
                .setGear(new Gear(legacyGearRotation(orientation)));
    }

    private void legacyPusher(Board board, int legacyX, int legacyY, LegacyOrientation orientation, int activeStep) {
        replaceLegacyTile(board, legacyX, legacyY, FieldType.FLOOR)
                .setPusher(new Pusher(legacyDirection(orientation), Set.of(activeStep)));
    }

    private void legacyPress(Board board, int legacyX, int legacyY, int activeStep) {
        replaceLegacyTile(board, legacyX, legacyY, FieldType.FLOOR)
                .setPress(new Press(Set.of(activeStep)));
    }

    private void legacyWall(Board board, int legacyX, int legacyY, LegacyOrientation orientation) {
        legacyTile(board, legacyX, legacyY).addWall(legacyDirection(orientation));
    }

    private void legacyLaser(Board board, int legacyX, int legacyY, LegacyOrientation orientation, int power) {
        int screenY = legacyScreenY(board, legacyY);
        replaceLegacyTile(board, legacyX, legacyY, FieldType.FLOOR);
        board.addLaser(new Laser(legacyX, screenY, legacyDirection(orientation), power));
    }

    private void legacyMirrorWalls(Board board) {
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                Tile tile = board.getTile(x, y);
                if (tile == null) continue;
                for (Direction wall : tile.getWalls()) {
                    Tile neighbor = board.getTile(x + wall.dx(), y + wall.dy());
                    if (neighbor != null) {
                        neighbor.addWall(wall.opposite());
                    }
                }
            }
        }
    }

    private void generateMap1(Board board, int numberCheckpoint, boolean only1Map) {
                legacyWall(board, 2, 0, LegacyOrientation.BOTTOM);
                legacyWall(board, 4, 0, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 5, 0, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 6, 0, LegacyOrientation.TOP, false);
                legacyWall(board, 7, 0, LegacyOrientation.BOTTOM);
                legacyWall(board, 9, 0, LegacyOrientation.BOTTOM);
                legacyCurve(board, 10, 0, LegacyOrientation.TOP, 2, false);
                legacyCurve(board, 11, 0, LegacyOrientation.RIGHT, 1, false);
                legacyConveyor(board, 0, 1, LegacyOrientation.RIGHT, false);
                legacyCurve(board, 1, 1, LegacyOrientation.RIGHT, 1, false);
                legacyWall(board, 3, 1, LegacyOrientation.RIGHT);
                legacyWall(board, 4, 1, LegacyOrientation.LEFT);
                legacyConveyor(board, 5, 1, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 6, 1, LegacyOrientation.TOP, false);
                legacyWall(board, 7, 1, LegacyOrientation.RIGHT);
                legacyWall(board, 8, 1, LegacyOrientation.LEFT);
                legacyRepair(board, 10, 1, 1);
                legacyWall(board, 10, 1, LegacyOrientation.TOP);
                legacyCurve(board, 11, 1, LegacyOrientation.TOP, 2, false);
                legacyWall(board, 0, 2, LegacyOrientation.LEFT);
                legacyConveyor(board, 1, 2, LegacyOrientation.TOP, false);
                legacyPit(board, 3, 2);
                legacyWall(board, 3, 2, LegacyOrientation.TOP);
                legacyConveyor(board, 5, 2, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 6, 2, LegacyOrientation.TOP, false);
                legacyWall(board, 6, 2, LegacyOrientation.RIGHT);
                legacyWall(board, 7, 2, LegacyOrientation.LEFT);
                legacyPit(board, 9, 2);
                legacyWall(board, 10, 2, LegacyOrientation.RIGHT);
                legacyWall(board, 10, 2, LegacyOrientation.BOTTOM);
                legacyWall(board, 11, 2, LegacyOrientation.LEFT);
                legacyWall(board, 11, 2, LegacyOrientation.RIGHT);
                legacyWall(board, 0, 3, LegacyOrientation.TOP);
                legacyConveyor(board, 1, 3, LegacyOrientation.TOP, false);
                legacyWall(board, 2, 3, LegacyOrientation.TOP);
                legacyWall(board, 3, 3, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 5, 3, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 6, 3, LegacyOrientation.TOP, false);
                legacyWall(board, 7, 3, LegacyOrientation.RIGHT);
                legacyWall(board, 8, 3, LegacyOrientation.LEFT);
                legacyWall(board, 10, 3, LegacyOrientation.RIGHT);
                legacyWall(board, 11, 3, LegacyOrientation.LEFT);
                legacyWall(board, 0, 4, LegacyOrientation.LEFT);
                legacyWall(board, 0, 4, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 1, 4, LegacyOrientation.TOP, false);
                legacyWall(board, 2, 4, LegacyOrientation.BOTTOM);
                legacyWall(board, 3, 4, LegacyOrientation.TOP);
                legacyWall(board, 4, 4, LegacyOrientation.TOP);
                legacyWall(board, 4, 4, LegacyOrientation.RIGHT);
                legacyConveyor(board, 5, 4, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 5, 4, LegacyOrientation.LEFT);
                legacyCurve(board, 6, 4, LegacyOrientation.TOP, 2, false);
                legacyCurve(board, 7, 4, LegacyOrientation.RIGHT, 1, false);
                legacyWall(board, 7, 4, LegacyOrientation.RIGHT);
                legacyWall(board, 8, 4, LegacyOrientation.LEFT);
                legacyWall(board, 8, 4, LegacyOrientation.TOP);
                legacyWall(board, 9, 4, LegacyOrientation.TOP);
                legacyWall(board, 11, 4, LegacyOrientation.RIGHT);
                legacyConveyor(board, 0, 5, LegacyOrientation.RIGHT, false);
                legacyCrossing(board, 1, 5, LegacyOrientation.RIGHT, 2, false);
                legacyConveyor(board, 2, 5, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 3, 5, LegacyOrientation.RIGHT, false);
                legacyWall(board, 3, 5, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 4, 5, LegacyOrientation.RIGHT, false);
                legacyWall(board, 4, 5, LegacyOrientation.BOTTOM);
                legacyCurve(board, 5, 5, LegacyOrientation.RIGHT, 2, false);
                legacyPit(board, 6, 5);
                legacyCurve(board, 7, 5, LegacyOrientation.TOP, 2, false);
                legacyConveyor(board, 8, 5, LegacyOrientation.RIGHT, false);
                legacyWall(board, 8, 5, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 9, 5, LegacyOrientation.RIGHT, false);
                legacyWall(board, 9, 5, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 10, 5, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 11, 5, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 0, 6, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 1, 6, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 2, 6, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 3, 6, LegacyOrientation.LEFT, false);
                legacyCurve(board, 4, 6, LegacyOrientation.BOTTOM, 2, false);
                legacyPit(board, 5, 6);
                legacyPit(board, 6, 6);
                legacyPit(board, 7, 6);
                legacyCurve(board, 8, 6, LegacyOrientation.LEFT, 2, false);
                legacyConveyor(board, 9, 6, LegacyOrientation.LEFT, false);
                legacyCrossing(board, 10, 6, LegacyOrientation.LEFT, 2, false);
                legacyConveyor(board, 11, 6, LegacyOrientation.LEFT, false);
                legacyWall(board, 0, 7, LegacyOrientation.LEFT);
                legacyWall(board, 3, 7, LegacyOrientation.TOP);
                legacyWall(board, 3, 7, LegacyOrientation.RIGHT);
                legacyCurve(board, 4, 7, LegacyOrientation.LEFT, 1, false);
                legacyWall(board, 4, 7, LegacyOrientation.LEFT);
                legacyCurve(board, 5, 7, LegacyOrientation.BOTTOM, 2, false);
                legacyPit(board, 6, 7);
                legacyCurve(board, 7, 7, LegacyOrientation.LEFT, 2, false);
                legacyCurve(board, 8, 7, LegacyOrientation.TOP, 1, false);
                legacyWall(board, 8, 7, LegacyOrientation.TOP);
                legacyWall(board, 8, 7, LegacyOrientation.RIGHT);
                legacyWall(board, 9, 7, LegacyOrientation.LEFT);
                legacyConveyor(board, 10, 7, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 11, 7, LegacyOrientation.RIGHT);
                legacyWall(board, 3, 8, LegacyOrientation.BOTTOM);
                legacyWall(board, 3, 8, LegacyOrientation.RIGHT);
                legacyWall(board, 4, 8, LegacyOrientation.LEFT);
                legacyConveyor(board, 5, 8, LegacyOrientation.BOTTOM, false);
                legacyCurve(board, 6, 8, LegacyOrientation.LEFT, 2, false);
                legacyCurve(board, 7, 8, LegacyOrientation.TOP, 1, false);
                legacyWall(board, 7, 8, LegacyOrientation.TOP);
                legacyWall(board, 7, 8, LegacyOrientation.RIGHT);
                legacyWall(board, 8, 8, LegacyOrientation.LEFT);
                legacyWall(board, 8, 8, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 10, 8, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 0, 9, LegacyOrientation.LEFT);
                legacyPit(board, 1, 9);
                legacyWall(board, 1, 9, LegacyOrientation.RIGHT);
                legacyWall(board, 2, 9, LegacyOrientation.LEFT);
                legacyWall(board, 3, 9, LegacyOrientation.TOP);
                legacyWall(board, 4, 9, LegacyOrientation.RIGHT);
                legacyConveyor(board, 5, 9, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 5, 9, LegacyOrientation.LEFT);
                legacyConveyor(board, 6, 9, LegacyOrientation.TOP, false);
                legacyPit(board, 7, 9);
                legacyWall(board, 7, 9, LegacyOrientation.BOTTOM);
                legacyWall(board, 8, 9, LegacyOrientation.TOP);
                legacyRepair(board, 9, 9, 2);
                legacyConveyor(board, 10, 9, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 11, 9, LegacyOrientation.RIGHT);
                legacyConveyor(board, 0, 10, LegacyOrientation.LEFT, false);
                legacyCurve(board, 1, 10, LegacyOrientation.BOTTOM, 2, false);
                legacyWall(board, 3, 10, LegacyOrientation.RIGHT);
                legacyWall(board, 3, 10, LegacyOrientation.BOTTOM);
                legacyWall(board, 4, 10, LegacyOrientation.LEFT);
                legacyConveyor(board, 5, 10, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 6, 10, LegacyOrientation.TOP, false);
                legacyPit(board, 7, 10);
                legacyWall(board, 7, 10, LegacyOrientation.RIGHT);
                legacyWall(board, 8, 10, LegacyOrientation.LEFT);
                legacyWall(board, 8, 10, LegacyOrientation.BOTTOM);
                legacyCurve(board, 10, 10, LegacyOrientation.LEFT, 1, false);
                legacyConveyor(board, 11, 10, LegacyOrientation.LEFT, false);
                legacyPit(board, 0, 11);
                legacyConveyor(board, 1, 11, LegacyOrientation.BOTTOM, false);
                legacyRepair(board, 3, 10, 1);
                legacyWall(board, 2, 11, LegacyOrientation.TOP);
                legacyWall(board, 4, 11, LegacyOrientation.TOP);
                legacyConveyor(board, 5, 11, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 6, 11, LegacyOrientation.TOP, false);
                legacyWall(board, 7, 11, LegacyOrientation.TOP);
                legacyWall(board, 9, 11, LegacyOrientation.TOP);
                legacyCheckpoint(board, 1, 7, 1);
                if (numberCheckpoint == 1) {
                        if (only1Map) {
                                legacyCheckpoint(board, 8, 4, 2);
                        }
                }
                legacyMirrorWalls(board);
                legacyLaser(board, 8, 3, LegacyOrientation.RIGHT, 2);
                legacyLaser(board, 3, 4, LegacyOrientation.BOTTOM, 1);
                legacyLaser(board, 9, 7, LegacyOrientation.RIGHT, 1);
                legacyLaser(board, 3, 9, LegacyOrientation.BOTTOM, 1);
                legacyMirrorWalls(board);
    }

    private void generateMap2(Board board, int numberCheckpoint, boolean only1Map) {
                legacyWall(board, 1, 0, LegacyOrientation.TOP);
                legacyWall(board, 2, 0, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 3, 0, LegacyOrientation.TOP, false);
                legacyWall(board, 4, 0, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 5, 0, LegacyOrientation.BOTTOM, true);
                legacyWall(board, 7, 0, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 8, 0, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 9, 0, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 10, 0, LegacyOrientation.TOP, false);
                legacyPit(board, 0, 1);
                legacyWall(board, 1, 1, LegacyOrientation.BOTTOM);
                legacyWall(board, 2, 1, LegacyOrientation.RIGHT);
                legacyConveyor(board, 3, 1, LegacyOrientation.TOP, false);
                legacyWall(board, 3, 1, LegacyOrientation.LEFT);
                legacyConveyor(board, 5, 1, LegacyOrientation.BOTTOM, true);
                legacyConveyor(board, 6, 1, LegacyOrientation.TOP, false);
                legacyConveyor(board, 8, 1, LegacyOrientation.BOTTOM, false);
                legacyRepair(board, 10, 1, 1);
                legacyConveyor(board, 11, 1, LegacyOrientation.RIGHT, false);
                legacyWall(board, 0, 2, LegacyOrientation.LEFT);
                legacyConveyor(board, 3, 2, LegacyOrientation.TOP, false);
                legacyConveyor(board, 5, 2, LegacyOrientation.BOTTOM, true);
                legacyConveyor(board, 6, 2, LegacyOrientation.TOP, false);
                legacyConveyor(board, 8, 2, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 10, 2, LegacyOrientation.TOP);
                legacyWall(board, 11, 2, LegacyOrientation.RIGHT);
                legacyConveyor(board, 0, 3, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 1, 3, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 2, 3, LegacyOrientation.LEFT, false);
                legacyGear(board, 3, 3, LegacyOrientation.LEFT);
                legacyConveyor(board, 5, 3, LegacyOrientation.BOTTOM, true);
                legacyConveyor(board, 6, 3, LegacyOrientation.TOP, false);
                legacyGear(board, 8, 3, LegacyOrientation.LEFT);
                legacyConveyor(board, 9, 3, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 10, 3, LegacyOrientation.LEFT, false);
                legacyWall(board, 10, 3, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 11, 3, LegacyOrientation.LEFT, false);
                legacyWall(board, 0, 4, LegacyOrientation.LEFT);
                legacyWall(board, 4, 4, LegacyOrientation.TOP);
                legacyWall(board, 4, 4, LegacyOrientation.RIGHT);
                legacyConveyor(board, 5, 4, LegacyOrientation.BOTTOM, true);
                legacyWall(board, 5, 4, LegacyOrientation.LEFT);
                legacyConveyor(board, 6, 4, LegacyOrientation.TOP, false);
                legacyWall(board, 6, 4, LegacyOrientation.RIGHT);
                legacyRepair(board, 7, 4, 2);
                legacyWall(board, 7, 4, LegacyOrientation.TOP);
                legacyWall(board, 7, 4, LegacyOrientation.LEFT);
                legacyWall(board, 11, 4, LegacyOrientation.RIGHT);
                legacyConveyor(board, 1, 5, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 2, 5, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 3, 5, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 4, 5, LegacyOrientation.RIGHT, false);
                legacyWall(board, 4, 5, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 7, 5, LegacyOrientation.RIGHT, true);
                legacyWall(board, 7, 5, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 8, 5, LegacyOrientation.RIGHT, true);
                legacyConveyor(board, 9, 5, LegacyOrientation.RIGHT, true);
                legacyConveyor(board, 10, 5, LegacyOrientation.RIGHT, true);
                legacyConveyor(board, 11, 5, LegacyOrientation.RIGHT, true);
                legacyConveyor(board, 0, 6, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 1, 6, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 2, 6, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 3, 6, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 4, 6, LegacyOrientation.LEFT, false);
                legacyWall(board, 4, 6, LegacyOrientation.TOP);
                legacyConveyor(board, 7, 6, LegacyOrientation.LEFT, false);
                legacyWall(board, 7, 6, LegacyOrientation.TOP);
                legacyConveyor(board, 8, 6, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 9, 6, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 10, 6, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 11, 6, LegacyOrientation.LEFT, false);
                legacyWall(board, 0, 7, LegacyOrientation.LEFT);
                legacyWall(board, 4, 7, LegacyOrientation.BOTTOM);
                legacyWall(board, 4, 7, LegacyOrientation.RIGHT);
                legacyConveyor(board, 5, 7, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 5, 7, LegacyOrientation.LEFT);
                legacyConveyor(board, 6, 7, LegacyOrientation.TOP, false);
                legacyWall(board, 6, 7, LegacyOrientation.RIGHT);
                legacyWall(board, 7, 7, LegacyOrientation.BOTTOM);
                legacyWall(board, 7, 7, LegacyOrientation.LEFT);
                legacyWall(board, 11, 7, LegacyOrientation.RIGHT);
                legacyConveyor(board, 0, 8, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 1, 8, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 2, 8, LegacyOrientation.RIGHT, false);
                legacyGear(board, 3, 8, LegacyOrientation.LEFT);
                legacyConveyor(board, 5, 8, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 6, 8, LegacyOrientation.TOP, false);
                legacyConveyor(board, 8, 8, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 9, 8, LegacyOrientation.RIGHT, true);
                legacyConveyor(board, 10, 8, LegacyOrientation.RIGHT, true);
                legacyConveyor(board, 11, 8, LegacyOrientation.RIGHT, true);
                legacyWall(board, 0, 9, LegacyOrientation.LEFT);
                legacyConveyor(board, 3, 9, LegacyOrientation.TOP, false);
                legacyConveyor(board, 5, 9, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 6, 9, LegacyOrientation.TOP, false);
                legacyConveyor(board, 8, 9, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 8, 9, LegacyOrientation.RIGHT);
                legacyWall(board, 9, 9, LegacyOrientation.LEFT);
                legacyWall(board, 11, 9, LegacyOrientation.RIGHT);
                legacyConveyor(board, 0, 10, LegacyOrientation.LEFT, false);
                legacyRepair(board, 1, 10, 1);
                legacyPit(board, 2, 10);
                legacyConveyor(board, 3, 10, LegacyOrientation.TOP, false);
                legacyConveyor(board, 5, 10, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 6, 10, LegacyOrientation.TOP, false);
                legacyConveyor(board, 8, 10, LegacyOrientation.BOTTOM, false);
                legacyGear(board, 10, 10, LegacyOrientation.RIGHT);
                legacyConveyor(board, 11, 10, LegacyOrientation.LEFT, false);
                legacyWall(board, 2, 11, LegacyOrientation.TOP);
                legacyConveyor(board, 3, 11, LegacyOrientation.TOP, false);
                legacyWall(board, 4, 11, LegacyOrientation.TOP);
                legacyConveyor(board, 5, 11, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 6, 11, LegacyOrientation.TOP, false);
                legacyWall(board, 7, 11, LegacyOrientation.TOP);
                legacyConveyor(board, 8, 11, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 9, 11, LegacyOrientation.TOP);
                legacyConveyor(board, 10, 11, LegacyOrientation.TOP, false);
                if (only1Map) {
                        legacyCheckpoint(board, 2, 2, 1);
                        legacyCheckpoint(board, 9, 10, 2);
                } else if (numberCheckpoint == 1) {
                        legacyCheckpoint(board, 6, 6, numberCheckpoint);
                } else {
                        legacyCheckpoint(board, 6, 6, numberCheckpoint);
                }
                legacyMirrorWalls(board);
                legacyLaser(board, 11, 9, LegacyOrientation.LEFT, 1);
                legacyMirrorWalls(board);
    }

    private void generateMap3(Board board, int numberCheckpoint, boolean only1Map) {
                legacyWall(board, 2, 0, LegacyOrientation.BOTTOM);
                legacyWall(board, 4, 0, LegacyOrientation.BOTTOM);
                legacyWall(board, 7, 0, LegacyOrientation.BOTTOM);
                legacyWall(board, 9, 0, LegacyOrientation.BOTTOM);
                legacyPit(board, 1, 1);
                legacyPit(board, 2, 1);
                legacyPit(board, 9, 1);
                legacyPit(board, 10, 1);
                legacyWall(board, 0, 2, LegacyOrientation.LEFT);
                legacyPit(board, 1, 2);
                legacyGear(board, 2, 2, LegacyOrientation.RIGHT);
                legacyConveyor(board, 3, 2, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 4, 2, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 5, 2, LegacyOrientation.LEFT, false);
                legacyWall(board, 5, 2, LegacyOrientation.TOP);
                legacyConveyor(board, 6, 2, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 7, 2, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 8, 2, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 9, 2, LegacyOrientation.BOTTOM, false);
                legacyPit(board, 10, 2);
                legacyWall(board, 11, 2, LegacyOrientation.RIGHT);
                legacyConveyor(board, 2, 3, LegacyOrientation.TOP, false);
                legacyGear(board, 3, 3, LegacyOrientation.LEFT);
                legacyConveyor(board, 4, 3, LegacyOrientation.RIGHT, false);
                legacyCrossing(board, 5, 3, LegacyOrientation.RIGHT, 1, false);
                legacyConveyor(board, 6, 3, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 7, 3, LegacyOrientation.RIGHT, false);
                legacyGear(board, 8, 3, LegacyOrientation.LEFT);
                legacyConveyor(board, 9, 3, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 0, 4, LegacyOrientation.LEFT);
                legacyConveyor(board, 2, 4, LegacyOrientation.TOP, false);
                legacyConveyor(board, 3, 4, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 5, 4, LegacyOrientation.BOTTOM, false);
                legacyPit(board, 6, 4);
                legacyPit(board, 7, 4);
                legacyConveyor(board, 8, 4, LegacyOrientation.TOP, false);
                legacyConveyor(board, 9, 4, LegacyOrientation.BOTTOM, false);
                legacyRepair(board, 10, 4, 1);
                legacyWall(board, 11, 4, LegacyOrientation.RIGHT);
                legacyConveyor(board, 2, 5, LegacyOrientation.TOP, false);
                legacyWall(board, 2, 5, LegacyOrientation.RIGHT);
                legacyConveyor(board, 3, 5, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 3, 5, LegacyOrientation.LEFT);
                legacyConveyor(board, 5, 5, LegacyOrientation.BOTTOM, false);
                legacyPit(board, 7, 5);
                legacyConveyor(board, 8, 5, LegacyOrientation.TOP, false);
                legacyWall(board, 8, 5, LegacyOrientation.RIGHT);
                legacyConveyor(board, 9, 5, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 9, 5, LegacyOrientation.LEFT);
                legacyConveyor(board, 2, 6, LegacyOrientation.TOP, false);
                legacyConveyor(board, 3, 6, LegacyOrientation.BOTTOM, false);
                legacyPit(board, 4, 6);
                legacyConveyor(board, 6, 6, LegacyOrientation.TOP, false);
                legacyConveyor(board, 8, 6, LegacyOrientation.TOP, false);
                legacyConveyor(board, 9, 6, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 0, 7, LegacyOrientation.LEFT);
                legacyRepair(board, 1, 7, 1);
                legacyConveyor(board, 2, 7, LegacyOrientation.TOP, false);
                legacyConveyor(board, 3, 7, LegacyOrientation.BOTTOM, false);
                legacyPit(board, 4, 7);
                legacyPit(board, 5, 7);
                legacyConveyor(board, 6, 7, LegacyOrientation.TOP, false);
                legacyRepair(board, 7, 7, 2);
                legacyConveyor(board, 8, 7, LegacyOrientation.TOP, false);
                legacyConveyor(board, 9, 7, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 11, 7, LegacyOrientation.RIGHT);
                legacyConveyor(board, 2, 8, LegacyOrientation.TOP, false);
                legacyGear(board, 3, 8, LegacyOrientation.LEFT);
                legacyConveyor(board, 4, 8, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 5, 8, LegacyOrientation.LEFT, false);
                legacyCrossing(board, 6, 8, LegacyOrientation.LEFT, 1, false);
                legacyWall(board, 6, 8, LegacyOrientation.TOP);
                legacyConveyor(board, 7, 8, LegacyOrientation.LEFT, false);
                legacyGear(board, 8, 8, LegacyOrientation.LEFT);
                legacyConveyor(board, 9, 8, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 0, 9, LegacyOrientation.LEFT);
                legacyPit(board, 1, 9);
                legacyGear(board, 2, 9, LegacyOrientation.RIGHT);
                legacyConveyor(board, 3, 9, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 4, 9, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 5, 9, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 6, 9, LegacyOrientation.RIGHT, false);
                legacyWall(board, 6, 9, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 7, 9, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 8, 9, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 9, 9, LegacyOrientation.RIGHT, false);
                legacyPit(board, 10, 9);
                legacyWall(board, 11, 9, LegacyOrientation.RIGHT);
                legacyPit(board, 1, 10);
                legacyPit(board, 2, 10);
                legacyPit(board, 9, 10);
                legacyPit(board, 10, 10);
                legacyWall(board, 2, 11, LegacyOrientation.TOP);
                legacyWall(board, 4, 11, LegacyOrientation.TOP);
                legacyWall(board, 7, 11, LegacyOrientation.TOP);
                legacyWall(board, 9, 11, LegacyOrientation.TOP);
                if (only1Map) {
                        legacyCheckpoint(board, 4, 1, 1);
                        legacyCheckpoint(board, 8, 10, 2);
                } else if (numberCheckpoint == 1) {
                        legacyCheckpoint(board, 4, 1, numberCheckpoint);
                } else {
                        legacyCheckpoint(board, 4, 4, numberCheckpoint);
                }
                legacyMirrorWalls(board);
    }

    private void generateMap4(Board board, int numberCheckpoint, boolean only1Map) {
                for (int i = 2; i < 10; i++) {
                        legacyConveyor(board, 1, i, LegacyOrientation.TOP, false);
                }
                legacyCrossing(board, 1, 5, LegacyOrientation.TOP, 1, false);
                for (int i = 2; i < 9; i++) {
                        legacyConveyor(board, 2, i, LegacyOrientation.TOP, true);
                }
                legacyWall(board, 2, 5, LegacyOrientation.RIGHT);
                for (int i = 3; i < 8; i++) {
                        legacyConveyor(board, 3, i, LegacyOrientation.TOP, false);
                }
                legacyWall(board, 3, 5, LegacyOrientation.LEFT);
                legacyWall(board, 3, 6, LegacyOrientation.RIGHT);
                for (int i = 4; i < 7; i++) {
                        legacyConveyor(board, 4, i, LegacyOrientation.TOP, true);
                }
                legacyWall(board, 4, 6, LegacyOrientation.LEFT);
                for (int i = 2; i < 10; i++) {
                        legacyConveyor(board, 10, i, LegacyOrientation.BOTTOM, true);
                }
                legacyCrossing(board, 10, 6, LegacyOrientation.BOTTOM, 1, true);
                for (int i = 3; i < 10; i++) {
                        legacyConveyor(board, 9, i, LegacyOrientation.BOTTOM, false);
                }
                legacyWall(board, 9, 6, LegacyOrientation.LEFT);
                for (int i = 4; i < 9; i++) {
                        legacyConveyor(board, 8, i, LegacyOrientation.BOTTOM, true);
                }
                legacyWall(board, 8, 6, LegacyOrientation.RIGHT);
                for (int i = 5; i < 8; i++) {
                        legacyConveyor(board, 7, i, LegacyOrientation.BOTTOM, false);
                }
                legacyWall(board, 7, 5, LegacyOrientation.RIGHT);
                legacyWall(board, 8, 5, LegacyOrientation.LEFT);
                for (int j = 3; j < 10; j++) {
                        legacyConveyor(board, j, 1, LegacyOrientation.LEFT, true);
                }
                legacyCrossing(board, 6, 1, LegacyOrientation.LEFT, 1, true);
                for (int j = 4; j < 9; j++) {
                        legacyConveyor(board, j, 2, LegacyOrientation.LEFT, false);
                }
                legacyWall(board, 6, 2, LegacyOrientation.TOP);
                legacyWall(board, 6, 3, LegacyOrientation.BOTTOM);
                for (int j = 5; j < 8; j++) {
                        legacyConveyor(board, j, 3, LegacyOrientation.LEFT, true);
                }
                for (int j = 2; j < 9; j++) {
                        legacyConveyor(board, j, 10, LegacyOrientation.RIGHT, false);
                }
                legacyCrossing(board, 5, 10, LegacyOrientation.RIGHT, 1, false);
                for (int j = 3; j < 8; j++) {
                        legacyConveyor(board, j, 9, LegacyOrientation.RIGHT, true);
                }
                for (int j = 4; j < 7; j++) {
                        legacyConveyor(board, j, 8, LegacyOrientation.RIGHT, false);
                }
                legacyPusher(board, 2, 0, LegacyOrientation.BOTTOM, 4);
                legacyPusher(board, 4, 0, LegacyOrientation.BOTTOM, 0);
                legacyConveyor(board, 5, 0, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 6, 0, LegacyOrientation.TOP, true);
                legacyPusher(board, 7, 0, LegacyOrientation.BOTTOM, 0);
                legacyPusher(board, 9, 0, LegacyOrientation.BOTTOM, 4);
                legacyConveyor(board, 10, 0, LegacyOrientation.TOP, true);
                legacyConveyor(board, 0, 1, LegacyOrientation.RIGHT, false);
                legacyCurve(board, 1, 1, LegacyOrientation.RIGHT, 1, false);
                legacyCurve(board, 2, 1, LegacyOrientation.LEFT, 2, true);
                legacyCrossing(board, 10, 1, LegacyOrientation.LEFT, 3, true);
                legacyPusher(board, 0, 2, LegacyOrientation.LEFT, 4);
                legacyCurve(board, 3, 2, LegacyOrientation.LEFT, 2, false);
                legacyCurve(board, 9, 2, LegacyOrientation.BOTTOM, 2, false);
                legacyPusher(board, 11, 2, LegacyOrientation.RIGHT, 4);
                legacyCurve(board, 4, 3, LegacyOrientation.LEFT, 2, true);
                legacyCurve(board, 8, 3, LegacyOrientation.BOTTOM, 2, true);
                legacyPusher(board, 0, 4, LegacyOrientation.LEFT, 0);
                legacyCurve(board, 5, 4, LegacyOrientation.LEFT, 2, false);
                legacyConveyor(board, 6, 4, LegacyOrientation.LEFT, false);
                legacyCurve(board, 7, 4, LegacyOrientation.BOTTOM, 2, false);
                legacyPusher(board, 11, 4, LegacyOrientation.RIGHT, 0);
                legacyConveyor(board, 0, 5, LegacyOrientation.RIGHT, false);
                legacyPit(board, 5, 5);
                legacyPit(board, 6, 5);
                legacyConveyor(board, 0, 6, LegacyOrientation.LEFT, false);
                legacyPit(board, 5, 6);
                legacyPit(board, 6, 6);
                legacyConveyor(board, 11, 6, LegacyOrientation.LEFT, true);
                legacyPusher(board, 0, 7, LegacyOrientation.LEFT, 0);
                legacyCurve(board, 4, 7, LegacyOrientation.TOP, 2, true);
                legacyConveyor(board, 5, 7, LegacyOrientation.RIGHT, true);
                legacyCurve(board, 6, 7, LegacyOrientation.RIGHT, 2, true);
                legacyPusher(board, 11, 7, LegacyOrientation.RIGHT, 0);
                legacyCurve(board, 3, 8, LegacyOrientation.TOP, 2, false);
                legacyCurve(board, 7, 8, LegacyOrientation.RIGHT, 2, false);
                legacyPusher(board, 0, 9, LegacyOrientation.LEFT, 4);
                legacyCurve(board, 2, 9, LegacyOrientation.TOP, 2, true);
                legacyCurve(board, 8, 9, LegacyOrientation.RIGHT, 2, true);
                legacyPusher(board, 11, 9, LegacyOrientation.RIGHT, 4);
                legacyCrossing(board, 1, 10, LegacyOrientation.RIGHT, 3, false);
                legacyCurve(board, 9, 10, LegacyOrientation.RIGHT, 2, false);
                legacyCurve(board, 10, 10, LegacyOrientation.LEFT, 1, true);
                legacyConveyor(board, 11, 10, LegacyOrientation.LEFT, true);
                legacyConveyor(board, 1, 11, LegacyOrientation.BOTTOM, false);
                legacyPusher(board, 2, 11, LegacyOrientation.TOP, 4);
                legacyPusher(board, 4, 11, LegacyOrientation.TOP, 0);
                legacyConveyor(board, 5, 11, LegacyOrientation.BOTTOM, false);
                legacyPusher(board, 7, 11, LegacyOrientation.TOP, 0);
                legacyPusher(board, 9, 11, LegacyOrientation.TOP, 4);
                if (only1Map) {
                        legacyCheckpoint(board, 1, 1, 1);
                        legacyCheckpoint(board, 8, 8, 2);
                } else if (numberCheckpoint == 1) {
                        legacyCheckpoint(board, 1, 1, numberCheckpoint);
                } else {
                        legacyCheckpoint(board, 8, 8, numberCheckpoint);
                }
                legacyMirrorWalls(board);
                legacyLaser(board, 7, 5, LegacyOrientation.LEFT, 1);
                legacyLaser(board, 8, 6, LegacyOrientation.LEFT, 1);
                legacyMirrorWalls(board);
    }

    private void generateMap5(Board board, int numberCheckpoint, boolean only1Map) {
                legacyConveyor(board, 0, 0, LegacyOrientation.TOP, true);
                legacyWall(board, 2, 0, LegacyOrientation.BOTTOM);
                legacyWall(board, 2, 0, LegacyOrientation.TOP);
                legacyCurve(board, 4, 0, LegacyOrientation.BOTTOM, 1, false);
                legacyWall(board, 4, 0, LegacyOrientation.BOTTOM);
                legacyCurve(board, 5, 0, LegacyOrientation.RIGHT, 2, false);
                legacyCrossing(board, 6, 0, LegacyOrientation.TOP, 2, false);
                legacyConveyor(board, 7, 0, LegacyOrientation.LEFT, true);
                legacyWall(board, 7, 0, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 8, 0, LegacyOrientation.LEFT, true);
                legacyWall(board, 8, 0, LegacyOrientation.TOP);
                legacyConveyor(board, 9, 0, LegacyOrientation.LEFT, true);
                legacyWall(board, 9, 0, LegacyOrientation.BOTTOM);
                legacyCrossing(board, 10, 0, LegacyOrientation.LEFT, 1, true);
                legacyWall(board, 10, 0, LegacyOrientation.TOP);
                legacyConveyor(board, 11, 0, LegacyOrientation.LEFT, true);
                legacyCrossing(board, 0, 1, LegacyOrientation.TOP, 1, true);
                legacyRepair(board, 1, 1, 1);
                legacyWall(board, 2, 1, LegacyOrientation.BOTTOM);
                legacyWall(board, 3, 1, LegacyOrientation.RIGHT);
                legacyConveyor(board, 4, 1, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 4, 1, LegacyOrientation.LEFT);
                legacyWall(board, 4, 1, LegacyOrientation.RIGHT);
                legacyPusher(board, 5, 1, LegacyOrientation.LEFT, 3);
                legacyWall(board, 5, 1, LegacyOrientation.LEFT);
                legacyPress(board, 6, 1, 1);
                legacyWall(board, 8, 1, LegacyOrientation.BOTTOM);
                legacyRepair(board, 9, 1, 2);
                legacyWall(board, 9, 1, LegacyOrientation.TOP);
                legacyWall(board, 9, 1, LegacyOrientation.RIGHT);
                legacyWall(board, 10, 1, LegacyOrientation.LEFT);
                legacyWall(board, 10, 1, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 11, 1, LegacyOrientation.TOP, false);
                legacyConveyor(board, 0, 2, LegacyOrientation.TOP, true);
                legacyWall(board, 0, 2, LegacyOrientation.LEFT);
                legacyWall(board, 1, 2, LegacyOrientation.TOP);
                legacyWall(board, 1, 2, LegacyOrientation.RIGHT);
                legacyWall(board, 2, 2, LegacyOrientation.LEFT);
                legacyWall(board, 3, 2, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 4, 2, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 5, 2, LegacyOrientation.TOP);
                legacyConveyor(board, 6, 2, LegacyOrientation.TOP, false);
                legacyWall(board, 7, 2, LegacyOrientation.TOP);
                legacyWall(board, 7, 2, LegacyOrientation.RIGHT);
                legacyPusher(board, 8, 2, LegacyOrientation.LEFT, 2);
                legacyWall(board, 9, 2, LegacyOrientation.TOP);
                legacyWall(board, 9, 2, LegacyOrientation.BOTTOM);
                legacyPusher(board, 10, 2, LegacyOrientation.RIGHT, 1);
                legacyWall(board, 10, 2, LegacyOrientation.RIGHT);
                legacyConveyor(board, 11, 2, LegacyOrientation.TOP, false);
                legacyWall(board, 11, 2, LegacyOrientation.RIGHT);
                legacyConveyor(board, 0, 3, LegacyOrientation.TOP, true);
                legacyWall(board, 1, 3, LegacyOrientation.BOTTOM);
                legacyWall(board, 2, 3, LegacyOrientation.RIGHT);
                legacyWall(board, 2, 3, LegacyOrientation.TOP);
                legacyWall(board, 3, 3, LegacyOrientation.LEFT);
                legacyWall(board, 3, 3, LegacyOrientation.RIGHT);
                legacyConveyor(board, 4, 3, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 4, 3, LegacyOrientation.LEFT);
                legacyRepair(board, 5, 3, 2);
                legacyConveyor(board, 6, 3, LegacyOrientation.TOP, false);
                legacyWall(board, 7, 3, LegacyOrientation.BOTTOM);
                legacyPit(board, 9, 3);
                legacyWall(board, 9, 3, LegacyOrientation.BOTTOM);
                legacyWall(board, 10, 3, LegacyOrientation.TOP);
                legacyCrossing(board, 11, 3, LegacyOrientation.TOP, 2, false);
                legacyConveyor(board, 0, 4, LegacyOrientation.TOP, true);
                legacyWall(board, 0, 4, LegacyOrientation.RIGHT);
                legacyWall(board, 1, 4, LegacyOrientation.LEFT);
                legacyWall(board, 1, 4, LegacyOrientation.TOP);
                legacyWall(board, 2, 4, LegacyOrientation.BOTTOM);
                legacyWall(board, 3, 4, LegacyOrientation.TOP);
                legacyPress(board, 4, 4, 2);
                legacyConveyor(board, 5, 4, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 6, 4, LegacyOrientation.TOP, false);
                legacyConveyor(board, 9, 4, LegacyOrientation.BOTTOM, true);
                legacyWall(board, 10, 4, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 11, 4, LegacyOrientation.TOP, false);
                legacyCrossing(board, 0, 5, LegacyOrientation.RIGHT, 2, true);
                legacyConveyor(board, 1, 5, LegacyOrientation.RIGHT, true);
                legacyWall(board, 1, 5, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 2, 5, LegacyOrientation.RIGHT, true);
                legacyWall(board, 2, 5, LegacyOrientation.TOP);
                legacyConveyor(board, 3, 5, LegacyOrientation.RIGHT, true);
                legacyWall(board, 3, 5, LegacyOrientation.BOTTOM);
                legacyCrossing(board, 4, 5, LegacyOrientation.BOTTOM, 2, false);
                legacyConveyor(board, 5, 5, LegacyOrientation.RIGHT, false);
                legacyPress(board, 6, 5, 3);
                legacyConveyor(board, 8, 5, LegacyOrientation.RIGHT, true);
                legacyCurve(board, 9, 5, LegacyOrientation.RIGHT, 2, true);
                legacyCurve(board, 11, 5, LegacyOrientation.TOP, 2, false);
                legacyWall(board, 1, 6, LegacyOrientation.TOP);
                legacyWall(board, 2, 6, LegacyOrientation.BOTTOM);
                legacyRepair(board, 5, 6, 2);
                legacyWall(board, 3, 6, LegacyOrientation.TOP);
                legacyConveyor(board, 4, 6, LegacyOrientation.TOP, false);
                legacyCrossing(board, 6, 6, LegacyOrientation.TOP, 2, false);
                legacyConveyor(board, 7, 6, LegacyOrientation.LEFT, true);
                legacyPusher(board, 8, 6, LegacyOrientation.TOP, 2);
                legacyConveyor(board, 9, 6, LegacyOrientation.LEFT, true);
                legacyConveyor(board, 10, 6, LegacyOrientation.LEFT, true);
                legacyCrossing(board, 11, 6, LegacyOrientation.LEFT, 2, true);
                legacyWall(board, 2, 7, LegacyOrientation.TOP);
                legacyWall(board, 3, 7, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 4, 7, LegacyOrientation.BOTTOM, false);
                legacyConveyor(board, 5, 7, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 6, 7, LegacyOrientation.TOP, false);
                legacyWall(board, 8, 7, LegacyOrientation.TOP);
                legacyWall(board, 8, 7, LegacyOrientation.BOTTOM);
                legacyWall(board, 9, 7, LegacyOrientation.BOTTOM);
                legacyWall(board, 10, 7, LegacyOrientation.TOP);
                legacyWall(board, 10, 7, LegacyOrientation.RIGHT);
                legacyConveyor(board, 11, 7, LegacyOrientation.BOTTOM, true);
                legacyWall(board, 11, 7, LegacyOrientation.RIGHT);
                legacyWall(board, 0, 8, LegacyOrientation.RIGHT);
                legacyWall(board, 1, 8, LegacyOrientation.LEFT);
                legacyWall(board, 2, 8, LegacyOrientation.BOTTOM);
                legacyWall(board, 3, 8, LegacyOrientation.RIGHT);
                legacyPress(board, 4, 8, 2);
                legacyWall(board, 4, 8, LegacyOrientation.LEFT);
                legacyWall(board, 5, 8, LegacyOrientation.BOTTOM);
                legacyWall(board, 5, 8, LegacyOrientation.TOP);
                legacyConveyor(board, 6, 8, LegacyOrientation.TOP, false);
                legacyWall(board, 8, 8, LegacyOrientation.BOTTOM);
                legacyWall(board, 9, 8, LegacyOrientation.TOP);
                legacyWall(board, 10, 8, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 11, 8, LegacyOrientation.BOTTOM, true);
                legacyWall(board, 0, 9, LegacyOrientation.LEFT);
                legacyWall(board, 1, 9, LegacyOrientation.TOP);
                legacyWall(board, 1, 9, LegacyOrientation.RIGHT);
                legacyWall(board, 2, 9, LegacyOrientation.LEFT);
                legacyWall(board, 2, 9, LegacyOrientation.RIGHT);
                legacyWall(board, 3, 9, LegacyOrientation.LEFT);
                legacyConveyor(board, 4, 9, LegacyOrientation.BOTTOM, false);
                legacyPit(board, 5, 9);
                legacyWall(board, 5, 9, LegacyOrientation.BOTTOM);
                legacyPress(board, 6, 9, 1);
                legacyWall(board, 7, 9, LegacyOrientation.RIGHT);
                legacyWall(board, 8, 9, LegacyOrientation.LEFT);
                legacyWall(board, 8, 9, LegacyOrientation.RIGHT);
                legacyWall(board, 9, 9, LegacyOrientation.LEFT);
                legacyWall(board, 9, 9, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 11, 9, LegacyOrientation.BOTTOM, true);
                legacyWall(board, 11, 9, LegacyOrientation.RIGHT);
                legacyWall(board, 1, 10, LegacyOrientation.BOTTOM);
                legacyWall(board, 2, 10, LegacyOrientation.TOP);
                legacyConveyor(board, 4, 10, LegacyOrientation.BOTTOM, false);
                legacyCurve(board, 5, 10, LegacyOrientation.LEFT, 1, false);
                legacyCurve(board, 6, 10, LegacyOrientation.TOP, 1, false);
                legacyWall(board, 9, 10, LegacyOrientation.TOP);
                legacyWall(board, 9, 10, LegacyOrientation.RIGHT);
                legacyRepair(board, 10, 10, 1);
                legacyCrossing(board, 11, 10, LegacyOrientation.BOTTOM, 1, true);
                legacyConveyor(board, 0, 11, LegacyOrientation.RIGHT, true);
                legacyCrossing(board, 1, 11, LegacyOrientation.RIGHT, 1, true);
                legacyConveyor(board, 2, 11, LegacyOrientation.RIGHT, true);
                legacyWall(board, 2, 11, LegacyOrientation.TOP);
                legacyWall(board, 2, 11, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 3, 11, LegacyOrientation.RIGHT, true);
                legacyCrossing(board, 4, 11, LegacyOrientation.BOTTOM, 3, false);
                legacyWall(board, 4, 11, LegacyOrientation.TOP);
                legacyCurve(board, 5, 11, LegacyOrientation.BOTTOM, 2, false);
                legacyPit(board, 6, 11);
                legacyWall(board, 7, 11, LegacyOrientation.TOP);
                legacyWall(board, 9, 11, LegacyOrientation.TOP);
                legacyWall(board, 9, 11, LegacyOrientation.BOTTOM);
                legacyWall(board, 10, 11, LegacyOrientation.RIGHT);
                legacyConveyor(board, 11, 11, LegacyOrientation.BOTTOM, true);
                if (only1Map) {
                        legacyCheckpoint(board, 2, 1, 1);
                        legacyCheckpoint(board, 9, 10, 2);
                } else if (numberCheckpoint == 1) {
                        legacyCheckpoint(board, 2, 1, numberCheckpoint);
                } else {
                        legacyCheckpoint(board, 5, 6, numberCheckpoint);
                }
                legacyMirrorWalls(board);
                legacyLaser(board, 9, 2, LegacyOrientation.TOP, 2);
                legacyLaser(board, 3, 3, LegacyOrientation.RIGHT, 1);
                legacyLaser(board, 1, 6, LegacyOrientation.BOTTOM, 1);
                legacyLaser(board, 2, 7, LegacyOrientation.BOTTOM, 1);
                legacyLaser(board, 5, 8, LegacyOrientation.TOP, 1);
                legacyLaser(board, 9, 8, LegacyOrientation.BOTTOM, 1);
                legacyLaser(board, 2, 9, LegacyOrientation.LEFT, 2);
                legacyLaser(board, 8, 9, LegacyOrientation.RIGHT, 2);
                legacyLaser(board, 9, 11, LegacyOrientation.TOP, 3);
                legacyMirrorWalls(board);
    }

    private void generateMap6(Board board, int numberCheckpoint, boolean only1Map) {
                legacyPit(board, 0, 0);
                legacyConveyor(board, 1, 0, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 2, 0, LegacyOrientation.TOP);
                legacyCurve(board, 4, 0, LegacyOrientation.BOTTOM, 1, false);
                legacyCurve(board, 5, 0, LegacyOrientation.RIGHT, 2, false);
                legacyConveyor(board, 6, 0, LegacyOrientation.TOP, false);
                legacyPit(board, 11, 0);
                legacyConveyor(board, 0, 1, LegacyOrientation.RIGHT, false);
                legacyCrossing(board, 1, 1, LegacyOrientation.BOTTOM, 2, false);
                legacyPit(board, 2, 1);
                legacyWall(board, 2, 1, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 4, 1, LegacyOrientation.BOTTOM, false);
                legacyPit(board, 5, 1);
                legacyConveyor(board, 6, 1, LegacyOrientation.TOP, false);
                legacyPit(board, 8, 1);
                legacyWall(board, 9, 1, LegacyOrientation.RIGHT);
                legacyWall(board, 10, 1, LegacyOrientation.LEFT);
                legacyWall(board, 0, 2, LegacyOrientation.LEFT);
                legacyCurve(board, 1, 2, LegacyOrientation.LEFT, 1, false);
                legacyCurve(board, 2, 2, LegacyOrientation.BOTTOM, 2, false);
                legacyWall(board, 3, 2, LegacyOrientation.TOP);
                legacyWall(board, 3, 2, LegacyOrientation.RIGHT);
                legacyConveyor(board, 4, 2, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 4, 2, LegacyOrientation.LEFT);
                legacyWall(board, 5, 2, LegacyOrientation.TOP);
                legacyCurve(board, 6, 2, LegacyOrientation.TOP, 2, false);
                legacyCurve(board, 7, 2, LegacyOrientation.RIGHT, 1, false);
                legacyWall(board, 8, 2, LegacyOrientation.TOP);
                legacyRepair(board, 10, 2, 1);
                legacyWall(board, 11, 2, LegacyOrientation.RIGHT);
                legacyWall(board, 0, 3, LegacyOrientation.RIGHT);
                legacyPit(board, 1, 3);
                legacyWall(board, 1, 3, LegacyOrientation.LEFT);
                legacyConveyor(board, 2, 3, LegacyOrientation.BOTTOM, false);
                legacyRepair(board, 3, 3, 2);
                legacyWall(board, 3, 3, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 4, 3, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 5, 3, LegacyOrientation.BOTTOM);
                legacyPit(board, 6, 3);
                legacyConveyor(board, 7, 3, LegacyOrientation.TOP, false);
                legacyPit(board, 8, 3);
                legacyWall(board, 8, 3, LegacyOrientation.RIGHT);
                legacyWall(board, 8, 3, LegacyOrientation.BOTTOM);
                legacyWall(board, 9, 3, LegacyOrientation.LEFT);
                legacyPit(board, 10, 3);
                legacyWall(board, 10, 3, LegacyOrientation.RIGHT);
                legacyWall(board, 11, 3, LegacyOrientation.LEFT);
                legacyWall(board, 0, 4, LegacyOrientation.LEFT);
                legacyCurve(board, 1, 4, LegacyOrientation.BOTTOM, 1, false);
                legacyCurve(board, 2, 4, LegacyOrientation.RIGHT, 2, false);
                legacyWall(board, 2, 4, LegacyOrientation.RIGHT);
                legacyPit(board, 3, 4);
                legacyWall(board, 3, 4, LegacyOrientation.LEFT);
                legacyConveyor(board, 4, 4, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 6, 4, LegacyOrientation.TOP);
                legacyCurve(board, 7, 4, LegacyOrientation.TOP, 2, false);
                legacyCurve(board, 8, 4, LegacyOrientation.RIGHT, 1, false);
                legacyWall(board, 11, 4, LegacyOrientation.RIGHT);
                legacyConveyor(board, 0, 5, LegacyOrientation.RIGHT, false);
                legacyCurve(board, 1, 5, LegacyOrientation.RIGHT, 2, false);
                legacyPit(board, 2, 5);
                legacyWall(board, 3, 5, LegacyOrientation.RIGHT);
                legacyCurve(board, 3, 5, LegacyOrientation.BOTTOM, 1, false);
                legacyWall(board, 3, 5, LegacyOrientation.LEFT);
                legacyCurve(board, 4, 5, LegacyOrientation.RIGHT, 2, false);
                legacyWall(board, 6, 5, LegacyOrientation.BOTTOM);
                legacyWall(board, 6, 5, LegacyOrientation.RIGHT);
                legacyPit(board, 7, 5);
                legacyWall(board, 7, 5, LegacyOrientation.LEFT);
                legacyCurve(board, 8, 5, LegacyOrientation.TOP, 2, false);
                legacyConveyor(board, 9, 5, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 10, 5, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 11, 5, LegacyOrientation.RIGHT, false);
                legacyConveyor(board, 0, 6, LegacyOrientation.LEFT, false);
                legacyPit(board, 2, 6);
                legacyConveyor(board, 3, 6, LegacyOrientation.BOTTOM, false);
                legacyPit(board, 4, 6);
                legacyWall(board, 4, 6, LegacyOrientation.TOP);
                legacyPit(board, 5, 6);
                legacyWall(board, 5, 6, LegacyOrientation.RIGHT);
                legacyWall(board, 6, 6, LegacyOrientation.LEFT);
                legacyWall(board, 6, 6, LegacyOrientation.TOP);
                legacyRepair(board, 7, 6, 2);
                legacyWall(board, 8, 6, LegacyOrientation.TOP);
                legacyPit(board, 9, 6);
                legacyWall(board, 10, 6, LegacyOrientation.TOP);
                legacyWall(board, 0, 7, LegacyOrientation.LEFT);
                legacyCurve(board, 2, 7, LegacyOrientation.BOTTOM, 1, false);
                legacyWall(board, 2, 7, LegacyOrientation.BOTTOM);
                legacyCurve(board, 3, 7, LegacyOrientation.RIGHT, 2, false);
                legacyWall(board, 4, 7, LegacyOrientation.BOTTOM);
                legacyWall(board, 6, 7, LegacyOrientation.BOTTOM);
                legacyWall(board, 8, 7, LegacyOrientation.BOTTOM);
                legacyWall(board, 10, 7, LegacyOrientation.BOTTOM);
                legacyWall(board, 10, 7, LegacyOrientation.RIGHT);
                legacyWall(board, 11, 7, LegacyOrientation.RIGHT);
                legacyPit(board, 1, 8);
                legacyConveyor(board, 2, 8, LegacyOrientation.BOTTOM, false);
                legacyPit(board, 3, 8);
                legacyPit(board, 5, 8);
                legacyWall(board, 5, 8, LegacyOrientation.TOP);
                legacyRepair(board, 10, 8, 2);
                legacyPit(board, 7, 8);
                legacyWall(board, 7, 8, LegacyOrientation.TOP);
                legacyPit(board, 9, 8);
                legacyWall(board, 9, 8, LegacyOrientation.TOP);
                legacyWall(board, 10, 8, LegacyOrientation.TOP);
                legacyWall(board, 0, 9, LegacyOrientation.LEFT);
                legacyRepair(board, 1, 9, 1);
                legacyConveyor(board, 2, 9, LegacyOrientation.BOTTOM, false);
                legacyCurve(board, 5, 9, LegacyOrientation.BOTTOM, 1, false);
                legacyWall(board, 5, 9, LegacyOrientation.BOTTOM);
                legacyConveyor(board, 6, 9, LegacyOrientation.RIGHT, false);
                legacyCurve(board, 7, 9, LegacyOrientation.RIGHT, 1, false);
                legacyWall(board, 7, 9, LegacyOrientation.BOTTOM);
                legacyWall(board, 9, 9, LegacyOrientation.BOTTOM);
                legacyWall(board, 10, 9, LegacyOrientation.BOTTOM);
                legacyWall(board, 10, 9, LegacyOrientation.RIGHT);
                legacyWall(board, 11, 9, LegacyOrientation.LEFT);
                legacyWall(board, 11, 9, LegacyOrientation.RIGHT);
                legacyCurve(board, 1, 10, LegacyOrientation.BOTTOM, 1, false);
                legacyCurve(board, 2, 10, LegacyOrientation.RIGHT, 2, false);
                legacyWall(board, 2, 10, LegacyOrientation.TOP);
                legacyWall(board, 3, 10, LegacyOrientation.RIGHT);
                legacyPit(board, 4, 10);
                legacyWall(board, 4, 10, LegacyOrientation.LEFT);
                legacyWall(board, 4, 10, LegacyOrientation.RIGHT);
                legacyConveyor(board, 5, 10, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 5, 10, LegacyOrientation.LEFT);
                legacyPit(board, 6, 10);
                legacyConveyor(board, 7, 10, LegacyOrientation.TOP, false);
                legacyPit(board, 8, 10);
                legacyPit(board, 9, 10);
                legacyCurve(board, 10, 10, LegacyOrientation.LEFT, 2, false);
                legacyConveyor(board, 11, 10, LegacyOrientation.LEFT, false);
                legacyConveyor(board, 1, 11, LegacyOrientation.BOTTOM, false);
                legacyWall(board, 2, 11, LegacyOrientation.TOP);
                legacyWall(board, 2, 11, LegacyOrientation.BOTTOM);
                legacyWall(board, 4, 11, LegacyOrientation.TOP);
                legacyConveyor(board, 5, 11, LegacyOrientation.BOTTOM, false);
                legacyCurve(board, 6, 11, LegacyOrientation.LEFT, 2, false);
                legacyCurve(board, 7, 11, LegacyOrientation.TOP, 1, false);
                legacyWall(board, 7, 11, LegacyOrientation.TOP);
                legacyWall(board, 8, 11, LegacyOrientation.BOTTOM);
                legacyWall(board, 9, 11, LegacyOrientation.TOP);
                legacyConveyor(board, 10, 11, LegacyOrientation.TOP, false);
                legacyPit(board, 11, 11);
                if (only1Map) {
                        legacyCheckpoint(board, 2, 1, 1);
                        legacyCheckpoint(board, 9, 11, 2);
                } else if (numberCheckpoint == 1) {
                        legacyCheckpoint(board, 2, 1, numberCheckpoint);
                } else {
                        legacyCheckpoint(board, 7, 5, numberCheckpoint);
                }
                legacyLaser(board, 5, 3, LegacyOrientation.TOP, 1);
                legacyMirrorWalls(board);
    }
}
