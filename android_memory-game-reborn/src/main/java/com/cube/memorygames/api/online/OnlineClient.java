package com.cube.memorygames.api.online;

import com.cube.memorygames.diagnostics.DiagnosticLog;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/**
 * Thin wrapper around Java-WebSocket with a main-thread listener callback
 * and a periodic app-level ping. Reconnection policy lives in
 * {@link OnlineSession}; this class only owns a single connection attempt.
 */
public class OnlineClient {

    public interface Listener {
        void onConnected();

        void onDisconnected(int code, String reason);

        void onMessage(String json);
    }

    private static final long PING_INTERVAL_MS = 10000L;
    private static final long CONNECT_TIMEOUT_MS = 10000L;

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closedByUser = new AtomicBoolean(false);
    private volatile WebSocketClient ws;
    private volatile boolean connected;
    /**
     * Half-dead TCP (phone Wi-Fi handover is the usual suspect) keeps the
     * receive path alive while silently eating everything we send — frames
     * vanish without any exception, so only the server's pong (or its
     * absence) can tell the connection is gone. A missed pong reports a
     * disconnect and OnlineSession's reconnect picks the socket back up.
     */
    private volatile boolean awaitingPong;
    private final Runnable pingRunnable = new Runnable() {
        @Override
        public void run() {
            WebSocketClient client = ws;
            if (client != null && connected && !closedByUser.get()) {
                if (OnlineClient.this.awaitingPong) {
                    DiagnosticLog.event("ws_ping_timeout", "reason", "ping_timeout");
                    connected = false;
                    OnlineClient.this.main.removeCallbacks(pingRunnable);
                    OnlineClient.this.main.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onDisconnected(-1, "ping timeout");
                        }
                    });
                    return;
                }
                OnlineClient.this.awaitingPong = true;
                try { client.send("{\"v\":1,\"t\":\"ping\",\"d\":{\"ts\":" + System.currentTimeMillis() + "}}"); }
                catch (RuntimeException e) { DiagnosticLog.event("ws_send_failed", "reason", e.getClass().getName());throw e; }
                main.postDelayed(this, PING_INTERVAL_MS);
            }
        }
    };

    public OnlineClient(Listener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected && ws != null && !closedByUser.get();
    }

    public void connect(final String endpoint) {
        DiagnosticLog.event("ws_connect");
        closedByUser.set(false);
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            DiagnosticLog.event("ws_error", "reason", e.getClass().getName());
            main.post(new Runnable() {
                @Override
                public void run() {
                    listener.onDisconnected(-1, "bad endpoint");
                }
            });
            return;
        }
        WebSocketClient client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                connected = true;
                DiagnosticLog.event("ws_connected");
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onConnected();
                    }
                });
                main.postDelayed(pingRunnable, PING_INTERVAL_MS);
            }

            @Override
            public void onMessage(String message) {
                // Ping/pong chatter is noise; everything else is worth a line
                // when diagnosing dropped frames on flaky links.
                if (!message.contains("\"t\":\"pong\"")) {
                    // Authentication frames contain bearer credentials; log only their type.
                    android.util.Log.d("OnlinePK", "ws frame type: " + OnlineMessages.typeOf(message));
                }
                if (message.contains("\"t\":\"pong\"")) {
                    awaitingPong = false;
                }
                final String json = message;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onMessage(json);
                    }
                });
            }

            @Override
            public void onClose(final int code, final String reason, boolean remote) {
                DiagnosticLog.event("ws_close", "code", code, "remote", remote);
                connected = false;
                main.removeCallbacks(pingRunnable);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onDisconnected(code, reason);
                    }
                });
            }

            @Override
            public void onError(final Exception ex) {
                DiagnosticLog.event("ws_error", "reason", ex.getClass().getName());
                // onClose always follows onError on a failed connection.
            }
        };
        if ("wss".equals(uri.getScheme())) {
            try {
                SSLSocketFactory factory = SSLContext.getDefault().getSocketFactory();
                client.setSocketFactory(factory);
            } catch (Exception ignored) {
                DiagnosticLog.event("ws_error", "reason", ignored.getClass().getName());
                // minSdk 21: TLS 1.2 is enabled by default; fall back to defaults.
            }
        }
        this.ws = client;
        client.connect();
    }

    public boolean send(String json) {
        WebSocketClient client = ws;
        if (client != null && connected && !closedByUser.get()) {
            try {
                client.send(json);
                return true;
            } catch (RuntimeException e) {
                DiagnosticLog.event("ws_send_failed", "reason", e.getClass().getName());
                return false;
            }
        }
        DiagnosticLog.event("ws_send_failed", "reason", "not_connected");
        return false;
    }

    public void close() {
        DiagnosticLog.event("ws_close", "reason", "framework", "remote", false);
        closedByUser.set(true);
        connected = false;
        main.removeCallbacks(pingRunnable);
        WebSocketClient client = ws;
        if (client != null) {
            try {
                client.close();
            } catch (RuntimeException ignored) {
            }
        }
        ws = null;
    }
}
