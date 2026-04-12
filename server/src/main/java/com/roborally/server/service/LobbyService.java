package com.roborally.server.service;

import com.roborally.common.enums.MessageType;
import com.roborally.common.protocol.Message;
import com.roborally.server.model.Board;
import com.roborally.server.model.Lobby;
import com.roborally.server.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
    private final BoardLoader boardLoader;
    private GameService gameService;

    public LobbyService(UserService userService, SessionManager sessionManager, BoardLoader boardLoader) {
        this.userService = userService;
        this.sessionManager = sessionManager;
        this.boardLoader = boardLoader;
    }

    @Autowired
    public void setGameService(@Lazy GameService gameService) {
        this.gameService = gameService;
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

        String initialBoard = "map6";
        if (maxPlayers <= 2) initialBoard = "map1";
        else if (maxPlayers <= 4) initialBoard = "map3";
        lobby.getGameSettings().put("boardName", initialBoard);
        Board initialBoardState = boardLoader.loadBoard(initialBoard);
        lobby.getGameSettings().put("checkpoints", sanitizeCheckpointSetting(null, initialBoardState));

        lobbies.put(lobbyId, lobby);
        userLobbyMap.put(hostUserId, lobbyId);

        log.info("Lobby created: '{}' (ID: {}) by user {}", name, lobbyId, hostUserId);
        broadcastGlobalLobbyList();
        return lobby;
    }

    // ─── Join ───────────────────────────────────────────

    public Lobby joinLobby(Long userId, String lobbyId, String password) {
        Lobby lobby = getLobbyOrThrow(lobbyId);

        synchronized (lobby) {
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
        }

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
        String leavingSessionId = userService.getSessionIdByUserId(userId);

        boolean wasHost;
        synchronized (lobby) {
            if (!lobby.containsPlayer(userId)) {
                userLobbyMap.remove(userId);
                return;
            }

            wasHost = lobby.isHost(userId);
            lobby.removePlayer(userId);
            userLobbyMap.remove(userId);

            if (wasHost && lobby.getPlayerCount() > 0) {
                Long newHost = lobby.getPlayerIds().get(0);
                lobby.setHostUserId(newHost);
                log.info("Host transferred to user {} in lobby '{}'", getUsernameById(newHost), lobby.getName());
            }
        }

        if (lobby.getStatus() == Lobby.LobbyStatus.IN_GAME && gameService != null) {
            gameService.handlePlayerLeave(lobbyId, userId);
        }

        log.info("User {} left lobby '{}'", username, lobby.getName());

        if (leavingSessionId != null) {
            sessionManager.sendMessage(leavingSessionId, Message.of(MessageType.LOBBY_CLOSED, Map.of(
                    "reason", "Du hast die Lobby verlassen.",
                    "lobbyId", lobbyId)));
        }

        if (lobby.getPlayerCount() == 0) {
            // Last player left → close lobby
            closeLobby(lobbyId);
        } else {
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

        synchronized (lobby) {
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
        }

        if (lobby.getStatus() == Lobby.LobbyStatus.IN_GAME && gameService != null) {
            gameService.handlePlayerLeave(lobbyId, targetUserId);
        }

        String kickedName = getUsernameById(targetUserId);
        log.info("User {} kicked from lobby '{}' by host", kickedName, lobby.getName());

        // Notify kicked player
        String kickedSessionId = userService.getSessionIdByUserId(targetUserId);
        if (kickedSessionId != null) {
            sessionManager.sendMessage(kickedSessionId, Message.of(MessageType.LOBBY_CLOSED, Map.of(
                    "reason", "Du wurdest aus der Lobby gekickt.",
                    "lobbyId", lobbyId)));
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
            Map<String, Object> mergedSettings = new LinkedHashMap<>(lobby.getGameSettings());
            mergedSettings.putAll(settings);

            String targetBoard = normalizeBoardName(mergedSettings.get("boardName"));
            Board board = boardLoader.loadBoard(targetBoard);
            if (board.getStartPositions().size() < lobby.getMaxPlayers()) {
                throw new IllegalArgumentException("Dieses Spielbrett unterstützt nur " + board.getStartPositions().size() + " Spieler. Aktuelle Max-Spieler: " + lobby.getMaxPlayers());
            }

            mergedSettings.put("boardName", targetBoard);
            mergedSettings.put("checkpoints", sanitizeCheckpointSetting(mergedSettings.get("checkpoints"), board));

            lobby.getGameSettings().putAll(mergedSettings);
        }

        broadcastLobbyUpdate(lobby);
    }

    private String normalizeBoardName(Object boardName) {
        if (boardName instanceof String rawName && !rawName.trim().isEmpty()) {
            return rawName.trim();
        }
        return "map1";
    }

    private int sanitizeCheckpointSetting(Object requestedValue, Board board) {
        int boardCheckpoints = board.getTotalCheckpoints();
        if (boardCheckpoints <= 0) {
            return 0;
        }

        int minCheckpoints = Math.min(2, boardCheckpoints);
        if (!(requestedValue instanceof Number number)) {
            return boardCheckpoints;
        }

        int requestedCheckpoints = number.intValue();
        if (requestedCheckpoints < minCheckpoints) {
            return minCheckpoints;
        }

        return Math.min(requestedCheckpoints, boardCheckpoints);
    }

    // ─── Close / Cleanup ────────────────────────────────

    public void closeLobby(String lobbyId) {
        Lobby lobby = lobbies.remove(lobbyId);
        if (lobby == null)
            return;

        if (gameService != null) {
            gameService.cancelActiveGame(lobbyId);
        }

        lobby.setStatus(Lobby.LobbyStatus.CLOSED);

        // Remove all player mappings
        for (Long pid : lobby.getPlayerIds()) {
            userLobbyMap.remove(pid);
        }

        broadcastToLobby(lobby, Message.of(MessageType.LOBBY_CLOSED, Map.of("lobbyId", lobbyId)));
        log.info("Lobby '{}' closed", lobby.getName());
        broadcastGlobalLobbyList();
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

    public void broadcastGlobalLobbyList() {
        sessionManager.broadcastAll(Message.of(MessageType.LOBBY_LIST, Map.of(
                "lobbies", getLobbyList())));
    }

    private void broadcastLobbyUpdate(Lobby lobby) {
        Map<Long, String> usernames = getUsernameMap(lobby.getPlayerIds());
        Message update = Message.of(MessageType.LOBBY_UPDATE, Map.of("lobby", lobby.toMap(usernames)));
        broadcastToLobby(lobby, update);
        // Also inform the whole server about the updated player count / settings
        broadcastGlobalLobbyList();
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
