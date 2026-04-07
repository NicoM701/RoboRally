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

        if (name.equalsIgnoreCase("map2")) {
            generateMap2(board, 1, false);
        } else if (name.equalsIgnoreCase("map3")) {
            generateMap3(board, 1, false);
        } else if (name.equalsIgnoreCase("map4")) {
            generateMap4(board, 1, false);
        } else if (name.equalsIgnoreCase("map5")) {
            generateMap5(board, 1, false);
        } else if (name.equalsIgnoreCase("map6")) {
            generateMap6(board, 1, false);
        } else {
            generateMap1(board, 1, false);
        }

        // In the original game, checkpoint generation wasn't always producing the starts.
        // Let's add them at bottom using a default rule if empty.
        if (board.getStartPositions().isEmpty()) {
            int maxSpawns = 8;
            if (name.equalsIgnoreCase("map1") || name.equalsIgnoreCase("map2")) maxSpawns = 2;
            else if (name.equalsIgnoreCase("map3") || name.equalsIgnoreCase("map4")) maxSpawns = 4;
            
            for (int x = 1; x <= maxSpawns; x++) {
                board.getTile(x, 11).setFieldType(FieldType.START);
                board.addStartPosition(x, 11);
            }
        }

        calculateConveyorCurves(board);
        return board;
    }

    private void calculateConveyorCurves(Board board) {
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                Tile tile = board.getTile(x, y);
                if (tile == null || tile.getConveyorBelt() == null) continue;
                
                ConveyorBelt cb = tile.getConveyorBelt();
                Direction outDir = cb.getDirection();
                
                Direction inDir = null;
                int inputs = 0;
                for (Direction d : Direction.values()) {
                    int nx = x + (d == Direction.EAST ? 1 : (d == Direction.WEST ? -1 : 0));
                    int ny = y + (d == Direction.SOUTH ? 1 : (d == Direction.NORTH ? -1 : 0));
                    Tile n = board.getTile(nx, ny);
                    if (n != null && n.getConveyorBelt() != null) {
                        if (n.getConveyorBelt().getDirection() == d.opposite()) {
                            inDir = d;
                            inputs++;
                        }
                    }
                }
                
                if (inputs > 0 && inDir.opposite() != outDir && !cb.isCrossing()) {
                    RotationDirection rot = (inDir.rotateClockwise() == outDir) ? RotationDirection.CLOCKWISE : RotationDirection.COUNTERCLOCKWISE;
                    cb.setCurveRotation(rot);
                    cb.setCurveFrom(inDir);
                }
            }
        }
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
