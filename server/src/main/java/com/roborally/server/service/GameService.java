package com.roborally.server.service;

import com.roborally.common.enums.Direction;
import com.roborally.common.enums.GamePhase;
import com.roborally.common.enums.MessageType;
import com.roborally.common.protocol.Message;
import com.roborally.server.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages game lifecycle: start, deal cards, program submission, execution,
 * cleanup.
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);
    private static final int DEFAULT_PROGRAMMING_TIMEOUT_SECONDS = 60;

    private final CardService cardService;
    private final BoardLoader boardLoader;
    private final MovementService movementService;
    private final LobbyService lobbyService;
    private final UserService userService;
    private final SessionManager sessionManager;

    // Active games: lobbyId → GameState
    private final Map<String, GameState> games = new ConcurrentHashMap<>();

    // Timer for programming phase
    private final ScheduledThreadPoolExecutor scheduler = createScheduler();
    private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final Map<String, Long> programmingDeadlines = new ConcurrentHashMap<>();

    public GameService(CardService cardService, BoardLoader boardLoader,
            MovementService movementService, LobbyService lobbyService,
            UserService userService, SessionManager sessionManager) {
        this.cardService = cardService;
        this.boardLoader = boardLoader;
        this.movementService = movementService;
        this.lobbyService = lobbyService;
        this.userService = userService;
        this.sessionManager = sessionManager;
    }

    private ScheduledThreadPoolExecutor createScheduler() {
        AtomicInteger threadCounter = new AtomicInteger(1);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2, runnable -> {
            Thread thread = new Thread(runnable, "roborally-game-timer-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
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
        String boardName = (String) settings.getOrDefault("boardName", "map1");
        boolean timerEnabled = Boolean.TRUE.equals(settings.get("timerEnabled"));

        // Create game state
        GameState game = new GameState(lobbyId);
        Board board = boardLoader.loadBoard(boardName);
        int checkpoints = resolveCheckpointCount(settings.get("checkpoints"), board);
        if (checkpoints > 0 && checkpoints != board.getTotalCheckpoints()) {
            board.setTotalCheckpoints(checkpoints);
        }
        if (checkpoints > 0) {
            lobby.getGameSettings().put("checkpoints", checkpoints);
        }
        game.setBoard(board);

        // Ensure the selected board can safely spawn all current players.
        List<int[]> starts = board.getStartPositions();
        List<Long> players = lobby.getPlayerIds();
        if (starts.size() < players.size()) {
            throw new IllegalArgumentException(
                    "Das gewählte Board unterstützt nur " + starts.size() + " Spieler, aber in der Lobby sind " + players.size() + ".");
        }

        // Initialize robots at start positions
        for (int i = 0; i < players.size(); i++) {
            Long playerId = players.get(i);
            int[] pos;
            if (i < starts.size()) {
                pos = starts.get(i);
            } else {
                int px = i % board.getWidth();
                int py = Math.max(0, board.getHeight() - 1 - (i / board.getWidth()));
                pos = new int[] { px, py };
            }
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

    private int resolveCheckpointCount(Object configuredValue, Board board) {
        int boardCheckpoints = board.getTotalCheckpoints();
        if (boardCheckpoints <= 0) {
            return 0;
        }

        int minCheckpoints = Math.min(2, boardCheckpoints);
        if (!(configuredValue instanceof Number number)) {
            return boardCheckpoints;
        }

        int requestedCheckpoints = number.intValue();
        if (requestedCheckpoints < minCheckpoints) {
            return minCheckpoints;
        }

        return Math.min(requestedCheckpoints, boardCheckpoints);
    }

    /**
     * Deal cards to all alive robots.
     */
    private void startDealPhase(GameState game, boolean timerEnabled) {
        if (!isGameActive(game)) {
            return;
        }

        game.nextRound();
        game.setPhase(GamePhase.DEALING_CARDS);
        game.clearSubmissions();

        int timerSeconds = resolveProgrammingTimeoutSeconds(game);

        // Deal cards to each active robot
        for (Robot robot : game.getActiveRobots()) {
            List<ProgramCard> hand = cardService.deal(game.getDeck(), game.getDiscardPile(), robot);
            game.getPlayerHands().put(robot.getPlayerId(), hand);
        }

        // Move to programming phase and start the real timer before broadcasting
        game.setPhase(GamePhase.PROGRAMMING);
        Long deadlineEpochMs = timerEnabled
                ? startProgrammingTimer(game, timerSeconds)
                : null;
        if (!timerEnabled) {
            cancelTimer(game.getLobbyId());
        }

        for (Robot robot : game.getActiveRobots()) {
            List<ProgramCard> hand = game.getHand(robot.getPlayerId());

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

                Map<String, Object> dealtPayload = new LinkedHashMap<>();
                dealtPayload.put("lobbyId", game.getLobbyId());
                dealtPayload.put("gameInstanceId", game.getGameInstanceId());
                dealtPayload.put("cards", handData);
                dealtPayload.put("blockedSlots", robot.getBlockedSlots());
                dealtPayload.put("round", game.getRound());
                dealtPayload.put("timerEnabled", timerEnabled);
                dealtPayload.put("timerSeconds", timerSeconds);
                if (deadlineEpochMs != null) {
                    dealtPayload.put("deadlineEpochMs", deadlineEpochMs);
                }

                sessionManager.sendToSession(sessionId, Message.of(MessageType.CARDS_DEALT, dealtPayload));
            }
        }

        broadcastProgrammingPhaseStart(game, timerEnabled, timerSeconds, deadlineEpochMs, null, null);
        broadcastPhaseUpdate(game, "PROGRAMMING");

        log.info("Round {} started - cards dealt to {} players", game.getRound(), game.getActiveRobots().size());
    }

    /**
     * Handle a player's program submission.
     */
    public void submitProgram(Long playerId, List<Integer> cardIds) {
        GameState game = getGameByPlayer(playerId);
        if (game == null)
            throw new IllegalArgumentException("Du bist in keinem Spiel.");
            
        synchronized (game) {
            if (!isGameActive(game))
                throw new IllegalArgumentException("Das Spiel läuft nicht mehr.");
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
                    "message", "Programm eingereicht!",
                    "lobbyId", game.getLobbyId(),
                    "gameInstanceId", game.getGameInstanceId())));
        }
        broadcastProgrammingProgress(game, playerId);

            log.info("Player {} submitted program for round {}", playerId, game.getRound());

            // Check if all players submitted
            if (game.allSubmitted()) {
                cancelTimer(game.getLobbyId());
                startExecutionPhase(game);
            }
        }
    }

    // ══════════════════════════════════════════════════════
    // Execution Phase
    // ══════════════════════════════════════════════════════

    private void startExecutionPhase(GameState game) {
        if (!isGameActive(game)) {
            return;
        }

        game.setPhase(GamePhase.EXECUTING);
        log.info("All programs submitted. Starting execution phase for round {}", game.getRound());
        broadcastPhaseUpdate(game, "EXECUTING");

        // Execute 5 steps (registers)
        for (int step = 0; step < 5; step++) {
            if (game.getActiveRobots().isEmpty())
                break;

            List<Map<String, Object>> stepResults = movementService.executeStep(game, step);

            // Check for checkpoint advancement
            checkCheckpoints(game);

            // Broadcast step result for client animation
            broadcastToGame(game, Message.of(MessageType.EXECUTION_STEP, createGamePayload(game, Map.of(
                    "round", game.getRound(),
                    "step", step + 1,
                    "results", stepResults,
                    "robots", getRobotStates(game)))));

            // Check for game over immediately after evaluating checkpoints
            for (Robot robot : game.getRobots().values()) {
                if (robot.getNextCheckpoint() > game.getBoard().getTotalCheckpoints()) {
                    endGame(game, robot.getPlayerId());
                    return;
                }
            }

            // Respawn destroyed robots that have lives left
            respawnDestroyedRobots(game);

            log.debug("Step {} executed: {} movements", step + 1, stepResults.size());
        }

        performCleanupPhase(game);
    }

    /**
     * Check if any robots reached their next checkpoint.
     */
    private void checkCheckpoints(GameState game) {
        for (Robot robot : game.getActiveRobots()) {
            Tile tile = game.getBoard().getTile(robot.getX(), robot.getY());
            if (tile != null && tile.getCheckpoint() != null) {
                int cpNumber = tile.getCheckpoint().getNumber();
                if (cpNumber == robot.getNextCheckpoint()) {
                    robot.advanceCheckpoint();
                    robot.setArchive(robot.getX(), robot.getY());
                    log.info("Robot {} reached checkpoint {}", robot.getPlayerId(), cpNumber);
                }
            }
        }
    }

    /**
     * Respawn destroyed robots that still have lives.
     */
    private void respawnDestroyedRobots(GameState game) {
        for (Robot robot : game.getRobots().values()) {
            if (robot.isDestroyed() && robot.isAlive()) {
                robot.loseLife();
                if (robot.isAlive()) {
                    robot.respawn(robot.getArchiveX(), robot.getArchiveY(), Direction.NORTH);
                    log.info("Robot {} respawned at archive ({},{}) with {} lives",
                            robot.getPlayerId(), robot.getArchiveX(), robot.getArchiveY(), robot.getLives());
                } else {
                    log.info("Robot {} permanently destroyed (no lives left)", robot.getPlayerId());
                }
            }
        }
    }

    /**
     * Get current robot states for broadcasting.
     */
    private List<Map<String, Object>> getRobotStates(GameState game) {
        return game.getRobots().values().stream().map(Robot::toMap).toList();
    }

    private void performCleanupPhase(GameState game) {
        if (!isGameActive(game)) {
            return;
        }

        game.setPhase(GamePhase.ROUND_CLEANUP);

        // Collect used cards
        for (Robot robot : game.getActiveRobots()) {
            cardService.collectUsedCards(game.getDiscardPile(), robot);
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
        if (!isGameActive(game)) {
            return;
        }

        game.setPhase(GamePhase.GAME_OVER);
        cancelTimer(game.getLobbyId());

        String winnerName = userService.getUserById(winnerId)
                .map(u -> u.getUsername()).orElse("???");

        broadcastToGame(game, Message.of(MessageType.GAME_OVER, createGamePayload(game, Map.of(
                "winners", List.of(Map.of("userId", winnerId, "username", winnerName))))));

        log.info("Game over! Winner: {} (ID: {})", winnerName, winnerId);
        cleanupGame(game);
    }

    private void endGameDraw(GameState game) {
        if (!isGameActive(game)) {
            return;
        }

        game.setPhase(GamePhase.GAME_OVER);
        cancelTimer(game.getLobbyId());

        broadcastToGame(game, Message.of(MessageType.GAME_OVER, createGamePayload(game, Map.of(
                "winners", List.of()))));

        log.info("Game over! All robots destroyed — draw.");
        cleanupGame(game);
    }

    private void cleanupGame(GameState game) {
        cleanupRuntimeState(game);
        games.remove(game.getLobbyId(), game);
        Lobby lobby = lobbyService.getLobbyById(game.getLobbyId());
        if (lobby != null) {
            lobby.setStatus(Lobby.LobbyStatus.WAITING);
        }
    }

    private void cleanupRuntimeState(GameState game) {
        game.deactivate();
        cancelTimer(game.getLobbyId());
        game.clearSubmissions();
        game.getPlayerHands().clear();
        game.getDeck().clear();
        game.getDiscardPile().clear();
        for (Robot robot : game.getRobots().values()) {
            robot.clearProgram();
        }
    }

    // ══════════════════════════════════════════════════════
    // Timer
    // ══════════════════════════════════════════════════════

    private Long startProgrammingTimer(GameState game, int timeoutSeconds) {
        cancelTimer(game.getLobbyId());
        Long deadlineEpochMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        programmingDeadlines.put(game.getLobbyId(), deadlineEpochMs);
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            synchronized (game) {
                if (!isGameActive(game)) {
                    log.debug("Skipping expired timer for inactive lobby {}", game.getLobbyId());
                    return;
                }
                log.info("Programming timer expired for lobby {}", game.getLobbyId());
                autoSubmitMissing(game);
                if (game.allSubmitted()) {
                    startExecutionPhase(game);
                }
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
        timers.put(game.getLobbyId(), timer);
        return deadlineEpochMs;
    }

    private int resolveProgrammingTimeoutSeconds(GameState game) {
        Lobby lobby = lobbyService.getLobbyById(game.getLobbyId());
        if (lobby == null) {
            return DEFAULT_PROGRAMMING_TIMEOUT_SECONDS;
        }

        Object configuredTimeout = lobby.getGameSettings().get("timerSeconds");
        if (configuredTimeout instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }

        return DEFAULT_PROGRAMMING_TIMEOUT_SECONDS;
    }

    private void broadcastProgrammingPhaseStart(GameState game, boolean timerEnabled, int timerSeconds,
            Long deadlineEpochMs, Long submittedPlayerId, String submittedUsername) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lobbyId", game.getLobbyId());
        payload.put("gameInstanceId", game.getGameInstanceId());
        payload.put("status", submittedPlayerId == null ? "started" : "progress");
        payload.put("phase", "PROGRAMMING");
        payload.put("round", game.getRound());
        payload.put("timerEnabled", timerEnabled);
        payload.put("timerSeconds", timerSeconds);
        payload.put("deadlineEpochMs", deadlineEpochMs);
        payload.put("submittedCount", game.getSubmittedPlayers().size());
        payload.put("totalPlayers", game.getActiveRobots().size());
        if (submittedPlayerId != null) {
            payload.put("submittedPlayerId", submittedPlayerId);
        }
        if (submittedUsername != null) {
            payload.put("submittedUsername", submittedUsername);
        }
        broadcastToGame(game, Message.of(MessageType.PROGRAMMING_PHASE_START, payload));
    }

    private void broadcastProgrammingProgress(GameState game, Long submittedPlayerId) {
        Long deadlineEpochMs = programmingDeadlines.get(game.getLobbyId());
        Lobby lobby = lobbyService.getLobbyById(game.getLobbyId());
        boolean timerEnabled = lobby == null || Boolean.TRUE.equals(lobby.getGameSettings().get("timerEnabled"));
        int timerSeconds = resolveProgrammingTimeoutSeconds(game);
        String submittedUsername = submittedPlayerId == null ? null
                : userService.getUserById(submittedPlayerId).map(User::getUsername).orElse("???");
        broadcastProgrammingPhaseStart(game, timerEnabled, timerSeconds, deadlineEpochMs, submittedPlayerId,
                submittedUsername);
    }

    private void cancelTimer(String lobbyId) {
        ScheduledFuture<?> timer = timers.remove(lobbyId);
        programmingDeadlines.remove(lobbyId);
        if (timer != null)
            timer.cancel(false);
    }

    /**
     * Auto-submit random programs for players who didn't submit in time.
     */
    private void autoSubmitMissing(GameState game) {
        if (!isGameActive(game)) {
            return;
        }

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

    public void handlePlayerDeparture(Long playerId) {
        Lobby lobby = lobbyService.getLobbyByUserId(playerId);
        if (lobby == null) {
            return;
        }

        abortActiveGameForLobbyDeparture(lobby.getId(), playerId);
    }

    public void abortActiveGameForLobbyDeparture(String lobbyId, Long playerId) {
        if (lobbyId == null) {
            return;
        }

        GameState game = games.get(lobbyId);
        if (game == null) {
            return;
        }

        Lobby lobby = lobbyService.getLobbyById(lobbyId);
        String lobbyName = lobby != null ? lobby.getName() : lobbyId;

        synchronized (game) {
            if (!isGameActive(game)) {
                return;
            }

            log.info("Player {} left active game in lobby '{}'; aborting game and cleaning up runtime resources",
                    playerId, lobbyName);
            broadcastToGame(game, Message.error("Spiel beendet: Ein Spieler hat die Lobby verlassen."));
            cleanupGame(game);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down game service ({} active games, {} active timers)", games.size(), timers.size());

        for (GameState game : new ArrayList<>(games.values())) {
            synchronized (game) {
                cleanupGame(game);
            }
        }

        scheduler.shutdownNow();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Game scheduler did not terminate cleanly within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for game scheduler shutdown", e);
        }
    }

    private boolean isGameActive(GameState game) {
        return game != null && game.isActive() && games.get(game.getLobbyId()) == game;
    }

    // ══════════════════════════════════════════════════════
    // Broadcasting
    // ══════════════════════════════════════════════════════

    private void broadcastGameState(GameState game) {
        broadcastToGame(game, Message.of(MessageType.GAME_STATE, game.toMap()));
    }

    private void broadcastPhaseUpdate(GameState game, String phase) {
        broadcastToGame(game, Message.of(MessageType.GAME_STATE, createGamePayload(game, Map.of(
                "phase", phase, "round", game.getRound()))));
    }

    private Map<String, Object> createGamePayload(GameState game, Map<String, Object> payload) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("lobbyId", game.getLobbyId());
        message.put("gameInstanceId", game.getGameInstanceId());
        message.putAll(payload);
        return message;
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
