/**
 * RoboRally WebSocket Client
 * Manages connection, auto-reconnect, and message dispatching.
 */
const RoboSocket = (() => {
    let ws = null;
    let reconnectAttempts = 0;
    const MAX_RECONNECT = 10;
    const RECONNECT_DELAY = 2000;
    const listeners = new Map();

    // ─── Connection Management ──────────────────────────

    function connect() {
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const url = `${protocol}//${location.host}/ws`;

        updateConnectionBar('connecting', 'Verbindung wird hergestellt...');

        ws = new WebSocket(url);

        ws.onopen = () => {
            console.log('[WS] Connected');
            reconnectAttempts = 0;
            updateConnectionBar('connected', 'Verbunden');
            emit('connected');
        };

        ws.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                console.log('[WS] ←', message.type, message.data || '');
                emit(message.type, message.data || {});
            } catch (e) {
                console.error('[WS] Parse error:', e);
            }
        };

        ws.onclose = (event) => {
            console.log('[WS] Disconnected:', event.code, event.reason);
            updateConnectionBar('disconnected', 'Verbindung getrennt');
            emit('disconnected');
            scheduleReconnect();
        };

        ws.onerror = (error) => {
            console.error('[WS] Error:', error);
        };
    }

    function disconnect() {
        reconnectAttempts = MAX_RECONNECT; // prevent reconnect
        if (ws) {
            ws.close(1000, 'Client disconnect');
            ws = null;
        }
    }

    function scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT) {
            updateConnectionBar('disconnected', 'Verbindung fehlgeschlagen — Seite neu laden');
            return;
        }
        reconnectAttempts++;
        const delay = RECONNECT_DELAY * Math.min(reconnectAttempts, 5);
        updateConnectionBar('connecting', `Erneuter Verbindungsversuch (${reconnectAttempts})...`);
        setTimeout(connect, delay);
    }

    function isConnected() {
        return ws && ws.readyState === WebSocket.OPEN;
    }

    // ─── Messaging ──────────────────────────────────────

    function send(type, data = {}) {
        if (!isConnected()) {
            console.warn('[WS] Not connected, cannot send:', type);
            return false;
        }
        const message = { type, data };
        console.log('[WS] →', type, data);
        ws.send(JSON.stringify(message));
        return true;
    }

    // ─── Event Emitter ──────────────────────────────────

    function on(event, callback) {
        if (!listeners.has(event)) {
            listeners.set(event, []);
        }
        listeners.get(event).push(callback);
    }

    function off(event, callback) {
        if (!listeners.has(event)) return;
        const arr = listeners.get(event);
        const idx = arr.indexOf(callback);
        if (idx > -1) arr.splice(idx, 1);
    }

    function emit(event, data) {
        if (listeners.has(event)) {
            listeners.get(event).forEach(cb => {
                try { cb(data); }
                catch (e) { console.error(`[WS] Listener error for ${event}:`, e); }
            });
        }
    }

    // ─── Connection Bar UI ──────────────────────────────

    function updateConnectionBar(state, text) {
        const bar = document.getElementById('connection-bar');
        const label = document.getElementById('connection-status-text');
        if (bar) {
            bar.className = `connection-bar ${state}`;
        }
        if (label) {
            label.textContent = text;
        }
    }

    // ─── Public API ─────────────────────────────────────

    return {
        connect,
        disconnect,
        send,
        on,
        off,
        isConnected
    };
})();
