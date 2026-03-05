package com.roborally.server.service;

import com.roborally.server.model.User;
import com.roborally.server.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ChatService.
 */
@SpringBootTest
class ChatServiceTest {

    @Autowired
    private ChatService chatService;
    @Autowired
    private UserService userService;
    @Autowired
    private LobbyService lobbyService;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Main chat: does not throw for logged-in user")
    void mainChat_loggedIn_noException() {
        User user = userService.register("chatter", "c@test.de", "pass");
        userService.login("chatter", "pass", "chat-session");

        assertDoesNotThrow(() -> chatService.sendMessage("chat-session", "MAIN", "Hello World!"));
    }

    @Test
    @DisplayName("Main chat: empty message is silently ignored")
    void mainChat_emptyMessage_ignored() {
        User user = userService.register("chatter", "c@test.de", "pass");
        userService.login("chatter", "pass", "chat-session");

        assertDoesNotThrow(() -> chatService.sendMessage("chat-session", "MAIN", ""));
        assertDoesNotThrow(() -> chatService.sendMessage("chat-session", "MAIN", "   "));
        assertDoesNotThrow(() -> chatService.sendMessage("chat-session", "MAIN", null));
    }

    @Test
    @DisplayName("Main chat: not logged in → sends error (no exception)")
    void mainChat_notLoggedIn_noException() {
        assertDoesNotThrow(() -> chatService.sendMessage("unknown-session", "MAIN", "test"));
    }

    @Test
    @DisplayName("Lobby chat: not in lobby → no exception")
    void lobbyChat_notInLobby_noException() {
        User user = userService.register("chatter", "c@test.de", "pass");
        userService.login("chatter", "pass", "chat-session");

        assertDoesNotThrow(() -> chatService.sendMessage("chat-session", "LOBBY", "test"));
    }

    @Test
    @DisplayName("Lobby chat: in lobby → no exception")
    void lobbyChat_inLobby_noException() {
        User user = userService.register("chatter", "c@test.de", "pass");
        userService.login("chatter", "pass", "chat-session");
        lobbyService.createLobby(user.getId(), "TestLobby", null, 4);

        assertDoesNotThrow(() -> chatService.sendMessage("chat-session", "LOBBY", "Hello lobby!"));
    }

    @Test
    @DisplayName("Chat: message is truncated to 500 chars")
    void chat_longMessage_truncated() {
        User user = userService.register("chatter", "c@test.de", "pass");
        userService.login("chatter", "pass", "chat-session");

        String longMsg = "x".repeat(1000);
        assertDoesNotThrow(() -> chatService.sendMessage("chat-session", "MAIN", longMsg));
    }

    @Test
    @DisplayName("Chat: default scope is MAIN when null")
    void chat_nullScope_defaultsToMain() {
        User user = userService.register("chatter", "c@test.de", "pass");
        userService.login("chatter", "pass", "chat-session");

        assertDoesNotThrow(() -> chatService.sendMessage("chat-session", null, "test"));
    }
}
