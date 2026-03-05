package com.roborally.server.service;

import com.roborally.common.enums.CardType;
import com.roborally.common.enums.Direction;
import com.roborally.common.enums.FieldType;
import com.roborally.server.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MovementServiceTest {

    private MovementService service;
    private GameState game;
    private Board board;

    @BeforeEach
    void setUp() {
        service = new MovementService();
        board = new Board("test", 12, 12);
        // Fill board with floor tiles
        for (int x = 0; x < 12; x++) {
            for (int y = 0; y < 12; y++) {
                board.setTile(x, y, new Tile(x, y, FieldType.FLOOR));
            }
        }
        game = new GameState("test-lobby");
        game.setBoard(board);
    }

    // ═══════════════════════════════════════
    // Wall Collision
    // ═══════════════════════════════════════

    @Test
    void wallBlocking_noWall_notBlocked() {
        assertFalse(service.isWallBlocking(board, 5, 5, Direction.NORTH));
    }

    @Test
    void wallBlocking_exitWall_blocked() {
        board.getTile(5, 5).addWall(Direction.NORTH);
        assertTrue(service.isWallBlocking(board, 5, 5, Direction.NORTH));
    }

    @Test
    void wallBlocking_entryWall_blocked() {
        board.getTile(5, 4).addWall(Direction.SOUTH);
        assertTrue(service.isWallBlocking(board, 5, 5, Direction.NORTH));
    }

    @Test
    void wallBlocking_wrongDirection_notBlocked() {
        board.getTile(5, 5).addWall(Direction.EAST);
        assertFalse(service.isWallBlocking(board, 5, 5, Direction.NORTH));
    }

    @Test
    void wallBlocking_outOfBounds_notBlocked() {
        // Moving off the board edge — no wall, just falls off
        assertFalse(service.isWallBlocking(board, 0, 0, Direction.NORTH));
    }

    // ═══════════════════════════════════════
    // Basic Movement
    // ═══════════════════════════════════════

    @Test
    void moveOneStep_success() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);

        boolean moved = service.moveOneStep(game, robot, Direction.NORTH);
        assertTrue(moved);
        assertEquals(5, robot.getX());
        assertEquals(4, robot.getY());
    }

    @Test
    void moveOneStep_blockedByWall() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);
        board.getTile(5, 5).addWall(Direction.NORTH);

        boolean moved = service.moveOneStep(game, robot, Direction.NORTH);
        assertFalse(moved);
        assertEquals(5, robot.getX());
        assertEquals(5, robot.getY()); // Didn't move
    }

    @Test
    void moveOneStep_fallsOffBoard() {
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);
        game.addRobot(1L, robot);

        service.moveOneStep(game, robot, Direction.NORTH);
        assertTrue(robot.isDestroyed());
    }

    @Test
    void moveOneStep_intopit_destroysRobot() {
        board.setTile(5, 4, new Tile(5, 4, FieldType.PIT));
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);

        service.moveOneStep(game, robot, Direction.NORTH);
        assertTrue(robot.isDestroyed());
    }

    // ═══════════════════════════════════════
    // Push Chain
    // ═══════════════════════════════════════

    @Test
    void moveOneStep_pushesRobot() {
        Robot pusher = new Robot(1L, 0, 5, 5, Direction.NORTH);
        Robot pushed = new Robot(2L, 1, 5, 4, Direction.SOUTH);
        game.addRobot(1L, pusher);
        game.addRobot(2L, pushed);

        boolean moved = service.moveOneStep(game, pusher, Direction.NORTH);
        assertTrue(moved);
        assertEquals(5, pusher.getX());
        assertEquals(4, pusher.getY());
        assertEquals(5, pushed.getX());
        assertEquals(3, pushed.getY());
    }

    @Test
    void pushChain_twoRobots() {
        Robot r1 = new Robot(1L, 0, 5, 5, Direction.NORTH);
        Robot r2 = new Robot(2L, 1, 5, 4, Direction.SOUTH);
        Robot r3 = new Robot(3L, 2, 5, 3, Direction.EAST);
        game.addRobot(1L, r1);
        game.addRobot(2L, r2);
        game.addRobot(3L, r3);

        boolean moved = service.moveOneStep(game, r1, Direction.NORTH);
        assertTrue(moved);
        assertEquals(4, r1.getY());
        assertEquals(3, r2.getY());
        assertEquals(2, r3.getY());
    }

    @Test
    void pushChain_blockedByWall() {
        Robot r1 = new Robot(1L, 0, 5, 5, Direction.NORTH);
        Robot r2 = new Robot(2L, 1, 5, 4, Direction.SOUTH);
        game.addRobot(1L, r1);
        game.addRobot(2L, r2);
        board.getTile(5, 4).addWall(Direction.NORTH); // Wall blocks push

        boolean moved = service.moveOneStep(game, r1, Direction.NORTH);
        assertFalse(moved);
        assertEquals(5, r1.getY()); // Didn't move
        assertEquals(4, r2.getY()); // Didn't move
    }

    @Test
    void pushRobot_offBoard_destroysPushed() {
        Robot r1 = new Robot(1L, 0, 5, 1, Direction.NORTH);
        Robot r2 = new Robot(2L, 1, 5, 0, Direction.SOUTH);
        game.addRobot(1L, r1);
        game.addRobot(2L, r2);

        boolean moved = service.moveOneStep(game, r1, Direction.NORTH);
        assertTrue(moved);
        assertEquals(0, r1.getY());
        assertTrue(r2.isDestroyed()); // Pushed off board
    }

    // ═══════════════════════════════════════
    // Card Execution
    // ═══════════════════════════════════════

    @Test
    void executeCard_move1() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);
        ProgramCard card = new ProgramCard(1, CardType.MOVE_1, 100);

        service.executeCard(game, robot, card);
        assertEquals(5, robot.getX());
        assertEquals(4, robot.getY());
    }

    @Test
    void executeCard_move2() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);
        ProgramCard card = new ProgramCard(1, CardType.MOVE_2, 200);

        service.executeCard(game, robot, card);
        assertEquals(3, robot.getY()); // Moved 2 steps north
    }

    @Test
    void executeCard_move3() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);
        ProgramCard card = new ProgramCard(1, CardType.MOVE_3, 300);

        service.executeCard(game, robot, card);
        assertEquals(2, robot.getY()); // Moved 3 steps north
    }

    @Test
    void executeCard_backup() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);
        ProgramCard card = new ProgramCard(1, CardType.BACKUP, 50);

        service.executeCard(game, robot, card);
        assertEquals(6, robot.getY()); // Moved south (backward) without turning
        assertEquals(Direction.NORTH, robot.getDirection()); // Still facing NORTH
    }

    @Test
    void executeCard_turnLeft() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);
        ProgramCard card = new ProgramCard(1, CardType.TURN_LEFT, 100);

        service.executeCard(game, robot, card);
        assertEquals(Direction.WEST, robot.getDirection());
        assertEquals(5, robot.getX()); // No movement
        assertEquals(5, robot.getY());
    }

    @Test
    void executeCard_turnRight() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);
        ProgramCard card = new ProgramCard(1, CardType.TURN_RIGHT, 100);

        service.executeCard(game, robot, card);
        assertEquals(Direction.EAST, robot.getDirection());
    }

    @Test
    void executeCard_uTurn() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);
        ProgramCard card = new ProgramCard(1, CardType.U_TURN, 100);

        service.executeCard(game, robot, card);
        assertEquals(Direction.SOUTH, robot.getDirection());
    }

    @Test
    void executeCard_destroyedRobot_skipped() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        robot.destroy();
        game.addRobot(1L, robot);
        ProgramCard card = new ProgramCard(1, CardType.MOVE_1, 100);

        service.executeCard(game, robot, card);
        assertEquals(5, robot.getY()); // Didn't move
    }

    @Test
    void moveForward_stoppedByWall() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);
        board.getTile(5, 4).addWall(Direction.SOUTH);

        service.moveForward(game, robot, 3);
        assertEquals(5, robot.getY()); // Stopped at wall, step 1 blocked
    }

    @Test
    void moveForward_destroyedMidMove() {
        board.setTile(5, 4, new Tile(5, 4, FieldType.PIT));
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, robot);

        service.moveForward(game, robot, 3);
        assertTrue(robot.isDestroyed()); // Fell in pit on step 1
    }

    // ═══════════════════════════════════════
    // Step Execution
    // ═══════════════════════════════════════

    @Test
    void executeStep_priorityOrdering() {
        Robot r1 = new Robot(1L, 0, 3, 5, Direction.NORTH);
        Robot r2 = new Robot(2L, 1, 7, 5, Direction.NORTH);
        game.addRobot(1L, r1);
        game.addRobot(2L, r2);

        r1.setSlot(0, new ProgramCard(1, CardType.MOVE_1, 100));
        r2.setSlot(0, new ProgramCard(2, CardType.MOVE_1, 500)); // Higher priority

        List<Map<String, Object>> results = service.executeStep(game, 0);
        assertEquals(2, results.size());
        // r2 should be first (higher priority)
        assertEquals(2L, results.get(0).get("playerId"));
        assertEquals(1L, results.get(1).get("playerId"));
    }

    @Test
    void executeStep_destroyedRobot_skipped() {
        Robot r1 = new Robot(1L, 0, 5, 5, Direction.NORTH);
        r1.destroy();
        game.addRobot(1L, r1);
        r1.setSlot(0, new ProgramCard(1, CardType.MOVE_1, 100));

        List<Map<String, Object>> results = service.executeStep(game, 0);
        assertEquals(0, results.size());
    }

    @Test
    void executeStep_noCard_skipped() {
        Robot r1 = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, r1);
        // No card set for slot 0

        List<Map<String, Object>> results = service.executeStep(game, 0);
        assertEquals(0, results.size());
    }

    @Test
    void executeStep_resultContainsAnimationData() {
        Robot r1 = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, r1);
        r1.setSlot(0, new ProgramCard(1, CardType.MOVE_1, 100));

        List<Map<String, Object>> results = service.executeStep(game, 0);
        assertEquals(1, results.size());

        Map<String, Object> result = results.get(0);
        assertEquals(1L, result.get("playerId"));
        assertEquals("MOVE_1", result.get("cardType"));
        assertEquals(100, result.get("priority"));
        assertEquals(5, result.get("fromX"));
        assertEquals(5, result.get("fromY"));
        assertEquals("NORTH", result.get("fromDir"));
        assertEquals(5, result.get("toX"));
        assertEquals(4, result.get("toY"));
        assertEquals(false, result.get("destroyed"));
    }

    // ═══════════════════════════════════════
    // Robot Queries
    // ═══════════════════════════════════════

    @Test
    void getRobotAt_found() {
        Robot r = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, r);
        assertSame(r, service.getRobotAt(game, 5, 5));
    }

    @Test
    void getRobotAt_notFound() {
        assertNull(service.getRobotAt(game, 5, 5));
    }

    @Test
    void checkDestruction_inBounds_notPit() {
        Robot r = new Robot(1L, 0, 5, 5, Direction.NORTH);
        game.addRobot(1L, r);
        service.checkDestruction(game, r);
        assertFalse(r.isDestroyed());
    }
}
