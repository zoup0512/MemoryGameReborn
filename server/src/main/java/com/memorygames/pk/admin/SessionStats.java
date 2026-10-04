package com.memorygames.pk.admin;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** WS session counters maintained by the gateway, read by the dashboard. */
@Component
public class SessionStats {

    public final AtomicLong connections = new AtomicLong();
    public final AtomicLong authenticated = new AtomicLong();

    public void connected() {
        this.connections.incrementAndGet();
    }

    public void disconnected() {
        this.connections.decrementAndGet();
        // authenticated count is corrected lazily on close via the handler
    }

    public void authed() {
        this.authenticated.incrementAndGet();
    }

    public void deauthed() {
        this.authenticated.decrementAndGet();
    }
}
