package com.roborally.server.service;

import com.roborally.common.enums.MessageType;
import com.roborally.common.protocol.Message;
import com.roborally.server.model.Lobby;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Chat service handling MAIN (global) and LOBBY scoped messages.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final UserService userService;
    private final LobbyService lobbyService;
    private final SessionManager sessionManager;

    public ChatService(UserService userService, LobbyService lobbyService, SessionManager sessionManager) {
        this.userService = userService;
        this.lobbyService = lobbyService;
        this.sessionManager = sessionManager;
    }

    /**
     * Send a chat message from a user.
     * 
     * @param sessionId session ID of the sender
     * @param scope     "MAIN" for global chat, "LOBBY" for lobby-only chat
     * @param text      message text
     */
    public void sendMessage(String sessionId, String scope, String text) {
        // Validate message
        if (text == null || text.trim().isEmpty()) {
            return; // silently ignore empty messages
        }

        Long userId = userService.getUserIdBySession(sessionId);
        if (userId == null) {
            sessionManager.sendMessage(sessionId, Message.error("Nicht eingeloggt."));
            return;
        }

        String username = userService.getUserById(userId)
                .map(u -> u.getUsername())
                .orElse("???");

        // Sanitize: trim and limit length
        String cleanText = text.trim();
        if (cleanText.length() > 500) {
            cleanText = cleanText.substring(0, 500);
        }

        Message chatMsg = Message.of(MessageType.CHAT_BROADCAST, Map.of(
                "from", username,
                "message", cleanText,
                "scope", scope != null ? scope : "MAIN",
                "timestamp", System.currentTimeMillis()));

        if ("LOBBY".equalsIgnoreCase(scope)) {
            // Send to lobby members only
            Lobby lobby = lobbyService.getLobbyByUserId(userId);
            if (lobby == null) {
                sessionManager.sendMessage(sessionId, Message.error("Du bist in keiner Lobby."));
                return;
            }
            lobbyService.broadcastToLobby(lobby, chatMsg);
            log.debug("Chat [LOBBY {}] {}: {}", lobby.getName(), username, cleanText);
        } else {
            // Send to all connected sessions
            sessionManager.broadcastAll(chatMsg);
            log.debug("Chat [MAIN] {}: {}", username, cleanText);
        }
    }
}
