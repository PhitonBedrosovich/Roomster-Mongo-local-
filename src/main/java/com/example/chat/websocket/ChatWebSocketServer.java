package com.example.chat.websocket;

import com.example.chat.model.Message;
import com.example.chat.model.User;
import com.example.chat.service.UserService;
import com.example.chat.config.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ChatWebSocketServer extends WebSocketServer {
    private final Map<String, Set<WebSocket>> rooms = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    public ChatWebSocketServer() {
        super(new InetSocketAddress(8082));
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("WebSocket connection opened: " + conn.getRemoteSocketAddress());
        
        String token = extractToken(handshake);
        if (token == null) {
            System.out.println("No token provided in WebSocket connection");
            conn.close(1008, "Authentication required");
            return;
        }
        
        try {
            // Проверяем, не истек ли токен
            if (jwtUtil.isTokenExpired(token)) {
                System.out.println("JWT token expired for connection: " + conn.getRemoteSocketAddress());
                conn.close(1008, "Token expired");
                return;
            }
            
            String username = jwtUtil.getUsernameFromToken(token);
            
            // Проверяем, существует ли пользователь в базе данных
            User user = userService.findByUsername(username);
            if (user == null) {
                System.out.println("User not found in database: " + username);
                conn.close(1008, "User not found");
                return;
            }
            
            conn.setAttachment(new ClientData(username, null));
            System.out.println("User authenticated successfully: " + username);
            System.out.println("Token expires at: " + jwtUtil.getExpirationDateFromToken(token));
            
            // Отправляем подтверждение успешной аутентификации
            try {
                conn.send(objectMapper.writeValueAsString(Map.of(
                    "type", "auth_success",
                    "username", username,
                    "expiresAt", jwtUtil.getExpirationDateFromToken(token).toString()
                )));
            } catch (Exception e) {
                System.out.println("Error sending auth success message: " + e.getMessage());
            }
            
        } catch (ExpiredJwtException e) {
            System.out.println("JWT token expired for connection: " + conn.getRemoteSocketAddress());
            conn.close(1008, "Token expired");
        } catch (MalformedJwtException e) {
            System.out.println("Malformed JWT token: " + e.getMessage());
            conn.close(1008, "Invalid token format");
        } catch (SignatureException e) {
            System.out.println("Invalid JWT signature: " + e.getMessage());
            conn.close(1008, "Invalid token signature");
        } catch (Exception e) {
            System.out.println("Authentication failed: " + e.getMessage());
            conn.close(1008, "Authentication failed");
        }
    }

    private String extractToken(ClientHandshake handshake) {
        // Сначала пытаемся получить токен из заголовка
        String token = handshake.getFieldValue("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        
        // Если нет в заголовке, пытаемся из URL параметра (для обратной совместимости)
        String resourceDescriptor = handshake.getResourceDescriptor();
        if (resourceDescriptor.contains("?token=")) {
            return resourceDescriptor.split("\\?token=")[1];
        }
        
        return null;
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("WebSocket connection closed: " + reason);
        ClientData clientData = conn.getAttachment();
        if (clientData != null && clientData.room != null) {
            String room = clientData.room;
            Set<WebSocket> roomSet = rooms.get(room);
            if (roomSet != null) {
                roomSet.remove(conn);
                if (roomSet.isEmpty()) rooms.remove(room);
                broadcastRoomUsers(room);
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Message received: " + message);
        try {
            Map<String, Object> data = objectMapper.readValue(
                message, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
            String type = (String) data.get("type");
            String room = (String) data.get("room");
            ClientData clientData = conn.getAttachment();
            String username = clientData.username;

            if ("join".equals(type)) {
                System.out.println("User " + username + " joining room: " + room);
                rooms.computeIfAbsent(room, k -> new HashSet<>()).add(conn);
                clientData.room = room;

                // Получаем время регистрации пользователя
                User user = userService.findByUsername(username);
                LocalDateTime userRegisteredAt = user != null ? user.getRegisteredAt() : LocalDateTime.now();

                // Отправлять отфильтрованную историю сообщений на основе времени регистрации пользователя
                Query query = new Query(Criteria.where("room").is(room)
                        .and("createdAt").gte(userRegisteredAt)).limit(50);
                List<Message> history = mongoTemplate.find(query, Message.class);
                try {
                    conn.send(objectMapper.writeValueAsString(Map.of("type", "history", "messages", history)));
                    System.out.println("Sent " + history.size() + " filtered history messages to " + username + " (registered at: " + userRegisteredAt + ")");
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    e.printStackTrace();
                }
                broadcastRoomUsers(room);
            } else if ("message".equals(type)) {
                System.out.println("Processing message from " + username + " in room " + room);
                Message msg = new Message();
                msg.setUsername(username);
                msg.setRoom(room);
                msg.setContent((String) data.get("message"));
                msg.setPrivate(data.containsKey("recipient"));
                msg.setRecipient((String) data.get("recipient"));
                mongoTemplate.save(msg);
                System.out.println("Message saved to database");

                if (msg.isPrivate()) {
                    getConnections().forEach(client -> {
                        String clientUsername = ((ClientData) client.getAttachment()).username;
                        if (clientUsername.equals(username) || clientUsername.equals(msg.getRecipient())) {
                            try {
                                client.send(objectMapper.writeValueAsString(Map.of("type", "message", "message", msg)));
                                System.out.println("Private message sent to " + clientUsername);
                            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } else {
                    rooms.get(room).forEach(client -> {
                        try {
                            client.send(objectMapper.writeValueAsString(Map.of("type", "message", "message", msg)));
                            System.out.println("Message broadcasted to room " + room);
                        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        } catch (Exception e) {
            System.out.println("Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port 8082");
    }

    private void broadcastRoomUsers(String room) {
        Set<WebSocket> roomSet = rooms.get(room);
        if (roomSet == null || roomSet.isEmpty()) return;
        Set<String> users = roomSet.stream()
                .map(ws -> ((ClientData) ws.getAttachment()).username)
                .collect(Collectors.toSet());
        roomSet.forEach(client -> {
            try {
                client.send(objectMapper.writeValueAsString(Map.of("type", "users", "users", users)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                e.printStackTrace();
            }
        });
    }

    private String getClientRoom(WebSocket conn) {
        ClientData data = conn.getAttachment();
        return data != null ? data.room : null;
    }

    private static class ClientData {
        String username;
        String room;

        ClientData(String username, String room) {
            this.username = username;
            this.room = room;
        }
    }
}