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
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("playerId", robot.getPlayerId());
            result.put("cardType", card.getType().name());
            result.put("priority", card.getPriority());
            result.put("fromX", prevX);
            result.put("fromY", prevY);
            result.put("fromDir", prevDir.name());
            result.put("toX", robot.getX());
            result.put("toY", robot.getY());
            result.put("toDir", robot.getDirection().name());
            result.put("destroyed", robot.isDestroyed());
            results.add(result);
        }

        return results;
    }
}
