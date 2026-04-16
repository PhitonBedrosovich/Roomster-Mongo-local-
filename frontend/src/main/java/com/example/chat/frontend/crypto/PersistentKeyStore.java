package com.example.chat.frontend.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

/**
 * Персистентное хранилище ключей.
 *
 * Все ключи сохраняются в ~/.roomster/<username>/keystore.json.
 * Файл зашифрован через PBKDF2 (пароль → мастер-ключ) + AES-256-GCM.
 *
 * Структура JSON внутри зашифрованного файла:
 * {
 *   "identityPrivate": "<base64>",
 *   "identityPublic":  "<base64>",
 *   "roomKeys":        { "<room>": "<base64>", ... },
 *   "pairwiseKeys":    { "<username>": "<base64>", ... }
 * }
 *
 * Внешняя обёртка (незашифрованная часть файла):
 * {
 *   "salt":       "<base64>",   // соль для PBKDF2
 *   "nonce":      "<base64>",   // nonce для AES-GCM
 *   "ciphertext": "<base64>"    // зашифрованный JSON выше
 * }
 */
public class PersistentKeyStore {
    private static final Logger logger = LoggerFactory.getLogger(PersistentKeyStore.class);

    private static final String BASE_DIR      = System.getProperty("user.home") + "/.roomster";
    private static final String KEYSTORE_FILE = "keystore.json";

    // PBKDF2 параметры
    private static final int PBKDF2_ITERATIONS = 310_000; // OWASP 2023 рекомендация
    private static final int PBKDF2_KEY_BITS   = 256;
    private static final int SALT_BYTES        = 32;
    private static final int NONCE_BYTES       = 12;
    private static final int GCM_TAG_BITS      = 128;

    private static final ObjectMapper mapper = new ObjectMapper();

    // ─────────────────────────────────────────────────────────────────────────
    // Публичный API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Проверяет, существует ли файл keystore для данного пользователя.
     */
    public static boolean keystoreExists(String username) {
        return keystorePath(username).toFile().exists();
    }

    /**
     * Сохраняет ВСЕ ключи из KeyStore на диск, зашифровав их паролем.
     *
     * @param username имя пользователя (определяет путь к файлу)
     * @param password пароль для шифрования
     * @throws Exception если что-то пошло не так
     */
    public static void save(String username, char[] password) throws Exception {
        // 1. Собираем данные из KeyStore
        // ВАЖНО: pairwise ключи НЕ сохраняем — они всегда пересчитываются через ECDH.
        // Если сохранить pairwise ключ вычисленный со старым публичным ключом собеседника,
        // а собеседник сгенерировал новые ключи — расшифровка сломается навсегда.
        // ECDH пересчёт занимает миллисекунды и всегда даёт правильный результат.
        Map<String, Object> plain = new LinkedHashMap<>();
        plain.put("identityPrivate", KeyStore.exportPrivateKeyBase64());
        plain.put("identityPublic",  KeyStore.exportPublicKeyBase64());
        plain.put("roomKeys",        KeyStore.exportRoomKeysBase64());
        // pairwiseKeys намеренно не включаем

        byte[] plainJson = mapper.writeValueAsBytes(plain);

        // 2. Генерируем соль и nonce
        SecureRandom rng = new SecureRandom();
        byte[] salt  = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        rng.nextBytes(salt);
        rng.nextBytes(nonce);

        // 3. Получаем мастер-ключ из пароля
        SecretKey masterKey = deriveKey(password, salt);

        // 4. Шифруем
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] ciphertext = cipher.doFinal(plainJson);

        // 5. Сохраняем в файл
        Map<String, String> file = new LinkedHashMap<>();
        file.put("salt",       Base64.getEncoder().encodeToString(salt));
        file.put("nonce",      Base64.getEncoder().encodeToString(nonce));
        file.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext));

        Path path = keystorePath(username);
        Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), file);

        logger.info("Хранилище ключей сохранено для пользователя: {}", username);
    }

    /**
     * Загружает ВСЕ ключи с диска и помещает их в KeyStore.
     *
     * @param username имя пользователя
     * @param password пароль для расшифровки
     * @throws Exception если файл повреждён или пароль неверный
     */
    @SuppressWarnings("unchecked")
    public static void load(String username, char[] password) throws Exception {
        Path path = keystorePath(username);
        if (!path.toFile().exists()) {
            throw new FileNotFoundException("Хранилище ключей не найдено: " + path);
        }

        // 1. Читаем внешнюю обёртку
        Map<String, String> file = mapper.readValue(path.toFile(), Map.class);
        byte[] salt       = Base64.getDecoder().decode(file.get("salt"));
        byte[] nonce      = Base64.getDecoder().decode(file.get("nonce"));
        byte[] ciphertext = Base64.getDecoder().decode(file.get("ciphertext"));

        // 2. Получаем мастер-ключ
        SecretKey masterKey = deriveKey(password, salt);

        // 3. Расшифровываем — AEADBadTagException если пароль неверный
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] plainJson;
        try {
            plainJson = cipher.doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            throw new SecurityException("Неверный пароль или файл повреждён", e);
        }

        // 4. Парсим JSON и восстанавливаем ключи
        Map<String, Object> plain = mapper.readValue(plainJson, Map.class);

        // Identity keypair
        String privB64 = (String) plain.get("identityPrivate");
        String pubB64  = (String) plain.get("identityPublic");
        if (privB64 != null && pubB64 != null) {
            KeyStore.importIdentityKeys(privB64, pubB64, username);
        }

        // Room keys
        Map<String, String> roomKeys = (Map<String, String>) plain.getOrDefault("roomKeys", Collections.emptyMap());
        KeyStore.importRoomKeysBase64(roomKeys);

        // Pairwise ключи НЕ загружаем — они пересчитываются через ECDH при первом обращении.
        // Это гарантирует корректность даже если у собеседника обновились ключи.

        logger.info("Для пользователя загружено хранилище ключей: {} ({} ключи комнат, попарные ключи будут пересчитаны через ECDH)",
                username, roomKeys.size());
    }

    /**
     * Удаляет keystore файл (например при смене пароля или сбросе).
     */
    public static void delete(String username) {
        try {
            Files.deleteIfExists(keystorePath(username));
            logger.info("Хранилище ключей удалено для пользователя: {}", username);
        } catch (IOException e) {
            logger.warn("Не удалось удалить хранилище ключей для пользователя: {}", username, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательные методы
    // ─────────────────────────────────────────────────────────────────────────

    private static Path keystorePath(String username) {
        // Безопасное имя директории: только буквы/цифры/дефис
        String safeUsername = username.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return Paths.get(BASE_DIR, safeUsername, KEYSTORE_FILE);
    }

    /**
     * Выводит AES-256 ключ из пароля через PBKDF2WithHmacSHA256.
     */
    private static SecretKey deriveKey(char[] password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        javax.crypto.SecretKeyFactory factory =
                javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS);
        try {
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword(); // очищаем пароль из памяти
        }
    }
}