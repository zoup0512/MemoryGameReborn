package com.memorygames.pk.match;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A delivery target for one match seat: a live socket or a bot (no-op). */
public interface PlayerEndpoint {

    void send(String json);

    boolean isOpen();

    class WebSocket implements PlayerEndpoint {

        /** Frames buffered per session before the connection is judged stuck. */
        private static final int SEND_QUEUE_CAPACITY = 256;
        /** Hard cap on one blocking send; enforced by the Spring decorator. */
        private static final int SEND_TIME_LIMIT_MS = 5000;
        private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

        private final WebSocketSession session;
        private final ConcurrentWebSocketSessionDecorator guarded;
        private final Runnable onOverflow;
        private final java.util.concurrent.atomic.AtomicBoolean closing =
                new java.util.concurrent.atomic.AtomicBoolean();
        /**
         * Sends must never run on the room/scheduler threads: a half-dead
         * client socket blocks StandardWebSocketSession.sendMessage forever
         * and one frozen client would wedge the whole match engine (found in
         * the 2026-10-04 device test). Each session owns a single sender
         * thread with a BOUNDED queue (P1-4): a client that stops reading
         * backs up at most {@link #SEND_QUEUE_CAPACITY} frames before the
         * connection is closed and the room forfeits it, instead of
         * accumulating unbounded memory and one parked thread per zombie.
         */
        private final ThreadPoolExecutor sender = new ThreadPoolExecutor(
                1, 1, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(SEND_QUEUE_CAPACITY),
                r -> {
                    Thread thread = new Thread(r, "pk-sender");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());

        public WebSocket(WebSocketSession session) {
            this(session, null);
        }

        public WebSocket(WebSocketSession session, Runnable onOverflow) {
            this.session = session;
            this.guarded = new ConcurrentWebSocketSessionDecorator(session,
                    SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
            this.onOverflow = onOverflow;
            this.sender.allowCoreThreadTimeOut(true);
        }

        @Override
        public void send(final String json) {
            if (this.closing.get()) {
                return;
            }
            try {
                this.sender.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (WebSocket.this.session.isOpen()) {
                                WebSocket.this.guarded.sendMessage(new TextMessage(json));
                            }
                        } catch (IOException | IllegalStateException e) {
                            // Force the disconnect path: a session that cannot
                            // be written (or blew the send-time limit) must be
                            // closed so the room forfeits it.
                            try {
                                WebSocket.this.session.close();
                            } catch (IOException | RuntimeException ignored) {
                            }
                        }
                    }
                });
            } catch (RejectedExecutionException full) {
                // Queue full or executor stopped: a stuck consumer, not a
                // burst we can absorb — drop the connection (P1-4).
                if (this.onOverflow != null) {
                    try {
                        this.onOverflow.run();
                    } catch (RuntimeException ignored) {
                    }
                }
                try {
                    this.session.close();
                } catch (IOException | RuntimeException ignored) {
                }
            }
        }

        @Override
        public boolean isOpen() {
            return this.session != null && this.session.isOpen();
        }

        /** Deliver the final notification and close on the same bounded sender. */
        public void sendAndClose(String json, CloseStatus status) {
            if (!this.closing.compareAndSet(false, true)) {
                return;
            }
            try {
                this.sender.execute(() -> {
                    try {
                        if (this.session.isOpen()) {
                            this.guarded.sendMessage(new TextMessage(json));
                        }
                    } catch (IOException | RuntimeException ignored) {
                    } finally {
                        try {
                            this.session.close(status);
                        } catch (IOException | RuntimeException ignored) {
                        }
                        this.sender.shutdown();
                    }
                });
            } catch (RejectedExecutionException full) {
                if (this.onOverflow != null) {
                    try {
                        this.onOverflow.run();
                    } catch (RuntimeException ignored) {
                    }
                }
                try {
                    this.session.close(status);
                } catch (IOException | RuntimeException ignored) {
                }
                this.sender.shutdownNow();
            }
        }

        public void shutdown() {
            this.closing.set(true);
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
