package com.example.chat.frontend.crypto;

import com.example.chat.frontend.service.RoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Обработчик обмена ключами через WebSocket.
 * Обрабатывает key_exchange и key_request сообщения.
 */
public class KeyExchangeHandler {
    private static final Logger logger = LoggerFactory.getLogger(KeyExchangeHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Consumer<String> sendWebSocketMessage;
    private final String currentUsername;
    private final String token;
    // Вызывается каждый раз когда получен/установлен новый ключ — для автосохранения keystore
    private final Runnable onKeysUpdated;

    public KeyExchangeHandler(Consumer<String> sendWebSocketMessage, String currentUsername,
                              String token, Runnable onKeysUpdated) {
        this.sendWebSocketMessage = sendWebSocketMessage;
        this.currentUsername = currentUsername;
        this.token = token;
        this.onKeysUpdated = onKeysUpdated != null ? onKeysUpdated : () -> {};
    }

    /**
     * Обрабатывает key_exchange сообщение (получение ключа комнаты или pairwise ключа).
     */
    public void handleKeyExchange(Map<String, Object> message) {
        try {
            String sender = (String) message.get("sender");
            String room = (String) message.get("room");
            String keyType = (String) message.get("keyType");
            String encryptedKeyBase64 = (String) message.get("encryptedKey");

            if (encryptedKeyBase64 == null || sender == null) {
                logger.warn("Invalid key_exchange message: missing required fields");
                return;
            }

            // Получаем публичный ключ отправителя
            PublicKey senderPublicKey = fetchPublicKey(sender);
            if (senderPublicKey == null) {
                logger.warn("Failed to fetch public key for sender: {}", sender);
                return;
            }

            // Вычисляем shared secret через ECDH
            byte[] sharedSecret = CryptoService.computeSharedSecret(
                    KeyStore.getMyPrivateKey(),
                    senderPublicKey
            );

            // Расшифровываем ключ
            byte[] encryptedKey = Base64.getDecoder().decode(encryptedKeyBase64);
            byte[] keyBytes = CryptoService.decryptWithSharedSecret(encryptedKey, sharedSecret);

            javax.crypto.SecretKey key = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");

            // Сохраняем ключ в зависимости от типа
            if ("room_key".equals(keyType)) {
                if (room != null) {
                    KeyStore.saveRoomKey(room, key);
                    logger.info("Room key received and saved for room: {}", room);
                    onKeysUpdated.run(); // автосохранение keystore
                    Platform.runLater(() -> {
                        // UI обновится через updateUsers
                    });
                }
            } else if ("pairwise_key".equals(keyType)) {
                if (sender != null) {
                    KeyStore.savePairwiseKey(sender, key);
                    logger.info("Pairwise key received and saved for user: {}", sender);
                    onKeysUpdated.run(); // автосохранение keystore
                }
            }

        } catch (Exception e) {
            logger.error("Error handling key_exchange message", e);
        }
    }

    /**
     * Обрабатывает key_request сообщение (запрос на получение ключа комнаты).
     */
    public void handleKeyRequest(Map<String, Object> message) {
        try {
            String requester = (String) message.get("requester");
            String room = (String) message.get("room");

            if (requester == null || room == null) {
                logger.warn("Invalid key_request message: missing required fields");
                return;
            }

            // Проверяем, есть ли у нас ключ для этой комнаты
            if (!KeyStore.hasRoomKey(room)) {
                logger.warn("Key requested for room {}, but we don't have the key", room);
                return;
            }

            // Получаем публичный ключ запрашивающего пользователя
            PublicKey requesterPublicKey = fetchPublicKey(requester);
            if (requesterPublicKey == null) {
                logger.warn("Failed to fetch public key for requester: {}", requester);
                return;
            }

            // Шифруем ключ комнаты для запрашивающего пользователя
            javax.crypto.SecretKey roomKey = KeyStore.getRoomKey(room);
            byte[] sharedSecret = CryptoService.computeSharedSecret(
                    KeyStore.getMyPrivateKey(),
                    requesterPublicKey
            );

            byte[] encryptedKey = CryptoService.encryptWithSharedSecret(roomKey.getEncoded(), sharedSecret);
            String encryptedKeyBase64 = Base64.getEncoder().encodeToString(encryptedKey);

            // Отправляем зашифрованный ключ
            sendKeyExchangeMessage(requester, room, encryptedKeyBase64, "room_key");

        } catch (Exception e) {
            logger.error("Error handling key_request message", e);
        }
    }

    /**
     * Отправляет запрос на получение ключа комнаты.
     */
    public void requestRoomKey(String roomName) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", "key_request",
                    "room", roomName,
                    "requester", currentUsername
            );

