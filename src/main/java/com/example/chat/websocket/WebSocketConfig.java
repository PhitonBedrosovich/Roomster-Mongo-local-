package com.example.chat.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSocketConfig {
    @Bean
    public ChatWebSocketServer chatWebSocketServer() {
        ChatWebSocketServer server = new ChatWebSocketServer();
        server.start();
        return server;
    }
}