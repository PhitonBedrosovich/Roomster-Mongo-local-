package com.example.chat.websocket;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class RedisMessageSubscriber implements MessageListener {
    @Autowired
    private ChatWebSocketServer chatWebSocketServer;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String messageJson = new String(message.getBody());
        // Ретранслируем сообщение всем локальным WebSocket-подключениям
        chatWebSocketServer.broadcastFromRedis(messageJson);
    }
} 