package com.roborally.server.service;

import com.roborally.server.model.User;
import com.roborally.server.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AtomicInteger guestCounter = new AtomicInteger(1);

    // Track online users: sessionId -> userId
    private final Map<String, Long> onlineUsers = new ConcurrentHashMap<>();
    // Track userId -> sessionId (reverse lookup)
    private final Map<Long, String> userSessions = new ConcurrentHashMap<>();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Register a new user account.
     */
    public User register(String username, String email, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username darf nicht leer sein.");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email darf nicht leer sein.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Passwort darf nicht leer sein.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username ist bereits vergeben.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email ist bereits vergeben.");
        }

        User user = new User(username, email, passwordEncoder.encode(password), false);
        return userRepository.save(user);
    }

    /**
     * Login with username and password.
     */
    public User login(String username, String password, String sessionId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Ungültiger Username oder Passwort."));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Ungültiger Username oder Passwort.");
        }

        // Check if already logged in
        if (userSessions.containsKey(user.getId())) {
            throw new IllegalArgumentException("Nutzer ist bereits eingeloggt.");
        }

        trackUserOnline(sessionId, user.getId());
        return user;
    }

    /**
     * Login as a guest with auto-generated username.
     */
    public User guestLogin(String sessionId) {
        String guestName = "Gast_" + guestCounter.getAndIncrement();
        // Ensure unique name
        while (userRepository.existsByUsername(guestName)) {
            guestName = "Gast_" + guestCounter.getAndIncrement();
        }

        User guest = new User(guestName, null, passwordEncoder.encode(UUID.randomUUID().toString()), true);
        guest = userRepository.save(guest);
        trackUserOnline(sessionId, guest.getId());
        return guest;
    }

    /**
     * Update user data (username, email, password).
     */
    public User updateUser(Long userId, String currentPassword,
            String newUsername, String newEmail, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Nutzer nicht gefunden."));

        if (user.isGuest()) {
            throw new IllegalArgumentException("Gäste können keine Daten ändern.");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Aktuelles Passwort ist falsch.");
        }

        if (newUsername != null && !newUsername.trim().isEmpty()) {
            if (!newUsername.equals(user.getUsername()) && userRepository.existsByUsername(newUsername)) {
                throw new IllegalArgumentException("Username ist bereits vergeben.");
            }
            user.setUsername(newUsername);
        }

        if (newEmail != null && !newEmail.trim().isEmpty()) {
            if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                throw new IllegalArgumentException("Email ist bereits vergeben.");
            }
            user.setEmail(newEmail);
        }

        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }

        return userRepository.save(user);
    }

    /**
     * Delete a user account.
     */
    public void deleteUser(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Nutzer nicht gefunden."));

        if (user.isGuest()) {
            throw new IllegalArgumentException("Gast-Accounts können nicht manuell gelöscht werden.");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Passwort ist falsch.");
        }

        // Remove from online tracking
        String sessionId = userSessions.remove(userId);
        if (sessionId != null) {
            onlineUsers.remove(sessionId);
        }

        userRepository.delete(user);
    }

    /**
     * Handle user logout (or disconnect).
     */
    public void logout(String sessionId) {
        Long userId = onlineUsers.remove(sessionId);
        if (userId != null) {
            userSessions.remove(userId);
            // If guest, delete account
            userRepository.findById(userId).ifPresent(user -> {
                if (user.isGuest()) {
                    userRepository.delete(user);
                }
            });
        }
    }

    /**
     * Get user by session ID.
     */
    public Optional<User> getUserBySession(String sessionId) {
        Long userId = onlineUsers.get(sessionId);
        if (userId == null)
            return Optional.empty();
        return userRepository.findById(userId);
    }

    /**
     * Get userId by session.
     */
    public Long getUserIdBySession(String sessionId) {
        return onlineUsers.get(sessionId);
    }

    /**
     * Check if a user is online.
     */
    public boolean isUserOnline(Long userId) {
        return userSessions.containsKey(userId);
    }

    private void trackUserOnline(String sessionId, Long userId) {
        onlineUsers.put(sessionId, userId);
        userSessions.put(userId, sessionId);
    }

    /**
     * Get user by ID.
     */
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    /**
     * Get session ID by user ID (reverse lookup).
     */
    public String getSessionIdByUserId(Long userId) {
        return userSessions.get(userId);
    }
}
