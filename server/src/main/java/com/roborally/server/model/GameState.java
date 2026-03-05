package com.roborally.server.model;

import com.roborally.common.enums.GamePhase;

import java.util.*;

/**
 * Full game state for a running game.
 */
public class GameState {

    private final String lobbyId;
    private Board board;
    private final Map<Long, Robot> robots = new LinkedHashMap<>();
    private GamePhase phase = GamePhase.WAITING;
    private int currentStep = 0; // 1-5 during execution
    private int round = 0;
    private final List<ProgramCard> deck = new ArrayList<>();
    private final List<ProgramCard> discardPile = new ArrayList<>();
    private final Map<Long, List<ProgramCard>> playerHands = new LinkedHashMap<>();
    private final Set<Long> submittedPlayers = new HashSet<>();

    public GameState(String lobbyId) {
        this.lobbyId = lobbyId;
    }

    // ─── Getters / Setters ──────────────────────────────

    public String getLobbyId() {
        return lobbyId;
    }

    public Board getBoard() {
        return board;
    }

    public void setBoard(Board board) {
        this.board = board;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int step) {
        this.currentStep = step;
    }

    public int getRound() {
        return round;
    }

    public void nextRound() {
        this.round++;
    }

    // ─── Robots ─────────────────────────────────────────

    public Map<Long, Robot> getRobots() {
        return robots;
    }

    public Robot getRobot(Long playerId) {
        return robots.get(playerId);
    }

    public void addRobot(Long playerId, Robot robot) {
        robots.put(playerId, robot);
    }

    /** Get living (not permanently dead) robots. */
    public List<Robot> getAliveRobots() {
        return robots.values().stream().filter(Robot::isAlive).toList();
    }

    /** Get active (alive and not destroyed this round) robots. */
    public List<Robot> getActiveRobots() {
        return robots.values().stream()
                .filter(r -> r.isAlive() && !r.isDestroyed())
                .toList();
    }

    // ─── Deck + Hands ───────────────────────────────────

    public List<ProgramCard> getDeck() {
        return deck;
    }

    public List<ProgramCard> getDiscardPile() {
        return discardPile;
    }

    public Map<Long, List<ProgramCard>> getPlayerHands() {
        return playerHands;
    }

    public List<ProgramCard> getHand(Long playerId) {
        return playerHands.getOrDefault(playerId, List.of());
    }

    // ─── Submission Tracking ────────────────────────────

    public Set<Long> getSubmittedPlayers() {
        return submittedPlayers;
    }

    public void markSubmitted(Long playerId) {
        submittedPlayers.add(playerId);
    }

    public boolean allSubmitted() {
        return submittedPlayers.containsAll(
                getActiveRobots().stream().map(Robot::getPlayerId).toList());
    }

    public void clearSubmissions() {
        submittedPlayers.clear();
    }

    /**
     * Serialize game state for client.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lobbyId", lobbyId);
        map.put("phase", phase.name());
        map.put("round", round);
        map.put("currentStep", currentStep);
        map.put("board", board != null ? board.toMap() : null);
        map.put("robots", robots.values().stream().map(Robot::toMap).toList());
        return map;
    }
}
