package com.roborally.server.model;

import com.roborally.common.enums.CardType;
import com.roborally.common.enums.Direction;
import com.roborally.common.enums.FieldType;
import com.roborally.common.enums.RotationDirection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TileElementsTest {

    // ═══════════════════════════════════════
    // ConveyorBelt
    // ═══════════════════════════════════════

    @Test
    void conveyorBelt_normalBelt() {
        ConveyorBelt belt = new ConveyorBelt(Direction.NORTH, false);
        assertEquals(Direction.NORTH, belt.getDirection());
        assertFalse(belt.isExpress());
        assertNull(belt.getCurveFrom());
    }

    @Test
    void conveyorBelt_expressBelt() {
        ConveyorBelt belt = new ConveyorBelt(Direction.EAST, true);
        assertTrue(belt.isExpress());
    }

    @Test
    void conveyorBelt_withCurve() {
        ConveyorBelt belt = new ConveyorBelt(Direction.NORTH, false, Direction.WEST);
        assertEquals(Direction.WEST, belt.getCurveFrom());
        assertTrue(belt.isCurve());
    }

    @Test
    void conveyorBelt_toMap() {
        ConveyorBelt belt = new ConveyorBelt(Direction.SOUTH, true, Direction.EAST);
        Map<String, Object> map = belt.toMap();
        assertEquals("SOUTH", map.get("direction"));
        assertEquals(true, map.get("express"));
        assertEquals("EAST", map.get("curveFrom"));
    }

    @Test
    void conveyorBelt_toMapNoCurve() {
        ConveyorBelt belt = new ConveyorBelt(Direction.NORTH, false);
        Map<String, Object> map = belt.toMap();
        assertFalse(map.containsKey("curveFrom"));
    }

    // ═══════════════════════════════════════
    // Pusher
    // ═══════════════════════════════════════

    @Test
    void pusher_basicProperties() {
        Pusher pusher = new Pusher(Direction.SOUTH, Set.of(1, 3, 5));
        assertEquals(Direction.SOUTH, pusher.getPushDirection());
        assertTrue(pusher.getActiveSteps().contains(1));
        assertTrue(pusher.getActiveSteps().contains(3));
        assertTrue(pusher.getActiveSteps().contains(5));
    }

    @Test
    void pusher_isActiveOnStep() {
        Pusher pusher = new Pusher(Direction.NORTH, Set.of(2, 4));
        assertTrue(pusher.isActiveOnStep(2));
        assertTrue(pusher.isActiveOnStep(4));
        assertFalse(pusher.isActiveOnStep(1));
        assertFalse(pusher.isActiveOnStep(3));
    }

    @Test
    void pusher_toMap() {
        Pusher pusher = new Pusher(Direction.EAST, Set.of(1, 3));
        Map<String, Object> map = pusher.toMap();
        assertEquals("EAST", map.get("direction"));
        assertNotNull(map.get("steps"));
    }

    // ═══════════════════════════════════════
    // Press
    // ═══════════════════════════════════════

    @Test
    void press_basicProperties() {
        Press press = new Press(Set.of(2, 4));
        assertTrue(press.getActiveSteps().contains(2));
        assertTrue(press.getActiveSteps().contains(4));
    }

    @Test
    void press_isActiveOnStep() {
        Press press = new Press(Set.of(1, 5));
        assertTrue(press.isActiveOnStep(1));
        assertTrue(press.isActiveOnStep(5));
        assertFalse(press.isActiveOnStep(3));
    }

    @Test
    void press_toMap() {
        Press press = new Press(Set.of(2));
        Map<String, Object> map = press.toMap();
        assertNotNull(map.get("steps"));
    }

    // ═══════════════════════════════════════
    // Laser
    // ═══════════════════════════════════════

    @Test
    void laser_basicProperties() {
        Laser laser = new Laser(3, 5, Direction.EAST, 2);
        assertEquals(3, laser.getX());
        assertEquals(5, laser.getY());
        assertEquals(Direction.EAST, laser.getDirection());
        assertEquals(2, laser.getStrength());
    }

    @Test
    void laser_toMap() {
        Laser laser = new Laser(1, 2, Direction.NORTH, 1);
        Map<String, Object> map = laser.toMap();
        assertEquals(1, map.get("x"));
        assertEquals(2, map.get("y"));
        assertEquals("NORTH", map.get("direction"));
        assertEquals(1, map.get("strength"));
    }

    // ═══════════════════════════════════════
    // Checkpoint
    // ═══════════════════════════════════════

    @Test
    void checkpoint_basicProperties() {
        Checkpoint cp = new Checkpoint(3);
        assertEquals(3, cp.getNumber());
    }

    @Test
    void checkpoint_toMap() {
        Checkpoint cp = new Checkpoint(2);
        Map<String, Object> map = cp.toMap();
        assertEquals(2, map.get("number"));
    }

    // ═══════════════════════════════════════
    // Gear
    // ═══════════════════════════════════════

    @Test
    void gear_clockwise() {
        Gear gear = new Gear(RotationDirection.CLOCKWISE);
        assertEquals(RotationDirection.CLOCKWISE, gear.getRotation());
    }

    @Test
    void gear_toMap() {
        Gear gear = new Gear(RotationDirection.COUNTERCLOCKWISE);
        Map<String, Object> map = gear.toMap();
        assertEquals("COUNTERCLOCKWISE", map.get("rotation"));
    }

    // ═══════════════════════════════════════
    // ProgramCard
    // ═══════════════════════════════════════

    @Test
    void programCard_basicProperties() {
        ProgramCard card = new ProgramCard(42, CardType.MOVE_2, 370);
        assertEquals(42, card.getId());
        assertEquals(CardType.MOVE_2, card.getType());
        assertEquals(370, card.getPriority());
    }

    @Test
    void programCard_blocked() {
        ProgramCard card = new ProgramCard(1, CardType.TURN_LEFT, 100);
        assertFalse(card.isBlocked());
        card.setBlocked(true);
        assertTrue(card.isBlocked());
    }

    @Test
    void programCard_assignedPlayer() {
        ProgramCard card = new ProgramCard(1, CardType.MOVE_1, 100);
        assertNull(card.getAssignedPlayerId());
        card.setAssignedPlayerId(42L);
        assertEquals(42L, card.getAssignedPlayerId());
    }

    // ═══════════════════════════════════════
    // Tile (additional coverage)
    // ═══════════════════════════════════════

    @Test
    void tile_setAndGetElements() {
        Tile tile = new Tile(5, 7, FieldType.FLOOR);
        assertEquals(5, tile.getX());
        assertEquals(7, tile.getY());
        assertEquals(FieldType.FLOOR, tile.getFieldType());

        tile.setConveyorBelt(new ConveyorBelt(Direction.NORTH, false));
        assertNotNull(tile.getConveyorBelt());

        tile.setGear(new Gear(RotationDirection.CLOCKWISE));
        assertNotNull(tile.getGear());

        tile.setPusher(new Pusher(Direction.SOUTH, Set.of(1)));
        assertNotNull(tile.getPusher());

        tile.setPress(new Press(Set.of(2)));
        assertNotNull(tile.getPress());

        tile.setCheckpoint(new Checkpoint(1));
        assertNotNull(tile.getCheckpoint());
    }

    @Test
    void tile_wallOperations() {
        Tile tile = new Tile(0, 0, FieldType.FLOOR);
        assertFalse(tile.hasWall(Direction.NORTH));
        tile.addWall(Direction.NORTH);
        assertTrue(tile.hasWall(Direction.NORTH));
        assertEquals(1, tile.getWalls().size());
    }

    @Test
    void tile_queries() {
        assertTrue(new Tile(0, 0, FieldType.PIT).isPit());
        assertFalse(new Tile(0, 0, FieldType.FLOOR).isPit());
        assertTrue(new Tile(0, 0, FieldType.REPAIR_1).isRepair());
        assertTrue(new Tile(0, 0, FieldType.REPAIR_2).isRepair());
        assertFalse(new Tile(0, 0, FieldType.FLOOR).isRepair());
        assertTrue(new Tile(0, 0, FieldType.START).isStart());
    }

    @Test
    void tile_setFieldType() {
        Tile tile = new Tile(0, 0, FieldType.FLOOR);
        tile.setFieldType(FieldType.PIT);
        assertEquals(FieldType.PIT, tile.getFieldType());
    }

    @Test
    void tile_toMap_withAllElements() {
        Tile tile = new Tile(3, 4, FieldType.FLOOR);
        tile.addWall(Direction.NORTH);
        tile.setConveyorBelt(new ConveyorBelt(Direction.EAST, true));
        tile.setGear(new Gear(RotationDirection.CLOCKWISE));
        tile.setPusher(new Pusher(Direction.SOUTH, Set.of(1)));
        tile.setPress(new Press(Set.of(2)));
        tile.setCheckpoint(new Checkpoint(1));

        Map<String, Object> map = tile.toMap();
        assertEquals(3, map.get("x"));
        assertEquals(4, map.get("y"));
        assertEquals("FLOOR", map.get("type"));
        assertNotNull(map.get("walls"));
        assertNotNull(map.get("conveyorBelt"));
        assertNotNull(map.get("gear"));
        assertNotNull(map.get("pusher"));
        assertNotNull(map.get("press"));
        assertNotNull(map.get("checkpoint"));
    }

    @Test
    void tile_toMap_noElements() {
        Tile tile = new Tile(0, 0, FieldType.FLOOR);
        Map<String, Object> map = tile.toMap();
        assertFalse(map.containsKey("walls"));
        assertFalse(map.containsKey("conveyorBelt"));
        assertFalse(map.containsKey("gear"));
    }
}
