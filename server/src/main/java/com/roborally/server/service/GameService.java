package com.roborally.server.service;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.GamePhase;
import com.roborally.common.enums.MessageType;
import com.roborally.common.protocol.Message;
import com.roborally.server.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages game lifecycle: start, deal cards, program submission, execution,
 * cleanup.
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);
    private static final int PROGRAMMING_TIMEOUT_SECONDS = 30;

    private final CardService cardService;
    private final BoardLoader boardLoader;
    private final LobbyService lobbyService;
    private final UserService userService;
    private final SessionManager sessionManager;

    // Active games: lobbyId → GameState
    private final Map<String, GameState> games = new ConcurrentHashMap<>();

    // Timer for programming phase
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    public GameService(CardService cardService, BoardLoader boardLoader,
            LobbyService lobbyService, UserService userService,
            SessionManager sessionManager) {
        this.cardService = cardService;
        this.boardLoader = boardLoader;
        this.lobbyService = lobbyService;
        this.userService = userService;
        this.sessionManager = sessionManager;
    }

    // ══════════════════════════════════════════════════════
    // Game Lifecycle
    // ══════════════════════════════════════════════════════

    /**
     * Start a game from a lobby.
     */
    public GameState startGame(Long hostUserId) {
        Lobby lobby = lobbyService.getLobbyByUserId(hostUserId);
        if (lobby == null)
            throw new IllegalArgumentException("Du bist in keiner Lobby.");
        if (!lobby.isHost(hostUserId))
            throw new IllegalArgumentException("Nur der Host kann das Spiel starten.");
        if (lobby.getPlayerCount() < 2)
            throw new IllegalArgumentException("Mindestens 2 Spieler benötigt.");

        String lobbyId = lobby.getId();
        if (games.containsKey(lobbyId))
            throw new IllegalArgumentException("Spiel läuft bereits.");

        // Get settings
        Map<String, Object> settings = lobby.getGameSettings();
        String boardName = (String) settings.getOrDefault("boardName", "Map 1");
        int checkpoints = (int) settings.getOrDefault("checkpoints", 3);
        boolean timerEnabled = Boolean.TRUE.equals(settings.get("timerEnabled"));

        // Create game state
        GameState game = new GameState(lobbyId);
        Board board = boardLoader.loadBoard(boardName);
        if (checkpoints > 0 && checkpoints != board.getTotalCheckpoints()) {
            board.setTotalCheckpoints(checkpoints);
        }
        game.setBoard(board);

        // Initialize robots at start positions
        List<int[]> starts = board.getStartPositions();
        List<Long> players = lobby.getPlayerIds();
        for (int i = 0; i < players.size(); i++) {
            Long playerId = players.get(i);
            int[] pos = i < starts.size() ? starts.get(i) : new int[] { 1 + i, 11 };
            Robot robot = new Robot(playerId, i, pos[0], pos[1], Direction.NORTH);
            game.addRobot(playerId, robot);
        }

        // Create and shuffle deck
        List<ProgramCard> deck = cardService.createDeck();
        cardService.shuffle(deck);
        game.getDeck().addAll(deck);

        // Mark lobby as in-game
        lobby.setStatus(com.roborally.server.model.Lobby.LobbyStatus.IN_GAME);
        games.put(lobbyId, game);

        // Send initial game state
        broadcastGameState(game);
        log.info("Game started in lobby '{}' with {} players on board '{}'", lobby.getName(), players.size(),
                boardName);

        // Start first round
        startDealPhase(game, timerEnabled);

        return game;
    }

    /**
     * Deal cards to all alive robots.
     */
    private void startDealPhase(GameState game, boolean timerEnabled) {
        game.nextRound();
        game.setPhase(GamePhase.DEALING_CARDS);
        game.clearSubmissions();

        // Deal cards to each active robot
        for (Robot robot : game.getActiveRobots()) {
            List<ProgramCard> hand = cardService.deal(game.getDeck(), game.getDiscardPile(), robot);
            game.getPlayerHands().put(robot.getPlayerId(), hand);

            // Send hand to player
            String sessionId = userService.getSessionIdByUserId(robot.getPlayerId());
            if (sessionId != null) {
                List<Map<String, Object>> handData = hand.stream().map(c -> {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("id", c.getId());
                    cm.put("type", c.getType().name());
                    cm.put("priority", c.getPriority());
                    cm.put("displayName", c.getType().getDisplayName());
                    return cm;
                }).toList();

                sessionManager.sendToSession(sessionId, Message.of(MessageType.CARDS_DEALT, Map.of(
                        "cards", handData,
                        "blockedSlots", robot.getBlockedSlots(),
                        "round", game.getRound())));
            }
        }

        // Move to programming phase
        game.setPhase(GamePhase.PROGRAMMING);
        broadcastPhaseUpdate(game, "PROGRAMMING");

        // Start programming timer
        if (timerEnabled) {
            startProgrammingTimer(game);
        }

        log.info("Round {} started - cards dealt to {} players", game.getRound(), game.getActiveRobots().size());
    }

    /**
     * Handle a player's program submission.
     */
    public void submitProgram(Long playerId, List<Integer> cardIds) {
        GameState game = getGameByPlayer(playerId);
        if (game == null)
            throw new IllegalArgumentException("Du bist in keinem Spiel.");
        if (game.getPhase() != GamePhase.PROGRAMMING)
            throw new IllegalArgumentException("Nicht in der Programmierphase.");
        if (game.getSubmittedPlayers().contains(playerId))
            throw new IllegalArgumentException("Programm bereits eingereicht.");

        Robot robot = game.getRobot(playerId);
        if (robot == null || robot.isDestroyed())
            throw new IllegalArgumentException("Dein Roboter ist nicht aktiv.");

        List<ProgramCard> hand = game.getHand(playerId);
        if (hand.isEmpty())
            throw new IllegalArgumentException("Keine Karten erhalten.");

        // Map card IDs to actual cards
        List<ProgramCard> program = new ArrayList<>();
        int blockedSlots = robot.getBlockedSlots();

        for (int i = 0; i < 5 - blockedSlots; i++) {
            if (i >= cardIds.size())
                throw new IllegalArgumentException("Nicht genug Karten ausgewählt.");
            int cardId = cardIds.get(i);
            ProgramCard card = hand.stream().filter(c -> c.getId() == cardId).findFirst().orElse(null);
            if (card == null)
                throw new IllegalArgumentException("Ungültige Karten-ID: " + cardId);
            program.add(card);
        }
        // Add blocked cards from previous rounds
        for (int i = 5 - blockedSlots; i < 5; i++) {
            program.add(robot.getSlot(i));
        }

        // Validate
        String error = cardService.validateProgram(robot, program, hand, game.getRound());
        if (error != null)
            throw new IllegalArgumentException(error);

        // Set program
        for (int i = 0; i < 5; i++) {
            robot.setSlot(i, program.get(i));
        }

        // Discard unused hand cards
        for (ProgramCard card : hand) {
            if (!program.contains(card)) {
                game.getDiscardPile().add(card);
            }
        }
        game.getPlayerHands().remove(playerId);

        game.markSubmitted(playerId);
        String sessionId = userService.getSessionIdByUserId(playerId);
        if (sessionId != null) {
            sessionManager.sendToSession(sessionId, Message.of(MessageType.PROGRAMMING_PHASE_START, Map.of(
                    "status", "submitted",
                    "message", "Programm eingereicht!")));
        }

        log.info("Player {} submitted program for round {}", playerId, game.getRound());

        // Check if all players submitted
        if (game.allSubmitted()) {
            cancelTimer(game.getLobbyId());
            startExecutionPhase(game);
        }
    }

    // ══════════════════════════════════════════════════════
    // Execution Phase (Sprint 4 - stub for now)
    // ══════════════════════════════════════════════════════

    private void startExecutionPhase(GameState game) {
        game.setPhase(GamePhase.EXECUTING);
        log.info("All programs submitted. Starting execution phase for round {}", game.getRound());
        broadcastPhaseUpdate(game, "EXECUTING");

        // Sprint 4: Execute 5 steps (card movement, factory elements, etc.)
        // For now, just move to next round
        performCleanupPhase(game);
    }

    private void performCleanupPhase(GameState game) {
        game.setPhase(GamePhase.ROUND_CLEANUP);

        // Collect used cards
        for (Robot robot : game.getActiveRobots()) {
            cardService.collectUsedCards(game.getDiscardPile(), robot);
        }

        // Check for game over
        for (Robot robot : game.getRobots().values()) {
            if (robot.getNextCheckpoint() > game.getBoard().getTotalCheckpoints()) {
                endGame(game, robot.getPlayerId());
                return;
            }
        }

        // Check all dead
        if (game.getAliveRobots().isEmpty()) {
            endGameDraw(game);
            return;
        }

        // Next round
        broadcastPhaseUpdate(game, "ROUND_CLEANUP");

        boolean timerEnabled = true;
        Lobby lobby = lobbyService.getLobbyById(game.getLobbyId());
        if (lobby != null) {
            timerEnabled = Boolean.TRUE.equals(lobby.getGameSettings().get("timerEnabled"));
        }
        startDealPhase(game, timerEnabled);
    }

    private void endGame(GameState game, Long winnerId) {
        game.setPhase(GamePhase.GAME_OVER);
        cancelTimer(game.getLobbyId());

        String winnerName = userService.getUserById(winnerId)
                .map(u -> u.getUsername()).orElse("???");

        broadcastToGame(game, Message.of(MessageType.GAME_OVER, Map.of(
                "winners", List.of(Map.of("userId", winnerId, "username", winnerName)))));

        log.info("Game over! Winner: {} (ID: {})", winnerName, winnerId);
        cleanupGame(game);
    }

    private void endGameDraw(GameState game) {
        game.setPhase(GamePhase.GAME_OVER);
        cancelTimer(game.getLobbyId());

        broadcastToGame(game, Message.of(MessageType.GAME_OVER, Map.of(
                "winners", List.of())));

        log.info("Game over! All robots destroyed — draw.");
        cleanupGame(game);
    }

    private void cleanupGame(GameState game) {
        games.remove(game.getLobbyId());
        Lobby lobby = lobbyService.getLobbyById(game.getLobbyId());
        if (lobby != null) {
            lobby.setStatus(Lobby.LobbyStatus.WAITING);
        }
    }

    // ══════════════════════════════════════════════════════
    // Timer
    // ══════════════════════════════════════════════════════

    private void startProgrammingTimer(GameState game) {
        cancelTimer(game.getLobbyId());
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            log.info("Programming timer expired for lobby {}", game.getLobbyId());
            autoSubmitMissing(game);
            if (game.allSubmitted()) {
                startExecutionPhase(game);
            }
        }, PROGRAMMING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        timers.put(game.getLobbyId(), timer);
    }

    private void cancelTimer(String lobbyId) {
        ScheduledFuture<?> timer = timers.remove(lobbyId);
        if (timer != null)
            timer.cancel(false);
    }

    /**
     * Auto-submit random programs for players who didn't submit in time.
     */
    private void autoSubmitMissing(GameState game) {
        for (Robot robot : game.getActiveRobots()) {
            if (!game.getSubmittedPlayers().contains(robot.getPlayerId())) {
                List<ProgramCard> hand = game.getHand(robot.getPlayerId());
                if (hand == null || hand.isEmpty())
                    continue;

                Collections.shuffle(hand);
                int needed = 5 - robot.getBlockedSlots();
                for (int i = 0; i < Math.min(needed, hand.size()); i++) {
                    robot.setSlot(i, hand.get(i));
                }
                game.markSubmitted(robot.getPlayerId());
                log.info("Auto-submitted random program for player {}", robot.getPlayerId());
            }
        }
    }

    // ══════════════════════════════════════════════════════
    // Queries
    // ══════════════════════════════════════════════════════

    public GameState getGame(String lobbyId) {
        return games.get(lobbyId);
    }

    public GameState getGameByPlayer(Long playerId) {
        Lobby lobby = lobbyService.getLobbyByUserId(playerId);
        if (lobby == null)
            return null;
        return games.get(lobby.getId());
    }

    // ══════════════════════════════════════════════════════
    // Broadcasting
    // ══════════════════════════════════════════════════════

    private void broadcastGameState(GameState game) {
        broadcastToGame(game, Message.of(MessageType.GAME_STATE, game.toMap()));
    }

    private void broadcastPhaseUpdate(GameState game, String phase) {
        broadcastToGame(game, Message.of(MessageType.GAME_STATE, Map.of(
                "phase", phase, "round", game.getRound())));
    }

    private void broadcastToGame(GameState game, Message message) {
        for (Long playerId : game.getRobots().keySet()) {
            String sessionId = userService.getSessionIdByUserId(playerId);
            if (sessionId != null) {
                sessionManager.sendToSession(sessionId, message);
            }
        }
    }
}
