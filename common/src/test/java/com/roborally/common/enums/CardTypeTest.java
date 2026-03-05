package com.roborally.common.enums;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CardType enum.
 */
class CardTypeTest {

    @Test
    @DisplayName("Movement cards: MOVE_1, MOVE_2, MOVE_3 are movements")
    void movementCards_isMovement() {
        assertTrue(CardType.MOVE_1.isMovement());
        assertTrue(CardType.MOVE_2.isMovement());
        assertTrue(CardType.MOVE_3.isMovement());
        assertTrue(CardType.BACKUP.isMovement());
    }

    @Test
    @DisplayName("Rotation cards: TURN_LEFT, TURN_RIGHT, U_TURN are rotations")
    void rotationCards_isRotation() {
        assertTrue(CardType.TURN_LEFT.isRotation());
        assertTrue(CardType.TURN_RIGHT.isRotation());
        assertTrue(CardType.U_TURN.isRotation());
    }

    @Test
    @DisplayName("Movement cards are NOT rotations")
    void movementCards_notRotation() {
        assertFalse(CardType.MOVE_1.isRotation());
        assertFalse(CardType.MOVE_2.isRotation());
        assertFalse(CardType.MOVE_3.isRotation());
        assertFalse(CardType.BACKUP.isRotation());
    }

    @Test
    @DisplayName("Rotation cards are NOT movements")
    void rotationCards_notMovement() {
        assertFalse(CardType.TURN_LEFT.isMovement());
        assertFalse(CardType.TURN_RIGHT.isMovement());
        assertFalse(CardType.U_TURN.isMovement());
    }

    @Test
    @DisplayName("MoveSteps: correct values")
    void moveSteps_correctValues() {
        assertEquals(1, CardType.MOVE_1.getMoveSteps());
        assertEquals(2, CardType.MOVE_2.getMoveSteps());
        assertEquals(3, CardType.MOVE_3.getMoveSteps());
        assertEquals(-1, CardType.BACKUP.getMoveSteps());
        assertEquals(0, CardType.TURN_LEFT.getMoveSteps());
        assertEquals(0, CardType.TURN_RIGHT.getMoveSteps());
        assertEquals(0, CardType.U_TURN.getMoveSteps());
    }

    @Test
    @DisplayName("DisplayNames: all non-null")
    void displayNames_nonNull() {
        for (CardType ct : CardType.values()) {
            assertNotNull(ct.getDisplayName());
            assertFalse(ct.getDisplayName().isEmpty());
        }
    }

    @Test
    @DisplayName("Exactly 7 card types")
    void cardTypeCount() {
        assertEquals(7, CardType.values().length);
    }
}
