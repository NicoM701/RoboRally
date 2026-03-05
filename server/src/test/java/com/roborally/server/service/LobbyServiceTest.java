package com.roborally.server.service;

import com.roborally.server.model.Lobby;
import com.roborally.server.model.User;
import com.roborally.server.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LobbyService with real UserService + SessionManager.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LobbyServiceTest {

    @Autowired
    private LobbyService lobbyService;
    @Autowired
    private UserService userService;
    @Autowired
    private SessionManager sessionManager;
    @Autowired
    private UserRepository userRepository;

    private Long hostId;
    private Long player2Id;
    private Long player3Id;

    @BeforeEach
    void setUp() {
        lobbyService.clearAll();
        userRepository.deleteAll();
        // Create 3 test users
        User host = userService.register("HostUser", "host@test.de", "pass");
        userService.login("HostUser", "pass", "session-host");
        hostId = host.getId();

        User p2 = userService.register("Player2", "p2@test.de", "pass");
        userService.login("Player2", "pass", "session-p2");
        player2Id = p2.getId();

        User p3 = userService.register("Player3", "p3@test.de", "pass");
        userService.login("Player3", "pass", "session-p3");
        player3Id = p3.getId();
    }

    // ─── Create Lobby ───────────────────────────────────

    @Test
    @DisplayName("Create: valid lobby")
    void createLobby_valid() {
        Lobby lobby = lobbyService.createLobby(hostId, "TestLobby", null, 4);

        assertNotNull(lobby.getId());
        assertEquals("TestLobby", lobby.getName());
        assertEquals(hostId, lobby.getHostUserId());
        assertEquals(4, lobby.getMaxPlayers());
        assertEquals(1, lobby.getPlayerCount());
        assertTrue(lobby.containsPlayer(hostId));
        assertFalse(lobby.hasPassword());
    }

    @Test
    @DisplayName("Create: with password")
    void createLobby_withPassword() {
        Lobby lobby = lobbyService.createLobby(hostId, "Private", "secret", 4);

        assertTrue(lobby.hasPassword());
    }

    @Test
    @DisplayName("Create: empty name → exception")
    void createLobby_emptyName_throws() {
        assertThrows(IllegalArgumentException.class, () -> lobbyService.createLobby(hostId, "", null, 4));
    }

    @Test
    @DisplayName("Create: invalid player count → exception")
    void createLobby_invalidPlayerCount_throws() {
        assertThrows(IllegalArgumentException.class, () -> lobbyService.createLobby(hostId, "Test", null, 1));
        assertThrows(IllegalArgumentException.class, () -> lobbyService.createLobby(hostId, "Test", null, 9));
    }

    @Test
    @DisplayName("Create: already in lobby → exception")
    void createLobby_alreadyInLobby_throws() {
        lobbyService.createLobby(hostId, "Lobby1", null, 4);

        assertThrows(IllegalArgumentException.class, () -> lobbyService.createLobby(hostId, "Lobby2", null, 4));
    }

    // ─── Join Lobby ─────────────────────────────────────

    @Test
    @DisplayName("Join: valid join")
    void joinLobby_valid() {
        Lobby lobby = lobbyService.createLobby(hostId, "Test", null, 4);

        lobbyService.joinLobby(player2Id, lobby.getId(), null);

        assertEquals(2, lobby.getPlayerCount());
        assertTrue(lobby.containsPlayer(player2Id));
    }

    @Test
    @DisplayName("Join: with correct password")
    void joinLobby_correctPassword() {
        Lobby lobby = lobbyService.createLobby(hostId, "Private", "mypass", 4);

        lobbyService.joinLobby(player2Id, lobby.getId(), "mypass");

        assertEquals(2, lobby.getPlayerCount());
    }

    @Test
    @DisplayName("Join: wrong password → exception")
    void joinLobby_wrongPassword_throws() {
        Lobby lobby = lobbyService.createLobby(hostId, "Private", "correct", 4);

        assertThrows(IllegalArgumentException.class, () -> lobbyService.joinLobby(player2Id, lobby.getId(), "wrong"));
    }

    @Test
    @DisplayName("Join: lobby full → exception")
    void joinLobby_full_throws() {
        Lobby lobby = lobbyService.createLobby(hostId, "Small", null, 2);
        lobbyService.joinLobby(player2Id, lobby.getId(), null);

        assertThrows(IllegalArgumentException.class, () -> lobbyService.joinLobby(player3Id, lobby.getId(), null));
    }

    @Test
    @DisplayName("Join: already in another lobby → exception")
    void joinLobby_alreadyInLobby_throws() {
        lobbyService.createLobby(hostId, "Lobby1", null, 4);
        Lobby lobby2 = lobbyService.createLobby(player3Id, "Lobby2", null, 4);

        // player2 joins lobby1
        lobbyService.joinLobby(player2Id,
                lobbyService.getLobbyIdByUserId(hostId), null);

        assertThrows(IllegalArgumentException.class, () -> lobbyService.joinLobby(player2Id, lobby2.getId(), null));
    }

    @Test
    @DisplayName("Join: nonexistent lobby → exception")
    void joinLobby_notFound_throws() {
        assertThrows(IllegalArgumentException.class, () -> lobbyService.joinLobby(player2Id, "nonexistent", null));
    }

    // ─── Leave Lobby ────────────────────────────────────

    @Test
    @DisplayName("Leave: player leaves")
    void leaveLobby_playerLeaves() {
        Lobby lobby = lobbyService.createLobby(hostId, "Test", null, 4);
        lobbyService.joinLobby(player2Id, lobby.getId(), null);

        lobbyService.leaveLobby(player2Id);

        assertEquals(1, lobby.getPlayerCount());
        assertFalse(lobby.containsPlayer(player2Id));
    }

    @Test
    @DisplayName("Leave: host leaves → host transferred")
    void leaveLobby_hostTransfer() {
        Lobby lobby = lobbyService.createLobby(hostId, "Test", null, 4);
        lobbyService.joinLobby(player2Id, lobby.getId(), null);

        lobbyService.leaveLobby(hostId);

        assertEquals(player2Id, lobby.getHostUserId());
        assertEquals(1, lobby.getPlayerCount());
    }

    @Test
    @DisplayName("Leave: last player → lobby closed")
    void leaveLobby_lastPlayer_lobbyClosed() {
        Lobby lobby = lobbyService.createLobby(hostId, "Test", null, 4);
        String lobbyId = lobby.getId();

        lobbyService.leaveLobby(hostId);

        assertNull(lobbyService.getLobbyById(lobbyId));
    }

    // ─── Kick Player ────────────────────────────────────

    @Test
    @DisplayName("Kick: host kicks player")
    void kickPlayer_valid() {
        Lobby lobby = lobbyService.createLobby(hostId, "Test", null, 4);
        lobbyService.joinLobby(player2Id, lobby.getId(), null);

        lobbyService.kickPlayer(hostId, player2Id);

        assertFalse(lobby.containsPlayer(player2Id));
        assertEquals(1, lobby.getPlayerCount());
    }

    @Test
    @DisplayName("Kick: non-host → exception")
    void kickPlayer_nonHost_throws() {
        Lobby lobby = lobbyService.createLobby(hostId, "Test", null, 4);
        lobbyService.joinLobby(player2Id, lobby.getId(), null);

        assertThrows(IllegalArgumentException.class, () -> lobbyService.kickPlayer(player2Id, hostId));
    }

    @Test
    @DisplayName("Kick: self → exception")
    void kickPlayer_self_throws() {
        lobbyService.createLobby(hostId, "Test", null, 4);

        assertThrows(IllegalArgumentException.class, () -> lobbyService.kickPlayer(hostId, hostId));
    }

    // ─── Settings ───────────────────────────────────────

    @Test
    @DisplayName("Update lobby settings: name + max players")
    void updateLobbySettings_valid() {
        Lobby lobby = lobbyService.createLobby(hostId, "OldName", null, 4);

        lobbyService.updateLobbySettings(hostId, "NewName", 6);

        assertEquals("NewName", lobby.getName());
        assertEquals(6, lobby.getMaxPlayers());
    }

    @Test
    @DisplayName("Update settings: non-host → exception")
    void updateLobbySettings_nonHost_throws() {
        Lobby lobby = lobbyService.createLobby(hostId, "Test", null, 4);
        lobbyService.joinLobby(player2Id, lobby.getId(), null);

        assertThrows(IllegalArgumentException.class,
                () -> lobbyService.updateLobbySettings(player2Id, "NewName", null));
    }

    @Test
    @DisplayName("Update game settings: merges data")
    void updateGameSettings_mergesData() {
        Lobby lobby = lobbyService.createLobby(hostId, "Test", null, 4);

        lobbyService.updateGameSettings(hostId, Map.of("boardName", "Plan C"));

        assertEquals("Plan C", lobby.getGameSettings().get("boardName"));
        assertTrue((Boolean) lobby.getGameSettings().get("timerEnabled")); // default unchanged
    }

    // ─── Lobby List ─────────────────────────────────────

    @Test
    @DisplayName("Lobby list: returns waiting lobbies")
    void getLobbyList_returnsWaiting() {
        lobbyService.createLobby(hostId, "Lobby1", null, 4);
        lobbyService.createLobby(player2Id, "Lobby2", null, 6);

        List<Map<String, Object>> list = lobbyService.getLobbyList();

        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("Lobby list: returns empty when no lobbies")
    void getLobbyList_empty() {
        List<Map<String, Object>> list = lobbyService.getLobbyList();
        assertTrue(list.isEmpty());
    }

    // ─── Querying ───────────────────────────────────────

    @Test
    @DisplayName("getLobbyByUserId: returns correct lobby")
    void getLobbyByUserId_returnsLobby() {
        lobbyService.createLobby(hostId, "Test", null, 4);

        Lobby found = lobbyService.getLobbyByUserId(hostId);

        assertNotNull(found);
        assertEquals("Test", found.getName());
    }

    @Test
    @DisplayName("getLobbyByUserId: null when not in lobby")
    void getLobbyByUserId_null() {
        assertNull(lobbyService.getLobbyByUserId(hostId));
    }

    // ─── Serialization ──────────────────────────────────

    @Test
    @DisplayName("Lobby toMap: contains all fields")
    void toMap_containsAllFields() {
        Lobby lobby = lobbyService.createLobby(hostId, "Test", null, 4);
        lobbyService.joinLobby(player2Id, lobby.getId(), null);

        Map<Long, String> usernames = Map.of(hostId, "HostUser", player2Id, "Player2");
        Map<String, Object> map = lobby.toMap(usernames);

        assertEquals("Test", map.get("name"));
        assertEquals(2, map.get("playerCount"));
        assertNotNull(map.get("players"));
        assertNotNull(map.get("gameSettings"));
    }
}
