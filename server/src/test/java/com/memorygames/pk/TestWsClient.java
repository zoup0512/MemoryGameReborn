package com.memorygames.pk;

import com.memorygames.pk.protocol.ProtocolCodec;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Minimal test-side WebSocket client mirroring the Android OnlineClient. */
class TestWsClient {

    static class Frame {

        final String type;
        final Object data;

        Frame(String type, Object data) {
            this.type = type;
            this.data = data;
        }

        <T> T as(Class<T> type) {
            return ProtocolCodec.convert(data, type);
        }
    }

    final LinkedBlockingQueue<Frame> inbox = new LinkedBlockingQueue<>();
    private final WebSocketClient ws;

    TestWsClient(URI uri) {
        this.ws = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
            }

            @Override
            public void onMessage(String message) {
                ProtocolCodec.Envelope envelope = ProtocolCodec.parse(message);
                if (envelope != null) {
                    TestWsClient.this.inbox.add(new Frame(envelope.t, envelope.d));
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
            }

            @Override
            public void onError(Exception ex) {
            }
        };
    }

    void connect() throws Exception {
        this.ws.connectBlocking(10, TimeUnit.SECONDS);
    }

    void close() throws Exception {
        this.ws.closeBlocking();
    }

    void send(String type, Object payload) {
        this.ws.send(ProtocolCodec.encode(type, payload));
    }

    Frame await(String type, long timeoutMs) {
        List<Frame> skipped = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            while (System.currentTimeMillis() < deadline) {
                Frame frame = this.inbox.poll(200, TimeUnit.MILLISECONDS);
                if (frame == null) {
                    continue;
                }
                if (type.equals(frame.type)) {
                    this.inbox.addAll(skipped);
                    return frame;
                }
                skipped.add(frame);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.inbox.addAll(skipped);
        return null;
    }

    boolean drain(String type) {
        boolean found = false;
        Frame frame;
        while ((frame = this.inbox.poll()) != null) {
            if (type.equals(frame.type)) {
                found = true;
            }
        }
        return found;
    }
}
