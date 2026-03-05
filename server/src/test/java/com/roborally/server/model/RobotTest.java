package com.roborally.server.model;

import com.roborally.common.enums.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Robot model.
 */
class RobotTest {

    @Test
    @DisplayName("New robot: default values")
    void newRobot_defaults() {
        Robot robot = new Robot(1L, 0, 5, 10, Direction.NORTH);

        assertEquals(1L, robot.getPlayerId());
        assertEquals(0, robot.getDamage());
        assertEquals(3, robot.getLives());
        assertFalse(robot.isDestroyed());
        assertEquals(1, robot.getNextCheckpoint());
        assertEquals(9, robot.getCardsToReceive());
        assertEquals(0, robot.getBlockedSlots());
    }

    @Test
    @DisplayName("Damage: reduces cards dealt")
    void damage_reducesCards() {
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);

        robot.addDamage(3);
        assertEquals(6, robot.getCardsToReceive()); // 9 - 3

        robot.addDamage(2);
        assertEquals(4, robot.getCardsToReceive()); // 9 - 5
        assertEquals(1, robot.getBlockedSlots()); // 5 - 4 = 1 blocked
    }

    @Test
    @DisplayName("Damage: blocked slots at 5+")
    void damage_blockedSlots() {
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);

        robot.addDamage(4);
        assertEquals(0, robot.getBlockedSlots());

        robot.addDamage(1); // 5 total
        assertEquals(1, robot.getBlockedSlots());

        robot.addDamage(3); // 8 total
        assertEquals(4, robot.getBlockedSlots());
    }

    @Test
    @DisplayName("Repair: reduces damage")
    void repair_reducesDamage() {
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);
        robot.addDamage(5);

        robot.repair(2);

        assertEquals(3, robot.getDamage());
    }

    @Test
    @DisplayName("Damage: caps at 10")
    void damage_capsAt10() {
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);

        robot.addDamage(15);

        assertEquals(10, robot.getDamage());
    }

    @Test
    @DisplayName("Destroy and respawn")
    void destroy_respawn() {
        Robot robot = new Robot(1L, 0, 5, 5, Direction.NORTH);
        robot.addDamage(2);

        robot.destroy();
        assertTrue(robot.isDestroyed());

        robot.loseLife();
        assertEquals(2, robot.getLives());

        robot.respawn(3, 3, Direction.SOUTH);
        assertFalse(robot.isDestroyed());
        assertEquals(3, robot.getX());
        assertEquals(3, robot.getY());
        assertEquals(Direction.SOUTH, robot.getDirection());
        assertEquals(4, robot.getDamage()); // 2 + 2
    }

    @Test
    @DisplayName("Checkpoint: advances")
    void checkpoint_advances() {
        Robot robot = new Robot(1L, 0, 0, 0, Direction.NORTH);

        assertEquals(1, robot.getNextCheckpoint());
        robot.advanceCheckpoint();
        assertEquals(2, robot.getNextCheckpoint());
    }

    @Test
    @DisplayName("Archive: set and get")
    void archive_setGet() {
        Robot robot = new Robot(1L, 0, 5, 10, Direction.NORTH);

        assertEquals(5, robot.getArchiveX());
        assertEquals(10, robot.getArchiveY());

        robot.setArchive(3, 7);
        assertEquals(3, robot.getArchiveX());
        assertEquals(7, robot.getArchiveY());
    }

    @Test
    @DisplayName("toMap: critical fields present")
    void toMap_containsFields() {
        Robot robot = new Robot(42L, 2, 3, 4, Direction.EAST);

        var map = robot.toMap();

        assertEquals(42L, map.get("playerId"));
        assertEquals(2, map.get("robotIndex"));
        assertEquals(3, map.get("x"));
        assertEquals(4, map.get("y"));
        assertEquals("EAST", map.get("direction"));
    }
}
