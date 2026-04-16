package com.roborally.server.service;

import com.roborally.common.enums.CardType;
import com.roborally.common.enums.Direction;
import com.roborally.server.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Core robot movement logic: move, push, wall collision, pit/edge death, card
 * execution.
 */
@Component
public class MovementService {

    private record PlannedBeltMove(Robot robot, ConveyorBelt belt, int sourceX, int sourceY, Direction travelDirection) {
    }

    private static final Logger log = LoggerFactory.getLogger(MovementService.class);

    // ══════════════════════════════════════════════════════
    // Wall Collision
    // ══════════════════════════════════════════════════════

    /**
     * Check if a wall blocks movement from (x,y) in the given direction.
     * Walls must be checked on both tiles: exit wall on current tile, entry wall on
     * target tile.
     */
    public boolean isWallBlocking(Board board, int x, int y, Direction direction) {
        // Check exit wall on current tile
        Tile current = board.getTile(x, y);
        if (current != null && current.hasWall(direction)) {
            return true;
        }

        // Check entry wall on target tile
        int nx = x + direction.dx();
        int ny = y + direction.dy();
        Tile target = board.getTile(nx, ny);
        if (target != null && target.hasWall(direction.opposite())) {
            return true;
        }

        return false;
    }

    // ══════════════════════════════════════════════════════
    // Robot Queries
    // ══════════════════════════════════════════════════════

    /**
     * Find a robot at the given position (among active robots).
     */
    public Robot getRobotAt(GameState game, int x, int y) {
        for (Robot r : game.getActiveRobots()) {
            if (r.getX() == x && r.getY() == y)
                return r;
        }
        return null;
    }

    // ══════════════════════════════════════════════════════
    // Movement
    // ══════════════════════════════════════════════════════

    /**
     * Move a robot one step in the given direction, handling wall collision and
     * pushing.
     * Returns true if the robot actually moved.
     */
    public boolean moveOneStep(GameState game, Robot robot, Direction direction) {
        Board board = game.getBoard();
        int x = robot.getX();
        int y = robot.getY();

        // Wall check
        if (isWallBlocking(board, x, y, direction)) {
            log.debug("Robot {} blocked by wall at ({},{}) going {}", robot.getPlayerId(), x, y, direction);
            return false;
        }

        int nx = x + direction.dx();
        int ny = y + direction.dy();

        // Check for robot at target position → push chain
        Robot blocking = getRobotAt(game, nx, ny);
        if (blocking != null) {
            boolean pushed = pushRobot(game, blocking, direction);
            if (!pushed) {
                log.debug("Robot {} push chain blocked at ({},{})", robot.getPlayerId(), nx, ny);
                return false; // Can't push → can't move
            }
        }

        // Move robot
        robot.setPosition(nx, ny);

        // Check if the new position is off-board or a pit
        checkDestruction(game, robot);

        return true;
    }

    /**
     * Push a robot in the given direction. Handles chain pushing.
     * Returns true if push succeeded.
     */
    public boolean pushRobot(GameState game, Robot robot, Direction direction) {
        Board board = game.getBoard();
        int x = robot.getX();
        int y = robot.getY();

        // Wall check from push position
        if (isWallBlocking(board, x, y, direction)) {
            return false; // Wall blocks push
        }

        int nx = x + direction.dx();
        int ny = y + direction.dy();

        // Chain push: check if another robot is at the target
        Robot nextRobot = getRobotAt(game, nx, ny);
        if (nextRobot != null) {
            boolean chainPushed = pushRobot(game, nextRobot, direction);
            if (!chainPushed)
                return false;
        }

        // Move the pushed robot
        robot.setPosition(nx, ny);
        log.debug("Robot {} pushed to ({},{})", robot.getPlayerId(), nx, ny);

        // Check destruction after push
        checkDestruction(game, robot);

        return true;
    }

