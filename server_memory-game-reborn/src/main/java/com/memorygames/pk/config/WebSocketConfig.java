package com.memorygames.pk.config;

import com.memorygames.pk.ws.PkWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Mounts the PK gateway at /ws (matching the client's endpoint config). */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PkWebSocketHandler handler;

    public WebSocketConfig(PkWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this.handler, "/ws").setAllowedOriginPatterns("*");
    }
}
