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

        // Game state
        RoboSocket.on('GAME_STATE', (data) => {
            gameState = data;
            showScreen('game');
            renderBoard();
            renderGameInfo();
        });

        RoboSocket.on('CARDS_DEALT', (data) => {
            dealtCards = data.cards || [];
            selectedCards = [];
            blockedSlots = data.blockedSlots || 0;
            renderCardHand();
            toast(`Runde ${data.round}: ${dealtCards.length} Karten erhalten!`, 'info');
        });

        RoboSocket.on('PROGRAMMING_PHASE_START', (data) => {
            if (data.status === 'submitted') {
                toast(data.message || 'Programm eingereicht!', 'success');
            }
        });

        RoboSocket.on('EXECUTION_STEP', (data) => {
            if (data.robots) {
                gameState.robots = data.robots;
                renderBoard();
            }
            toast(`Schritt ${data.step} ausgeführt`, 'info');
        });

        RoboSocket.on('GAME_OVER', (data) => {
            showScreen('end');
            renderEndScreen(data);
        });

        // Errors
        RoboSocket.on('ERROR', (data) => {
            const msg = data.message || 'Unbekannter Fehler.';
            if (currentScreen === 'login') {
                showAuthMessage(msg);
            } else {
                toast(msg, 'error');
            }
        });

        // Reconnect
        RoboSocket.on('connected', () => {
            if (currentScreen === 'menu' && currentUser) {
                // Re-login silently after reconnect is not possible
            }
        });
    }

    // ═══════════════════════════════════════════════════
    // GAME STATE
    // ═══════════════════════════════════════════════════

    let gameState = null;
    let dealtCards = [];
    let selectedCards = [];
    let blockedSlots = 0;

    const TILE_SIZE = 48;
    const ROBOT_COLORS = ['#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6', '#1abc9c', '#e67e22', '#34495e'];
    const TILE_COLORS = {
        FLOOR: '#3d4f5f',
        PIT: '#0d0d0d',
        START: '#4a6741',
        REPAIR_1: '#2e6b9e',
        REPAIR_2: '#1e4d7e',
        WALL: '#c0c0c0'
    };

    const CARD_ICONS = {
        MOVE_1: '↑1', MOVE_2: '↑2', MOVE_3: '↑3',
        BACKUP: '↓', TURN_LEFT: '↶', TURN_RIGHT: '↷', U_TURN: '↩'
    };

    function renderBoard() {
        const canvas = document.getElementById('game-board-canvas');
        if (!canvas || !gameState) return;
        const ctx = canvas.getContext('2d');

        const board = gameState.board || {};
        const w = board.width || 12;
        const h = board.height || 12;

        canvas.width = w * TILE_SIZE;
        canvas.height = h * TILE_SIZE;

        // Draw tiles
        const tiles = board.tiles || [];
        for (const t of tiles) {
            const px = t.x * TILE_SIZE;
            const py = t.y * TILE_SIZE;

            // Tile background
            ctx.fillStyle = TILE_COLORS[t.type] || TILE_COLORS.FLOOR;
            ctx.fillRect(px, py, TILE_SIZE, TILE_SIZE);

            // Grid line
            ctx.strokeStyle = '#2c3e50';
            ctx.lineWidth = 1;
            ctx.strokeRect(px, py, TILE_SIZE, TILE_SIZE);

            // Conveyor belt arrow
            if (t.conveyorBelt) {
                ctx.fillStyle = t.conveyorBelt.express ? '#f1c40f' : '#95a5a6';
                ctx.font = '16px Inter';
                ctx.textAlign = 'center';
                const arrows = { NORTH: '▲', SOUTH: '▼', EAST: '▶', WEST: '◀' };
                ctx.fillText(arrows[t.conveyorBelt.direction] || '•', px + TILE_SIZE / 2, py + TILE_SIZE / 2 + 5);
            }

            // Gear
            if (t.gear) {
                ctx.fillStyle = '#f39c12';
                ctx.font = '18px Inter';
                ctx.textAlign = 'center';
                ctx.fillText(t.gear.rotation === 'CLOCKWISE' ? '⟳' : '⟲', px + TILE_SIZE / 2, py + TILE_SIZE / 2 + 6);
            }

            // Checkpoint
            if (t.checkpoint) {
                ctx.fillStyle = '#e74c3c';
                ctx.font = 'bold 20px Inter';
                ctx.textAlign = 'center';
                ctx.fillText(t.checkpoint.number, px + TILE_SIZE / 2, py + TILE_SIZE / 2 + 7);
            }

            // Walls
            if (t.walls && t.walls.length) {
                ctx.strokeStyle = TILE_COLORS.WALL;
                ctx.lineWidth = 3;
                for (const wall of t.walls) {
                    ctx.beginPath();
                    if (wall === 'NORTH') { ctx.moveTo(px, py); ctx.lineTo(px + TILE_SIZE, py); }
                    if (wall === 'SOUTH') { ctx.moveTo(px, py + TILE_SIZE); ctx.lineTo(px + TILE_SIZE, py + TILE_SIZE); }
                    if (wall === 'WEST') { ctx.moveTo(px, py); ctx.lineTo(px, py + TILE_SIZE); }
                    if (wall === 'EAST') { ctx.moveTo(px + TILE_SIZE, py); ctx.lineTo(px + TILE_SIZE, py + TILE_SIZE); }
                    ctx.stroke();
                }
            }
        }

        // Draw lasers
        const lasers = board.lasers || [];
        for (const laser of lasers) {
            ctx.strokeStyle = 'rgba(255, 0, 0, 0.4)';
            ctx.lineWidth = laser.strength > 1 ? 3 : 1;
            const lx = laser.x * TILE_SIZE + TILE_SIZE / 2;
            const ly = laser.y * TILE_SIZE + TILE_SIZE / 2;
            ctx.beginPath();
            ctx.arc(lx, ly, 4, 0, Math.PI * 2);
            ctx.stroke();
        }

        // Draw robots
        const robots = gameState.robots || [];
        for (const robot of robots) {
            if (robot.destroyed) continue;
            const rx = robot.x * TILE_SIZE + TILE_SIZE / 2;
            const ry = robot.y * TILE_SIZE + TILE_SIZE / 2;
            const color = ROBOT_COLORS[robot.robotIndex % ROBOT_COLORS.length];

            // Robot body
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.arc(rx, ry, TILE_SIZE / 3, 0, Math.PI * 2);
            ctx.fill();
            ctx.strokeStyle = '#fff';
            ctx.lineWidth = 2;
            ctx.stroke();

            // Direction indicator
            const dirAngles = { NORTH: -Math.PI / 2, EAST: 0, SOUTH: Math.PI / 2, WEST: Math.PI };
            const angle = dirAngles[robot.direction] || 0;
            ctx.fillStyle = '#fff';
            ctx.beginPath();
            ctx.moveTo(rx + Math.cos(angle) * 14, ry + Math.sin(angle) * 14);
            ctx.lineTo(rx + Math.cos(angle + 2.5) * 6, ry + Math.sin(angle + 2.5) * 6);
            ctx.lineTo(rx + Math.cos(angle - 2.5) * 6, ry + Math.sin(angle - 2.5) * 6);
            ctx.closePath();
            ctx.fill();
        }
    }

    function renderGameInfo() {
        const panel = document.getElementById('game-info-panel');
        if (!panel || !gameState) return;

        const robots = gameState.robots || [];
        let html = `<div class="game-phase-indicator"><strong>Phase:</strong> ${gameState.phase || '—'} | <strong>Runde:</strong> ${gameState.round || 1}</div>`;
        html += '<div class="robot-status-list">';
        for (const r of robots) {
            const color = ROBOT_COLORS[r.robotIndex % ROBOT_COLORS.length];
            html += `<div class="robot-status" style="border-left: 4px solid ${color}">
                <span class="robot-name">Spieler ${r.playerId}</span>
                <span>❤️ ${r.lives} | 💥 ${r.damage} | 🏁 CP${r.nextCheckpoint - 1}${r.destroyed ? ' | ☠️' : ''}</span>
            </div>`;
        }
        html += '</div>';
        panel.innerHTML = html;
    }

    function renderCardHand() {
        const panel = document.getElementById('game-cards-panel');
        if (!panel) return;

        const needed = 5 - blockedSlots;
        let html = `<h3>🎴 Deine Karten <small>(${selectedCards.length}/${needed} gewählt)</small></h3>`;
        html += '<div class="card-hand">';
        for (const card of dealtCards) {
            const isSelected = selectedCards.includes(card.id);
            const idx = selectedCards.indexOf(card.id);
            html += `<div class="program-card ${isSelected ? 'selected' : ''}"
                          onclick="App.toggleCard(${card.id})"
                          title="${card.displayName} (Priorität: ${card.priority})">
                <div class="card-icon">${CARD_ICONS[card.type] || '?'}</div>
                <div class="card-name">${card.displayName}</div>
                <div class="card-priority">${card.priority}</div>
                ${isSelected ? '<div class="card-order">' + (idx + 1) + '</div>' : ''}
            </div>`;
        }
        html += '</div>';

        if (selectedCards.length === needed) {
            html += '<button class="btn btn-primary btn-submit-program" onclick="App.submitProgram()">✅ Programm einreichen</button>';
        }

        panel.innerHTML = html;
    }

    function toggleCard(cardId) {
        const needed = 5 - blockedSlots;
        const idx = selectedCards.indexOf(cardId);
        if (idx >= 0) {
            selectedCards.splice(idx, 1);
        } else if (selectedCards.length < needed) {
            selectedCards.push(cardId);
        }
        renderCardHand();
    }

    function submitProgram() {
        if (selectedCards.length !== 5 - blockedSlots) {
            toast('Wähle erst die richtige Anzahl Karten!', 'error');
            return;
        }
        RoboSocket.send('SUBMIT_PROGRAM', { cardIds: selectedCards });
        dealtCards = [];
        selectedCards = [];
        renderCardHand();
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
        showScreen,
        toggleCard,
        submitProgram
    };
})();
