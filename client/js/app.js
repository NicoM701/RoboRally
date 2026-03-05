/**
 * RoboRally Main Application
 * Screen management, auth flows, lobby interactions, chat.
 */
const App = (() => {
    // ─── State ──────────────────────────────────────────
    let currentUser = null;   // { userId, username, isGuest }
    let currentScreen = 'login';

    // ─── Initialization ─────────────────────────────────

    function init() {
        bindAuthEvents();
        bindMenuEvents();
        bindLobbyEvents();
        bindChatEvents();
        bindSettingsEvents();
        bindServerMessages();

        RoboSocket.connect();
    }

    // ─── Screen Management ──────────────────────────────

    function showScreen(name) {
        document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
        const screen = document.getElementById(`screen-${name}`);
        if (screen) {
            screen.classList.add('active');
            currentScreen = name;
        }
    }

    // ─── Toast Notifications ────────────────────────────

    function toast(message, type = 'info') {
        const container = document.getElementById('toast-container');
        const el = document.createElement('div');
        el.className = `toast toast-${type}`;
        el.textContent = message;
        container.appendChild(el);
        setTimeout(() => el.remove(), 4000);
    }

    // ─── Auth Message ───────────────────────────────────

    function showAuthMessage(text, isError = true) {
        const el = document.getElementById('auth-message');
        el.textContent = text;
        el.className = `auth-message ${isError ? 'error' : 'success'}`;
    }

    function hideAuthMessage() {
        const el = document.getElementById('auth-message');
        el.className = 'auth-message hidden';
    }

    // ═══════════════════════════════════════════════════
    // AUTH EVENTS
    // ═══════════════════════════════════════════════════

    function bindAuthEvents() {
        // Toggle login/register forms
        document.getElementById('show-register').addEventListener('click', (e) => {
            e.preventDefault();
            hideAuthMessage();
            document.getElementById('login-form').classList.remove('active');
            document.getElementById('register-form').classList.add('active');
        });

        document.getElementById('show-login').addEventListener('click', (e) => {
            e.preventDefault();
            hideAuthMessage();
            document.getElementById('register-form').classList.remove('active');
            document.getElementById('login-form').classList.add('active');
        });

        // Login button
        document.getElementById('btn-login').addEventListener('click', () => {
            const username = document.getElementById('login-username').value.trim();
            const password = document.getElementById('login-password').value;

            if (!username || !password) {
                showAuthMessage('Bitte Username und Passwort eingeben.');
                return;
            }

            RoboSocket.send('LOGIN', { username, password });
        });

        // Enter key on login fields
        document.getElementById('login-password').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') document.getElementById('btn-login').click();
        });
        document.getElementById('login-username').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') document.getElementById('btn-login').click();
        });

        // Guest button
        document.getElementById('btn-guest').addEventListener('click', () => {
            RoboSocket.send('GUEST_LOGIN', {});
        });

        // Register button
        document.getElementById('btn-register').addEventListener('click', () => {
            const username = document.getElementById('reg-username').value.trim();
            const email = document.getElementById('reg-email').value.trim();
            const password = document.getElementById('reg-password').value;
            const confirmPassword = document.getElementById('reg-password-confirm').value;

            if (!username || !email || !password) {
                showAuthMessage('Alle Felder müssen ausgefüllt werden.');
                return;
            }
            if (password !== confirmPassword) {
                showAuthMessage('Passwörter stimmen nicht überein.');
                return;
            }

            RoboSocket.send('REGISTER', { username, email, password });
        });

        // Enter key on register fields
        document.getElementById('reg-password-confirm').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') document.getElementById('btn-register').click();
        });
    }

    // ═══════════════════════════════════════════════════
    // MENU EVENTS
    // ═══════════════════════════════════════════════════

    function bindMenuEvents() {
        // Logout
        document.getElementById('btn-logout').addEventListener('click', () => {
            RoboSocket.send('LOGOUT', {});
            currentUser = null;
            showScreen('login');
            // Clear login fields
            document.getElementById('login-username').value = '';
            document.getElementById('login-password').value = '';
            hideAuthMessage();
            toast('Erfolgreich ausgeloggt.', 'success');
        });

        // Create lobby modal
        document.getElementById('btn-create-lobby').addEventListener('click', () => {
            document.getElementById('modal-create-lobby').classList.remove('hidden');
        });

        document.getElementById('btn-cancel-create-lobby').addEventListener('click', () => {
            document.getElementById('modal-create-lobby').classList.add('hidden');
        });

        document.getElementById('btn-confirm-create-lobby').addEventListener('click', () => {
            const name = document.getElementById('lobby-name').value.trim() || 'Neue Lobby';
            const password = document.getElementById('lobby-password').value;
            const maxPlayers = parseInt(document.getElementById('lobby-max-players').value);

            RoboSocket.send('CREATE_LOBBY', { name, password, maxPlayers });
            document.getElementById('modal-create-lobby').classList.add('hidden');
        });

        // Settings modal
        document.getElementById('btn-settings').addEventListener('click', () => {
            const modal = document.getElementById('modal-settings');
            modal.classList.remove('hidden');

            // Show guest warning
            const warning = document.getElementById('settings-guest-warning');
            if (currentUser && currentUser.isGuest) {
                warning.classList.remove('hidden');
                // Disable inputs for guests
                modal.querySelectorAll('input').forEach(i => i.disabled = true);
                document.getElementById('btn-save-settings').disabled = true;
                document.getElementById('btn-delete-account').disabled = true;
            } else {
                warning.classList.add('hidden');
                modal.querySelectorAll('input').forEach(i => i.disabled = false);
                document.getElementById('btn-save-settings').disabled = false;
                document.getElementById('btn-delete-account').disabled = false;
            }
        });
    }

    // ═══════════════════════════════════════════════════
    // SETTINGS EVENTS
    // ═══════════════════════════════════════════════════

    function bindSettingsEvents() {
        document.getElementById('btn-cancel-settings').addEventListener('click', () => {
            document.getElementById('modal-settings').classList.add('hidden');
            clearSettingsForm();
        });

        document.getElementById('btn-save-settings').addEventListener('click', () => {
            const currentPassword = document.getElementById('settings-current-password').value;
            const newUsername = document.getElementById('settings-username').value.trim();
            const newEmail = document.getElementById('settings-email').value.trim();
            const newPassword = document.getElementById('settings-new-password').value;

            if (!currentPassword) {
                toast('Aktuelles Passwort zur Bestätigung nötig.', 'error');
                return;
            }

            RoboSocket.send('UPDATE_USER', { currentPassword, newUsername, newEmail, newPassword });
        });

        document.getElementById('btn-delete-account').addEventListener('click', () => {
            const password = document.getElementById('settings-current-password').value;
            if (!password) {
                toast('Passwort zur Bestätigung nötig.', 'error');
                return;
            }
            if (confirm('Account wirklich löschen? Dies kann nicht rückgängig gemacht werden.')) {
                RoboSocket.send('DELETE_USER', { password });
            }
        });
    }

    function clearSettingsForm() {
        document.getElementById('settings-username').value = '';
        document.getElementById('settings-email').value = '';
        document.getElementById('settings-new-password').value = '';
        document.getElementById('settings-current-password').value = '';
    }

    // ═══════════════════════════════════════════════════
    // LOBBY EVENTS
    // ═══════════════════════════════════════════════════

    function bindLobbyEvents() {
        document.getElementById('btn-leave-lobby').addEventListener('click', () => {
            RoboSocket.send('LEAVE_LOBBY', {});
            showScreen('menu');
        });

        document.getElementById('btn-start-game').addEventListener('click', () => {
            RoboSocket.send('START_GAME', {});
        });

        document.getElementById('btn-add-bot').addEventListener('click', () => {
            RoboSocket.send('ADD_BOT', {});
        });
    }

    // ═══════════════════════════════════════════════════
    // CHAT EVENTS
    // ═══════════════════════════════════════════════════

    function bindChatEvents() {
        // Main menu chat
        bindChatInput('main-chat-input', 'btn-main-chat-send', 'MAIN');
        // Lobby chat
        bindChatInput('lobby-chat-input', 'btn-lobby-chat-send', 'LOBBY');
        // Game chat
        bindChatInput('game-chat-input', 'btn-game-chat-send', 'LOBBY');
    }

    function bindChatInput(inputId, buttonId, scope) {
        const input = document.getElementById(inputId);
        const button = document.getElementById(buttonId);

        if (!input || !button) return;

        const sendFn = () => {
            const msg = input.value.trim();
            if (!msg) return;
            RoboSocket.send('CHAT_MESSAGE', { message: msg, scope });
            input.value = '';
        };

        button.addEventListener('click', sendFn);
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') sendFn();
        });
    }

    // ═══════════════════════════════════════════════════
    // SERVER MESSAGE HANDLERS
    // ═══════════════════════════════════════════════════

    function bindServerMessages() {
        // Auth responses
        RoboSocket.on('LOGIN_SUCCESS', (data) => {
            currentUser = {
                userId: data.userId,
                username: data.username,
                isGuest: data.isGuest || false
            };
            document.getElementById('menu-username').textContent = currentUser.username;
            showScreen('menu');
            toast(`Willkommen, ${currentUser.username}!`, 'success');

            // Request lobby list
            RoboSocket.send('REQUEST_LOBBY_LIST', {});
        });

        RoboSocket.on('LOGIN_FAILED', (data) => {
            showAuthMessage(data.reason || 'Login fehlgeschlagen.');
        });

        RoboSocket.on('REGISTER_SUCCESS', (data) => {
            showAuthMessage('Registrierung erfolgreich! Du kannst dich jetzt einloggen.', false);
            // Switch to login form
            document.getElementById('register-form').classList.remove('active');
            document.getElementById('login-form').classList.add('active');
        });

        RoboSocket.on('REGISTER_FAILED', (data) => {
            showAuthMessage(data.reason || 'Registrierung fehlgeschlagen.');
        });

        // User management responses
        RoboSocket.on('USER_UPDATED', (data) => {
            if (data.username) {
                currentUser.username = data.username;
                document.getElementById('menu-username').textContent = data.username;
            }
            document.getElementById('modal-settings').classList.add('hidden');
            clearSettingsForm();
            toast('Daten erfolgreich aktualisiert.', 'success');
        });

        RoboSocket.on('USER_DELETED', () => {
            currentUser = null;
            showScreen('login');
            document.getElementById('modal-settings').classList.add('hidden');
            clearSettingsForm();
            toast('Account gelöscht.', 'info');
        });

        // Lobby responses
        RoboSocket.on('LOBBY_LIST', (data) => {
            renderLobbyList(data.lobbies || []);
        });

        RoboSocket.on('LOBBY_UPDATE', (data) => {
            renderLobbyRoom(data.lobby || data);
            if (currentScreen === 'menu') {
                showScreen('lobby');
            }
        });

        RoboSocket.on('LOBBY_CLOSED', () => {
            showScreen('menu');
            toast('Lobby wurde geschlossen.', 'info');
            RoboSocket.send('REQUEST_LOBBY_LIST', {});
        });

        RoboSocket.on('PLAYER_JOINED', (data) => {
            toast(`${data.username} ist beigetreten.`, 'info');
        });

        RoboSocket.on('PLAYER_LEFT', (data) => {
            toast(`${data.username} hat die Lobby verlassen.`, 'info');
        });

        // Chat
        RoboSocket.on('CHAT_BROADCAST', (data) => {
            appendChatMessage(data.from, data.message, data.scope);
        });

        // Game
        RoboSocket.on('GAME_STATE', (data) => {
            showScreen('game');
        });

        RoboSocket.on('GAME_OVER', (data) => {
            showScreen('end');
            renderEndScreen(data);
        });

        // Errors
        RoboSocket.on('ERROR', (data) => {
            const msg = data.message || 'Unbekannter Fehler.';
            // Show in auth screen if on login
            if (currentScreen === 'login') {
                showAuthMessage(msg);
            } else {
                toast(msg, 'error');
            }
        });

        // Reconnect: re-send lobby list request if in menu
        RoboSocket.on('connected', () => {
            if (currentScreen === 'menu' && currentUser) {
                // Re-login silently after reconnect is not possible,
                // so return to login screen
            }
        });
    }

    // ═══════════════════════════════════════════════════
    // UI RENDERERS
    // ═══════════════════════════════════════════════════

    function renderLobbyList(lobbies) {
        const container = document.getElementById('lobby-list');

        if (lobbies.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <p>🏭 Noch keine Lobbys vorhanden.</p>
                    <p>Erstelle eine neue Lobby und lade Freunde ein!</p>
                </div>`;
            return;
        }

        container.innerHTML = lobbies.map(lobby => `
            <div class="lobby-item" data-lobby-id="${lobby.id}" onclick="App.joinLobby('${lobby.id}', ${lobby.hasPassword})">
                <div class="lobby-item-info">
                    <h3>${escapeHtml(lobby.name)}</h3>
                    <span>Host: ${escapeHtml(lobby.host || '?')}</span>
                </div>
                <div class="lobby-item-meta">
                    <span class="lobby-player-count">${lobby.playerCount}/${lobby.maxPlayers}</span>
                    ${lobby.hasPassword ? '<span class="lobby-lock">🔒</span>' : ''}
                </div>
            </div>
        `).join('');
    }

    function renderLobbyRoom(lobby) {
        document.getElementById('lobby-room-name').textContent = lobby.name || 'Lobby';

        // Players
        const playerList = document.getElementById('lobby-player-list');
        const players = lobby.players || [];
        playerList.innerHTML = players.map(p => {
            let badges = '';
            if (p.isHost) badges += '<span class="player-badge host">HOST</span>';
            if (p.isBot) badges += '<span class="player-badge bot">BOT</span>';
            if (p.isGuest) badges += '<span class="player-badge guest">GAST</span>';

            return `
                <div class="player-item" data-user-id="${p.userId}">
                    <span class="player-name">${escapeHtml(p.username)}</span>
                    ${badges}
                </div>`;
        }).join('');

        // Show start button only for host
        const isHost = currentUser && players.some(p => p.userId === currentUser.userId && p.isHost);
        const startBtn = document.getElementById('btn-start-game');
        if (isHost) {
            startBtn.classList.remove('hidden');
        } else {
            startBtn.classList.add('hidden');
        }
    }

    function renderEndScreen(data) {
        const container = document.getElementById('end-winner');
        const winners = data.winners || [];

        if (winners.length === 1) {
            container.innerHTML = `<p>🏆 <strong>${escapeHtml(winners[0].username)}</strong> hat gewonnen!</p>`;
        } else if (winners.length > 1) {
            container.innerHTML = `<p>Es ist ein Unentschieden zwischen:</p>
                <p>${winners.map(w => `<strong>${escapeHtml(w.username)}</strong>`).join(', ')}</p>`;
        } else {
            container.innerHTML = '<p>Kein Gewinner.</p>';
        }
    }

    function appendChatMessage(from, text, scope) {
        let containerId;
        if (currentScreen === 'game') {
            containerId = 'game-chat-messages';
        } else if (currentScreen === 'lobby') {
            containerId = 'lobby-chat-messages';
        } else {
            containerId = 'main-chat-messages';
        }

        const container = document.getElementById(containerId);
        if (!container) return;

        const msg = document.createElement('div');
        msg.className = 'chat-msg';
        msg.innerHTML = `<span class="chat-author">${escapeHtml(from)}</span><span class="chat-text">${escapeHtml(text)}</span>`;
        container.appendChild(msg);
        container.scrollTop = container.scrollHeight;
    }

    // ═══════════════════════════════════════════════════
    // PUBLIC METHODS
    // ═══════════════════════════════════════════════════

    function joinLobby(lobbyId, hasPassword) {
        if (hasPassword) {
            const pw = prompt('Lobby-Passwort eingeben:');
            if (pw === null) return; // canceled
            RoboSocket.send('JOIN_LOBBY', { lobbyId, password: pw });
        } else {
            RoboSocket.send('JOIN_LOBBY', { lobbyId });
        }
    }

    // ─── Utilities ──────────────────────────────────────

    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    // ─── Boot ───────────────────────────────────────────

    document.addEventListener('DOMContentLoaded', init);

    // Back to menu from end screen
    document.addEventListener('DOMContentLoaded', () => {
        const btn = document.getElementById('btn-back-to-menu');
        if (btn) {
            btn.addEventListener('click', () => {
                showScreen('menu');
                RoboSocket.send('REQUEST_LOBBY_LIST', {});
            });
        }
    });

    return {
        joinLobby,
        toast,
        showScreen
    };
})();
