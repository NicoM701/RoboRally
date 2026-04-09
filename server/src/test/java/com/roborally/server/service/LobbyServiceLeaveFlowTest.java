package com.roborally.server.service;

import com.roborally.common.enums.MessageType;
import com.roborally.server.model.Board;
import com.roborally.server.model.Lobby;
import com.roborally.server.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LobbyServiceLeaveFlowTest {

    @Mock
    private UserService userService;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private BoardLoader boardLoader;

    @InjectMocks
    private LobbyService lobbyService;

    @BeforeEach
    void setUp() {
        Board board = new Board("map3", 12, 12);
        board.addStartPosition(1, 11);
        board.addStartPosition(2, 11);
        board.addStartPosition(3, 11);
        board.addStartPosition(4, 11);
        board.setTotalCheckpoints(3);
        when(boardLoader.loadBoard("map3")).thenReturn(board);
        when(userService.getUserById(anyLong())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return Optional.of(new User("User" + userId, "u" + userId + "@test.de", "hash", false));
        });
    }

    @Test
    void leaveLobby_notifiesLeavingPlayerAndClearsMembership() {
        Lobby lobby = lobbyService.createLobby(1L, "Test", null, 4);
        lobbyService.joinLobby(2L, lobby.getId(), null);
        when(userService.getSessionIdByUserId(2L)).thenReturn("session-2");

        lobbyService.leaveLobby(2L);

        assertNull(lobbyService.getLobbyIdByUserId(2L));
        assertFalse(lobby.containsPlayer(2L));
        verify(sessionManager).sendMessage(eq("session-2"), argThat(message ->
                message.getType() == MessageType.LOBBY_CLOSED
                        && "Du hast die Lobby verlassen.".equals(message.getData().get("reason"))));
    }

    @Test
    void leaveLobby_lastPlayerStillGetsResetMessageAndLobbyCloses() {
        Lobby lobby = lobbyService.createLobby(1L, "Solo", null, 4);
        when(userService.getSessionIdByUserId(1L)).thenReturn("session-1");

        lobbyService.leaveLobby(1L);

        assertNull(lobbyService.getLobbyById(lobby.getId()));
        verify(sessionManager).sendMessage(eq("session-1"), argThat(message ->
                message.getType() == MessageType.LOBBY_CLOSED
                        && "Du hast die Lobby verlassen.".equals(message.getData().get("reason"))));
    }
}
