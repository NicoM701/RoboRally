package com.roborally.server.service;

import com.roborally.server.model.User;
import com.roborally.server.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UserService with real H2 database.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    // ─── Registration ───────────────────────────────────

    @Test
    @DisplayName("Register: valid user")
    void register_validUser_succeeds() {
        User user = userService.register("player1", "p1@mail.de", "secret123");

        assertNotNull(user.getId());
        assertEquals("player1", user.getUsername());
        assertEquals("p1@mail.de", user.getEmail());
        assertFalse(user.isGuest());
    }

    @Test
    @DisplayName("Register: duplicate username → exception")
    void register_duplicateUsername_throws() {
        userService.register("player1", "a@mail.de", "pass1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.register("player1", "b@mail.de", "pass2"));
        assertTrue(ex.getMessage().contains("Username"));
    }

    @Test
    @DisplayName("Register: duplicate email → exception")
    void register_duplicateEmail_throws() {
        userService.register("player1", "same@mail.de", "pass1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.register("player2", "same@mail.de", "pass2"));
        assertTrue(ex.getMessage().contains("Email"));
    }

    @Test
    @DisplayName("Register: empty username → exception")
    void register_emptyUsername_throws() {
        assertThrows(IllegalArgumentException.class, () -> userService.register("", "x@mail.de", "pass"));
    }

    @Test
    @DisplayName("Register: empty password → exception")
    void register_emptyPassword_throws() {
        assertThrows(IllegalArgumentException.class, () -> userService.register("user", "x@mail.de", ""));
    }

    @Test
    @DisplayName("Register: null email → exception")
    void register_nullEmail_throws() {
        assertThrows(IllegalArgumentException.class, () -> userService.register("user", null, "pass"));
    }

    // ─── Login ──────────────────────────────────────────

    @Test
    @DisplayName("Login: valid credentials → success")
    void login_validCredentials_succeeds() {
        userService.register("player1", "p@mail.de", "mypass");

        User loggedIn = userService.login("player1", "mypass", "session-1");

        assertEquals("player1", loggedIn.getUsername());
        assertTrue(userService.isUserOnline(loggedIn.getId()));
    }

    @Test
    @DisplayName("Login: wrong password → exception")
    void login_wrongPassword_throws() {
        userService.register("player1", "p@mail.de", "correct");

        assertThrows(IllegalArgumentException.class, () -> userService.login("player1", "wrong", "s1"));
    }

    @Test
    @DisplayName("Login: non-existent user → exception")
    void login_unknownUser_throws() {
        assertThrows(IllegalArgumentException.class, () -> userService.login("ghost", "pass", "s1"));
    }

    @Test
    @DisplayName("Login: already logged in → exception (single-session)")
    void login_alreadyLoggedIn_throws() {
        userService.register("player1", "p@mail.de", "pass");
        userService.login("player1", "pass", "session-1");

        assertThrows(IllegalArgumentException.class, () -> userService.login("player1", "pass", "session-2"));
    }

    // ─── Guest Login ────────────────────────────────────

    @Test
    @DisplayName("Guest: creates unique guest account")
    void guestLogin_createsGuestAccount() {
        User guest = userService.guestLogin("gs-1");

        assertNotNull(guest.getId());
        assertTrue(guest.getUsername().startsWith("Gast_"));
        assertTrue(guest.isGuest());
        assertTrue(userService.isUserOnline(guest.getId()));
    }

    @Test
    @DisplayName("Guest: multiple guests get unique names")
    void guestLogin_multipleGuests_uniqueNames() {
        User g1 = userService.guestLogin("gs-1");
        User g2 = userService.guestLogin("gs-2");

        assertNotEquals(g1.getUsername(), g2.getUsername());
        assertNotEquals(g1.getId(), g2.getId());
    }

    // ─── Logout ─────────────────────────────────────────

    @Test
    @DisplayName("Logout: user goes offline")
    void logout_userGoesOffline() {
        userService.register("player1", "p@mail.de", "pass");
        User user = userService.login("player1", "pass", "s-1");

        userService.logout("s-1");

        assertFalse(userService.isUserOnline(user.getId()));
    }

    @Test
    @DisplayName("Logout: guest account is deleted")
    void logout_guestAccountDeleted() {
        User guest = userService.guestLogin("gs-1");
        Long guestId = guest.getId();

        userService.logout("gs-1");

        assertFalse(userService.isUserOnline(guestId));
        assertFalse(userRepository.findById(guestId).isPresent());
    }

    @Test
    @DisplayName("Logout: registered account is NOT deleted")
    void logout_registeredAccountNotDeleted() {
        userService.register("player1", "p@mail.de", "pass");
        User user = userService.login("player1", "pass", "s-1");

        userService.logout("s-1");

        assertTrue(userRepository.findById(user.getId()).isPresent());
    }

    @Test
    @DisplayName("Logout: can login again after logout")
    void logout_canLoginAgain() {
        userService.register("player1", "p@mail.de", "pass");
        userService.login("player1", "pass", "s-1");
        userService.logout("s-1");

        User again = userService.login("player1", "pass", "s-2");
        assertEquals("player1", again.getUsername());
    }

    // ─── Update User ────────────────────────────────────

    @Test
    @DisplayName("Update: change username")
    void updateUser_changeUsername_succeeds() {
        User user = userService.register("old", "u@mail.de", "pass");

        User updated = userService.updateUser(user.getId(), "pass", "newname", null, null);

        assertEquals("newname", updated.getUsername());
    }

    @Test
    @DisplayName("Update: wrong password → exception")
    void updateUser_wrongPassword_throws() {
        User user = userService.register("player", "u@mail.de", "pass");

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateUser(user.getId(), "wrong", "newname", null, null));
    }

    @Test
    @DisplayName("Update: guest cannot update → exception")
    void updateUser_guestCannotUpdate() {
        User guest = userService.guestLogin("gs-1");

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateUser(guest.getId(), "any", "newname", null, null));
    }

    @Test
    @DisplayName("Update: duplicate new username → exception")
    void updateUser_duplicateUsername_throws() {
        userService.register("taken", "t@mail.de", "pass");
        User user = userService.register("player", "p@mail.de", "pass");

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateUser(user.getId(), "pass", "taken", null, null));
    }

    @Test
    @DisplayName("Update: change password")
    void updateUser_changePassword_succeeds() {
        User user = userService.register("player", "u@mail.de", "oldpass");

        userService.updateUser(user.getId(), "oldpass", null, null, "newpass");

        // Verify by logging in with new password
        User loggedIn = userService.login("player", "newpass", "s-1");
        assertEquals("player", loggedIn.getUsername());
    }

    // ─── Delete User ────────────────────────────────────

    @Test
    @DisplayName("Delete: valid password → account removed")
    void deleteUser_validPassword_succeeds() {
        User user = userService.register("player", "u@mail.de", "pass");

        userService.deleteUser(user.getId(), "pass");

        assertFalse(userRepository.findById(user.getId()).isPresent());
    }

    @Test
    @DisplayName("Delete: wrong password → exception")
    void deleteUser_wrongPassword_throws() {
        User user = userService.register("player", "u@mail.de", "pass");

        assertThrows(IllegalArgumentException.class, () -> userService.deleteUser(user.getId(), "wrong"));
    }

    @Test
    @DisplayName("Delete: guest cannot delete → exception")
    void deleteUser_guestCannotDelete() {
        User guest = userService.guestLogin("gs-1");

        assertThrows(IllegalArgumentException.class, () -> userService.deleteUser(guest.getId(), "any"));
    }

    // ─── Session Queries ────────────────────────────────

    @Test
    @DisplayName("getUserBySession: returns user when online")
    void getUserBySession_onlineUser_returnsUser() {
        userService.register("player", "u@mail.de", "pass");
        userService.login("player", "pass", "s-1");

        Optional<User> result = userService.getUserBySession("s-1");

        assertTrue(result.isPresent());
        assertEquals("player", result.get().getUsername());
    }

    @Test
    @DisplayName("getUserBySession: returns empty for unknown session")
    void getUserBySession_unknownSession_empty() {
        Optional<User> result = userService.getUserBySession("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getUserIdBySession: returns userId when online")
    void getUserIdBySession_onlineUser_returnsId() {
        userService.register("player", "u@mail.de", "pass");
        User user = userService.login("player", "pass", "s-1");

        Long id = userService.getUserIdBySession("s-1");
        assertEquals(user.getId(), id);
    }

    @Test
    @DisplayName("getUserIdBySession: returns null for unknown session")
    void getUserIdBySession_unknown_returnsNull() {
        assertNull(userService.getUserIdBySession("nope"));
    }
}
