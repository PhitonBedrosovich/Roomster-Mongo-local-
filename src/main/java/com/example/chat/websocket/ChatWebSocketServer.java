package com.example.chat.websocket;

import com.example.chat.model.Message;
import com.example.chat.model.User;
import com.example.chat.service.UserService;
import com.example.chat.service.MessageService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ChatWebSocketServer extends WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketServer.class);
    private final Map<String, Set<WebSocket>> rooms = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;
    private static final String REDIS_CHANNEL = "chat-messages";

    public ChatWebSocketServer() {
        super(new InetSocketAddress(8082));
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("WebSocket connection opened: {}", conn.getRemoteSocketAddress());

        String token = extractToken(handshake);
        if (token == null) {
            logger.warn("No token provided in WebSocket connection from: {}", conn.getRemoteSocketAddress());
            conn.close(1008, "Authentication required");
            return;
        }

        try {
            // Проверяем, не истек ли токен
            if (jwtUtil.isTokenExpired(token)) {
                logger.warn("JWT token expired for connection: {}", conn.getRemoteSocketAddress());
                conn.close(1008, "Token expired");
                return;
            }

            String username = jwtUtil.getUsernameFromToken(token);

            // Проверяем, существует ли пользователь в базе данных
            User user = userService.findByUsername(username);
            if (user == null) {
                logger.warn("User not found in database: {}", username);
                conn.close(1008, "User not found");
                return;
            }

            conn.setAttachment(new ClientData(username, null));
            logger.info("User authenticated successfully: {}", username);
            logger.debug("Token expires at: {}", jwtUtil.getExpirationDateFromToken(token));

            // Отправляем подтверждение успешной аутентификации
            try {
                conn.send(objectMapper.writeValueAsString(Map.of(
                        "type", "auth_success",
                        "username", username,
                        "expiresAt", jwtUtil.getExpirationDateFromToken(token).toString()
                )));
            } catch (Exception e) {
                logger.error("Error sending auth success message: {}", e.getMessage(), e);
            }

        } catch (ExpiredJwtException e) {
            logger.warn("JWT token expired for connection: {}", conn.getRemoteSocketAddress());
            conn.close(1008, "Token expired");
        } catch (MalformedJwtException e) {
            logger.warn("Malformed JWT token: {}", e.getMessage());
            conn.close(1008, "Invalid token format");
        } catch (SignatureException e) {
            logger.warn("Invalid JWT signature: {}", e.getMessage());
            conn.close(1008, "Invalid token signature");
        } catch (Exception e) {
            logger.error("Authentication failed: {}", e.getMessage(), e);
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
        logger.info("WebSocket connection closed: {} (code: {}, remote: {})", reason, code, remote);
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
        logger.debug("Message received: {}", message);
        try {
            Map<String, Object> data = objectMapper.readValue(
                    message, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
            String type = (String) data.get("type");
            String room = (String) data.get("room");
            ClientData clientData = conn.getAttachment();
            String username = clientData.username;

            if ("join".equals(type)) {
                logger.info("User {} joining room: {}", username, room);
                rooms.computeIfAbsent(room, k -> new HashSet<>()).add(conn);
                clientData.room = room;

                // Получаем время регистрации пользователя
                User user = userService.findByUsername(username);
                LocalDateTime userRegisteredAt = user != null ? user.getRegisteredAt() : LocalDateTime.now();

                // Используем кэшированную историю сообщений
                List<Message> history = messageService.getRoomMessages(room, userRegisteredAt, username);
                try {
                    conn.send(objectMapper.writeValueAsString(Map.of("type", "history", "messages", history)));
                    logger.info("Sent {} filtered history messages to {} (registered at: {})",
                            history.size(), username, userRegisteredAt);
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    logger.error("Error sending history messages: {}", e.getMessage(), e);
                }
                broadcastRoomUsers(room);
            } else if ("message".equals(type)) {
                logger.info("Processing message from {} in room {}", username, room);
                Message msg = new Message();
                msg.setUsername(username);
                msg.setRoom(room);
                msg.setContent((String) data.get("message"));
                msg.setPrivate(data.containsKey("recipient"));
                msg.setRecipient((String) data.get("recipient"));
                mongoTemplate.save(msg);
                logger.debug("Message saved to database");

                // Сброс кэша истории комнаты для всех пользователей
                messageService.invalidateRoomCache(room);

                // Публикуем сообщение в Redis — broadcastFromRedis() сам разошлёт по WebSocket.
                // Прямой broadcast убран, чтобы не дублировать: иначе каждый клиент получал бы
                // сообщение дважды (напрямую + через Redis-подписчика).
                try {
                    String msgJson = objectMapper.writeValueAsString(msg);
                    redisTemplate.convertAndSend(REDIS_CHANNEL, msgJson);
                } catch (Exception e) {
                    logger.error("Error publishing message to Redis: {}", e.getMessage(), e);
                }
            } else if ("key_exchange".equals(type)) {
                // Обработка обмена ключами (E2E)
                logger.debug("Key exchange message from {} in room {}", username, room);
                String recipient = (String) data.get("recipient");

                // Добавляем отправителя в сообщение
                data.put("sender", username);

                if (recipient != null && !recipient.isEmpty()) {
                    // Приватный обмен ключами: отправляем только получателю
                    getConnections().forEach(client -> {
                        String clientUsername = ((ClientData) client.getAttachment()).username;
                        if (clientUsername.equals(recipient)) {
                            try {
                                client.send(objectMapper.writeValueAsString(data));
                                logger.debug("Key exchange message sent to {}", clientUsername);
                            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                                logger.error("Error sending key exchange message: {}", e.getMessage(), e);
                            }
                        }
                    });
                } else {
                    // Broadcast в комнату (для групповых ключей)
                    Set<WebSocket> roomSet = rooms.get(room);
                    if (roomSet != null) {
                        roomSet.forEach(client -> {
                            try {
                                client.send(objectMapper.writeValueAsString(data));
                                logger.debug("Key exchange message broadcasted to room {}", room);
                            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                                logger.error("Error broadcasting key exchange message: {}", e.getMessage(), e);
                            }
                        });
                    }
                }
            } else if ("key_request".equals(type)) {
                // Обработка запроса ключа
                logger.debug("Key request from {} for room {}", username, room);
                String requester = username;

                // Добавляем запрашивающего в сообщение
                data.put("requester", requester);

                // Отправляем запрос всем в комнате (кто-то должен ответить)
                Set<WebSocket> roomSet = rooms.get(room);
                if (roomSet != null) {
                    roomSet.forEach(client -> {
                        String clientUsername = ((ClientData) client.getAttachment()).username;
                        // Не отправляем запрос самому запрашивающему
                        if (!clientUsername.equals(requester)) {
                            try {
                                client.send(objectMapper.writeValueAsString(data));
                                logger.debug("Key request forwarded to {}", clientUsername);
                            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                                logger.error("Error forwarding key request: {}", e.getMessage(), e);
                            }
                        }
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("WebSocket error for connection {}: {}",
                conn != null ? conn.getRemoteSocketAddress() : "unknown", ex.getMessage(), ex);
    }

    @Override
    public void onStart() {
        logger.info("WebSocket server started on port 8082");
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
                logger.error("Error broadcasting room users: {}", e.getMessage(), e);
            }
        });
    }

    private String getClientRoom(WebSocket conn) {
        ClientData data = conn.getAttachment();
        return data != null ? data.room : null;
    }

    // Метод для рассылки сообщений, пришедших из Redis, локальным WebSocket-подключениям
    public void broadcastFromRedis(String messageJson) {
        try {
            Message msg = objectMapper.readValue(messageJson, Message.class);
            String room = msg.getRoom();
            if (msg.isPrivate()) {
                getConnections().forEach(client -> {
                    String clientUsername = ((ClientData) client.getAttachment()).username;
                    if (clientUsername.equals(msg.getUsername()) || clientUsername.equals(msg.getRecipient())) {
                        try {
                            client.send(objectMapper.writeValueAsString(Map.of("type", "message", "message", msg)));
                        } catch (Exception e) {
                            logger.error("Error sending private message from Redis: {}", e.getMessage(), e);
                        }
                    }
                });
            } else {
                Set<WebSocket> roomSet = rooms.get(room);
                if (roomSet != null) {
                    roomSet.forEach(client -> {
                        try {
                            client.send(objectMapper.writeValueAsString(Map.of("type", "message", "message", msg)));
                        } catch (Exception e) {
                            logger.error("Error broadcasting message from Redis: {}", e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Error in broadcastFromRedis: {}", e.getMessage(), e);
        }
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