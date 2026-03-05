package com.roborally.server.controller;

import com.roborally.common.enums.MessageType;
import com.roborally.common.protocol.Message;
import com.roborally.server.model.Lobby;
import com.roborally.server.model.User;
import com.roborally.server.service.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Routes incoming WebSocket messages to the appropriate service methods.
 */
@Component
public class MessageRouter {

    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);
    private final UserService userService;
    private final LobbyService lobbyService;
    private final ChatService chatService;
    private final GameService gameService;
    private final SessionManager sessionManager;

    public MessageRouter(UserService userService, LobbyService lobbyService,
            ChatService chatService, GameService gameService, SessionManager sessionManager) {
        this.userService = userService;
        this.lobbyService = lobbyService;
        this.chatService = chatService;
        this.gameService = gameService;
        this.sessionManager = sessionManager;
    }

    /**
     * Route a message to the appropriate handler.
     */
    public void route(WebSocketSession session, Message message) {
        if (message.getType() == null) {
            sessionManager.sendMessage(session, Message.error("Nachrichtentyp fehlt."));
            return;
        }

        try {
            switch (message.getType()) {
                // ── Auth ──
                case LOGIN -> handleLogin(session, message);
                case REGISTER -> handleRegister(session, message);
                case GUEST_LOGIN -> handleGuestLogin(session, message);
                case LOGOUT -> handleLogout(session);
                case UPDATE_USER -> handleUpdateUser(session, message);
                case DELETE_USER -> handleDeleteUser(session, message);

                // ── Lobby ──
                case CREATE_LOBBY -> handleCreateLobby(session, message);
                case JOIN_LOBBY -> handleJoinLobby(session, message);
                case LEAVE_LOBBY -> handleLeaveLobby(session);
                case KICK_PLAYER -> handleKickPlayer(session, message);
                case UPDATE_LOBBY_SETTINGS -> handleUpdateLobbySettings(session, message);
                case UPDATE_GAME_SETTINGS -> handleUpdateGameSettings(session, message);
                case REQUEST_LOBBY_LIST -> handleRequestLobbyList(session);

                // ── Chat ──
                case CHAT_MESSAGE -> handleChatMessage(session, message);

                // ── Game ──
                case START_GAME -> handleStartGame(session);
                case SUBMIT_PROGRAM -> handleSubmitProgram(session, message);

                case ADD_BOT, CHOOSE_RESPAWN_DIRECTION,
                        CHOOSE_ARCHIVE_LOCATION, TOGGLE_SHUTDOWN ->
                    sessionManager.sendMessage(session, Message.error("Feature noch nicht implementiert."));

                default ->
                    sessionManager.sendMessage(session,
                            Message.error("Unbekannter Nachrichtentyp: " + message.getType()));
            }
        } catch (IllegalArgumentException e) {
            sessionManager.sendMessage(session, Message.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error handling message {}: {}", message.getType(), e.getMessage(), e);
            sessionManager.sendMessage(session, Message.error("Interner Serverfehler."));
        }
    }

    /**
     * Handle client disconnection.
     */
    public void handleDisconnect(WebSocketSession session) {
        try {
            // Leave lobby if in one
            Long userId = userService.getUserIdBySession(session.getId());
            if (userId != null) {
                lobbyService.leaveLobby(userId);
            }
            userService.logout(session.getId());
        } catch (Exception e) {
            log.error("Error during disconnect cleanup for {}: {}", session.getId(), e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════
    // User Handlers
    // ══════════════════════════════════════════════════════

    private void handleLogin(WebSocketSession session, Message message) {
        String username = message.getString("username");
        String password = message.getString("password");

        User user = userService.login(username, password, session.getId());
        sessionManager.sendMessage(session, Message.of(MessageType.LOGIN_SUCCESS, Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "isGuest", user.isGuest())));
        log.info("User logged in: {} (ID: {})", user.getUsername(), user.getId());
    }

    private void handleRegister(WebSocketSession session, Message message) {
        String username = message.getString("username");
        String email = message.getString("email");
        String password = message.getString("password");

        User user = userService.register(username, email, password);
        sessionManager.sendMessage(session, Message.of(MessageType.REGISTER_SUCCESS, Map.of(
                "userId", user.getId(),
                "username", user.getUsername())));
        log.info("User registered: {} (ID: {})", user.getUsername(), user.getId());
    }

    private void handleGuestLogin(WebSocketSession session, Message message) {
        User guest = userService.guestLogin(session.getId());
        sessionManager.sendMessage(session, Message.of(MessageType.LOGIN_SUCCESS, Map.of(
                "userId", guest.getId(),
                "username", guest.getUsername(),
                "isGuest", true)));
        log.info("Guest logged in: {} (ID: {})", guest.getUsername(), guest.getId());
    }

    private void handleLogout(WebSocketSession session) {
        Long userId = userService.getUserIdBySession(session.getId());
        if (userId != null) {
            lobbyService.leaveLobby(userId);
        }
        userService.logout(session.getId());
        sessionManager.sendMessage(session, Message.of(MessageType.LOGOUT_SUCCESS));
        log.info("User logged out from session: {}", session.getId());
    }

    private void handleUpdateUser(WebSocketSession session, Message message) {
        Long userId = userService.getUserIdBySession(session.getId());
        if (userId == null) {
            sessionManager.sendMessage(session, Message.error("Nicht eingeloggt."));
            return;
        }

        String currentPassword = message.getString("currentPassword");
        String newUsername = message.getString("newUsername");
        String newEmail = message.getString("newEmail");
        String newPassword = message.getString("newPassword");

        User updated = userService.updateUser(userId, currentPassword, newUsername, newEmail, newPassword);
        sessionManager.sendMessage(session, Message.of(MessageType.USER_UPDATED, Map.of(
                "userId", updated.getId(),
                "username", updated.getUsername())));
    }

    private void handleDeleteUser(WebSocketSession session, Message message) {
        Long userId = userService.getUserIdBySession(session.getId());
        if (userId == null) {
            sessionManager.sendMessage(session, Message.error("Nicht eingeloggt."));
            return;
        }

        String password = message.getString("password");
        userService.deleteUser(userId, password);
        sessionManager.sendMessage(session, Message.of(MessageType.USER_DELETED));
    }

    // ══════════════════════════════════════════════════════
    // Lobby Handlers
    // ══════════════════════════════════════════════════════

    private void handleCreateLobby(WebSocketSession session, Message message) {
        Long userId = requireLogin(session);
        if (userId == null)
            return;

        String name = message.getString("name");
        String password = message.getString("password");
        Integer maxPlayers = message.getInt("maxPlayers");

        Lobby lobby = lobbyService.createLobby(userId, name, password,
                maxPlayers != null ? maxPlayers : 4);

        // Send lobby update to the creator
        Map<Long, String> usernames = Map.of(userId, getUsernameOrFallback(userId));
        sessionManager.sendMessage(session, Message.of(MessageType.LOBBY_UPDATE, Map.of(
                "lobby", lobby.toMap(usernames))));

        log.info("Lobby created: '{}' by user {}", lobby.getName(), userId);
    }

    private void handleJoinLobby(WebSocketSession session, Message message) {
        Long userId = requireLogin(session);
        if (userId == null)
            return;

        String lobbyId = message.getString("lobbyId");
        String password = message.getString("password");

        lobbyService.joinLobby(userId, lobbyId, password);
    }

    private void handleLeaveLobby(WebSocketSession session) {
        Long userId = requireLogin(session);
        if (userId == null)
            return;

        lobbyService.leaveLobby(userId);
    }

    private void handleKickPlayer(WebSocketSession session, Message message) {
        Long userId = requireLogin(session);
        if (userId == null)
            return;

        Object targetObj = message.get("targetUserId");
        Long targetUserId = targetObj instanceof Number ? ((Number) targetObj).longValue() : null;
        if (targetUserId == null) {
            sessionManager.sendMessage(session, Message.error("Ziel-Spieler nicht angegeben."));
            return;
        }

        lobbyService.kickPlayer(userId, targetUserId);
    }

    private void handleUpdateLobbySettings(WebSocketSession session, Message message) {
        Long userId = requireLogin(session);
        if (userId == null)
            return;

        String newName = message.getString("name");
        Integer newMaxPlayers = message.getInt("maxPlayers");

        lobbyService.updateLobbySettings(userId, newName, newMaxPlayers);
    }

    private void handleUpdateGameSettings(WebSocketSession session, Message message) {
        Long userId = requireLogin(session);
        if (userId == null)
            return;

        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) message.get("settings");
        lobbyService.updateGameSettings(userId, settings);
    }

    private void handleRequestLobbyList(WebSocketSession session) {
        sessionManager.sendMessage(session, Message.of(MessageType.LOBBY_LIST, Map.of(
                "lobbies", lobbyService.getLobbyList())));
    }

    // ══════════════════════════════════════════════════════
    // Chat Handler
    // ══════════════════════════════════════════════════════

    private void handleChatMessage(WebSocketSession session, Message message) {
        String text = message.getString("message");
        String scope = message.getString("scope");
        chatService.sendMessage(session.getId(), scope, text);
    }

    // ══════════════════════════════════════════════════════
    // Game Handlers
    // ══════════════════════════════════════════════════════

    private void handleStartGame(WebSocketSession session) {
        Long userId = requireLogin(session);
        if (userId == null)
            return;

        gameService.startGame(userId);
    }

    @SuppressWarnings("unchecked")
    private void handleSubmitProgram(WebSocketSession session, Message message) {
        Long userId = requireLogin(session);
        if (userId == null)
            return;

        List<Object> rawCards = (List<Object>) message.get("cardIds");
        if (rawCards == null) {
            sessionManager.sendMessage(session, Message.error("Karten-IDs fehlen."));
            return;
        }
        List<Integer> cardIds = rawCards.stream()
                .map(o -> o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(o.toString()))
                .collect(Collectors.toList());

        gameService.submitProgram(userId, cardIds);
    }

    // ══════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════

    private Long requireLogin(WebSocketSession session) {
        Long userId = userService.getUserIdBySession(session.getId());
        if (userId == null) {
            sessionManager.sendMessage(session, Message.error("Nicht eingeloggt."));
        }
        return userId;
    }

    private String getUsernameOrFallback(Long userId) {
        return userService.getUserById(userId)
                .map(User::getUsername)
                .orElse("???");
    }
}
