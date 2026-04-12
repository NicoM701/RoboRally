/**
 * RoboRally Main Application
 * Screen management, auth flows, lobby interactions, chat.
 */
const App = (() => {
    // ─── State ──────────────────────────────────────────
    let currentUser = null;   // { userId, username, isGuest }
    let currentScreen = 'login';
    let currentLobby = null;
    let availableBoards = [];

    // ─── Initialization ─────────────────────────────────

    function init() {
        bindAuthEvents();
        bindMenuEvents();
        bindLobbyEvents();
        bindChatEvents();
        bindSettingsEvents();
        bindServerMessages();
        startGameUiLoop();

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
            resetGamePresentation();
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
            currentLobby = null;
            resetGamePresentation();
            RoboSocket.send('LEAVE_LOBBY', {});
            showScreen('menu');
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
            resetGamePresentation();
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
            currentLobby = data.lobby || data;
            renderLobbyRoom(currentLobby);
            if (currentScreen === 'game') {
                gameState = null;
                dealtCards = [];
                selectedCards = [];
                blockedSlots = 0;
                showScreen('lobby');
            } else if (currentScreen === 'menu') {
                showScreen('lobby');
            }
        });

        RoboSocket.on('LOBBY_CLOSED', () => {
            currentLobby = null;
            resetGamePresentation();
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
            if (!shouldAcceptGameState(data)) {
                return;
            }

            applyIncomingGameState(data);
        });

        RoboSocket.on('CARDS_DEALT', (data) => {
            if (!shouldAcceptCurrentGameMessage(data)) {
                return;
            }

            applyDealtCards(data);
        });

        RoboSocket.on('PROGRAMMING_PHASE_START', (data) => {
            if (!shouldAcceptCurrentGameMessage(data)) {
                return;
            }

            if (data.status === 'submitted') {
                confirmProgramSubmission();
                toast(data.message || 'Programm eingereicht!', 'success');
                return;
            }

            applyProgrammingUpdate(data);
            if (data.submittedUsername && data.submittedPlayerId !== currentUser?.userId) {
                pushGameEvent(`${data.submittedUsername} hat sein Programm eingerastet.`, 'programming');
            }
            renderCardHand();
            renderGameInfo();
        });

        RoboSocket.on('EXECUTION_STEP', (data) => {
            queueExecutionStep(data);
        });

        RoboSocket.on('GAME_OVER', (data) => {
            if (!shouldAcceptGameOver(data)) {
                return;
            }

            if (executionPlayback.isPlaying || executionPlayback.queue.length > 0) {
                executionPlayback.pendingGameOver = data;
                return;
            }

            showGameOver(data);
        });

        // Errors
        RoboSocket.on('ERROR', (data) => {
            const msg = data.message || 'Unbekannter Fehler.';
            rollbackPendingProgramSubmission();
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
    let submittedProgramPreview = [];
    let gameEventLog = [];
    let programmingState = createProgrammingState();
    let executionPlayback = createExecutionPlaybackState();
    let gameUiLoop = null;

    const TILE_SIZE = 48;
    const ROBOT_COLORS = ['#3498db', '#2ecc71', '#95a5a6', '#e67e22', '#ff66cc', '#9b59b6', '#e74c3c', '#f1c40f'];
    const ROBOT_LABELS = ['Blau', 'Grün', 'Grau', 'Orange', 'Pink', 'Lila', 'Rot', 'Gelb'];
    const TILE_COLORS = {
        FLOOR: '#3d4f5f',
        PIT: '#0d0d0d',
        START: '#4a6741',
        WALL: '#c0c0c0'
    };
    const PHASE_LABELS = {
        WAITING: 'Bereitmachen',
        DEALING_CARDS: 'Karten werden verteilt',
        PROGRAMMING: 'Programmieren',
        EXECUTING: 'Ausführung',
        ROUND_CLEANUP: 'Aufräumen',
        GAME_OVER: 'Spiel vorbei'
    };
    const PHASE_COPY = {
        WAITING: 'Noch einen Moment. Die Fabrik wird vorbereitet.',
        DEALING_CARDS: 'Neue Hand kommt rein. Gleich geht’s wieder los.',
        PROGRAMMING: 'Wähle jetzt 5 Register in der Reihenfolge aus, in der dein Roboter sie fahren soll.',
        EXECUTING: 'Die Register werden nacheinander abgespielt. Jetzt lieber schauen als hektisch klicken.',
        ROUND_CLEANUP: 'Die Runde wird gerade abgeschlossen.',
        GAME_OVER: 'Die Fabrik hat gesprochen.'
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
    const CARD_LABELS = {
        MOVE_1: 'Vor 1',
        MOVE_2: 'Vor 2',
        MOVE_3: 'Vor 3',
        BACKUP: 'Rückwärts',
        TURN_LEFT: 'Links drehen',
        TURN_RIGHT: 'Rechts drehen',
        U_TURN: 'Wenden'
    };

    function createProgrammingState() {
        return {
            enabled: false,
            totalSeconds: 60,
            deadlineEpochMs: null,
            submittedCount: 0,
            totalPlayers: 0,
            isSubmitted: false,
            submitPending: false,
            pendingCardIds: []
        };
    }

    function createExecutionPlaybackState() {
        return {
            queue: [],
            isPlaying: false,
            round: null,
            currentStep: 0,
            currentSummary: '',
            pendingGameOver: null,
            timerId: null
        };
    }

    function resetMatchPresentationState() {
        dealtCards = [];
        selectedCards = [];
        blockedSlots = 0;
        submittedProgramPreview = [];
        gameEventLog = [];
        programmingState = createProgrammingState();
        executionPlayback = createExecutionPlaybackState();
    }

    function clearExecutionPlaybackTimer() {
        if (executionPlayback?.timerId) {
            window.clearTimeout(executionPlayback.timerId);
            executionPlayback.timerId = null;
        }
    }

    function startGameUiLoop() {
        if (gameUiLoop) return;
        gameUiLoop = window.setInterval(() => {
            if (currentScreen !== 'game' || !gameState) return;
            if (getDisplayedPhase() === 'PROGRAMMING' || executionPlayback.isPlaying) {
                renderGameInfo();
            }
        }, 250);
    }

    function resetGamePresentation() {
        clearExecutionPlaybackTimer();
        gameState = null;
        resetMatchPresentationState();
    }

    function applyIncomingGameState(data) {
        const isNewGameInstance = !gameState?.gameInstanceId || data?.gameInstanceId !== gameState.gameInstanceId;

        if (isNewGameInstance) {
            clearExecutionPlaybackTimer();
            resetMatchPresentationState();
            gameState = { ...data };
        } else {
            Object.assign(gameState, data);
        }

        if (!programmingState.totalPlayers && gameState.robots) {
            programmingState.totalPlayers = gameState.robots.filter(robot => !robot.destroyed).length;
        }

        if (data.phase === 'PROGRAMMING') {
            applyProgrammingUpdate({ phase: 'PROGRAMMING' });
        }

        showScreen('game');
        renderBoard();
        renderGameInfo();
    }

    function hasLobbyGameContext() {
        return Boolean(currentUser && currentLobby && ['lobby', 'game', 'end'].includes(currentScreen));
    }

    function hasActiveGamePresentation() {
        return Boolean(currentUser && currentLobby && gameState && currentScreen === 'game');
    }

    function hasCompleteGameMessageContext(data) {
        return Boolean(currentLobby?.id && data?.lobbyId && data?.gameInstanceId);
    }

    function hasMatchingGameStateLobby(data) {
        if (!hasCompleteGameMessageContext(data)) {
            return false;
        }

        return data.lobbyId === currentLobby.id;
    }

    function hasMatchingCurrentGameInstance(data) {
        return Boolean(
            hasMatchingGameStateLobby(data)
            && gameState?.gameInstanceId
            && data.gameInstanceId === gameState.gameInstanceId
        );
    }

    function shouldAcceptCurrentGameMessage(data) {
        return hasActiveGamePresentation() && hasMatchingCurrentGameInstance(data);
    }

    function isFullGameStateSnapshot(data) {
        return Boolean(data?.board && Array.isArray(data?.robots));
    }

    function shouldAcceptGameState(data) {
        if (!hasLobbyGameContext() || !hasMatchingGameStateLobby(data)) {
            return false;
        }

        if (!gameState) {
            return isFullGameStateSnapshot(data);
        }

        if (currentScreen === 'end' || gameState?.phase === 'GAME_OVER') {
            return data.gameInstanceId !== gameState.gameInstanceId && isFullGameStateSnapshot(data);
        }

        if (!hasMatchingCurrentGameInstance(data)) {
            return false;
        }

        return hasMatchingExecutionRobots(data?.robots);
    }

    function hasMatchingExecutionRobots(robots) {
        if (!Array.isArray(robots) || !robots.length || !Array.isArray(gameState?.robots) || !gameState.robots.length) {
            return true;
        }

        const currentPlayerIds = [...new Set(gameState.robots.map(robot => robot.playerId))].sort((a, b) => a - b);
        const incomingPlayerIds = [...new Set(robots.map(robot => robot.playerId))].sort((a, b) => a - b);

        if (currentPlayerIds.length !== incomingPlayerIds.length) {
            return false;
        }

        return currentPlayerIds.every((playerId, index) => playerId === incomingPlayerIds[index]);
    }

    function shouldAcceptExecutionStep(data) {
        if (!shouldAcceptCurrentGameMessage(data)) {
            return false;
        }

        if (!hasMatchingExecutionRobots(data?.robots)) {
            return false;
        }

        if (executionPlayback.isPlaying || executionPlayback.queue.length > 0) {
            return true;
        }

        if (gameState?.phase !== 'EXECUTING') {
            return false;
        }

        if (data?.round && gameState?.round && data.round !== gameState.round) {
            return false;
        }

        return true;
    }

    function shouldAcceptGameOver(data) {
        if (!hasMatchingCurrentGameInstance(data)) {
            return false;
        }

        if (executionPlayback.isPlaying || executionPlayback.queue.length > 0) {
            return true;
        }

        if (!hasActiveGamePresentation()) {
            return false;
        }

        return ['EXECUTING', 'ROUND_CLEANUP', 'GAME_OVER'].includes(gameState?.phase);
    }

    function getPlayerName(playerId) {
        const player = currentLobby?.players?.find(entry => entry.userId === playerId);
        return player?.username || `Spieler ${playerId}`;
    }

    function getRobotLabel(robot) {
        return `${ROBOT_LABELS[robot.robotIndex % ROBOT_LABELS.length]}-Roboter`;
    }

    function getRobotAccent(robot) {
        return ROBOT_COLORS[robot.robotIndex % ROBOT_COLORS.length];
    }

    function getLocalRobot() {
        if (!currentUser || !gameState?.robots) return null;
        return gameState.robots.find(robot => robot.playerId === currentUser.userId) || null;
    }

    function pushGameEvent(text, tone = 'info') {
        if (!text) return;
        gameEventLog.unshift({ text, tone, id: `${Date.now()}-${Math.random()}` });
        gameEventLog = gameEventLog.slice(0, 8);
    }

    function getDisplayedPhase() {
        if (executionPlayback.isPlaying || executionPlayback.queue.length > 0) {
            return 'EXECUTING';
        }
        return gameState?.phase || 'WAITING';
    }

    function getDisplayedRound() {
        if (executionPlayback.isPlaying && executionPlayback.round) {
            return executionPlayback.round;
        }
        return gameState?.round || 1;
    }

    function getPhaseLabel(phase) {
        return PHASE_LABELS[phase] || phase || 'Unbekannt';
    }

    function getPhaseCopy(phase) {
        if (phase === 'PROGRAMMING' && programmingState.isSubmitted) {
            return 'Dein Programm sitzt. Jetzt können die anderen fertig planen oder der Timer läuft aus.';
        }
        if (phase === 'EXECUTING' && gameState?.phase === 'PROGRAMMING') {
            return 'Der Replay der letzten Register läuft noch. Deine nächste Hand ist schon da – du kannst parallel weiterprogrammieren.';
        }
        if (phase === 'EXECUTING' && executionPlayback.currentSummary) {
            return executionPlayback.currentSummary;
        }
        return PHASE_COPY[phase] || 'Die Fabrik läuft.';
    }

    function getProgrammingRemainingMs() {
        if (!programmingState.enabled || !programmingState.deadlineEpochMs) return 0;
        return Math.max(0, programmingState.deadlineEpochMs - Date.now());
    }

    function getProgrammingProgressPercent() {
        if (!programmingState.enabled || !programmingState.totalSeconds) return 100;
        const remainingRatio = getProgrammingRemainingMs() / (programmingState.totalSeconds * 1000);
        return Math.max(0, Math.min(100, remainingRatio * 100));
    }

    function formatCountdown(ms) {
        const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
    }

    function getRequiredCardCount() {
        return Math.max(0, 5 - blockedSlots);
    }

    function getLocalProgramStatus() {
        const requiredCards = getRequiredCardCount();

        if (programmingState.isSubmitted) {
            return {
                label: 'Programm bestätigt',
                detail: 'Dein Programm ist sicher auf dem Server. Jetzt fehlt nur noch der Rest.',
                tone: 'success'
            };
        }

        if (programmingState.submitPending) {
            return {
                label: 'Bestätigung läuft',
                detail: 'Dein Programm wurde abgeschickt und wartet gerade auf Server-Bestätigung.',
                tone: 'warning'
            };
        }

        if (dealtCards.length) {
            if (selectedCards.length === requiredCards) {
                return {
                    label: 'Bereit zum Einreichen',
                    detail: 'Alles gewählt – ein Klick fehlt noch.',
                    tone: 'info'
                };
            }

            return {
                label: `${selectedCards.length}/${requiredCards} Karten gewählt`,
                detail: `Wähle noch ${Math.max(0, requiredCards - selectedCards.length)} Karte(n), dann kannst du einreichen.`,
                tone: 'info'
            };
        }

        if (gameState?.phase === 'PROGRAMMING') {
            return {
                label: 'Warte auf deine Hand',
                detail: 'Sobald die Karten da sind, kannst du direkt loslegen.',
                tone: 'muted'
            };
        }

        return {
            label: 'Zwischen den Phasen',
            detail: 'Gleich geht die nächste Hand auf.',
            tone: 'muted'
        };
    }

    function getNextStepHint(phase, showProgrammingStatus) {
        if (showProgrammingStatus) {
            if (programmingState.isSubmitted) {
                return 'Sobald alle eingeloggt sind oder der Timer endet, startet die Register-Ausführung.';
            }
            if (programmingState.submitPending) {
                return 'Nach der Bestätigung ist dein Platz fix und du wartest nur noch auf den Rest.';
            }
            if (executionPlayback.isPlaying) {
                return 'Der Replay der letzten Register läuft noch, aber deine neue Planung ist schon offen.';
            }
            return 'Programmiere jetzt deinen Zug – danach feuert die Fabrik Register 1 bis 5 nacheinander ab.';
        }

        if (phase === 'EXECUTING') {
            if (executionPlayback.currentStep >= 5) {
                return 'Nach dem letzten Register startet direkt die nächste Programmierphase.';
            }
            return `Als Nächstes kommt Register ${Math.min(5, executionPlayback.currentStep + 1)}/5.`;
        }

        return 'Sobald die Runde vorbereitet ist, öffnet sich die nächste Programmierphase.';
    }

    function getPhaseModeLabel(phase, showProgrammingStatus) {
        if (executionPlayback.isPlaying && showProgrammingStatus) {
            return 'Replay + Programmierung';
        }
        if (phase === 'EXECUTING') {
            return 'Replay aktiv';
        }
        if (showProgrammingStatus) {
            return 'Planung live';
        }
        return 'Status';
    }

    function renderRegisterTrack() {
        const currentStep = executionPlayback.isPlaying ? executionPlayback.currentStep : 0;

        return `<div class="register-track-panel">
            <div class="timer-row">
                <span>Rundenfluss</span>
                <strong>${executionPlayback.isPlaying ? `Register ${currentStep}/5` : gameState?.phase === 'PROGRAMMING' ? 'Programmierung offen' : 'Bereit'}</strong>
            </div>
            <div class="register-steps">
                ${Array.from({ length: 5 }, (_, index) => {
                    const step = index + 1;
                    let state = 'upcoming';
                    if (executionPlayback.isPlaying) {
                        state = step < currentStep ? 'done' : step === currentStep ? 'current' : 'upcoming';
                    } else if (gameState?.phase === 'PROGRAMMING') {
                        state = 'planning';
                    }

                    return `<div class="register-step ${state}">
                        <span class="register-step-index">${step}</span>
                        <small>Register</small>
                    </div>`;
                }).join('')}
            </div>
        </div>`;
    }

    function renderCardStatusStrip() {
        const requiredCards = getRequiredCardCount();
        const localProgramStatus = getLocalProgramStatus();

        const chips = [
            `${selectedCards.length}/${requiredCards} gewählt`,
            `Benötigt ${requiredCards}`
        ];

        if (blockedSlots > 0) {
            chips.push(`${blockedSlots} blockiert`);
        }

        if (programmingState.totalPlayers) {
            chips.push(`${programmingState.submittedCount}/${programmingState.totalPlayers} eingereicht`);
        }

        chips.push(localProgramStatus.label);

        return `<div class="card-status-strip">${chips.map(label => `<span class="card-status-chip">${escapeHtml(label)}</span>`).join('')}</div>`;
    }

    function applyProgrammingUpdate(data = {}) {
        if ('timerEnabled' in data) {
            programmingState.enabled = Boolean(data.timerEnabled);
            if (!programmingState.enabled) {
                programmingState.deadlineEpochMs = null;
            }
        }

        const timerSeconds = Number(data.timerSeconds);
        if (Number.isFinite(timerSeconds) && timerSeconds > 0) {
            programmingState.totalSeconds = timerSeconds;
        }

        const deadlineEpochMs = Number(data.deadlineEpochMs);
        if (Number.isFinite(deadlineEpochMs) && deadlineEpochMs > 0) {
            programmingState.deadlineEpochMs = deadlineEpochMs;
        }

        const submittedCount = Number(data.submittedCount);
        if (Number.isFinite(submittedCount)) {
            programmingState.submittedCount = submittedCount;
        }

        const totalPlayers = Number(data.totalPlayers);
        if (Number.isFinite(totalPlayers) && totalPlayers >= 0) {
            programmingState.totalPlayers = totalPlayers;
        } else if (!programmingState.totalPlayers && gameState?.robots) {
            programmingState.totalPlayers = gameState.robots.filter(robot => !robot.destroyed).length;
        }

        if ((data.phase === 'PROGRAMMING' || data.status === 'started') && !('submittedPlayerId' in data)) {
            programmingState.isSubmitted = false;
            programmingState.submitPending = false;
            programmingState.pendingCardIds = [];
        }
        if (data.status === 'submitted') {
            programmingState.isSubmitted = true;
        }
    }

    function applyDealtCards(data, { silent = false } = {}) {
        dealtCards = data.cards || [];
        selectedCards = [];
        blockedSlots = data.blockedSlots || 0;
        submittedProgramPreview = [];
        applyProgrammingUpdate({ ...data, phase: 'PROGRAMMING', status: 'started' });
        renderCardHand();
        renderGameInfo();
        if (!silent) {
            pushGameEvent(`Runde ${data.round}: ${dealtCards.length} Karten eingetroffen. Jetzt programmieren.`, 'programming');
            toast(`Runde ${data.round}: ${dealtCards.length} Karten erhalten!`, 'info');
        }
    }

    function getSelectedProgramPreview(cardIds = selectedCards) {
        return cardIds
            .map(cardId => dealtCards.find(card => card.id === cardId))
            .filter(Boolean);
    }

    function confirmProgramSubmission() {
        submittedProgramPreview = getSelectedProgramPreview(
            programmingState.pendingCardIds.length ? programmingState.pendingCardIds : selectedCards
        );
        programmingState.submitPending = false;
        programmingState.pendingCardIds = [];
        programmingState.isSubmitted = true;
        dealtCards = [];
        selectedCards = [];
        pushGameEvent('Programm eingeloggt. Jetzt darf der Rest nachziehen.', 'programming');
        renderCardHand();
        renderGameInfo();
    }

    function rollbackPendingProgramSubmission() {
        if (!programmingState.submitPending) return;
        programmingState.submitPending = false;
        programmingState.pendingCardIds = [];
        renderCardHand();
        renderGameInfo();
    }

    function summarizeExecutionStep(stepData) {
        const results = stepData.results || [];
        if (!results.length) {
            return 'Keine sichtbaren Bewegungen in diesem Register.';
        }

        const counts = results.reduce((acc, result) => {
            const key = result.cardType || 'UNKNOWN';
            acc[key] = (acc[key] || 0) + 1;
            return acc;
        }, {});

        const localAction = currentUser
            ? results.find(result => result.playerId === currentUser.userId && CARD_LABELS[result.cardType])
            : null;

        const parts = [];
        if (localAction) {
            parts.push(`Du: ${CARD_LABELS[localAction.cardType]}`);
        }

        const boardEffects = [
            ['BELT', 'Förderband'],
            ['PUSHER', 'Schieber'],
            ['GEAR', 'Zahnrad'],
            ['BOARD_LASER', 'Board-Laser'],
            ['ROBOT_LASER', 'Roboter-Laser']
        ];

        for (const [key, label] of boardEffects) {
            if (counts[key]) {
                parts.push(`${counts[key]}× ${label}`);
            }
        }

        const moveCards = Object.keys(CARD_LABELS)
            .filter(key => counts[key])
            .slice(0, localAction ? 2 : 3)
            .map(key => `${counts[key]}× ${CARD_LABELS[key]}`);
        if (moveCards.length && !localAction) {
            parts.push(moveCards.join(', '));
        }

        return parts.join(' • ') || `${results.length} Aktionen`;
    }

    function getExecutionStepDelay(stepData) {
        const resultCount = stepData.results?.length || 0;
        return resultCount > 8 ? 1500 : 1150;
    }

    function queueExecutionStep(data) {
        if (!shouldAcceptExecutionStep(data)) {
            return;
        }

        executionPlayback.queue.push({
            ...data,
            round: data.round || executionPlayback.round || gameState?.round || 1,
            summary: summarizeExecutionStep(data)
        });

        if (!executionPlayback.isPlaying) {
            executionPlayback.isPlaying = true;
            executionPlayback.round = data.round || gameState?.round || 1;
            executionPlayback.currentStep = 0;
            executionPlayback.currentSummary = 'Die Fabrik löst deine Register der Reihe nach auf.';
            dealtCards = [];
            selectedCards = [];
            renderCardHand();
            pushGameEvent(`Ausführung gestartet. Die Register werden jetzt gut lesbar abgespielt.`, 'exec');
            processExecutionQueue();
        }
    }

    function processExecutionQueue() {
        if (!executionPlayback.queue.length) {
            finishExecutionPlayback();
            return;
        }

        const nextStep = executionPlayback.queue.shift();
        executionPlayback.isPlaying = true;
        executionPlayback.round = nextStep.round || executionPlayback.round || 1;
        executionPlayback.currentStep = nextStep.step || 0;
        executionPlayback.currentSummary = nextStep.summary;

        if (gameState && nextStep.robots) {
            gameState.robots = nextStep.robots;
            gameState.currentStep = nextStep.step || gameState.currentStep;
        }

        renderBoard();
        renderGameInfo();
        pushGameEvent(`Register ${nextStep.step}: ${nextStep.summary}`, 'exec');

        clearExecutionPlaybackTimer();
        executionPlayback.timerId = window.setTimeout(processExecutionQueue, getExecutionStepDelay(nextStep));
    }

    function finishExecutionPlayback() {
        clearExecutionPlaybackTimer();
        executionPlayback.isPlaying = false;
        executionPlayback.currentStep = 0;
        executionPlayback.currentSummary = '';
        executionPlayback.round = null;

        const pendingGameOver = executionPlayback.pendingGameOver;

        executionPlayback.pendingGameOver = null;

        renderGameInfo();

        if (pendingGameOver) {
            showGameOver(pendingGameOver);
        }
    }

    function showGameOver(data) {
        showScreen('end');
        renderEndScreen(data);
    }

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
        const localPlayerId = currentUser?.userId;

        for (const robot of robots) {
            if (robot.destroyed) continue;
            const rx = robot.x * TILE_SIZE;
            const ry = robot.y * TILE_SIZE;
            const isLocalRobot = localPlayerId === robot.playerId;

            if (isLocalRobot) {
                ctx.save();
                ctx.fillStyle = 'rgba(6, 214, 160, 0.18)';
                ctx.strokeStyle = '#06d6a0';
                ctx.lineWidth = 3;
                ctx.beginPath();
                ctx.arc(rx + TILE_SIZE / 2, ry + TILE_SIZE / 2, TILE_SIZE * 0.4, 0, Math.PI * 2);
                ctx.fill();
                ctx.stroke();
                ctx.restore();
            }

            const img = getAsset(getRobotImagePath(robot));
            if (img) {
                ctx.drawImage(img, rx, ry, TILE_SIZE, TILE_SIZE);
            } else {
                ctx.fillStyle = getRobotAccent(robot);
                ctx.beginPath();
                ctx.arc(rx + TILE_SIZE/2, ry + TILE_SIZE/2, TILE_SIZE/3, 0, Math.PI * 2);
                ctx.fill();
            }

            if (isLocalRobot) {
                ctx.save();
                ctx.fillStyle = '#06d6a0';
                ctx.fillRect(rx + 4, ry + 4, 22, 14);
                ctx.fillStyle = '#0a0e17';
                ctx.font = '700 10px Inter, sans-serif';
                ctx.fillText('DU', rx + 8, ry + 14);
                ctx.restore();
            }
        }
    }

    function renderGameInfo() {
        const panel = document.getElementById('game-info-panel');
        if (!panel || !gameState) return;

        const robots = [...(gameState.robots || [])].sort((a, b) => {
            const aLocal = a.playerId === currentUser?.userId ? 1 : 0;
            const bLocal = b.playerId === currentUser?.userId ? 1 : 0;
            if (aLocal !== bLocal) return bLocal - aLocal;
            if (a.destroyed !== b.destroyed) return a.destroyed ? 1 : -1;
            return a.playerId - b.playerId;
        });
        const localRobot = getLocalRobot();
        const phase = getDisplayedPhase();
        const programmingActive = gameState.phase === 'PROGRAMMING';
        const showProgrammingStatus = phase === 'PROGRAMMING' || programmingActive;
        const round = getDisplayedRound();
        const remainingMs = getProgrammingRemainingMs();
        const timerPercent = getProgrammingProgressPercent();
        const timerTone = remainingMs <= 10000 ? 'danger' : remainingMs <= 20000 ? 'warning' : '';
        const submittedText = programmingState.totalPlayers
            ? `${programmingState.submittedCount}/${programmingState.totalPlayers} eingereicht`
            : 'Status folgt';
        const localProgramStatus = getLocalProgramStatus();
        const nextStepHint = getNextStepHint(phase, showProgrammingStatus);
        const aliveRobots = robots.filter(robot => !robot.destroyed).length;

        let html = '<div class="game-command-center">';
        html += `<div class="phase-hero phase-${phase.toLowerCase()}">
            <div class="phase-badge">Runde ${round}</div>
            <div class="phase-title-row">
                <h3>${getPhaseLabel(phase)}</h3>
                <span class="register-pill">${escapeHtml(getPhaseModeLabel(phase, showProgrammingStatus))}</span>
            </div>
            <p class="phase-copy">${escapeHtml(getPhaseCopy(phase))}</p>
            <div class="phase-focus-grid">
                <div class="focus-card focus-now ${localProgramStatus.tone}">
                    <span class="focus-label">JETZT</span>
                    <strong>${escapeHtml(localProgramStatus.label)}</strong>
                    <p>${escapeHtml(localProgramStatus.detail)}</p>
                </div>
                <div class="focus-card">
                    <span class="focus-label">ALS NÄCHSTES</span>
                    <strong>${showProgrammingStatus ? 'Ausführung startet nach der Planung' : 'Nächste Zustandsänderung'}</strong>
                    <p>${escapeHtml(nextStepHint)}</p>
                </div>
            </div>
            <div class="phase-meta">
                <span>${showProgrammingStatus ? submittedText : `Roboter aktiv: ${robots.filter(robot => !robot.destroyed).length}`}</span>
                <span>${localRobot ? `${escapeHtml(getPlayerName(localRobot.playerId))} steuert ${escapeHtml(getRobotLabel(localRobot))}` : 'Roboter wird gesucht'}</span>
                <span>${aliveRobots} aktiv · ${robots.length - aliveRobots} zerstört</span>
            </div>
            ${showProgrammingStatus ? `
                <div class="timer-panel ${timerTone}">
                    <div class="timer-row">
                        <span>Programmierung</span>
                        <strong>${programmingState.enabled ? formatCountdown(remainingMs) : 'Kein Timer'}</strong>
                    </div>
                    <div class="timer-track"><div class="timer-fill ${timerTone}" style="width:${timerPercent}%"></div></div>
                </div>
            ` : ''}
            ${renderRegisterTrack()}
        </div>`;

        html += `<div class="game-section pilot-card ${localRobot ? '' : 'empty'}">
            <div class="section-kicker">DEIN ROBOTER</div>
            ${localRobot ? `
                <div class="pilot-header">
                    <span class="pilot-chip" style="--pilot-accent:${getRobotAccent(localRobot)}">DU</span>
                    <div>
                        <strong>${escapeHtml(getPlayerName(localRobot.playerId))}</strong>
                        <div class="pilot-subtitle">${escapeHtml(getRobotLabel(localRobot))}</div>
                    </div>
                </div>
                <div class="pilot-metrics">
                    <span>📍 ${localRobot.x + 1}/${localRobot.y + 1}</span>
                    <span>❤️ ${localRobot.lives}</span>
                    <span>💥 ${localRobot.damage}</span>
                    <span>🏁 CP ${Math.max(0, localRobot.nextCheckpoint - 1)}</span>
                </div>
                <div class="pilot-callout ${localProgramStatus.tone}">${escapeHtml(localProgramStatus.detail)}</div>
            ` : '<p>Dein Roboter ist noch nicht im Spiel sichtbar.</p>'}
        </div>`;

        html += '<div class="game-section"><div class="section-kicker">ROSTER</div><div class="robot-status-list">';
        for (const r of robots) {
            const color = getRobotAccent(r);
            const isLocalRobot = currentUser?.userId === r.playerId;
            html += `<div class="robot-status ${isLocalRobot ? 'you' : ''}" style="--robot-accent:${color}">
                <div class="robot-status-main">
                    <span class="robot-swatch"></span>
                    <div>
                        <span class="robot-name">${escapeHtml(getPlayerName(r.playerId))}</span>
                        <div class="robot-status-sub">${escapeHtml(getRobotLabel(r))}${isLocalRobot ? ' • DU' : ''}</div>
                    </div>
                </div>
                <span class="robot-status-meta">❤️ ${r.lives} · 💥 ${r.damage} · 🏁 ${Math.max(0, r.nextCheckpoint - 1)}${r.destroyed ? ' · ☠️' : ''}</span>
            </div>`;
        }
        html += '</div></div>';

        html += '<div class="game-section"><div class="section-kicker">EINSATZPROTOKOLL</div><div class="event-log">';
        if (gameEventLog.length === 0) {
            html += '<div class="event-item empty">Noch keine Meldungen. Sobald es kracht, landet es hier.</div>';
        } else {
            html += gameEventLog.map(event => `
                <div class="event-item ${event.tone}">
                    <span class="event-pill ${event.tone}">${event.tone === 'exec' ? 'EXEC' : event.tone === 'programming' ? 'PLAN' : 'INFO'}</span>
                    <span>${escapeHtml(event.text)}</span>
                </div>`).join('');
        }
        html += '</div></div></div>';
        panel.innerHTML = html;
    }

    function renderCardHand() {
        const panel = document.getElementById('game-cards-panel');
        if (!panel) return;

        const needed = getRequiredCardCount();
        const localProgramStatus = getLocalProgramStatus();
        if (!dealtCards.length) {
            const previewCards = submittedProgramPreview.length
                ? `<div class="program-plan">${submittedProgramPreview.map((card, index) => `
                    <div class="program-slot-preview">
                        <span class="slot-index">${index + 1}</span>
                        <span>${CARD_ICONS[card.type] || '?'}</span>
                        <small>${escapeHtml(card.displayName)}</small>
                    </div>`).join('')}</div>`
                : '';

            panel.innerHTML = `
                <h3>🎴 Programmierung</h3>
                ${renderCardStatusStrip()}
                <div class="card-hand-empty">
                    <strong>${executionPlayback.isPlaying ? 'Ausführung läuft' : programmingState.isSubmitted ? 'Programm eingeloggt' : 'Warte auf die nächste Hand'}</strong>
                    <p>${executionPlayback.isPlaying
                        ? gameState?.phase === 'PROGRAMMING'
                            ? 'Der Replay läuft noch, aber deine nächste Programmierphase ist bereits live. Sobald Karten da sind, kannst du parallel planen.'
                            : 'Die Register werden gerade Schritt für Schritt abgespielt.'
                        : programmingState.isSubmitted
                            ? 'Deine Auswahl ist gesichert. Jetzt die Show genießen.'
                            : `${localProgramStatus.detail}`}</p>
                    ${previewCards}
                </div>`;
            return;
        }

        let html = `<h3>🎴 Deine Karten <small>(${selectedCards.length}/${needed} gewählt)</small></h3>`;
        html += renderCardStatusStrip();
        if (executionPlayback.isPlaying) {
            html += '<div class="programming-summary emphasis">🎬 Replay läuft noch – du kannst trotzdem schon die nächste Runde planen.</div>';
        }
        if (blockedSlots > 0) {
            html += `<div class="programming-summary">${blockedSlots} Register sind durch Schaden blockiert und bleiben aus der Vorrunde liegen.</div>`;
        }
        if (programmingState.submitPending) {
            html += '<div class="programming-summary">⏳ Programm wird bestätigt …</div>';
        }
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

        if (selectedCards.length) {
            const chosenCards = selectedCards
                .map(cardId => dealtCards.find(card => card.id === cardId))
                .filter(Boolean);
            html += `<div class="program-plan">${chosenCards.map((card, index) => `
                <div class="program-slot-preview">
                    <span class="slot-index">${index + 1}</span>
                    <span>${CARD_ICONS[card.type] || '?'}</span>
                    <small>${escapeHtml(card.displayName)}</small>
                </div>`).join('')}</div>`;
        }

        if (selectedCards.length === needed) {
            html += `<button class="btn btn-primary btn-submit-program" onclick="App.submitProgram()" ${programmingState.submitPending ? 'disabled' : ''}>${programmingState.submitPending ? '⏳ Wird eingereicht …' : '✅ Programm einreichen'}</button>`;
        }

        panel.innerHTML = html;
    }

    function toggleCard(cardId) {
        if (programmingState.submitPending || programmingState.isSubmitted) {
            return;
        }
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
        if (programmingState.submitPending || programmingState.isSubmitted) {
            return;
        }
        if (selectedCards.length !== 5 - blockedSlots) {
            toast('Wähle erst die richtige Anzahl Karten!', 'error');
            return;
        }
        programmingState.submitPending = true;
        programmingState.pendingCardIds = [...selectedCards];
        renderCardHand();
        renderGameInfo();
        RoboSocket.send('SUBMIT_PROGRAM', { cardIds: selectedCards });
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
            if (currentUser && p.userId === currentUser.userId) badges += '<span class="player-badge you">DU</span>';

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

    const api = {
        joinLobby,
        toast,
        showScreen,
        toggleCard,
        submitProgram
    };

    if (typeof globalThis !== 'undefined' && globalThis.__APP_TEST_HOOKS__) {
        api.__testHooks = {
            resetGamePresentation,
            queueExecutionStep,
            shouldAcceptGameState,
            shouldAcceptExecutionStep,
            shouldAcceptGameOver,
            setState(state = {}) {
                if (Object.prototype.hasOwnProperty.call(state, 'currentUser')) {
                    currentUser = state.currentUser;
                }
                if (Object.prototype.hasOwnProperty.call(state, 'currentScreen')) {
                    currentScreen = state.currentScreen;
                }
                if (Object.prototype.hasOwnProperty.call(state, 'currentLobby')) {
                    currentLobby = state.currentLobby;
                }
                if (Object.prototype.hasOwnProperty.call(state, 'gameState')) {
                    gameState = state.gameState;
                }
                if (Object.prototype.hasOwnProperty.call(state, 'executionPlayback')) {
                    executionPlayback = {
                        ...createExecutionPlaybackState(),
                        ...(state.executionPlayback || {}),
                        queue: [...(state.executionPlayback?.queue || [])]
                    };
                }
            },
            getExecutionPlaybackState() {
                return executionPlayback;
            }
        };
    }

    return api;
})();
