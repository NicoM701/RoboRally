package com.roborally.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roborally.common.protocol.Message;
import com.roborally.server.service.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionManager sessionManager;
    private final MessageRouter messageRouter;

    public GameWebSocketHandler(SessionManager sessionManager, MessageRouter messageRouter) {
        this.sessionManager = sessionManager;
        this.messageRouter = messageRouter;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.addSession(session);
        log.info("New WebSocket connection: {} (Total: {})", 
                session.getId(), sessionManager.getActiveSessionCount());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            Message message = objectMapper.readValue(textMessage.getPayload(), Message.class);
            log.debug("Received from {}: {}", session.getId(), message.getType());
            messageRouter.route(session, message);
        } catch (Exception e) {
            log.error("Error processing message from {}: {}", session.getId(), e.getMessage());
            sessionManager.sendMessage(session, Message.error("Ungültige Nachricht: " + e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket disconnected: {} (Status: {})", session.getId(), status);
        messageRouter.handleDisconnect(session);
        sessionManager.removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
    }
}
