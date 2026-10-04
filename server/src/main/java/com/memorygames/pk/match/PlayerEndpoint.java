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
        /**
         * Sends must never run on the room/scheduler threads: a half-dead
         * client socket blocks StandardWebSocketSession.sendMessage forever
         * and one frozen client would wedge the whole match engine (found in
         * the 2026-10-04 device test: two scheduler threads blocked on dead
         * sockets froze every active room). Each session owns a single
         * sender thread; blocked senders are abandoned on shutdown.
         */
        private final java.util.concurrent.ExecutorService sender =
                java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "pk-sender");
                    thread.setDaemon(true);
                    return thread;
                });

        public WebSocket(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void send(final String json) {
            try {
                this.sender.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (WebSocket.this.session.isOpen()) {
                                WebSocket.this.session.sendMessage(new TextMessage(json));
                            }
                        } catch (IOException | IllegalStateException e) {
                            // Force the disconnect path: a session that cannot
                            // be written must be closed so the room forfeits it.
                            try {
                                WebSocket.this.session.close();
                            } catch (IOException | RuntimeException ignored) {
                            }
                        }
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // Session already shut down.
            }
        }

        @Override
        public boolean isOpen() {
            return this.session != null && this.session.isOpen();
        }

        public void shutdown() {
            this.sender.shutdownNow();
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
