package com.roborally.server.service;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.FieldType;
import com.roborally.common.enums.RotationDirection;
import com.roborally.server.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
            Map<String, Object> boardMap = new LinkedHashMap<>();
            boardMap.put("id", name);
            boardMap.put("name", name);
            boardMap.put("maxPlayers", board.getStartPositions().size());
            boardMap.put("boardData", board.toMap());
            list.add(boardMap);
        }
        return list;
    }

    public Board createDefaultBoard(String name) {
        String normalizedName = name == null ? "map1" : name.toLowerCase(Locale.ROOT);
        Board board = new Board(name, 12, 12);
        LegacyBoardSpec legacyBoard = LegacyBoardSpecs.forName(normalizedName);

        applyLegacyBoard(board, legacyBoard);
        mirrorLegacyWalls(board);
        addFallbackStartPositions(board, normalizedName);
        board.setTotalCheckpoints(legacyBoard.totalCheckpoints());
        return board;
    }

    private void applyLegacyBoard(Board board, LegacyBoardSpec legacyBoard) {
        for (LegacyOp op : legacyBoard.ops()) {
            switch (op.kind()) {
                case WALL -> tileAtLegacy(board, op.x(), op.y()).addWall(directionFromLegacy(op.orientation()));
                case PIT -> resetTile(tileAtLegacy(board, op.x(), op.y()), FieldType.PIT);
                case REPAIR -> resetTile(tileAtLegacy(board, op.x(), op.y()),
                        op.value() == 2 ? FieldType.REPAIR_2 : FieldType.REPAIR_1);
                case CHECKPOINT -> applyCheckpoint(tileAtLegacy(board, op.x(), op.y()), op.value());
                case CONVEYOR -> applyConveyor(tileAtLegacy(board, op.x(), op.y()),
                        directionFromLegacy(op.orientation()), op.express());
                case CURVE -> applyCurve(tileAtLegacy(board, op.x(), op.y()),
                        directionFromLegacy(op.orientation()), op.value(), op.express());
                case CROSSING -> applyCrossing(tileAtLegacy(board, op.x(), op.y()),
                        directionFromLegacy(op.orientation()), op.value(), op.express());
                case GEAR -> applyGear(tileAtLegacy(board, op.x(), op.y()), gearRotationFromLegacy(op.orientation()));
                case PUSHER -> applyPusher(tileAtLegacy(board, op.x(), op.y()),
                        directionFromLegacy(op.orientation()), op.value());
                case PRESS -> applyPress(tileAtLegacy(board, op.x(), op.y()), op.value());
                case LASER -> board.addLaser(new Laser(
                        boardXFromLegacy(op.x()),
                        boardYFromLegacy(board, op.y()),
                        directionFromLegacy(op.orientation()),
                        op.value()));
            }
        }
    }

    /**
     * Old Board.java uses {@code fields[x][y]} with the origin at the bottom-left.
     *
     * <p>The old JavaFX client rendered those fields with the same {@code x} coordinate and only flipped
     * {@code y} on draw ({@code drawY = height - y - 1}). The clean adapter therefore is just a vertical flip:
     * {@code legacy(x, y) -> board(x, height - 1 - y)}.</p>
     */
    private int boardXFromLegacy(int legacyX) {
        return legacyX;
    }

    private int boardYFromLegacy(Board board, int legacyY) {
        return board.getHeight() - 1 - legacyY;
    }

    private Tile tileAtLegacy(Board board, int legacyX, int legacyY) {
        return board.getTile(boardXFromLegacy(legacyX), boardYFromLegacy(board, legacyY));
    }

    private Direction directionFromLegacy(LegacyOrientation legacyOrientation) {
        return switch (legacyOrientation) {
            case LEFT -> Direction.WEST;
            case RIGHT -> Direction.EAST;
            case TOP -> Direction.NORTH;
            case BOTTOM -> Direction.SOUTH;
        };
    }

    private RotationDirection gearRotationFromLegacy(LegacyOrientation legacyOrientation) {
        return switch (legacyOrientation) {
            case LEFT, TOP -> RotationDirection.CLOCKWISE;
            case RIGHT, BOTTOM -> RotationDirection.COUNTERCLOCKWISE;
        };
    }

    private RotationDirection curveRotationFromLegacy(int legacyCurve) {
        return legacyCurve == 2 ? RotationDirection.CLOCKWISE : RotationDirection.COUNTERCLOCKWISE;
    }

    private String crossingTypeFromLegacy(int legacyCrossing) {
        return switch (legacyCrossing) {
            case 2 -> "RIGHT";
            case 3 -> "LEFTRIGHT";
            default -> "LEFT";
        };
    }

    private void resetTile(Tile tile, FieldType fieldType) {
        tile.setFieldType(fieldType);
        tile.setConveyorBelt(null);
        tile.setGear(null);
        tile.setPusher(null);
        tile.setPress(null);
        tile.setCheckpoint(null);
    }

    private void applyCheckpoint(Tile tile, int checkpointNumber) {
        // Legacy maps can place checkpoints on top of conveyors, so only clear
        // incompatible top-level elements instead of wiping the whole tile.
        tile.setFieldType(FieldType.FLOOR);
        tile.setGear(null);
        tile.setPusher(null);
        tile.setPress(null);
        tile.setCheckpoint(new Checkpoint(checkpointNumber));
    }

    private void applyConveyor(Tile tile, Direction direction, boolean express) {
        resetTile(tile, FieldType.FLOOR);
        tile.setConveyorBelt(new ConveyorBelt(direction, express));
    }

    private void applyCurve(Tile tile, Direction direction, int legacyCurve, boolean express) {
        ConveyorBelt conveyorBelt = new ConveyorBelt(direction, express);
        conveyorBelt.setCurveRotation(curveRotationFromLegacy(legacyCurve));
        resetTile(tile, FieldType.FLOOR);
        tile.setConveyorBelt(conveyorBelt);
    }

    private void applyCrossing(Tile tile, Direction direction, int legacyCrossing, boolean express) {
        ConveyorBelt conveyorBelt = new ConveyorBelt(direction, express);
        conveyorBelt.setCrossingType(crossingTypeFromLegacy(legacyCrossing));
        resetTile(tile, FieldType.FLOOR);
        tile.setConveyorBelt(conveyorBelt);
    }

    private void applyGear(Tile tile, RotationDirection rotationDirection) {
        resetTile(tile, FieldType.FLOOR);
        tile.setGear(new Gear(rotationDirection));
    }

    private void applyPusher(Tile tile, Direction direction, int activeStep) {
        resetTile(tile, FieldType.FLOOR);
        tile.setPusher(new Pusher(direction, pusherStepsFromLegacy(activeStep)));
    }

    private void applyPress(Tile tile, int activeStep) {
        resetTile(tile, FieldType.FLOOR);
        tile.setPress(new Press(pressStepsFromLegacy(activeStep)));
    }

    private Set<Integer> pusherStepsFromLegacy(int legacyTurn) {
        return switch (legacyTurn) {
            case 1 -> Set.of(1);
            case 2 -> Set.of(2);
            case 3 -> Set.of(3);
            case 4 -> Set.of(2, 4);
            default -> Set.of(1, 3, 5);
        };
    }

    private Set<Integer> pressStepsFromLegacy(int legacyRoundsActive) {
        return switch (legacyRoundsActive) {
            case 2, 4 -> Set.of(2, 4);
            case 3 -> Set.of(3);
            default -> Set.of(1, 5);
        };
    }

    private void mirrorLegacyWalls(Board board) {
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                Tile tile = board.getTile(x, y);
                for (Direction wall : tile.getWalls()) {
                    Tile neighbor = board.getTile(x + wall.dx(), y - wall.dy());
                    if (neighbor != null) {
                        neighbor.addWall(wall.opposite());
                    }
                }
            }
        }
    }

    private void addFallbackStartPositions(Board board, String normalizedName) {
        if (!board.getStartPositions().isEmpty()) {
            return;
        }

        int maxSpawns = 8;
        if (normalizedName.equals("map1") || normalizedName.equals("map2")) {
            maxSpawns = 2;
        } else if (normalizedName.equals("map3") || normalizedName.equals("map4")) {
            maxSpawns = 4;
        }

        boolean markStartTiles = !isLegacyNamedMap(normalizedName);
        for (int x = 1; x <= maxSpawns; x++) {
            board.addStartPosition(x, 11, markStartTiles);
        }
    }

    private boolean isLegacyNamedMap(String normalizedName) {
        return normalizedName.equals("map1")
                || normalizedName.equals("map2")
                || normalizedName.equals("map3")
                || normalizedName.equals("map4")
                || normalizedName.equals("map5")
                || normalizedName.equals("map6");
    }
}