    /**
     * Check if a robot is off the board or on a pit → destroy it.
     */
    public void checkDestruction(GameState game, Robot robot) {
        Board board = game.getBoard();
        int x = robot.getX();
        int y = robot.getY();

        if (!board.isInBounds(x, y)) {
            log.info("Robot {} fell off the board at ({},{})", robot.getPlayerId(), x, y);
            robot.destroy();
            return;
        }

        Tile tile = board.getTile(x, y);
        if (tile != null && tile.isPit()) {
            log.info("Robot {} fell into pit at ({},{})", robot.getPlayerId(), x, y);
            robot.destroy();
        }
    }

    // ══════════════════════════════════════════════════════
    // Card Execution
    // ══════════════════════════════════════════════════════

    /**
     * Execute a single card for a robot.
     */
    public void executeCard(GameState game, Robot robot, ProgramCard card) {
        if (robot.isDestroyed())
            return;

        CardType type = card.getType();
        switch (type) {
            case MOVE_1 -> moveForward(game, robot, 1);
            case MOVE_2 -> moveForward(game, robot, 2);
            case MOVE_3 -> moveForward(game, robot, 3);
            case BACKUP -> moveBackward(game, robot);
            case TURN_LEFT -> robot.setDirection(robot.getDirection().rotateCounterClockwise());
            case TURN_RIGHT -> robot.setDirection(robot.getDirection().rotateClockwise());
            case U_TURN -> robot.setDirection(robot.getDirection().rotate180());
        }
    }

    /**
     * Move a robot forward N steps.
     */
    public void moveForward(GameState game, Robot robot, int steps) {
        for (int i = 0; i < steps; i++) {
            if (robot.isDestroyed())
                break;
            boolean moved = moveOneStep(game, robot, robot.getDirection());
            if (!moved)
                break; // Wall or immovable push
        }
    }

    /**
     * Move a robot backward 1 step (no turning).
     */
    public void moveBackward(GameState game, Robot robot) {
        Direction backward = robot.getDirection().opposite();
        moveOneStep(game, robot, backward);
    }

    // ══════════════════════════════════════════════════════
    // Step Execution
    // ══════════════════════════════════════════════════════

    /**
     * Execute one step (register) for all robots, ordered by card priority.
     * 
     * @param stepNumber 0-based step index (0 = first register)
     */
    public List<Map<String, Object>> executeStep(GameState game, int stepNumber) {
        List<Map<String, Object>> results = new ArrayList<>();

        // Gather all robots with their cards for this step, sorted by priority (highest
        // first)
        List<Robot> robots = new ArrayList<>(game.getActiveRobots());
        robots.sort((a, b) -> {
            ProgramCard cardA = a.getSlot(stepNumber);
            ProgramCard cardB = b.getSlot(stepNumber);
            int prioA = cardA != null ? cardA.getPriority() : 0;
            int prioB = cardB != null ? cardB.getPriority() : 0;
            return Integer.compare(prioB, prioA); // Highest priority first
        });

        // Execute each robot's card for this step
        for (Robot robot : robots) {
            if (robot.isDestroyed())
                continue;
            ProgramCard card = robot.getSlot(stepNumber);
            if (card == null)
                continue;

            int prevX = robot.getX();
            int prevY = robot.getY();
            Direction prevDir = robot.getDirection();

            executeCard(game, robot, card);

            // Record result for animation
            recordResult(card.getType().name(), robot, card.getPriority(), prevX, prevY, prevDir, results);
        }

        // ─── Process Board Elements at the end of the registry ───
        processBoardElements(game, stepNumber, results);

        return results;
    }

    // ══════════════════════════════════════════════════════
    // Board Elements Execution
    // ══════════════════════════════════════════════════════

