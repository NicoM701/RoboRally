package com.roborally.server.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a game lobby where players gather before starting a match.
 */
public class Lobby {

    private final String id;
    private String name;
    private String passwordHash; // null = no password
    private Long hostUserId;
    private int maxPlayers;
    private final List<Long> playerIds = new CopyOnWriteArrayList<>();
    private final Map<String, Object> gameSettings = new LinkedHashMap<>();
    private LobbyStatus status = LobbyStatus.WAITING;

    public enum LobbyStatus {
        WAITING, IN_GAME, CLOSED
    }

    public Lobby(String id, String name, Long hostUserId, int maxPlayers) {
        this.id = id;
        this.name = name;
        this.hostUserId = hostUserId;
        this.maxPlayers = maxPlayers;
        this.playerIds.add(hostUserId);

        // Default game settings
        gameSettings.put("boardName", "Plan B");
        gameSettings.put("timerEnabled", true);
        gameSettings.put("timerSeconds", 60);
    }

    // ─── Getters ────────────────────────────────────────

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Long getHostUserId() {
        return hostUserId;
    }

    public void setHostUserId(Long hostUserId) {
        this.hostUserId = hostUserId;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public List<Long> getPlayerIds() {
        return Collections.unmodifiableList(playerIds);
    }

    public Map<String, Object> getGameSettings() {
        return gameSettings;
    }

    public LobbyStatus getStatus() {
        return status;
    }

    public void setStatus(LobbyStatus status) {
        this.status = status;
    }

    // ─── Player Management ──────────────────────────────

    public boolean addPlayer(Long userId) {
        if (playerIds.size() >= maxPlayers || playerIds.contains(userId)) {
            return false;
        }
        return playerIds.add(userId);
    }

    public boolean removePlayer(Long userId) {
        return playerIds.remove(userId);
    }

    public boolean containsPlayer(Long userId) {
        return playerIds.contains(userId);
    }

    public int getPlayerCount() {
        return playerIds.size();
    }

    public boolean isFull() {
        return playerIds.size() >= maxPlayers;
    }

    public boolean isHost(Long userId) {
        return hostUserId != null && hostUserId.equals(userId);
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    /**
     * Convert lobby state to a Map for JSON serialization.
     */
    public Map<String, Object> toMap(Map<Long, String> usernames) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (Long pid : playerIds) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("userId", pid);
            pm.put("username", usernames.getOrDefault(pid, "???"));
            pm.put("isHost", pid.equals(hostUserId));
            pm.put("isBot", false); // TODO: bot support in Sprint 7
            pm.put("isGuest", false); // simplified for now
            players.add(pm);
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("hostUserId", hostUserId);
        map.put("maxPlayers", maxPlayers);
        map.put("playerCount", playerIds.size());
        map.put("hasPassword", hasPassword());
        map.put("status", status.name());
        map.put("players", players);
        map.put("gameSettings", gameSettings);
        return map;
    }

    /**
     * Summary for lobby list (less detail than full toMap).
     */
    public Map<String, Object> toListEntry(String hostName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("host", hostName);
        map.put("playerCount", playerIds.size());
        map.put("maxPlayers", maxPlayers);
        map.put("hasPassword", hasPassword());
        return map;
    }
}
