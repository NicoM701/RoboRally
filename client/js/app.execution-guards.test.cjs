const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function createElementStub() {
    return {
        className: '',
        textContent: '',
        innerHTML: '',
        value: '',
        disabled: false,
        appendChild() {},
        remove() {},
        addEventListener() {},
        querySelectorAll() { return []; },
        classList: {
            add() {},
            remove() {}
        },
        getContext() {
            return {
                drawImage() {},
                fillRect() {},
                clearRect() {}
            };
        }
    };
}

function loadAppHooks() {
    const sandbox = {
        console,
        setTimeout,
        clearTimeout,
        setInterval,
        clearInterval,
        location: { protocol: 'http:', host: 'example.test' },
        prompt: () => null,
        confirm: () => false,
        document: {
            addEventListener() {},
            querySelectorAll() { return []; },
            getElementById() { return null; },
            createElement: createElementStub
        },
        RoboSocket: {
            send() { return true; },
            on() {},
            connect() {}
        },
        __APP_TEST_HOOKS__: true
    };

    sandbox.window = sandbox;
    sandbox.globalThis = sandbox;

    const appSource = fs.readFileSync(path.join(__dirname, 'app.js'), 'utf8');
    vm.runInNewContext(`${appSource}\nthis.__APP__ = App;`, sandbox, { filename: 'app.js' });

    return sandbox.__APP__.__testHooks;
}

test('late execution steps are ignored after reset removes the active game context', () => {
    const hooks = loadAppHooks();

    hooks.resetGamePresentation();
    hooks.setState({
        currentUser: null,
        currentLobby: null,
        currentScreen: 'login',
        gameState: null,
        executionPlayback: { queue: [], isPlaying: false }
    });

    hooks.queueExecutionStep({
        round: 1,
        step: 1,
        robots: [{ playerId: 1 }],
        results: []
    });

    const playback = hooks.getExecutionPlaybackState();
    assert.equal(playback.queue.length, 0);
    assert.equal(playback.isPlaying, false);
});

test('execution steps are ignored when the current game is not in the execution phase yet', () => {
    const hooks = loadAppHooks();

    hooks.setState({
        currentUser: { userId: 1, username: 'Nico' },
        currentLobby: { id: 'fresh-lobby' },
        currentScreen: 'game',
        gameState: {
            phase: 'PROGRAMMING',
            round: 1,
            robots: [{ playerId: 1 }, { playerId: 2 }]
        },
        executionPlayback: { queue: [], isPlaying: false }
    });

    assert.equal(hooks.shouldAcceptExecutionStep({
        round: 1,
        step: 1,
        robots: [{ playerId: 1 }, { playerId: 2 }],
        results: []
    }), false);
});

test('execution steps are accepted for the active execution phase with matching round and roster', () => {
    const hooks = loadAppHooks();

    hooks.setState({
        currentUser: { userId: 1, username: 'Nico' },
        currentLobby: { id: 'fresh-lobby' },
        currentScreen: 'game',
        gameState: {
            phase: 'EXECUTING',
            round: 2,
            robots: [{ playerId: 1 }, { playerId: 2 }]
        },
        executionPlayback: { queue: [], isPlaying: false }
    });

    assert.equal(hooks.shouldAcceptExecutionStep({
        round: 2,
        step: 1,
        robots: [{ playerId: 1 }, { playerId: 2 }],
        results: []
    }), true);
});

test('mismatched robot rosters are rejected as stale execution steps', () => {
    const hooks = loadAppHooks();

    hooks.setState({
        currentUser: { userId: 1, username: 'Nico' },
        currentLobby: { id: 'fresh-lobby' },
        currentScreen: 'game',
        gameState: {
            phase: 'EXECUTING',
            round: 2,
            robots: [{ playerId: 1 }, { playerId: 2 }]
        },
        executionPlayback: { queue: [], isPlaying: false }
    });

    assert.equal(hooks.shouldAcceptExecutionStep({
        round: 2,
        step: 1,
        robots: [{ playerId: 1 }, { playerId: 3 }],
        results: []
    }), false);
});

test('game over messages are ignored when there is no active game context or playback', () => {
    const hooks = loadAppHooks();

    hooks.setState({
        currentUser: null,
        currentLobby: null,
        currentScreen: 'menu',
        gameState: null,
        executionPlayback: { queue: [], isPlaying: false }
    });

    assert.equal(hooks.shouldAcceptGameOver(), false);
});

test('game over messages are ignored during unrelated non-terminal phases', () => {
    const hooks = loadAppHooks();

    hooks.setState({
        currentUser: { userId: 1, username: 'Nico' },
        currentLobby: { id: 'fresh-lobby' },
        currentScreen: 'game',
        gameState: {
            phase: 'PROGRAMMING',
            round: 2,
            robots: [{ playerId: 1 }, { playerId: 2 }]
        },
        executionPlayback: { queue: [], isPlaying: false }
    });

    assert.equal(hooks.shouldAcceptGameOver(), false);
});

test('game over messages are still accepted during cleanup for the current game', () => {
    const hooks = loadAppHooks();

    hooks.setState({
        currentUser: { userId: 1, username: 'Nico' },
        currentLobby: { id: 'fresh-lobby' },
        currentScreen: 'game',
        gameState: {
            phase: 'ROUND_CLEANUP',
            round: 2,
            robots: [{ playerId: 1 }, { playerId: 2 }]
        },
        executionPlayback: { queue: [], isPlaying: false }
    });

    assert.equal(hooks.shouldAcceptGameOver(), true);
});
