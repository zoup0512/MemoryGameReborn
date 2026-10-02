package com.memorygames.pk.match;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/** A delivery target for one match seat: a live socket or a bot (no-op). */
public interface PlayerEndpoint {

    void send(String json);

    boolean isOpen();

    class WebSocket implements PlayerEndpoint {

        private final WebSocketSession session;
        private final Object lock = new Object();

        public WebSocket(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void send(String json) {
            synchronized (this.lock) {
                try {
                    if (this.session.isOpen()) {
                        this.session.sendMessage(new TextMessage(json));
                    }
                } catch (IOException | IllegalStateException ignored) {
                    // Closing sockets are handled via the disconnect path.
                }
            }
        }

        @Override
        public boolean isOpen() {
            return this.session != null && this.session.isOpen();
        }
    }

    class Bot implements PlayerEndpoint {

        @Override
        public void send(String json) {
            // Bots consume nothing; their behaviour is driven by the room.
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }
}
