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
                logger.warn("Сообщение об обмене ключами недействительно: отсутствуют обязательные поля");
                return;
            }

            // Получаем публичный ключ отправителя
            PublicKey senderPublicKey = fetchPublicKey(sender);
            if (senderPublicKey == null) {
                logger.warn("Не удалось получить открытый ключ отправителя: {}", sender);
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
                    logger.info("Ключ от номера получен и сохранен: {}", room);
                    onKeysUpdated.run(); // автосохранение keystore
                    Platform.runLater(() -> {
                        // UI обновится через updateUsers
                    });
                }
            } else if ("pairwise_key".equals(keyType)) {
                if (sender != null) {
                    KeyStore.savePairwiseKey(sender, key);
                    logger.info("Получен и сохранен для пользователя парный ключ: {}", sender);
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
                logger.warn("Сообщение об обмене ключами недействительно: отсутствуют обязательные поля");
                return;
            }

            // Проверяем, есть ли у нас ключ для этой комнаты
            if (!KeyStore.hasRoomKey(room)) {
                logger.warn("Запрошен ключ от комнаты {}, но у нас его нет", room);
                return;
            }

            // Получаем публичный ключ запрашивающего пользователя
            PublicKey requesterPublicKey = fetchPublicKey(requester);
            if (requesterPublicKey == null) {
                logger.warn("Не удалось получить открытый ключ для запрашивающего лица: {}", requester);
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
            logger.error("Ошибка при обработке сообщения key_request", e);
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
            logger.info("Запрос на ключ от комнаты отправлен: {}", roomName);
        } catch (Exception e) {
            logger.error("Ошибка при отправке запроса ключа", e);
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
            logger.info("Сообщение об обмене ключами отправлено в {} для комнаты {}", recipient, room);
        } catch (Exception e) {
            logger.error("Ошибка при отправке сообщения об обмене ключами", e);
        }
    }

    /**
     * Распределяет ключ комнаты всем пользователям при создании комнаты.
     */
    public void distributeRoomKey(String roomName, List<String> users) {
        if (!KeyStore.hasRoomKey(roomName)) {
            logger.warn("Не удалось распространить ключ комнаты: ключ для комнаты {} не найден", roomName);
            return;
        }

        javax.crypto.SecretKey roomKey = KeyStore.getRoomKey(roomName);

        for (String username : users) {
            if (username.equals(currentUsername)) {
                continue;
            }

            RoomService.getPublicKeyAsync(username, token).thenAccept(publicKeyData -> {
                if (publicKeyData == null) {
                    logger.warn("Не удалось получить открытый ключ для пользователя: {}", username);
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
                    logger.error("Ошибка при выдаче пользователю ключа от комнаты: " + username, e);
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
                logger.warn("Не удалось получить открытый ключ для пользователя: {}", otherUsername);
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

                byte[] keyBytes = CryptoService.deriveKeyFromSharedSecret(sharedSecret);
                javax.crypto.SecretKey pairwiseKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");

                KeyStore.savePairwiseKey(otherUsername, pairwiseKey);
                logger.info("Парный ключ устанавливается совместно с пользователем: {}", otherUsername);
                onKeysUpdated.run(); // автосохранение keystore

                if (onReady != null) onReady.run();

            } catch (Exception e) {
                logger.error("Ошибка при установлении парного ключа с пользователем: " + otherUsername, e);
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

            // TOFU: вычисляем fingerprint и сравниваем с сохранённым
            String fingerprint = computeFingerprint(publicKeyBase64);
            String known = PersistentKeyStore.getKnownFingerprint(username);

            if (known == null) {
                // Первый контакт — сохраняем fingerprint
                PersistentKeyStore.saveKnownFingerprint(username, fingerprint);
                logger.info("TOFU: сохранён fingerprint для {}: {}", username, fingerprint);
            } else if (!known.equals(fingerprint)) {
                // Ключ изменился — предупреждаем
                logger.warn("TOFU: ключ пользователя {} изменился! Старый: {}, новый: {}", username, known, fingerprint);
                throw new SecurityException("Ключ пользователя " + username +
                        " изменился. Возможна MITM-атака. Fingerprint: " + fingerprint);
            }

            return CryptoService.publicKeyFromBase64(publicKeyBase64, algorithm);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Ошибка при получении открытого ключа для пользователя: " + username, e);
            return null;
        }
    }

    /**
     * Вычисляет SHA-256 fingerprint публичного ключа.
     */
    private String computeFingerprint(String publicKeyBase64) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(java.util.Base64.getDecoder().decode(publicKeyBase64));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02X", hash[i]));
                if (i < 7) sb.append(":");
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вычислить fingerprint", e);
        }
    }
}