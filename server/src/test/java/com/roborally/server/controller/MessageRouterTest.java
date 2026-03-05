package com.roborally.server.controller;

import com.roborally.common.enums.MessageType;
import com.roborally.common.protocol.Message;
import com.roborally.server.model.Lobby;
import com.roborally.server.model.User;
import com.roborally.server.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageRouter using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class MessageRouterTest {

    @Mock
    private UserService userService;
    @Mock
    private LobbyService lobbyService;
    @Mock
    private ChatService chatService;
    @Mock
    private GameService gameService;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private WebSocketSession session;

    @InjectMocks
    private MessageRouter messageRouter;

    @BeforeEach
    void setUp() {
        lenient().when(session.getId()).thenReturn("test-session-1");
    }

    // ─── Null type ──────────────────────────────────────

    @Test
    @DisplayName("Route: null type → sends error")
    void route_nullType_sendsError() {
        messageRouter.route(session, new Message(null));
        verify(sessionManager).sendMessage(eq(session), argThat(m -> m.getType() == MessageType.ERROR));
    }

    // ─── LOGIN ──────────────────────────────────────────

    @Test
    @DisplayName("Login: valid → LOGIN_SUCCESS")
    void route_login_success() {
        User mockUser = new User("player1", "p@mail.de", "hash", false);
        mockUser.setId(42L);
        when(userService.login("player1", "secret", "test-session-1")).thenReturn(mockUser);

        messageRouter.route(session,
                new Message(MessageType.LOGIN, Map.of("username", "player1", "password", "secret")));

        verify(sessionManager).sendMessage(eq(session),
                argThat(m -> m.getType() == MessageType.LOGIN_SUCCESS && m.getData().get("userId").equals(42L)));
    }

    @Test
    @DisplayName("Login: failure → ERROR")
    void route_login_failure() {
        when(userService.login(any(), any(), any())).thenThrow(new IllegalArgumentException("Ungültig."));

        messageRouter.route(session, new Message(MessageType.LOGIN, Map.of("username", "bad", "password", "wrong")));

        verify(sessionManager).sendMessage(eq(session), argThat(m -> m.getType() == MessageType.ERROR));
    }

    // ─── REGISTER ───────────────────────────────────────

    @Test
    @DisplayName("Register: valid → REGISTER_SUCCESS")
    void route_register_success() {
        User mockUser = new User("newuser", "n@mail.de", "hash", false);
        mockUser.setId(1L);
        when(userService.register("newuser", "n@mail.de", "pass")).thenReturn(mockUser);

        messageRouter.route(session, new Message(MessageType.REGISTER,
                Map.of("username", "newuser", "email", "n@mail.de", "password", "pass")));

        verify(sessionManager).sendMessage(eq(session), argThat(m -> m.getType() == MessageType.REGISTER_SUCCESS));
    }

    // ─── GUEST_LOGIN ────────────────────────────────────

    @Test
    @DisplayName("Guest login → LOGIN_SUCCESS with isGuest=true")
    void route_guestLogin_success() {
        User guest = new User("Gast_1", null, "hash", true);
        guest.setId(99L);
        when(userService.guestLogin("test-session-1")).thenReturn(guest);

        messageRouter.route(session, new Message(MessageType.GUEST_LOGIN));

        verify(sessionManager).sendMessage(eq(session), argThat(
                m -> m.getType() == MessageType.LOGIN_SUCCESS && Boolean.TRUE.equals(m.getData().get("isGuest"))));
    }

    // ─── LOGOUT ─────────────────────────────────────────

    @Test
    @DisplayName("Logout → LOGOUT_SUCCESS + lobby leave")
    void route_logout_success() {
        when(userService.getUserIdBySession("test-session-1")).thenReturn(5L);

        messageRouter.route(session, new Message(MessageType.LOGOUT));

        verify(lobbyService).leaveLobby(5L);
        verify(userService).logout("test-session-1");
        verify(sessionManager).sendMessage(eq(session), argThat(m -> m.getType() == MessageType.LOGOUT_SUCCESS));
    }

    // ─── CREATE_LOBBY ───────────────────────────────────

    @Test
    @DisplayName("Create lobby: logged in → LOBBY_UPDATE")
    void route_createLobby_success() {
        when(userService.getUserIdBySession("test-session-1")).thenReturn(10L);
        Lobby mockLobby = new Lobby("abc123", "TestLobby", 10L, 4);
        when(lobbyService.createLobby(eq(10L), eq("TestLobby"), isNull(), eq(4))).thenReturn(mockLobby);
        when(userService.getUserById(10L))
                .thenReturn(java.util.Optional.of(new User("HostUser", "h@m.de", "hash", false)));

        messageRouter.route(session,
                new Message(MessageType.CREATE_LOBBY, Map.of("name", "TestLobby", "maxPlayers", 4)));

        verify(sessionManager).sendMessage(eq(session), argThat(m -> m.getType() == MessageType.LOBBY_UPDATE));
    }

    @Test
    @DisplayName("Create lobby: not logged in → ERROR")
    void route_createLobby_notLoggedIn() {
        when(userService.getUserIdBySession("test-session-1")).thenReturn(null);

        messageRouter.route(session, new Message(MessageType.CREATE_LOBBY, Map.of("name", "Test")));

        verify(sessionManager).sendMessage(eq(session), argThat(m -> m.getType() == MessageType.ERROR));
    }

    // ─── JOIN_LOBBY ─────────────────────────────────────

    @Test
    @DisplayName("Join lobby: logged in → calls lobbyService")
    void route_joinLobby_success() {
        when(userService.getUserIdBySession("test-session-1")).thenReturn(10L);

        messageRouter.route(session, new Message(MessageType.JOIN_LOBBY, Map.of("lobbyId", "abc123")));

        verify(lobbyService).joinLobby(10L, "abc123", null);
    }

    // ─── LEAVE_LOBBY ────────────────────────────────────

    @Test
    @DisplayName("Leave lobby: calls lobbyService")
    void route_leaveLobby_success() {
        when(userService.getUserIdBySession("test-session-1")).thenReturn(10L);

        messageRouter.route(session, new Message(MessageType.LEAVE_LOBBY));

        verify(lobbyService).leaveLobby(10L);
    }

    // ─── KICK_PLAYER ────────────────────────────────────

    @Test
    @DisplayName("Kick player: valid → calls lobbyService")
    void route_kickPlayer_success() {
        when(userService.getUserIdBySession("test-session-1")).thenReturn(10L);

        messageRouter.route(session, new Message(MessageType.KICK_PLAYER, Map.of("targetUserId", 20)));

        verify(lobbyService).kickPlayer(10L, 20L);
    }

    // ─── REQUEST_LOBBY_LIST ─────────────────────────────

    @Test
    @DisplayName("Request lobby list → LOBBY_LIST")
    void route_requestLobbyList() {
        when(lobbyService.getLobbyList()).thenReturn(List.of());

        messageRouter.route(session, new Message(MessageType.REQUEST_LOBBY_LIST));

        verify(sessionManager).sendMessage(eq(session), argThat(m -> m.getType() == MessageType.LOBBY_LIST));
    }

    // ─── CHAT_MESSAGE ───────────────────────────────────

    @Test
    @DisplayName("Chat message → delegates to chatService")
    void route_chatMessage() {
        messageRouter.route(session,
                new Message(MessageType.CHAT_MESSAGE, Map.of("message", "hello", "scope", "MAIN")));

        verify(chatService).sendMessage("test-session-1", "MAIN", "hello");
    }

    // ─── Disconnect ─────────────────────────────────────

    @Test
    @DisplayName("handleDisconnect → leaves lobby + logout")
    void handleDisconnect_leavesLobbyAndLogs() {
        when(userService.getUserIdBySession("test-session-1")).thenReturn(5L);

        messageRouter.handleDisconnect(session);

        verify(lobbyService).leaveLobby(5L);
        verify(userService).logout("test-session-1");
    }

    @Test
    @DisplayName("handleDisconnect: exception caught")
    void handleDisconnect_exceptionCaught() {
        when(userService.getUserIdBySession(any())).thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> messageRouter.handleDisconnect(session));
    }

    // ─── START_GAME ─────────────────────────────────────

    @Test
    @DisplayName("Start game: logged in → calls gameService")
    void route_startGame_success() {
        when(userService.getUserIdBySession("test-session-1")).thenReturn(10L);

        messageRouter.route(session, new Message(MessageType.START_GAME));

        verify(gameService).startGame(10L);
    }

    // ─── Game stubs ─────────────────────────────────────

    @Test
    @DisplayName("Unimplemented game messages → error")
    void route_addBot_notImplemented() {
        messageRouter.route(session, new Message(MessageType.ADD_BOT));

        verify(sessionManager).sendMessage(eq(session), argThat(m -> m.getType() == MessageType.ERROR
                && m.getData().get("message").toString().contains("nicht implementiert")));
    }
}
