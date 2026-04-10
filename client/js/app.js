/**
 * RoboRally Main Application
 * Screen management, auth flows, lobby interactions, chat.
 */
const App = (() => {
    // ─── State ──────────────────────────────────────────
    let currentUser = null;   // { userId, username, isGuest }
    let currentScreen = 'login';
    let currentLobby = null;
    let joiningLobbyId = null;
    let exitingLobbyId = null;
    let availableBoards = [];

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

    function clearRoundState() {
        dealtCards = [];
        selectedCards = [];
        blockedSlots = 0;

        const panel = document.getElementById('game-cards-panel');
        if (panel) {
            panel.innerHTML = '';
        }
    }

    function resetLobbyAndGameState() {
        currentLobby = null;
        gameState = null;
        clearRoundState();

        const winner = document.getElementById('end-winner');
        if (winner) {
            winner.innerHTML = '';
        }
    }

    function returnToHomeScreen() {
        showScreen(currentUser ? 'menu' : 'login');
    }

    function leaveCurrentLobby({ requestLobbyList = false } = {}) {
        const lobbyIdToLeave = currentLobby?.id || gameState?.lobbyId;
        joiningLobbyId = null;
        if (lobbyIdToLeave) {
            exitingLobbyId = lobbyIdToLeave;
            RoboSocket.send('LEAVE_LOBBY', {});
        } else {
            exitingLobbyId = null;
        }

        resetLobbyAndGameState();
        returnToHomeScreen();

        if (requestLobbyList && currentUser) {
            RoboSocket.send('REQUEST_LOBBY_LIST', {});
        }
    }

    function getActiveLobbyId() {
        return currentLobby?.id || gameState?.lobbyId || null;
    }

    function shouldIgnoreLobbyScopedMessage(lobbyId) {
        if (!lobbyId) {
            return false;
        }

        const activeLobbyId = getActiveLobbyId();
        if (joiningLobbyId && lobbyId === joiningLobbyId) {
            return false;
        }
        if (exitingLobbyId && lobbyId === exitingLobbyId) {
            return true;
        }
        if (activeLobbyId && lobbyId !== activeLobbyId) {
            return true;
        }
        if (!activeLobbyId && joiningLobbyId && lobbyId !== joiningLobbyId) {
            return true;
        }

        return false;
    }

    function acceptLobbyScopedMessage(lobbyId) {
        if (!lobbyId) {
            return;
        }
        if (joiningLobbyId === lobbyId) {
            joiningLobbyId = null;
        }
        if (exitingLobbyId === lobbyId) {
            exitingLobbyId = null;
        }
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
            joiningLobbyId = null;
            exitingLobbyId = null;
            resetLobbyAndGameState();
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
            leaveCurrentLobby();
        });

        document.getElementById('btn-start-game').addEventListener('click', () => {
            RoboSocket.send('START_GAME', {});
        });

        document.getElementById('btn-add-bot').addEventListener('click', () => {
            RoboSocket.send('ADD_BOT', {});
        });

        document.getElementById('game-board-select').addEventListener('change', (e) => {
            const boardName = e.target.value;
            const checkpoints = syncCheckpointSelect(boardName);
            renderMapPreview(boardName);
            const settings = { boardName };
            if (checkpoints !== null) {
                settings.checkpoints = checkpoints;
            }
            RoboSocket.send('UPDATE_GAME_SETTINGS', { settings });
        });

        document.getElementById('game-checkpoints').addEventListener('change', (e) => {
            const boardName = document.getElementById('game-board-select').value;
            const checkpoints = syncCheckpointSelect(boardName, e.target.value);
            if (checkpoints !== null) {
                RoboSocket.send('UPDATE_GAME_SETTINGS', { settings: { checkpoints } });
            }
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
            joiningLobbyId = null;
            exitingLobbyId = null;
            resetLobbyAndGameState();
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
            RoboSocket.send('REQUEST_AVAILABLE_BOARDS', {});
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
            joiningLobbyId = null;
            exitingLobbyId = null;
            resetLobbyAndGameState();
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
            const nextLobby = data.lobby || data;
            if (shouldIgnoreLobbyScopedMessage(nextLobby?.id)) {
                return;
            }
            acceptLobbyScopedMessage(nextLobby?.id);
            currentLobby = nextLobby;
            renderLobbyRoom(currentLobby);
            if (currentScreen === 'menu') {
                showScreen('lobby');
            }
        });

        RoboSocket.on('LOBBY_CLOSED', (data) => {
            const closedLobbyId = data?.lobbyId;

            if (closedLobbyId && exitingLobbyId === closedLobbyId) {
                if (currentUser) {
                    RoboSocket.send('REQUEST_LOBBY_LIST', {});
                }
                return;
            }

            if (shouldIgnoreLobbyScopedMessage(closedLobbyId)) {
                return;
            }

            acceptLobbyScopedMessage(closedLobbyId);
            resetLobbyAndGameState();
            returnToHomeScreen();
            if (currentUser) {
                toast(data?.reason || 'Lobby wurde geschlossen.', 'info');
                RoboSocket.send('REQUEST_LOBBY_LIST', {});
            }
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
            if (shouldIgnoreLobbyScopedMessage(data?.lobbyId)) {
                return;
            }
            acceptLobbyScopedMessage(data?.lobbyId);
            if (!gameState) {
                gameState = data;
            } else {
                Object.assign(gameState, data);
            }
            showScreen('game');
            renderBoard();
            renderGameInfo();
        });

        RoboSocket.on('CARDS_DEALT', (data) => {
            if (shouldIgnoreLobbyScopedMessage(data?.lobbyId)) {
                return;
            }
            acceptLobbyScopedMessage(data?.lobbyId);
            dealtCards = data.cards || [];
            selectedCards = [];
            blockedSlots = data.blockedSlots || 0;
            renderCardHand();
            toast(`Runde ${data.round}: ${dealtCards.length} Karten erhalten!`, 'info');
        });

        RoboSocket.on('PROGRAMMING_PHASE_START', (data) => {
            if (shouldIgnoreLobbyScopedMessage(data?.lobbyId)) {
                return;
            }
            acceptLobbyScopedMessage(data?.lobbyId);
            if (data.status === 'submitted') {
                toast(data.message || 'Programm eingereicht!', 'success');
            }
        });

        RoboSocket.on('EXECUTION_STEP', (data) => {
            if (shouldIgnoreLobbyScopedMessage(data?.lobbyId)) {
                return;
            }
            acceptLobbyScopedMessage(data?.lobbyId);
            if (!gameState) {
                return;
            }
            if (data.robots) {
                gameState.robots = data.robots;
                renderBoard();
            }
            toast(`Schritt ${data.step} ausgeführt`, 'info');
        });

        RoboSocket.on('GAME_OVER', (data) => {
            if (shouldIgnoreLobbyScopedMessage(data?.lobbyId)) {
                return;
            }
            acceptLobbyScopedMessage(data?.lobbyId);
            clearRoundState();
            showScreen('end');
            renderEndScreen(data);
        });

        // Errors
        RoboSocket.on('ERROR', (data) => {
            const msg = data.message || 'Unbekannter Fehler.';
            if (!getActiveLobbyId()) {
                joiningLobbyId = null;
            }
            if (currentScreen === 'login') {
                showAuthMessage(msg);
            } else {
                toast(msg, 'error');
            }
            // Request settings again if an update failed (e.g., spawn size error)
            RoboSocket.send('REQUEST_LOBBY_LIST', {});
        });

        RoboSocket.on('AVAILABLE_BOARDS', (data) => {
            availableBoards = data.boards || [];
            updateMapSelects();
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
        WALL: '#c0c0c0'
    };

    const ASSETS = {};
    const ASSET_STATES = {};
    function getAsset(src) {
        if (!src) return null;
        if (!ASSETS[src]) {
            const img = new Image();
            img.src = src;
            img.onload = () => {
                ASSET_STATES[src] = 'loaded';
                if (currentScreen === 'game') renderBoard();
                else updateMapSelects();
            };
            img.onerror = () => {
                ASSET_STATES[src] = 'error';
                if (currentScreen === 'game') renderBoard();
                else updateMapSelects();
            };
            ASSETS[src] = img;
            ASSET_STATES[src] = 'loading';
            return null;
        }
        return ASSET_STATES[src] === 'loaded' && ASSETS[src].complete && ASSETS[src].naturalWidth > 0 ? ASSETS[src] : null;
    }

    function resolveLayerPath(layer) {
        if (typeof layer === 'string') return layer;
        if (!layer || !layer.src) return null;
        if (layer.fallback && ASSET_STATES[layer.src] === 'error') return layer.fallback;
        return layer.src;
    }

    function getLayerAsset(layer) {
        return getAsset(resolveLayerPath(layer));
    }

    function normalizeCurveRotation(curveRotation) {
        if (curveRotation === 'LEFT' || curveRotation === 'RIGHT') return curveRotation;
        if (!curveRotation) return null;
        if (curveRotation === 'CLOCKWISE') return 'RIGHT';
        if (curveRotation === 'COUNTERCLOCKWISE') return 'LEFT';
        return null;
    }

    function getCurveRotationFromDirections(curveFrom, direction) {
        if (!curveFrom || !direction) return null;
        const clockwiseTurns = {
            NORTH: 'EAST',
            EAST: 'SOUTH',
            SOUTH: 'WEST',
            WEST: 'NORTH'
        };
        if (clockwiseTurns[curveFrom] === direction) return 'RIGHT';
        const counterClockwiseTurns = {
            NORTH: 'WEST',
            WEST: 'SOUTH',
            SOUTH: 'EAST',
            EAST: 'NORTH'
        };
        if (counterClockwiseTurns[curveFrom] === direction) return 'LEFT';
        return null;
    }

    function getTileLayerPaths(t) {
        if (t.type === 'PIT') return ['/assets/fields/PIT_TOP.png'];
        const layers = ['/assets/fields/DEFAULT_TOP.png'];
        if (t.type === 'START') layers.push('/assets/fields/START_TOP.png');
        if (t.type === 'REPAIR_1') layers.push('/assets/fields/REPAIR_TOP.png');
        if (t.type === 'REPAIR_2') layers.push('/assets/fields/REPAIR_TWICE_TOP.png');
        
        let dirMap = { NORTH: 'TOP', SOUTH: 'BOTTOM', EAST: 'RIGHT', WEST: 'LEFT' };
        
        if (t.conveyorBelt) {
            const pre = t.conveyorBelt.express ? 'EXPRESS_BELT_' : 'CONVEYOR_BELT_';
            let dir = dirMap[t.conveyorBelt.direction] || 'TOP';
            const curveRotation = normalizeCurveRotation(t.conveyorBelt.curveRotation)
                || getCurveRotationFromDirections(t.conveyorBelt.curveFrom, t.conveyorBelt.direction);
            
            if (curveRotation) {
                const curveMap = { LEFT: 'LEFT_', RIGHT: 'RIGHT_' };
                const cDir = curveMap[curveRotation];
                const curveAssetDir = dir === 'LEFT' ? 'RIGHT' : dir === 'RIGHT' ? 'LEFT' : dir;
                layers.push(`/assets/fields/${pre}CURVE_${cDir}${curveAssetDir}.png`);
            } else if (t.conveyorBelt.crossing) {
                const crossingType = (t.conveyorBelt.crossingType || 'LEFTRIGHT').toUpperCase();
                const fallback = `/assets/fields/${pre}CROSSING_LEFTRIGHT_${dir}.png`;
                if (crossingType === 'LEFTRIGHT') {
                    layers.push(fallback);
                } else {
                    layers.push({
                        src: `/assets/fields/${pre}CROSSING_${crossingType}_${dir}.png`,
                        fallback
                    });
                }
            } else {
                layers.push(`/assets/fields/${pre}${dir}.png`);
            }
        }
        if (t.gear) {
            layers.push(t.gear.rotation === 'CLOCKWISE' ? '/assets/fields/CLOCKWISE_TURN_TOP.png' : '/assets/fields/COUNTER_CLOCKWISE_TURN_TOP.png');
        }
        if (t.pusher) {
            const steps = Array.isArray(t.pusher.steps) ? [...t.pusher.steps].sort((a, b) => a - b) : [];
            const pusherVariant = steps.join(',') === '1' ? '1'
                : steps.join(',') === '2' ? '2'
                : steps.join(',') === '3' ? '3'
                : steps.join(',') === '2,4' ? '24'
                : '135';
            layers.push(`/assets/fields/PUSHER_${pusherVariant}_CONTRACTED_${dirMap[t.pusher.direction] || 'TOP'}.png`);
        }
        if (t.press) {
            const steps = Array.isArray(t.press.steps) ? [...t.press.steps].sort((a, b) => a - b) : [];
            const pressVariant = steps.join(',') === '2,4' ? '24'
                : steps.join(',') === '3' ? '3'
                : '15';
            layers.push(`/assets/fields/PRESS_${pressVariant}_OPEN.png`);
        }
        if (t.checkpoint) {
            let num = Math.min(t.checkpoint.number, 6);
            layers.push(`/assets/fields/CHECKPOINT_${num}_TOP.png`);
        }
        if (t.walls && t.walls.length > 0) {
            for (const wall of t.walls) {
                layers.push(`/assets/overlays/WALL_${dirMap[wall] || 'TOP'}.png`);
            }
        }
        return layers;
    }

    function getBoardTileLookup(tiles) {
        return new Map((tiles || []).map(tile => [`${tile.x},${tile.y}`, tile]));
    }

    function drawBoardLasers(ctx, board, tiles, robots = [], tileSize = TILE_SIZE) {
        const lasers = board.lasers || [];
        if (lasers.length === 0) return;

        const boardWidth = board.width || 12;
        const boardHeight = board.height || 12;
        const dirMap = { NORTH: 'TOP', SOUTH: 'BOTTOM', EAST: 'RIGHT', WEST: 'LEFT' };
        const tileLookup = getBoardTileLookup(tiles);
        const robotPositions = new Set((robots || [])
            .filter(robot => !robot.destroyed)
            .map(robot => `${robot.x},${robot.y}`));

        for (const laser of lasers) {
            const dir = dirMap[laser.direction] || 'TOP';
            const sourceDir = dir === 'TOP' ? 'BOTTOM'
                : dir === 'BOTTOM' ? 'TOP'
                : dir === 'LEFT' ? 'RIGHT'
                : 'LEFT';
            const laserAsset = laser.strength === 3 ? `TRIPLE_LASER_SOURCE_${sourceDir}.png` :
                               laser.strength === 2 ? `DOUBLE_LASER_SOURCE_${sourceDir}.png` : `LASER_SOURCE_${sourceDir}.png`;
            const img = getAsset('/assets/fields/' + laserAsset);
            if (img) ctx.drawImage(img, laser.x * tileSize, laser.y * tileSize, tileSize, tileSize);

            let cx = laser.x;
            let cy = laser.y;
            const dx = laser.direction === 'EAST' ? 1 : laser.direction === 'WEST' ? -1 : 0;
            const dy = laser.direction === 'SOUTH' ? 1 : laser.direction === 'NORTH' ? -1 : 0;
            const isHorizontal = dx !== 0;

            const beamAssetPrefix = laser.strength === 3 ? 'TRIPLE_LASER_OVERLAY_' :
                                    laser.strength === 2 ? 'DOUBLE_LASER_OVERLAY_' : '';
            const beamAssetSuffix = isHorizontal ? (laser.strength > 1 ? 'HORIZONTAL.png' : 'HorizontalLaserOverlay.png') :
                                                   (laser.strength > 1 ? 'VERTICAL.png' : 'VerticalLaserOverlay.png');
            const beamImg = getAsset(`/assets/overlays/${beamAssetPrefix}${beamAssetSuffix}`);
            const targetDirOpposite = laser.direction === 'NORTH' ? 'SOUTH' :
                                      laser.direction === 'SOUTH' ? 'NORTH' :
                                      laser.direction === 'EAST' ? 'WEST' : 'EAST';

            let blocked = false;
            while (!blocked) {
                const currentTile = tileLookup.get(`${cx},${cy}`);
                if (currentTile && currentTile.walls && currentTile.walls.includes(laser.direction)) {
                    blocked = true;
                    break;
                }

                const nextX = cx + dx;
                const nextY = cy + dy;

                if (nextX < 0 || nextY < 0 || nextX >= boardWidth || nextY >= boardHeight) {
                    blocked = true;
                    break;
                }

                const targetTile = tileLookup.get(`${nextX},${nextY}`);
                if (targetTile && targetTile.walls && targetTile.walls.includes(targetDirOpposite)) {
                    blocked = true;
                    break;
                }

                cx = nextX;
                cy = nextY;

                if (beamImg) {
                    ctx.drawImage(beamImg, cx * tileSize, cy * tileSize, tileSize, tileSize);
                }

                if (robotPositions.has(`${cx},${cy}`)) {
                    blocked = true;
                    break;
                }
            }
        }
    }

    function getBoardInfo(boardName) {
        return availableBoards.find(board => board.id === boardName) || null;
    }

    function syncCheckpointSelect(boardName, desiredValue) {
        const checkpointSelect = document.getElementById('game-checkpoints');
        if (!checkpointSelect) return null;

        const boardInfo = getBoardInfo(boardName);
        if (!boardInfo) {
            return null;
        }

        const maxCheckpoints = Math.max(0, Number(boardInfo.boardData?.totalCheckpoints) || 0);
        if (maxCheckpoints === 0) {
            checkpointSelect.innerHTML = '';
            checkpointSelect.disabled = true;
            return 0;
        }

        const minCheckpoints = Math.min(2, maxCheckpoints);
        const numericValue = Number.parseInt(desiredValue ?? checkpointSelect.value, 10);
        const selectedCheckpoints = Number.isFinite(numericValue) ? numericValue : maxCheckpoints;
        const clampedCheckpoints = Math.min(maxCheckpoints, Math.max(minCheckpoints, selectedCheckpoints));

        checkpointSelect.innerHTML = '';
        for (let value = minCheckpoints; value <= maxCheckpoints; value++) {
            const option = document.createElement('option');
            option.value = String(value);
            option.textContent = String(value);
            checkpointSelect.appendChild(option);
        }

        checkpointSelect.value = String(clampedCheckpoints);
        return clampedCheckpoints;
    }

    function getRobotImagePath(robot) {
        const colors = ['BLUE', 'GREEN', 'GREY', 'ORANGE', 'PINK', 'PURPLE', 'RED', 'YELLOW'];
        const color = colors[robot.robotIndex % colors.length];
        const dirMap = { NORTH: 'TOP', SOUTH: 'BOTTOM', EAST: 'RIGHT', WEST: 'LEFT' };
        return `/assets/robots/${color}ROBOT_${dirMap[robot.direction] || 'TOP'}.png`;
    }

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

        const defaultFloor = getAsset('/assets/fields/DEFAULT_TOP.png');
        for (let y = 0; y < h; y++) {
            for (let x = 0; x < w; x++) {
                const px = x * TILE_SIZE;
                const py = y * TILE_SIZE;
                if (defaultFloor) {
                    ctx.drawImage(defaultFloor, px, py, TILE_SIZE, TILE_SIZE);
                } else {
                    ctx.fillStyle = TILE_COLORS.FLOOR;
                    ctx.fillRect(px, py, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        const tiles = board.tiles || [];
        for (const t of tiles) {
            const px = t.x * TILE_SIZE;
            const py = t.y * TILE_SIZE;

            const layers = getTileLayerPaths(t);
            for (const layer of layers) {
                const img = getLayerAsset(layer);
                if (img) ctx.drawImage(img, px, py, TILE_SIZE, TILE_SIZE);
            }

        }

        const robots = gameState.robots || [];
        drawBoardLasers(ctx, board, tiles, robots, TILE_SIZE);

        for (const robot of robots) {
            if (robot.destroyed) continue;
            const rx = robot.x * TILE_SIZE;
            const ry = robot.y * TILE_SIZE;
            const img = getAsset(getRobotImagePath(robot));
            if (img) {
                ctx.drawImage(img, rx, ry, TILE_SIZE, TILE_SIZE);
            } else {
                ctx.fillStyle = ROBOT_COLORS[robot.robotIndex % ROBOT_COLORS.length];
                ctx.beginPath();
                ctx.arc(rx + TILE_SIZE/2, ry + TILE_SIZE/2, TILE_SIZE/3, 0, Math.PI * 2);
                ctx.fill();
            }
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
        currentLobby = lobby;
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

        // Configure start button and settings access only for host
        const isHost = currentUser && players.some(p => p.userId === currentUser.userId && p.isHost);
        const startBtn = document.getElementById('btn-start-game');
        const addBotBtn = document.getElementById('btn-add-bot');
        const boardSelect = document.getElementById('game-board-select');
        const checkpointSelect = document.getElementById('game-checkpoints');
        const laserCheck = document.getElementById('game-robot-lasers');
        const shutdownCheck = document.getElementById('game-shutdown');

        if (isHost) {
            startBtn.classList.remove('hidden');
            if (addBotBtn) addBotBtn.classList.remove('hidden');
            if (boardSelect) boardSelect.disabled = false;
            if (checkpointSelect) checkpointSelect.disabled = false;
            if (laserCheck) laserCheck.disabled = false;
            if (shutdownCheck) shutdownCheck.disabled = false;
        } else {
            startBtn.classList.add('hidden');
            if (addBotBtn) addBotBtn.classList.add('hidden');
            if (boardSelect) boardSelect.disabled = true;
            if (checkpointSelect) checkpointSelect.disabled = true;
            if (laserCheck) laserCheck.disabled = true;
            if (shutdownCheck) shutdownCheck.disabled = true;
        }

        const currentBoard = lobby.gameSettings?.boardName || 'map1';
        if (boardSelect) {
            boardSelect.value = currentBoard;
        }
        const checkpoints = syncCheckpointSelect(currentBoard, lobby.gameSettings?.checkpoints);
        if (checkpointSelect && checkpoints === null && lobby.gameSettings?.checkpoints) {
            checkpointSelect.value = lobby.gameSettings.checkpoints;
        }
        renderMapPreview(currentBoard);
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

    function updateMapSelects() {
        const select = document.getElementById('game-board-select');
        if (!select) return;
        const currentVal = currentLobby?.gameSettings?.boardName || select.value;
        select.innerHTML = availableBoards.map(b => `<option value="${b.id}">${b.name} (Max: ${b.maxPlayers})</option>`).join('');
        if (availableBoards.some(b => b.id === currentVal)) {
            select.value = currentVal;
        }

        const checkpoints = syncCheckpointSelect(select.value, currentLobby?.gameSettings?.checkpoints);
        if (currentLobby && checkpoints !== null) {
            currentLobby.gameSettings = currentLobby.gameSettings || {};
            currentLobby.gameSettings.checkpoints = checkpoints;
        }
        renderMapPreview(select.value);
    }

    function renderMapPreview(boardName) {
        const select = document.getElementById('game-board-select');
        if (!select) return;
        boardName = boardName || select.value;
        const boardInfo = getBoardInfo(boardName);
        if (!boardInfo) return;

        const info = document.getElementById('map-preview-info');
        if (info) info.textContent = `Max. Spieler: ${boardInfo.maxPlayers} | Checkpoints: ${boardInfo.boardData.totalCheckpoints}`;

        const canvas = document.getElementById('map-preview-canvas');
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        const PREVIEW_TILE_SIZE = 20;

        const board = boardInfo.boardData;
        const w = board.width || 12;
        const h = board.height || 12;

        canvas.width = w * PREVIEW_TILE_SIZE;
        canvas.height = h * PREVIEW_TILE_SIZE;

        const defaultFloor = getAsset('/assets/fields/DEFAULT_TOP.png');
        for (let y = 0; y < h; y++) {
            for (let x = 0; x < w; x++) {
                const px = x * PREVIEW_TILE_SIZE;
                const py = y * PREVIEW_TILE_SIZE;
                if (defaultFloor) {
                    ctx.drawImage(defaultFloor, px, py, PREVIEW_TILE_SIZE, PREVIEW_TILE_SIZE);
                } else {
                    ctx.fillStyle = TILE_COLORS.FLOOR;
                    ctx.fillRect(px, py, PREVIEW_TILE_SIZE, PREVIEW_TILE_SIZE);
                }
            }
        }

        const tiles = board.tiles || [];
        for (const t of tiles) {
            const px = t.x * PREVIEW_TILE_SIZE;
            const py = t.y * PREVIEW_TILE_SIZE;

            const layers = getTileLayerPaths(t);
            for (const layer of layers) {
                const img = getLayerAsset(layer);
                if (img) ctx.drawImage(img, px, py, PREVIEW_TILE_SIZE, PREVIEW_TILE_SIZE);
            }

        }

        drawBoardLasers(ctx, board, tiles, [], PREVIEW_TILE_SIZE);
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
        joiningLobbyId = lobbyId;
        if (hasPassword) {
            const pw = prompt('Lobby-Passwort eingeben:');
            if (pw === null) {
                joiningLobbyId = null;
                return; // canceled
            }
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
                leaveCurrentLobby();
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
