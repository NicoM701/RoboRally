package com.roborally.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roborally.common.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages WebSocket sessions and message broadcasting.
 */
@Service
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // All active sessions: sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void addSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("Session connected: {}", session.getId());
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session.getId());
        log.info("Session disconnected: {}", session.getId());
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Send a message to a specific session.
     */
    public void sendMessage(String sessionId, Message message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.error("Failed to send message to session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * Send a message to a specific WebSocketSession.
     */
    public void sendMessage(WebSocketSession session, Message message) {
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    /**
     * Broadcast a message to a set of session IDs.
     */
    public void broadcast(Set<String> sessionIds, Message message) {
        for (String sessionId : sessionIds) {
            sendMessage(sessionId, message);
        }
    }

    /**
     * Broadcast a message to ALL connected sessions.
     */
    public void broadcastAll(Message message) {
        for (WebSocketSession session : sessions.values()) {
            sendMessage(session, message);
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Alias for sendMessage(sessionId, message) — used by GameService.
     */
    public void sendToSession(String sessionId, Message message) {
        sendMessage(sessionId, message);
    }
}
