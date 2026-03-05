package com.roborally.server.service;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.FieldType;
import com.roborally.common.enums.RotationDirection;
import com.roborally.server.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Loads board configurations from JSON resources.
 */
@Component
public class BoardLoader {

    private static final Logger log = LoggerFactory.getLogger(BoardLoader.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Load a board by name (e.g. "Plan B" → boards/plan_b.json).
     */
    public Board loadBoard(String boardName) {
        String fileName = "boards/" + boardName.toLowerCase().replace(" ", "_") + ".json";
        try {
            ClassPathResource resource = new ClassPathResource(fileName);
            InputStream is = resource.getInputStream();
            Map<String, Object> data = objectMapper.readValue(is, new TypeReference<>() {
            });
            return parseBoard(boardName, data);
        } catch (IOException e) {
            log.warn("Board file {} not found, creating default board", fileName);
            return createDefaultBoard(boardName);
        }
    }

    /**
     * Create a default 12×12 board with simple layout.
     */
    public Board createDefaultBoard(String name) {
        Board board = new Board(name, 12, 12);

        // Start positions (bottom row)
        for (int x = 1; x <= 8; x++) {
            board.getTile(x, 11).setFieldType(FieldType.START);
            board.addStartPosition(x, 11);
        }

        // Some walls
        board.getTile(3, 3).addWall(Direction.SOUTH);
        board.getTile(3, 4).addWall(Direction.NORTH);
        board.getTile(8, 3).addWall(Direction.SOUTH);
        board.getTile(8, 4).addWall(Direction.NORTH);
        board.getTile(5, 7).addWall(Direction.EAST);
        board.getTile(6, 7).addWall(Direction.WEST);

        // Conveyor belts (a simple lane going north)
        for (int y = 9; y >= 2; y--) {
            board.getTile(0, y).setConveyorBelt(new ConveyorBelt(Direction.NORTH, false));
            board.getTile(11, y).setConveyorBelt(new ConveyorBelt(Direction.NORTH, true));
        }

        // Gears
        board.getTile(4, 5).setGear(new Gear(RotationDirection.CLOCKWISE));
        board.getTile(7, 5).setGear(new Gear(RotationDirection.COUNTERCLOCKWISE));

        // Pits
        board.getTile(0, 0).setFieldType(FieldType.PIT);
        board.getTile(11, 0).setFieldType(FieldType.PIT);
        board.getTile(5, 5).setFieldType(FieldType.PIT);
        board.getTile(6, 5).setFieldType(FieldType.PIT);

        // Repair fields
        board.getTile(3, 8).setFieldType(FieldType.REPAIR_1);
        board.getTile(8, 8).setFieldType(FieldType.REPAIR_1);

        // Checkpoints
        board.getTile(3, 2).setCheckpoint(new Checkpoint(1));
        board.getTile(8, 2).setCheckpoint(new Checkpoint(2));
        board.getTile(5, 0).setCheckpoint(new Checkpoint(3));
        board.setTotalCheckpoints(3);

        // Lasers
        board.addLaser(new Laser(5, 0, Direction.SOUTH, 1));
        board.addLaser(new Laser(6, 0, Direction.SOUTH, 1));

        log.info("Created default board '{}' (12x12, {} checkpoints)", name, board.getTotalCheckpoints());
        return board;
    }

    @SuppressWarnings("unchecked")
    private Board parseBoard(String name, Map<String, Object> data) {
        int width = (int) data.getOrDefault("width", 12);
        int height = (int) data.getOrDefault("height", 12);
        Board board = new Board(name, width, height);

        // Parse tiles
        List<Map<String, Object>> tiles = (List<Map<String, Object>>) data.getOrDefault("tiles", List.of());
        for (Map<String, Object> td : tiles) {
            int x = (int) td.get("x");
            int y = (int) td.get("y");
            Tile tile = board.getTile(x, y);
            if (tile == null)
                continue;

            if (td.containsKey("type")) {
                tile.setFieldType(FieldType.valueOf((String) td.get("type")));
            }
            if (td.containsKey("walls")) {
                List<String> walls = (List<String>) td.get("walls");
                walls.forEach(w -> tile.addWall(Direction.valueOf(w)));
            }
            if (td.containsKey("conveyorBelt")) {
                Map<String, Object> cb = (Map<String, Object>) td.get("conveyorBelt");
                Direction dir = Direction.valueOf((String) cb.get("direction"));
                boolean express = Boolean.TRUE.equals(cb.get("express"));
                Direction curveFrom = cb.containsKey("curveFrom") ? Direction.valueOf((String) cb.get("curveFrom"))
                        : null;
                tile.setConveyorBelt(new ConveyorBelt(dir, express, curveFrom));
            }
            if (td.containsKey("gear")) {
                Map<String, Object> g = (Map<String, Object>) td.get("gear");
                tile.setGear(new Gear(RotationDirection.valueOf((String) g.get("rotation"))));
            }
            if (td.containsKey("checkpoint")) {
                Map<String, Object> cp = (Map<String, Object>) td.get("checkpoint");
                tile.setCheckpoint(new Checkpoint((int) cp.get("number")));
            }
            if (td.containsKey("pusher")) {
                Map<String, Object> p = (Map<String, Object>) td.get("pusher");
                Direction dir = Direction.valueOf((String) p.get("direction"));
                List<Integer> steps = (List<Integer>) p.get("steps");
                tile.setPusher(new Pusher(dir, new HashSet<>(steps)));
            }
            if (td.containsKey("press")) {
                Map<String, Object> p = (Map<String, Object>) td.get("press");
                List<Integer> steps = (List<Integer>) p.get("steps");
                tile.setPress(new Press(new HashSet<>(steps)));
            }

            // Mark start positions
            if (tile.isStart())
                board.addStartPosition(x, y);
        }

        // Parse lasers
        List<Map<String, Object>> lasers = (List<Map<String, Object>>) data.getOrDefault("lasers", List.of());
        for (Map<String, Object> ld : lasers) {
            board.addLaser(new Laser(
                    (int) ld.get("x"), (int) ld.get("y"),
                    Direction.valueOf((String) ld.get("direction")),
                    (int) ld.getOrDefault("strength", 1)));
        }

        board.setTotalCheckpoints((int) data.getOrDefault("totalCheckpoints",
                tiles.stream().filter(t -> t.containsKey("checkpoint")).count()));

        log.info("Loaded board '{}' ({}x{}, {} checkpoints)", name, width, height, board.getTotalCheckpoints());
        return board;
    }
}
