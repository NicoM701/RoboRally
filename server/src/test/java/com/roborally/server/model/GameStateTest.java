package com.roborally.server.model;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.GamePhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {

    private GameState game;

    @BeforeEach
    void setUp() {
        game = new GameState("lobby-1");
    }

    @Test
    void constructor_setsLobbyId() {
        assertEquals("lobby-1", game.getLobbyId());
    }

    @Test
    void defaultPhase_isWaiting() {
        assertEquals(GamePhase.WAITING, game.getPhase());
    }

    @Test
    void setPhase_updatesPhase() {
        game.setPhase(GamePhase.PROGRAMMING);
        assertEquals(GamePhase.PROGRAMMING, game.getPhase());
    }

    @Test
    void nextRound_incrementsRound() {
        assertEquals(0, game.getRound());
        game.nextRound();
        assertEquals(1, game.getRound());
        game.nextRound();
        assertEquals(2, game.getRound());
    }

    @Test
    void currentStep_getSet() {
        assertEquals(0, game.getCurrentStep());
        game.setCurrentStep(3);
        assertEquals(3, game.getCurrentStep());
    }

    @Test
    void board_getSet() {
        assertNull(game.getBoard());
        Board b = new Board("test", 10, 10);
        game.setBoard(b);
        assertSame(b, game.getBoard());
    }

    @Test
    void addRobot_andGetRobot() {
        Robot r = new Robot(1L, 0, 0, 0, Direction.NORTH);
        game.addRobot(1L, r);
        assertSame(r, game.getRobot(1L));
        assertNull(game.getRobot(99L));
    }

    @Test
    void getRobots_returnsAll() {
        game.addRobot(1L, new Robot(1L, 0, 0, 0, Direction.NORTH));
        game.addRobot(2L, new Robot(2L, 1, 1, 1, Direction.SOUTH));
        assertEquals(2, game.getRobots().size());
    }

    @Test
    void getActiveRobots_excludesDestroyedAndDead() {
        Robot alive = new Robot(1L, 0, 0, 0, Direction.NORTH);
        Robot destroyed = new Robot(2L, 1, 1, 1, Direction.SOUTH);
        destroyed.destroy();
        Robot dead = new Robot(3L, 2, 2, 2, Direction.EAST);
        dead.loseLife();
        dead.loseLife();
        dead.loseLife(); // 0 lives

        game.addRobot(1L, alive);
        game.addRobot(2L, destroyed);
        game.addRobot(3L, dead);

        List<Robot> active = game.getActiveRobots();
        assertEquals(1, active.size());
        assertEquals(1L, active.get(0).getPlayerId());
    }

    @Test
    void getAliveRobots_includesDestroyedButAlive() {
        Robot alive = new Robot(1L, 0, 0, 0, Direction.NORTH);
        Robot destroyed = new Robot(2L, 1, 1, 1, Direction.SOUTH);
        destroyed.destroy(); // destroyed but still has lives
        Robot dead = new Robot(3L, 2, 2, 2, Direction.EAST);
        dead.loseLife();
        dead.loseLife();
        dead.loseLife();

        game.addRobot(1L, alive);
        game.addRobot(2L, destroyed);
        game.addRobot(3L, dead);

        List<Robot> aliveRobots = game.getAliveRobots();
        assertEquals(2, aliveRobots.size()); // 1L and 2L
    }

    @Test
    void deck_initiallyEmpty() {
        assertTrue(game.getDeck().isEmpty());
    }

    @Test
    void discardPile_initiallyEmpty() {
        assertTrue(game.getDiscardPile().isEmpty());
    }

    @Test
    void playerHands_getAndSet() {
        assertTrue(game.getPlayerHands().isEmpty());
        game.getPlayerHands().put(1L, List.of());
        assertEquals(1, game.getPlayerHands().size());
    }

    @Test
    void getHand_returnsEmptyIfNoHand() {
        assertTrue(game.getHand(99L).isEmpty());
    }

    @Test
    void submissionTracking() {
        Robot r1 = new Robot(1L, 0, 0, 0, Direction.NORTH);
        Robot r2 = new Robot(2L, 1, 1, 1, Direction.SOUTH);
        game.addRobot(1L, r1);
        game.addRobot(2L, r2);

        assertFalse(game.allSubmitted());

        game.markSubmitted(1L);
        assertFalse(game.allSubmitted());
        assertTrue(game.getSubmittedPlayers().contains(1L));

        game.markSubmitted(2L);
        assertTrue(game.allSubmitted());

        game.clearSubmissions();
        assertTrue(game.getSubmittedPlayers().isEmpty());
    }

    @Test
    void toMap_containsAllFields() {
        Board b = new Board("test", 12, 12);
        game.setBoard(b);
        game.setPhase(GamePhase.PROGRAMMING);
        game.nextRound();
        game.addRobot(1L, new Robot(1L, 0, 0, 0, Direction.NORTH));

        Map<String, Object> map = game.toMap();
        assertEquals("lobby-1", map.get("lobbyId"));
        assertEquals("PROGRAMMING", map.get("phase"));
        assertEquals(1, map.get("round"));
        assertNotNull(map.get("board"));
        assertNotNull(map.get("robots"));
    }

    @Test
    void toMap_nullBoard() {
        Map<String, Object> map = game.toMap();
        assertNull(map.get("board"));
    }
}
