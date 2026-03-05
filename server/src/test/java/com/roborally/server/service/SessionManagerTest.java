package com.roborally.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roborally.common.protocol.Message;
import com.roborally.common.enums.MessageType;
import org.junit.jupiter.api.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionManager using Mockito mocks.
 */
class SessionManagerTest {

    private SessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
    }

    private WebSocketSession mockSession(String id, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(open);
        return session;
    }

    // ─── Add / Remove ───────────────────────────────────

    @Test
    @DisplayName("addSession: session count increases")
    void addSession_countsCorrectly() {
        assertEquals(0, sessionManager.getActiveSessionCount());

        sessionManager.addSession(mockSession("s1", true));
        assertEquals(1, sessionManager.getActiveSessionCount());

        sessionManager.addSession(mockSession("s2", true));
        assertEquals(2, sessionManager.getActiveSessionCount());
    }

    @Test
    @DisplayName("removeSession: session count decreases")
    void removeSession_countsCorrectly() {
        WebSocketSession s1 = mockSession("s1", true);
        sessionManager.addSession(s1);
        sessionManager.addSession(mockSession("s2", true));

        sessionManager.removeSession(s1);
        assertEquals(1, sessionManager.getActiveSessionCount());
    }

    @Test
    @DisplayName("getSession: returns correct session")
    void getSession_returnsCorrectSession() {
        WebSocketSession s1 = mockSession("s1", true);
        sessionManager.addSession(s1);

        assertSame(s1, sessionManager.getSession("s1"));
        assertNull(sessionManager.getSession("nonexistent"));
    }

    // ─── Send Message ───────────────────────────────────

    @Test
    @DisplayName("sendMessage(session, message): sends JSON to open session")
    void sendMessage_openSession_sendsJson() throws IOException {
        WebSocketSession session = mockSession("s1", true);
        sessionManager.addSession(session);

        Message msg = Message.of(MessageType.LOGIN_SUCCESS);
        sessionManager.sendMessage(session, msg);

        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("sendMessage: skips closed session (no exception)")
    void sendMessage_closedSession_noOp() throws IOException {
        WebSocketSession session = mockSession("s1", false);
        sessionManager.addSession(session);

        sessionManager.sendMessage(session, Message.error("test"));

        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("sendMessage: null session does not throw")
    void sendMessage_nullSession_noException() {
        assertDoesNotThrow(() -> sessionManager.sendMessage((WebSocketSession) null, Message.error("test")));
    }

    @Test
    @DisplayName("sendMessage(sessionId, message): looks up and sends")
    void sendMessage_byId_sendsCorrectly() throws IOException {
        WebSocketSession session = mockSession("s1", true);
        sessionManager.addSession(session);

        sessionManager.sendMessage("s1", Message.of(MessageType.ERROR));

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("sendMessage(sessionId): unknown ID is no-op")
    void sendMessage_unknownId_noOp() {
        assertDoesNotThrow(() -> sessionManager.sendMessage("nonexistent", Message.error("test")));
    }

    // ─── Broadcast ──────────────────────────────────────

    @Test
    @DisplayName("broadcast: sends to specified session IDs")
    void broadcast_sendsToSpecifiedSessions() throws IOException {
        WebSocketSession s1 = mockSession("s1", true);
        WebSocketSession s2 = mockSession("s2", true);
        WebSocketSession s3 = mockSession("s3", true);
        sessionManager.addSession(s1);
        sessionManager.addSession(s2);
        sessionManager.addSession(s3);

        sessionManager.broadcast(Set.of("s1", "s3"), Message.error("test"));

        verify(s1).sendMessage(any(TextMessage.class));
        verify(s2, never()).sendMessage(any());
        verify(s3).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("broadcastAll: sends to all connected sessions")
    void broadcastAll_sendsToAll() throws IOException {
        WebSocketSession s1 = mockSession("s1", true);
        WebSocketSession s2 = mockSession("s2", true);
        sessionManager.addSession(s1);
        sessionManager.addSession(s2);

        sessionManager.broadcastAll(Message.error("all"));

        verify(s1).sendMessage(any(TextMessage.class));
        verify(s2).sendMessage(any(TextMessage.class));
    }

    // ─── Error Handling ─────────────────────────────────

    @Test
    @DisplayName("sendMessage: IOException is caught, does not propagate")
    void sendMessage_ioException_caught() throws IOException {
        WebSocketSession session = mockSession("s1", true);
        doThrow(new IOException("network error")).when(session).sendMessage(any());
        sessionManager.addSession(session);

        // Should not throw
        assertDoesNotThrow(() -> sessionManager.sendMessage(session, Message.error("test")));
    }
}
