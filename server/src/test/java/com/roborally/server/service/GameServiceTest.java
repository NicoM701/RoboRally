package com.roborally.server.service;

import com.roborally.common.enums.CardType;
import com.roborally.common.enums.Direction;
import com.roborally.common.enums.GamePhase;
import com.roborally.server.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GameService — game lifecycle, deal, submit, execute, timer,
 * cleanup.
 */
@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private CardService cardService;
    @Mock
    private BoardLoader boardLoader;
    @Mock
    private MovementService movementService;
    @Mock
    private LobbyService lobbyService;
    @Mock
    private UserService userService;
    @Mock
    private SessionManager sessionManager;

    @InjectMocks
    private GameService gameService;

    private Lobby lobby;

    @BeforeEach
    void setUp() {
        lobby = new Lobby("lobby-1", "TestLobby", 1L, 4);
        lobby.addPlayer(2L);
    }

    @AfterEach
    void tearDown() {
        gameService.shutdown();
    }

    // ═══════════════════════════════════════
    // startGame
    // ═══════════════════════════════════════

    @Test
    void startGame_notInLobby_throws() {
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> gameService.startGame(1L));
    }

    @Test
    void startGame_notHost_throws() {
        when(lobbyService.getLobbyByUserId(2L)).thenReturn(lobby);
        assertThrows(IllegalArgumentException.class, () -> gameService.startGame(2L));
    }

    @Test
    void startGame_insufficientPlayers_throws() {
        Lobby soloLobby = new Lobby("s", "Solo", 1L, 4);
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(soloLobby);
        assertThrows(IllegalArgumentException.class, () -> gameService.startGame(1L));
    }

    @Test
    void startGame_alreadyRunning_throws() {
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        Board board = new Board("test", 12, 12);
        board.addStartPosition(1, 11);
        board.addStartPosition(2, 11);
        board.setTotalCheckpoints(3);
        when(boardLoader.loadBoard(anyString())).thenReturn(board);
        when(cardService.createDeck()).thenReturn(createMockDeck());
        // Lenient stubs for broadcasting
        lenient().when(userService.getSessionIdByUserId(anyLong())).thenReturn(null);
        lenient().when(movementService.executeStep(any(), anyInt())).thenReturn(List.of());

        // Start the first game
        gameService.startGame(1L);

        // Second start should throw
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        assertThrows(IllegalArgumentException.class, () -> gameService.startGame(1L));
    }

    @Test
    void startGame_tooManyPlayersForBoard_throws() {
        lobby.addPlayer(3L);
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        Board board = new Board("test", 12, 12);
        board.addStartPosition(1, 11);
        board.addStartPosition(2, 11);
        board.setTotalCheckpoints(3);
        when(boardLoader.loadBoard(anyString())).thenReturn(board);

        assertThrows(IllegalArgumentException.class, () -> gameService.startGame(1L));
    }

    @Test
    void startGame_success_createsGameAndDeals() {
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        Board board = new Board("test", 12, 12);
        board.addStartPosition(1, 11);
        board.addStartPosition(2, 11);
        board.setTotalCheckpoints(3);
        when(boardLoader.loadBoard(anyString())).thenReturn(board);
        when(cardService.createDeck()).thenReturn(createMockDeck());
        when(userService.getSessionIdByUserId(anyLong())).thenReturn("session-1");
        lenient().when(movementService.executeStep(any(), anyInt())).thenReturn(List.of());
        when(cardService.deal(any(), any(), any())).thenReturn(createMockHand());

        GameState game = gameService.startGame(1L);

        assertNotNull(game);
        assertEquals(GamePhase.PROGRAMMING, game.getPhase()); // Should be in programming after dealing
        assertEquals(1, game.getRound());
        assertEquals(2, game.getRobots().size());
        verify(cardService).createDeck();
        verify(cardService).shuffle(any());
    }

    @Test
    void startGame_missingCheckpoints_defaultsToBoardTotal() {
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        Board board = new Board("test", 12, 12);
        board.addStartPosition(1, 11);
        board.addStartPosition(2, 11);
        board.setTotalCheckpoints(2);
        when(boardLoader.loadBoard(anyString())).thenReturn(board);
        when(cardService.createDeck()).thenReturn(createMockDeck());
        lenient().when(userService.getSessionIdByUserId(anyLong())).thenReturn(null);
        lenient().when(movementService.executeStep(any(), anyInt())).thenReturn(List.of());
        when(cardService.deal(any(), any(), any())).thenReturn(createMockHand());

        GameState game = gameService.startGame(1L);
        assertEquals(2, game.getBoard().getTotalCheckpoints());
        assertEquals(2, lobby.getGameSettings().get("checkpoints"));
    }

    @Test
    void startGame_excessiveCheckpoints_clampsToBoardTotal() {
        lobby.getGameSettings().put("checkpoints", 5);
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        Board board = new Board("test", 12, 12);
        board.addStartPosition(1, 11);
        board.addStartPosition(2, 11);
        board.setTotalCheckpoints(2);
        when(boardLoader.loadBoard(anyString())).thenReturn(board);
        when(cardService.createDeck()).thenReturn(createMockDeck());
        lenient().when(userService.getSessionIdByUserId(anyLong())).thenReturn(null);
        lenient().when(movementService.executeStep(any(), anyInt())).thenReturn(List.of());
        when(cardService.deal(any(), any(), any())).thenReturn(createMockHand());

        GameState game = gameService.startGame(1L);
        assertEquals(2, game.getBoard().getTotalCheckpoints());
        assertEquals(2, lobby.getGameSettings().get("checkpoints"));
    }

    @Test
    void startGame_validCustomCheckpointsBelowBoardTotal_setsOnBoard() {
        lobby.getGameSettings().put("checkpoints", 5);
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        Board board = new Board("test", 12, 12);
        board.addStartPosition(1, 11);
        board.addStartPosition(2, 11);
        board.setTotalCheckpoints(6);
        when(boardLoader.loadBoard(anyString())).thenReturn(board);
        when(cardService.createDeck()).thenReturn(createMockDeck());
        lenient().when(userService.getSessionIdByUserId(anyLong())).thenReturn(null);
        lenient().when(movementService.executeStep(any(), anyInt())).thenReturn(List.of());
        when(cardService.deal(any(), any(), any())).thenReturn(createMockHand());

        GameState game = gameService.startGame(1L);
        assertEquals(5, game.getBoard().getTotalCheckpoints());
    }

    // ═══════════════════════════════════════
    // submitProgram
    // ═══════════════════════════════════════

    @Test
    void submitProgram_notInGame_throws() {
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> gameService.submitProgram(1L, List.of(1, 2, 3, 4, 5)));
    }

    @Test
    void submitProgram_wrongPhase_throws() {
        GameState game = startTestGame();
        game.setPhase(GamePhase.EXECUTING);
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);

        assertThrows(IllegalArgumentException.class,
                () -> gameService.submitProgram(1L, List.of(1, 2, 3, 4, 5)));
    }

    @Test
    void submitProgram_alreadySubmitted_throws() {
        GameState game = startTestGame();
        game.markSubmitted(1L);
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);

        assertThrows(IllegalArgumentException.class,
                () -> gameService.submitProgram(1L, List.of(1, 2, 3, 4, 5)));
    }

    @Test
    void submitProgram_destroyedRobot_throws() {
        GameState game = startTestGame();
        game.getRobot(1L).destroy();
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);

        assertThrows(IllegalArgumentException.class,
                () -> gameService.submitProgram(1L, List.of(1, 2, 3, 4, 5)));
    }

    @Test
    void submitProgram_invalidCardId_throws() {
        GameState game = startTestGame();
        game.getPlayerHands().put(1L, createMockHand());
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);

        // Card ID 999 not in hand
        assertThrows(IllegalArgumentException.class,
                () -> gameService.submitProgram(1L, List.of(1, 2, 3, 4, 999)));
    }

    @Test
    void submitProgram_notEnoughCards_throws() {
        GameState game = startTestGame();
        game.getPlayerHands().put(1L, createMockHand());
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);

        // Only 3 cards but need 5
        assertThrows(IllegalArgumentException.class,
                () -> gameService.submitProgram(1L, List.of(1, 2, 3)));
    }

    @Test
    void submitProgram_success_setsProgram() {
        GameState game = startTestGame();
        List<ProgramCard> hand = createMockHand();
        game.getPlayerHands().put(1L, hand);
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        when(cardService.validateProgram(any(), any(), any(), anyInt())).thenReturn(null);
        when(userService.getSessionIdByUserId(1L)).thenReturn("session-1");

        gameService.submitProgram(1L, List.of(1, 2, 3, 4, 5));

        assertTrue(game.getSubmittedPlayers().contains(1L));
        Robot robot = game.getRobot(1L);
        assertNotNull(robot.getSlot(0));
        assertNotNull(robot.getSlot(4));
    }

    @Test
    void submitProgram_validationFails_throws() {
        GameState game = startTestGame();
        game.getPlayerHands().put(1L, createMockHand());
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        when(cardService.validateProgram(any(), any(), any(), anyInt())).thenReturn("Fehler");

        assertThrows(IllegalArgumentException.class,
                () -> gameService.submitProgram(1L, List.of(1, 2, 3, 4, 5)));
    }

    @Test
    void submitProgram_allSubmitted_startsExecution() {
        GameState game = startTestGame();
        // Submit for player 2 first
        game.markSubmitted(2L);
        Robot r2 = game.getRobot(2L);
        for (int i = 0; i < 5; i++) {
            r2.setSlot(i, new ProgramCard(20 + i, CardType.MOVE_1, 100 + i));
        }

        List<ProgramCard> hand = createMockHand();
        game.getPlayerHands().put(1L, hand);
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        when(cardService.validateProgram(any(), any(), any(), anyInt())).thenReturn(null);
        when(userService.getSessionIdByUserId(anyLong())).thenReturn("session-1");
        when(movementService.executeStep(any(), anyInt())).thenReturn(List.of());
        lenient().when(lobbyService.getLobbyById(anyString())).thenReturn(lobby);

        gameService.submitProgram(1L, List.of(1, 2, 3, 4, 5));

        // After all submitted, execution should complete and next round starts
        assertTrue(game.getRound() >= 1);
        verify(movementService, times(5)).executeStep(any(), anyInt());
    }

    // ═══════════════════════════════════════
    // Queries
    // ═══════════════════════════════════════

    @Test
    void getGame_noGame_returnsNull() {
        assertNull(gameService.getGame("nonexistent"));
    }

    @Test
    void getGameByPlayer_noLobby_returnsNull() {
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(null);
        assertNull(gameService.getGameByPlayer(1L));
    }

    @Test
    void getGameByPlayer_noGame_returnsNull() {
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        assertNull(gameService.getGameByPlayer(1L));
    }

    @Test
    void getGame_afterStart_returnsGame() {
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        Board board = new Board("test", 12, 12);
        board.addStartPosition(1, 11);
        board.addStartPosition(2, 11);
        board.setTotalCheckpoints(3);
        when(boardLoader.loadBoard(anyString())).thenReturn(board);
        when(cardService.createDeck()).thenReturn(createMockDeck());
        lenient().when(userService.getSessionIdByUserId(anyLong())).thenReturn(null);
        lenient().when(movementService.executeStep(any(), anyInt())).thenReturn(List.of());
        when(cardService.deal(any(), any(), any())).thenReturn(createMockHand());

        gameService.startGame(1L);

        GameState game = gameService.getGame(lobby.getId());
        assertNotNull(game);
    }

    // ═══════════════════════════════════════
    // Execution + Checkpoints + Respawn
    // ═══════════════════════════════════════

    @Test
    void execution_robotDestroyed_respawns() {
        GameState game = startTestGame();
        Robot robot = game.getRobot(1L);
        assertEquals(3, robot.getLives());
        assertFalse(robot.isDestroyed());

        // Simulate what happens when a robot is destroyed during execution
        robot.destroy();
        assertTrue(robot.isDestroyed());
        assertTrue(robot.isAlive()); // Still has lives

        // After respawn
        robot.loseLife();
        robot.respawn(1, 11, Direction.NORTH);
        assertFalse(robot.isDestroyed());
        assertEquals(2, robot.getLives());
    }

    // ═══════════════════════════════════════
    // Game Over scenarios
    // ═══════════════════════════════════════

    @Test
    void gameOver_allDead_draw() {
        GameState game = startTestGame();
        // Kill all robots
        for (Robot robot : game.getRobots().values()) {
            robot.loseLife();
            robot.loseLife();
            robot.loseLife();
        }
        assertTrue(game.getAliveRobots().isEmpty());
    }

    @Test
    void gameOver_winnerReachesAllCheckpoints() {
        GameState game = startTestGame();
        game.getBoard().setTotalCheckpoints(2);
        Robot robot = game.getRobot(1L);
        robot.advanceCheckpoint();
        robot.advanceCheckpoint();
        // Robot next checkpoint is now 3, which > totalCheckpoints(2) → winner!
        assertTrue(robot.getNextCheckpoint() > game.getBoard().getTotalCheckpoints());
    }

    // ═══════════════════════════════════════
    // Timer behavior
    // ═══════════════════════════════════════

    @Test
    void startGame_withTimerEnabled() {
        lobby.getGameSettings().put("timerEnabled", true);
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        Board board = new Board("test", 12, 12);
        board.addStartPosition(1, 11);
        board.addStartPosition(2, 11);
        board.setTotalCheckpoints(3);
        when(boardLoader.loadBoard(anyString())).thenReturn(board);
        when(cardService.createDeck()).thenReturn(createMockDeck());
        lenient().when(userService.getSessionIdByUserId(anyLong())).thenReturn(null);
        lenient().when(movementService.executeStep(any(), anyInt())).thenReturn(List.of());
        when(cardService.deal(any(), any(), any())).thenReturn(createMockHand());

        GameState game = gameService.startGame(1L);
        assertNotNull(game);
        assertEquals(GamePhase.PROGRAMMING, game.getPhase());
    }

    @Test
    void handlePlayerDeparture_activeGame_abortsAndCleansUp() {
        GameState game = startTestGame();
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        when(userService.getSessionIdByUserId(1L)).thenReturn("session-1");
        when(userService.getSessionIdByUserId(2L)).thenReturn("session-2");
        when(lobbyService.getLobbyById(lobby.getId())).thenReturn(lobby);

        gameService.handlePlayerDeparture(1L);

        assertNull(gameService.getGame(lobby.getId()));
        assertFalse(game.isActive());
        assertEquals(Lobby.LobbyStatus.WAITING, lobby.getStatus());
        assertTrue(game.getPlayerHands().isEmpty());
        assertTrue(game.getDeck().isEmpty());
        verify(sessionManager).sendToSession(eq("session-1"), argThat(m -> m.getType() == com.roborally.common.enums.MessageType.ERROR));
        verify(sessionManager).sendToSession(eq("session-2"), argThat(m -> m.getType() == com.roborally.common.enums.MessageType.ERROR));
    }

    @Test
    void shutdown_cleansGamesTimersAndScheduler() {
        lobby.getGameSettings().put("timerEnabled", true);
        GameState game = startTestGame();

        gameService.shutdown();

        assertNull(gameService.getGame(lobby.getId()));
        assertFalse(game.isActive());
        assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(gameService, "timers")).isEmpty());
        ScheduledThreadPoolExecutor scheduler =
                (ScheduledThreadPoolExecutor) ReflectionTestUtils.getField(gameService, "scheduler");
        assertNotNull(scheduler);
        assertTrue(scheduler.isShutdown());
    }

    @Test
    void startGame_insufficientStartPositions_throws() {
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        Board board = new Board("test", 12, 12);
        // Only 1 start position for 2 players
        board.addStartPosition(1, 11);
        board.setTotalCheckpoints(3);
        when(boardLoader.loadBoard(anyString())).thenReturn(board);

        assertThrows(IllegalArgumentException.class, () -> gameService.startGame(1L));
    }

    // ═══════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════

    private GameState startTestGame() {
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(lobby);
        Board board = new Board("test", 12, 12);
        board.addStartPosition(1, 11);
        board.addStartPosition(2, 11);
        board.setTotalCheckpoints(3);
        when(boardLoader.loadBoard(anyString())).thenReturn(board);
        when(cardService.createDeck()).thenReturn(createMockDeck());
        lenient().when(userService.getSessionIdByUserId(anyLong())).thenReturn(null);
        lenient().when(movementService.executeStep(any(), anyInt())).thenReturn(List.of());
        lenient().when(cardService.deal(any(), any(), any())).thenReturn(createMockHand());
        lenient().when(lobbyService.getLobbyById(anyString())).thenReturn(lobby);

        return gameService.startGame(1L);
    }

    private List<ProgramCard> createMockDeck() {
        List<ProgramCard> deck = new ArrayList<>();
        int id = 1;
        for (CardType type : CardType.values()) {
            for (int i = 0; i < 12; i++) {
                deck.add(new ProgramCard(id++, type, id * 10));
            }
        }
        return deck;
    }

    private List<ProgramCard> createMockHand() {
        List<ProgramCard> hand = new ArrayList<>();
        hand.add(new ProgramCard(1, CardType.MOVE_1, 100));
        hand.add(new ProgramCard(2, CardType.MOVE_2, 200));
        hand.add(new ProgramCard(3, CardType.MOVE_3, 300));
        hand.add(new ProgramCard(4, CardType.TURN_LEFT, 400));
        hand.add(new ProgramCard(5, CardType.TURN_RIGHT, 500));
        hand.add(new ProgramCard(6, CardType.U_TURN, 600));
        hand.add(new ProgramCard(7, CardType.BACKUP, 700));
        hand.add(new ProgramCard(8, CardType.MOVE_1, 800));
        hand.add(new ProgramCard(9, CardType.MOVE_1, 900));
        return hand;
    }
}