    private void recordResult(String type, Robot r, int priority, int fromX, int fromY, Direction fromDir, List<Map<String, Object>> results) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("playerId", r.getPlayerId());
        res.put("cardType", type);
        res.put("priority", priority);
        res.put("fromX", fromX);
        res.put("fromY", fromY);
        res.put("fromDir", fromDir.name());
        res.put("toX", r.getX());
        res.put("toY", r.getY());
        res.put("toDir", r.getDirection().name());
        res.put("destroyed", r.isDestroyed());
        results.add(res);
    }

    private List<Robot> getMovableRobots(GameState game) {
        return game.getActiveRobots().stream().filter(r -> !r.isDestroyed()).toList();
    }

    private void processBoardElements(GameState game, int stepNumber, List<Map<String, Object>> results) {
        processExpressBelts(game, results);
        processAllBelts(game, results);
        processPushers(game, stepNumber, results);
        processGears(game, results);
        processLasers(game, results);
    }

    private void processExpressBelts(GameState game, List<Map<String, Object>> results) {
        processBelts(game, true, results);
    }

    private void processAllBelts(GameState game, List<Map<String, Object>> results) {
        processBelts(game, false, results);
    }

    private void processBelts(GameState game, boolean expressOnly, List<Map<String, Object>> results) {
        Board board = game.getBoard();
        List<PlannedBeltMove> plannedMoves = new ArrayList<>();
        for (Robot r : getMovableRobots(game)) {
            Tile tile = board.getTile(r.getX(), r.getY());
            if (tile != null && tile.getConveyorBelt() != null) {
                ConveyorBelt belt = tile.getConveyorBelt();
                if (!expressOnly || belt.isExpress()) {
                    plannedMoves.add(new PlannedBeltMove(
                            r,
                            belt,
                            r.getX(),
                            r.getY(),
                            resolveLegacyBeltMoveDirection(belt)));
                }
            }
        }

        plannedMoves.sort(Comparator.comparing(move -> move.robot().getPlayerId()));

        // Legacy boards store curve directions as the incoming/straight-through side, not
        // the literal outgoing move direction. A curve therefore needs one extra turn when the
        // robot starts its movement on that tile, and the destination tile may rotate the robot
        // again depending on how the two belts connect.
        for (PlannedBeltMove move : plannedMoves) {
            Robot r = move.robot();
            if (r.isDestroyed()) continue;
            if (r.getX() != move.sourceX() || r.getY() != move.sourceY()) {
                continue;
            }

            ConveyorBelt sourceBelt = move.belt();
            int prevX = r.getX();
            int prevY = r.getY();
            Direction prevDir = r.getDirection();

            Direction travelDirection = move.travelDirection();
            boolean moved = moveOneStep(game, r, travelDirection);
            if (moved) {
                Tile newTile = board.getTile(r.getX(), r.getY());
                applyLegacyBeltTurn(r, travelDirection, newTile);
                recordResult("BELT", r, 0, prevX, prevY, prevDir, results);
            }
        }
    }

    private Direction resolveLegacyBeltMoveDirection(com.roborally.server.model.ConveyorBelt belt) {
        com.roborally.common.enums.RotationDirection curveRotation = belt.getCurveRotation();
        if (curveRotation == null) {
            return belt.getDirection();
        }
        return curveRotation == com.roborally.common.enums.RotationDirection.CLOCKWISE
                ? belt.getDirection().rotateClockwise()
                : belt.getDirection().rotateCounterClockwise();
    }

    private void applyLegacyBeltTurn(Robot robot, Direction incomingDirection, Tile destinationTile) {
        if (destinationTile == null || destinationTile.getConveyorBelt() == null) {
            return;
        }

        com.roborally.server.model.ConveyorBelt destinationBelt = destinationTile.getConveyorBelt();
        Direction baseDirection = destinationBelt.getDirection();

        com.roborally.common.enums.RotationDirection curveRotation = destinationBelt.getCurveRotation();
        if (curveRotation != null && incomingDirection == baseDirection) {
            if (curveRotation == com.roborally.common.enums.RotationDirection.CLOCKWISE) {
                robot.setDirection(robot.getDirection().rotateClockwise());
            } else {
                robot.setDirection(robot.getDirection().rotateCounterClockwise());
            }
            return;
        }

        if (!destinationBelt.isCrossing()) {
            return;
        }

        String crossingType = destinationBelt.getCrossingType() == null
                ? "LEFTRIGHT"
                : destinationBelt.getCrossingType().toUpperCase(Locale.ROOT);

        if ((crossingType.equals("LEFT") || crossingType.equals("LEFTRIGHT"))
                && incomingDirection == baseDirection.rotateClockwise()) {
            robot.setDirection(robot.getDirection().rotateCounterClockwise());
        } else if ((crossingType.equals("RIGHT") || crossingType.equals("LEFTRIGHT"))
                && incomingDirection == baseDirection.rotateCounterClockwise()) {
            robot.setDirection(robot.getDirection().rotateClockwise());
        }
    }

    private void processPushers(GameState game, int stepNumber, List<Map<String, Object>> results) {
        Board board = game.getBoard();
        for (Robot r : getMovableRobots(game)) {
            Tile tile = board.getTile(r.getX(), r.getY());
            if (tile != null && tile.getPusher() != null) {
                com.roborally.server.model.Pusher pusher = tile.getPusher();
                if (pusher.isActiveOnStep(stepNumber + 1)) {
                    int prevX = r.getX();
                    int prevY = r.getY();
                    Direction prevDir = r.getDirection();
                    boolean moved = pushRobot(game, r, pusher.getPushDirection());
                    if (moved) {
                        recordResult("PUSHER", r, 0, prevX, prevY, prevDir, results);
                    }
                }
            }
        }
    }

    private void processGears(GameState game, List<Map<String, Object>> results) {
        Board board = game.getBoard();
        for (Robot r : getMovableRobots(game)) {
            Tile tile = board.getTile(r.getX(), r.getY());
            if (tile != null && tile.getGear() != null) {
                com.roborally.server.model.Gear gear = tile.getGear();
                int prevX = r.getX();
                int prevY = r.getY();
                Direction prevDir = r.getDirection();
                
                if (gear.getRotation() == com.roborally.common.enums.RotationDirection.CLOCKWISE) {
                    r.setDirection(r.getDirection().rotateClockwise());
                } else {
                    r.setDirection(r.getDirection().rotateCounterClockwise());
                }
                recordResult("GEAR", r, 0, prevX, prevY, prevDir, results);
            }
        }
    }

    private void processLasers(GameState game, List<Map<String, Object>> results) {
        Board board = game.getBoard();
        
        for (com.roborally.server.model.Laser laser : board.getLasers()) {
            traceLaser(game, laser.getX(), laser.getY(), laser.getDirection(), laser.getStrength(), results, false);
        }
        
        for (Robot r : getMovableRobots(game)) {
            traceLaser(game, r.getX(), r.getY(), r.getDirection(), 1, results, true);
        }
    }

    private void traceLaser(GameState game, int startX, int startY, Direction dir, int strength, List<Map<String, Object>> results, boolean isRobotLaser) {
        Board board = game.getBoard();
        int currX = startX;
        int currY = startY;
        
        while (board.isInBounds(currX, currY)) {
            if (isWallBlocking(board, currX, currY, dir)) {
                break;
            }
            
            currX += dir.dx();
            currY += dir.dy();
            
            Robot hit = getRobotAt(game, currX, currY);
            if (hit != null && !hit.isDestroyed()) {
                hit.addDamage(strength);
                if (hit.getDamage() >= 10) { 
                    hit.destroy();
                }
                
                int prevX = hit.getX();
                int prevY = hit.getY();
                Direction prevDir = hit.getDirection();
                recordResult(isRobotLaser ? "ROBOT_LASER" : "BOARD_LASER", hit, 0, prevX, prevY, prevDir, results);
                break;
            }
        }
    }
}
