import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';

class FakeClassList {
    constructor(initial = []) {
        this.classes = new Set(initial);
    }

    add(...names) {
        names.forEach(name => this.classes.add(name));
    }

    remove(...names) {
        names.forEach(name => this.classes.delete(name));
    }

    contains(name) {
        return this.classes.has(name);
    }
}

function createElement(id = null, initialClasses = []) {
    return {
        id,
        value: '',
        innerHTML: '',
        textContent: '',
        disabled: false,
        checked: false,
        className: initialClasses.join(' '),
        classList: new FakeClassList(initialClasses),
        children: [],
        listeners: new Map(),
        style: {},
        scrollTop: 0,
        scrollHeight: 0,
        width: 0,
        height: 0,
        addEventListener(type, callback) {
            if (!this.listeners.has(type)) {
                this.listeners.set(type, []);
            }
            this.listeners.get(type).push(callback);
        },
        dispatchEvent(type, event = {}) {
            for (const callback of this.listeners.get(type) || []) {
                callback(event);
            }
        },
        appendChild(child) {
            this.children.push(child);
            this.scrollHeight = this.children.length;
            return child;
        },
        remove() {
            this.removed = true;
        },
        querySelectorAll() {
            return [];
        },
        getContext() {
            return {
                fillStyle: '',
                strokeStyle: '',
                lineWidth: 1,
                drawImage() {},
                fillRect() {},
                clearRect() {},
                beginPath() {},
                moveTo() {},
                lineTo() {},
                stroke() {},
                fill() {},
                arc() {},
                save() {},
                restore() {},
                translate() {},
                rotate() {},
                scale() {},
                strokeRect() {},
                fillText() {},
                setLineDash() {}
            };
        }
    };
}

function createDocument() {
    const elements = new Map();
    const documentListeners = new Map();
    const screens = ['login', 'menu', 'lobby', 'game', 'end'].map(name => {
        const screen = createElement(`screen-${name}`, ['screen']);
        if (name === 'login') {
            screen.classList.add('active');
        }
        elements.set(screen.id, screen);
        return screen;
    });

    const document = {
        getElementById(id) {
            if (!elements.has(id)) {
                const initialClasses = [];
                if (id === 'login-form') {
                    initialClasses.push('active');
                }
                if (id === 'register-form' || id.startsWith('modal-')) {
                    initialClasses.push('hidden');
                }
                elements.set(id, createElement(id, initialClasses));
            }
            return elements.get(id);
        },
        querySelectorAll(selector) {
            if (selector === '.screen') {
                return screens;
            }
            return [];
        },
        createElement(tagName) {
            return createElement(tagName);
        },
        addEventListener(type, callback) {
            if (!documentListeners.has(type)) {
                documentListeners.set(type, []);
            }
            documentListeners.get(type).push(callback);
        },
        fire(type) {
            for (const callback of documentListeners.get(type) || []) {
                callback();
            }
        }
    };

    return { document, elements };
}

function createRoboSocket() {
    const listeners = new Map();
    const sent = [];

    return {
        sent,
        connectCalls: 0,
        connect() {
            this.connectCalls += 1;
        },
        send(type, data = {}) {
            sent.push({ type, data });
            return true;
        },
        on(event, callback) {
            if (!listeners.has(event)) {
                listeners.set(event, []);
            }
            listeners.get(event).push(callback);
        },
        off(event, callback) {
            const callbacks = listeners.get(event) || [];
            listeners.set(event, callbacks.filter(fn => fn !== callback));
        },
        emit(event, data = {}) {
            for (const callback of listeners.get(event) || []) {
                callback(data);
            }
        },
        isConnected() {
            return true;
        }
    };
}

function loadApp() {
    const appPath = path.resolve('client/js/app.js');
    const source = fs.readFileSync(appPath, 'utf8');
    const { document, elements } = createDocument();
    const RoboSocket = createRoboSocket();

    class FakeImage {
        constructor() {
            this.complete = false;
            this.naturalWidth = 0;
        }

        set src(value) {
            this._src = value;
        }
    }

    const context = vm.createContext({
        console,
        document,
        RoboSocket,
        location: { protocol: 'http:', host: 'localhost' },
        prompt: () => null,
        confirm: () => true,
        setTimeout: () => 0,
        clearTimeout: () => {},
        Image: FakeImage,
        Map,
        Set,
        JSON,
        Math
    });
    context.globalThis = context;

    vm.runInContext(`${source}\n;globalThis.__app = App;`, context, { filename: appPath });

    return {
        App: context.__app,
        RoboSocket,
        document,
        elements
    };
}

test('failed join error clears pending lobby so later lobby update is accepted', () => {
    const { App, RoboSocket, document, elements } = loadApp();

    document.fire('DOMContentLoaded');
    assert.equal(RoboSocket.connectCalls, 1);

    RoboSocket.emit('LOGIN_SUCCESS', { userId: 7, username: 'Host', isGuest: false });
    App.joinLobby('missing-lobby', false);
    RoboSocket.emit('ERROR', { message: 'Lobby nicht gefunden.' });

    RoboSocket.emit('LOBBY_UPDATE', {
        lobby: {
            id: 'created-lobby',
            name: 'Created Lobby',
            players: [{ userId: 7, username: 'Host', isHost: true }],
            gameSettings: {}
        }
    });

    assert.equal(elements.get('lobby-room-name').textContent, 'Created Lobby');
    assert.equal(elements.get('screen-lobby').classList.contains('active'), true);
});