            String jsonMessage = objectMapper.writeValueAsString(msg);
            sendWebSocketMessage.accept(jsonMessage);
            logger.info("Key request sent for room: {}", roomName);
        } catch (Exception e) {
            logger.error("Error sending key request", e);
        }
    }

    /**
     * Отправляет зашифрованный ключ другому пользователю.
     */
    public void sendKeyExchangeMessage(String recipient, String room, String encryptedKeyBase64, String keyType) {
        try {
            Map<String, Object> msg = new java.util.HashMap<>();
            msg.put("type", "key_exchange");
            msg.put("room", room);
            msg.put("recipient", recipient);
            msg.put("keyType", keyType);
            msg.put("encryptedKey", encryptedKeyBase64);
            msg.put("sender", currentUsername);

            String jsonMessage = objectMapper.writeValueAsString(msg);
            sendWebSocketMessage.accept(jsonMessage);
            logger.info("Key exchange message sent to {} for room {}", recipient, room);
        } catch (Exception e) {
            logger.error("Error sending key exchange message", e);
        }
    }

    /**
     * Распределяет ключ комнаты всем пользователям при создании комнаты.
     */
    public void distributeRoomKey(String roomName, List<String> users) {
        if (!KeyStore.hasRoomKey(roomName)) {
            logger.warn("Cannot distribute room key: key not found for room {}", roomName);
            return;
        }

        javax.crypto.SecretKey roomKey = KeyStore.getRoomKey(roomName);

        for (String username : users) {
            if (username.equals(currentUsername)) {
                continue;
            }

            RoomService.getPublicKeyAsync(username, token).thenAccept(publicKeyData -> {
                if (publicKeyData == null) {
                    logger.warn("Failed to get public key for user: {}", username);
                    return;
                }

                try {
                    String publicKeyBase64 = publicKeyData.get("publicKey");
                    String algorithm = publicKeyData.getOrDefault("algorithm", "EC").toString();

                    PublicKey publicKey = CryptoService.publicKeyFromBase64(publicKeyBase64, algorithm);

                    byte[] sharedSecret = CryptoService.computeSharedSecret(
                            KeyStore.getMyPrivateKey(),
                            publicKey
                    );

                    byte[] encryptedKey = CryptoService.encryptWithSharedSecret(roomKey.getEncoded(), sharedSecret);
                    String encryptedKeyBase64 = Base64.getEncoder().encodeToString(encryptedKey);

                    sendKeyExchangeMessage(username, roomName, encryptedKeyBase64, "room_key");

                } catch (Exception e) {
                    logger.error("Error distributing room key to user: " + username, e);
                }
            });
        }
    }

    /**
     * Устанавливает pairwise ключ для приватного диалога.
     * Синхронная версия (оставлена для обратной совместимости).
     */
    public void establishPairwiseKey(String otherUsername) {
        establishPairwiseKeyAsync(otherUsername, null);
    }

    /**
     * Устанавливает pairwise ключ асинхронно.
     * После того как ключ готов — вызывает onReady (если не null).
     *
     * ECDH симметричен: sharedSecret(A_priv, B_pub) == sharedSecret(B_priv, A_pub).
     * Поэтому получателю не нужно ждать key_exchange — достаточно вычислить самостоятельно.
     */
    public void establishPairwiseKeyAsync(String otherUsername, Runnable onReady) {
        if (KeyStore.hasPairwiseKey(otherUsername)) {
            if (onReady != null) onReady.run();
            return;
        }

        RoomService.getPublicKeyAsync(otherUsername, token).thenAccept(publicKeyData -> {
            if (publicKeyData == null) {
                logger.warn("Failed to get public key for user: {}", otherUsername);
                return;
            }

            try {
                String publicKeyBase64 = publicKeyData.get("publicKey");
                String algorithm = publicKeyData.getOrDefault("algorithm", "EC").toString();

                PublicKey publicKey = CryptoService.publicKeyFromBase64(publicKeyBase64, algorithm);

                byte[] sharedSecret = CryptoService.computeSharedSecret(
                        KeyStore.getMyPrivateKey(),
                        publicKey
                );

                byte[] keyBytes = new byte[32];
                System.arraycopy(sharedSecret, 0, keyBytes, 0, Math.min(32, sharedSecret.length));
                javax.crypto.SecretKey pairwiseKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");

                KeyStore.savePairwiseKey(otherUsername, pairwiseKey);
                logger.info("Pairwise key established with user: {}", otherUsername);
                onKeysUpdated.run(); // автосохранение keystore

                if (onReady != null) onReady.run();

            } catch (Exception e) {
                logger.error("Error establishing pairwise key with user: " + otherUsername, e);
            }
        });
    }

    /**
     * Получает публичный ключ пользователя с сервера (синхронно).
     */
    private PublicKey fetchPublicKey(String username) {
        try {
            java.util.concurrent.CompletableFuture<Map<String, String>> future =
                    RoomService.getPublicKeyAsync(username, token);

            Map<String, String> publicKeyData = future.get();

            if (publicKeyData == null) return null;

            String publicKeyBase64 = publicKeyData.get("publicKey");
            String algorithm = publicKeyData.getOrDefault("algorithm", "EC").toString();

            return CryptoService.publicKeyFromBase64(publicKeyBase64, algorithm);
        } catch (Exception e) {
            logger.error("Error fetching public key for user: " + username, e);
            return null;
        }
    }
}