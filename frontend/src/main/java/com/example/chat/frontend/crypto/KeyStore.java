package com.example.chat.frontend.crypto;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Хранилище криптографических ключей.
 * Хранит ключи в памяти. Опционально может сохранять в файл (для персистентности).
 */
public class KeyStore {
    // Ключи комнат: Map<roomName, SecretKey>
    private static final Map<String, SecretKey> roomKeys = new ConcurrentHashMap<>();

    // Ключи приватных диалогов: Map<username, SecretKey>
    private static final Map<String, SecretKey> pairwiseKeys = new ConcurrentHashMap<>();

    // Identity ключи текущего пользователя
    private static PrivateKey myPrivateKey;
    private static PublicKey myPublicKey;
    private static String currentUsername;

    /**
     * Сохраняет ключ комнаты.
     */
    public static void saveRoomKey(String room, SecretKey key) {
        if (room == null || key == null) {
            throw new IllegalArgumentException("Название комнаты и ключ должны быть непустыми");
        }
        roomKeys.put(room, key);
    }

    /**
     * Получает ключ комнаты.
     */
    public static SecretKey getRoomKey(String room) {
        return roomKeys.get(room);
    }

    /**
     * Проверяет наличие ключа комнаты.
     */
    public static boolean hasRoomKey(String room) {
        return roomKeys.containsKey(room);
    }

    /**
     * Удаляет ключ комнаты.
     */
    public static void removeRoomKey(String room) {
        roomKeys.remove(room);
    }

    /**
     * Сохраняет ключ приватного диалога.
     */
    public static void savePairwiseKey(String username, SecretKey key) {
        if (username == null || key == null) {
            throw new IllegalArgumentException("Имя пользователя и ключ не должны быть пустыми");
        }
        pairwiseKeys.put(username, key);
    }

    /**
     * Получает ключ приватного диалога.
     */
    public static SecretKey getPairwiseKey(String username) {
        return pairwiseKeys.get(username);
    }

    /**
     * Проверяет наличие ключа приватного диалога.
     */
    public static boolean hasPairwiseKey(String username) {
        return pairwiseKeys.containsKey(username);
    }

    /**
     * Удаляет ключ приватного диалога.
     */
    public static void removePairwiseKey(String username) {
        pairwiseKeys.remove(username);
    }

    /**
     * Сохраняет identity ключевую пару текущего пользователя.
     */
    public static void saveIdentityKeyPair(KeyPair keyPair, String username) {
        if (keyPair == null || username == null) {
            throw new IllegalArgumentException("Пара ключей и имя пользователя должны быть заполнены");
        }
        myPrivateKey = keyPair.getPrivate();
        myPublicKey = keyPair.getPublic();
        currentUsername = username;
    }

    /**
     * Получает приватный ключ текущего пользователя.
     */
    public static PrivateKey getMyPrivateKey() {
        return myPrivateKey;
    }

    /**
     * Получает публичный ключ текущего пользователя.
     */
    public static PublicKey getMyPublicKey() {
        return myPublicKey;
    }

    /**
     * Проверяет, установлены ли identity ключи.
     */
    public static boolean hasIdentityKeys() {
        return myPrivateKey != null && myPublicKey != null;
    }

    /**
     * Получает имя текущего пользователя.
     */
    public static String getCurrentUsername() {
        return currentUsername;
    }

    /**
     * Очищает все ключи (используется при выходе из системы).
     */
    public static void clear() {
        roomKeys.clear();
        pairwiseKeys.clear();
        myPrivateKey = null;
        myPublicKey = null;
        currentUsername = null;
    }

    /**
     * Очищает ключи для конкретной комнаты.
     */
    public static void clearRoomKeys() {
        roomKeys.clear();
    }

    /**
     * Очищает ключи приватных диалогов.
     */
    public static void clearPairwiseKeys() {
        pairwiseKeys.clear();
    }

    /**
     * Получает количество сохраненных ключей комнат (для отладки).
     */
    public static int getRoomKeysCount() {
        return roomKeys.size();
    }

    /**
     * Получает количество сохраненных pairwise ключей (для отладки).
     */
    public static int getPairwiseKeysCount() {
        return pairwiseKeys.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Массовый экспорт/импорт для PersistentKeyStore
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Экспортирует все ключи комнат в Map<roomName, base64>.
     */
    public static Map<String, String> exportRoomKeysBase64() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        roomKeys.forEach((room, key) ->
                result.put(room, Base64.getEncoder().encodeToString(key.getEncoded())));
        return result;
    }

    /**
     * Импортирует ключи комнат из Map<roomName, base64>.
     */
    public static void importRoomKeysBase64(Map<String, String> map) {
        map.forEach((room, b64) -> {
            byte[] keyBytes = Base64.getDecoder().decode(b64);
            SecretKey key = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
            roomKeys.put(room, key);
        });
    }

    /**
     * Экспортирует все pairwise ключи в Map<username, base64>.
     */
    public static Map<String, String> exportPairwiseKeysBase64() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        pairwiseKeys.forEach((user, key) ->
                result.put(user, Base64.getEncoder().encodeToString(key.getEncoded())));
        return result;
    }

    /**
     * Импортирует pairwise ключи из Map<username, base64>.
     */
    public static void importPairwiseKeysBase64(Map<String, String> map) {
        map.forEach((user, b64) -> {
            byte[] keyBytes = Base64.getDecoder().decode(b64);
            SecretKey key = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
            pairwiseKeys.put(user, key);
        });
    }

    /**
     * Экспортирует публичный ключ в Base64 для передачи на сервер.
     */
    public static String exportPublicKeyBase64() {
        if (myPublicKey == null) {
            return null;
        }
        return CryptoService.publicKeyToBase64(myPublicKey);
    }

    /**
     * Импортирует identity ключи из Base64 строк (для восстановления из файла).
     * ВНИМАНИЕ: Приватный ключ должен быть зашифрован паролем перед сохранением!
     */
    public static void importIdentityKeys(String privateKeyBase64, String publicKeyBase64, String username) {
        if (privateKeyBase64 == null || publicKeyBase64 == null || username == null) {
            throw new IllegalArgumentException("Все параметры должны быть заполнены");
        }

        try {
            myPrivateKey = CryptoService.privateKeyFromBase64(privateKeyBase64, "EC");
            myPublicKey = CryptoService.publicKeyFromBase64(publicKeyBase64, "EC");
            currentUsername = username;
        } catch (Exception e) {
            throw new RuntimeException("Не удалось импортировать ключи идентификации", e);
        }
    }

    /**
     * Экспортирует приватный ключ в Base64 (для сохранения в файл).
     * ВНИМАНИЕ: Перед сохранением в файл ключ должен быть зашифрован паролем!
     */
    public static String exportPrivateKeyBase64() {
        if (myPrivateKey == null) {
            return null;
        }
        return CryptoService.privateKeyToBase64(myPrivateKey);
    }
}