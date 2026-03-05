package com.roborally.server.service;

import com.roborally.common.enums.MessageType;
import com.roborally.common.protocol.Message;
import com.roborally.server.model.Lobby;
import com.roborally.server.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages game lobbies: creation, joining, leaving, kicking, settings, and
 * lifecycle.
 */
@Service
public class LobbyService {

    private static final Logger log = LoggerFactory.getLogger(LobbyService.class);

    private final Map<String, Lobby> lobbies = new ConcurrentHashMap<>();
    /** userId → lobbyId: tracks which lobby each user is in */
    private final Map<Long, String> userLobbyMap = new ConcurrentHashMap<>();

    private final UserService userService;
    private final SessionManager sessionManager;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LobbyService(UserService userService, SessionManager sessionManager) {
        this.userService = userService;
        this.sessionManager = sessionManager;
    }

    // ─── Create ─────────────────────────────────────────

    public Lobby createLobby(Long hostUserId, String name, String password, int maxPlayers) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Lobby-Name darf nicht leer sein.");
        }
        if (maxPlayers < 2 || maxPlayers > 8) {
            throw new IllegalArgumentException("Spieleranzahl muss zwischen 2 und 8 liegen.");
        }
        if (userLobbyMap.containsKey(hostUserId)) {
            throw new IllegalArgumentException("Du bist bereits in einer Lobby.");
        }

        String lobbyId = UUID.randomUUID().toString().substring(0, 8);
        Lobby lobby = new Lobby(lobbyId, name.trim(), hostUserId, maxPlayers);

        if (password != null && !password.isEmpty()) {
            lobby.setPasswordHash(passwordEncoder.encode(password));
        }

        lobbies.put(lobbyId, lobby);
        userLobbyMap.put(hostUserId, lobbyId);

        log.info("Lobby created: '{}' (ID: {}) by user {}", name, lobbyId, hostUserId);
        return lobby;
    }

    // ─── Join ───────────────────────────────────────────

    public Lobby joinLobby(Long userId, String lobbyId, String password) {
        Lobby lobby = getLobbyOrThrow(lobbyId);

        if (lobby.getStatus() != Lobby.LobbyStatus.WAITING) {
            throw new IllegalArgumentException("Die Lobby ist nicht mehr offen.");
        }
        if (lobby.containsPlayer(userId)) {
            throw new IllegalArgumentException("Du bist bereits in dieser Lobby.");
        }
        if (lobby.isFull()) {
            throw new IllegalArgumentException("Die Lobby ist voll.");
        }
        if (userLobbyMap.containsKey(userId)) {
            throw new IllegalArgumentException("Du bist bereits in einer anderen Lobby.");
        }
        if (lobby.hasPassword()) {
            if (password == null || !passwordEncoder.matches(password, lobby.getPasswordHash())) {
                throw new IllegalArgumentException("Falsches Lobby-Passwort.");
            }
        }

        lobby.addPlayer(userId);
        userLobbyMap.put(userId, lobbyId);

        String username = getUsernameById(userId);
        log.info("User {} joined lobby '{}'", username, lobby.getName());

        // Notify all lobby members of the update
        broadcastLobbyUpdate(lobby);

        // Notify the joining player
        broadcastToLobby(lobby, Message.of(MessageType.PLAYER_JOINED, Map.of(
                "userId", userId,
                "username", username)));

        return lobby;
    }

    // ─── Leave ──────────────────────────────────────────

    public void leaveLobby(Long userId) {
        String lobbyId = userLobbyMap.get(userId);
        if (lobbyId == null)
            return;

        Lobby lobby = lobbies.get(lobbyId);
        if (lobby == null) {
            userLobbyMap.remove(userId);
            return;
        }

        String username = getUsernameById(userId);
        lobby.removePlayer(userId);
        userLobbyMap.remove(userId);

        log.info("User {} left lobby '{}'", username, lobby.getName());

        if (lobby.getPlayerCount() == 0) {
            // Last player left → close lobby
            closeLobby(lobbyId);
        } else {
            // Transfer host if host left
            if (lobby.isHost(userId)) {
                Long newHost = lobby.getPlayerIds().get(0);
                lobby.setHostUserId(newHost);
                log.info("Host transferred to user {} in lobby '{}'", getUsernameById(newHost), lobby.getName());
            }

            broadcastToLobby(lobby, Message.of(MessageType.PLAYER_LEFT, Map.of(
                    "userId", userId,
                    "username", username)));
            broadcastLobbyUpdate(lobby);
        }
    }

    // ─── Kick Player ────────────────────────────────────

    public void kickPlayer(Long hostUserId, Long targetUserId) {
        String lobbyId = userLobbyMap.get(hostUserId);
        if (lobbyId == null) {
            throw new IllegalArgumentException("Du bist in keiner Lobby.");
        }

        Lobby lobby = getLobbyOrThrow(lobbyId);

        if (!lobby.isHost(hostUserId)) {
            throw new IllegalArgumentException("Nur der Host kann Spieler kicken.");
        }
        if (hostUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Du kannst dich nicht selbst kicken.");
        }
        if (!lobby.containsPlayer(targetUserId)) {
            throw new IllegalArgumentException("Spieler ist nicht in dieser Lobby.");
        }

        lobby.removePlayer(targetUserId);
        userLobbyMap.remove(targetUserId);

        String kickedName = getUsernameById(targetUserId);
        log.info("User {} kicked from lobby '{}' by host", kickedName, lobby.getName());

        // Notify kicked player
        String kickedSessionId = userService.getSessionIdByUserId(targetUserId);
        if (kickedSessionId != null) {
            sessionManager.sendMessage(kickedSessionId, Message.of(MessageType.LOBBY_CLOSED, Map.of(
                    "reason", "Du wurdest aus der Lobby gekickt.")));
        }

        broadcastLobbyUpdate(lobby);
    }

    // ─── Update Settings ────────────────────────────────

    public void updateLobbySettings(Long userId, String newName, Integer newMaxPlayers) {
        String lobbyId = userLobbyMap.get(userId);
        if (lobbyId == null)
            throw new IllegalArgumentException("Du bist in keiner Lobby.");

        Lobby lobby = getLobbyOrThrow(lobbyId);
        if (!lobby.isHost(userId))
            throw new IllegalArgumentException("Nur der Host kann Einstellungen ändern.");

        if (newName != null && !newName.trim().isEmpty()) {
            lobby.setName(newName.trim());
        }
        if (newMaxPlayers != null) {
            if (newMaxPlayers < lobby.getPlayerCount()) {
                throw new IllegalArgumentException(
                        "Neue Maximalzahl darf nicht kleiner als aktuelle Spielerzahl sein.");
            }
            if (newMaxPlayers < 2 || newMaxPlayers > 8) {
                throw new IllegalArgumentException("Spieleranzahl muss zwischen 2 und 8 liegen.");
            }
            lobby.setMaxPlayers(newMaxPlayers);
        }

        broadcastLobbyUpdate(lobby);
    }

    public void updateGameSettings(Long userId, Map<String, Object> settings) {
        String lobbyId = userLobbyMap.get(userId);
        if (lobbyId == null)
            throw new IllegalArgumentException("Du bist in keiner Lobby.");

        Lobby lobby = getLobbyOrThrow(lobbyId);
        if (!lobby.isHost(userId))
            throw new IllegalArgumentException("Nur der Host kann Spieleinstellungen ändern.");

        // Merge settings
        if (settings != null) {
            lobby.getGameSettings().putAll(settings);
        }

        broadcastLobbyUpdate(lobby);
    }

    // ─── Close / Cleanup ────────────────────────────────

    public void closeLobby(String lobbyId) {
        Lobby lobby = lobbies.remove(lobbyId);
        if (lobby == null)
            return;

        lobby.setStatus(Lobby.LobbyStatus.CLOSED);

        // Remove all player mappings
        for (Long pid : lobby.getPlayerIds()) {
            userLobbyMap.remove(pid);
        }

        broadcastToLobby(lobby, Message.of(MessageType.LOBBY_CLOSED));
        log.info("Lobby '{}' closed", lobby.getName());
    }

    // ─── Queries ────────────────────────────────────────

    public List<Map<String, Object>> getLobbyList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Lobby lobby : lobbies.values()) {
            if (lobby.getStatus() == Lobby.LobbyStatus.WAITING) {
                String hostName = getUsernameById(lobby.getHostUserId());
                list.add(lobby.toListEntry(hostName));
            }
        }
        return list;
    }

    public Lobby getLobbyByUserId(Long userId) {
        String lobbyId = userLobbyMap.get(userId);
        if (lobbyId == null)
            return null;
        return lobbies.get(lobbyId);
    }

    public Lobby getLobbyById(String lobbyId) {
        return lobbies.get(lobbyId);
    }

    public String getLobbyIdByUserId(Long userId) {
        return userLobbyMap.get(userId);
    }

    // ─── Broadcast Helpers ──────────────────────────────

    private void broadcastLobbyUpdate(Lobby lobby) {
        Map<Long, String> usernames = getUsernameMap(lobby.getPlayerIds());
        Message update = Message.of(MessageType.LOBBY_UPDATE, Map.of("lobby", lobby.toMap(usernames)));
        broadcastToLobby(lobby, update);
    }

    public void broadcastToLobby(Lobby lobby, Message message) {
        Set<String> sessionIds = new HashSet<>();
        for (Long pid : lobby.getPlayerIds()) {
            String sid = userService.getSessionIdByUserId(pid);
            if (sid != null)
                sessionIds.add(sid);
        }
        sessionManager.broadcast(sessionIds, message);
    }

    // ─── Internal Helpers ───────────────────────────────

    private Lobby getLobbyOrThrow(String lobbyId) {
        Lobby lobby = lobbies.get(lobbyId);
        if (lobby == null) {
            throw new IllegalArgumentException("Lobby nicht gefunden.");
        }
        return lobby;
    }

    private String getUsernameById(Long userId) {
        return userService.getUserById(userId)
                .map(User::getUsername)
                .orElse("???");
    }

    private Map<Long, String> getUsernameMap(List<Long> userIds) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Long uid : userIds) {
            map.put(uid, getUsernameById(uid));
        }
        return map;
    }

    /**
     * Clear all lobbies and user-lobby mappings (for testing only).
     */
    public void clearAll() {
        lobbies.clear();
        userLobbyMap.clear();
    }
}
