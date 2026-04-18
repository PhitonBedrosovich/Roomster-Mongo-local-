package com.example.chat.frontend.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Сервис для криптографических операций:
 * - Генерация ключей (AES, EC)
 * - Шифрование/расшифровка AES-GCM
 * - ECDH для обмена ключами
 */
public class CryptoService {
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256; // бит
    private static final int GCM_TAG_LENGTH = 128; // бит
    private static final int GCM_NONCE_LENGTH = 12; // байт (96 бит для GCM)
    private static final String EC_ALGORITHM = "EC";
    private static final String EC_CURVE = "secp256r1"; // NIST P-256
    
    /**
     * Генерирует случайный AES-256 ключ.
     */
    public static SecretKey generateAES256Key() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
            keyGenerator.init(AES_KEY_SIZE);
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Не удалось сгенерировать ключ AES", e);
        }
    }
    
    /**
     * Генерирует пару EC ключей для ECDH.
     */
    public static KeyPair generateECKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(EC_ALGORITHM);
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(EC_CURVE);
            keyPairGenerator.initialize(ecSpec);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Не удалось сгенерировать пару ключей EC", e);
        }
    }
    
    /**
     * Шифрует текст с использованием AES-GCM.
     * 
     * @param plaintext Текст для шифрования
     * @param key Секретный ключ AES
     * @return Зашифрованное сообщение с nonce и auth tag
     */
    public static EncryptedMessage encryptAESGCM(String plaintext, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            
            // Генерируем случайный nonce
            SecureRandom random = new SecureRandom();
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            random.nextBytes(nonce);
            
            // Инициализируем cipher для шифрования
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            
            // Шифруем
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            // В GCM auth tag добавляется автоматически в конец ciphertext
            // Разделяем ciphertext и auth tag
            int ciphertextLength = ciphertext.length - (GCM_TAG_LENGTH / 8);
            byte[] ciphertextOnly = Arrays.copyOf(ciphertext, ciphertextLength);
            byte[] authTag = Arrays.copyOfRange(ciphertext, ciphertextLength, ciphertext.length);
            
            return new EncryptedMessage(nonce, ciphertextOnly, authTag);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось зашифровать сообщение", e);
        }
    }
    
    /**
     * Расшифровывает сообщение, зашифрованное AES-GCM.
     * 
     * @param encrypted Зашифрованное сообщение
     * @param key Секретный ключ AES
     * @return Расшифрованный текст
     * @throws javax.crypto.AEADBadTagException если сообщение повреждено или ключ неверный
     */
    public static String decryptAESGCM(EncryptedMessage encrypted, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            
            // Восстанавливаем полный ciphertext (ciphertext + auth tag)
            byte[] fullCiphertext = new byte[encrypted.getCiphertext().length + encrypted.getAuthTag().length];
            System.arraycopy(encrypted.getCiphertext(), 0, fullCiphertext, 0, encrypted.getCiphertext().length);
            System.arraycopy(encrypted.getAuthTag(), 0, fullCiphertext, encrypted.getCiphertext().length, encrypted.getAuthTag().length);
            
            // Инициализируем cipher для расшифровки
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, encrypted.getNonce());
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            
            // Расшифровываем
            byte[] plaintextBytes = cipher.doFinal(fullCiphertext);
            return new String(plaintextBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new SecurityException("Не удалось расшифровать: сообщение может быть повреждено или ключ неверен", e);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось расшифровать сообщение", e);
        }
    }
    
    /**
     * Вычисляет общий секрет через ECDH (Elliptic Curve Diffie-Hellman).
     * 
     * @param privateKey Приватный ключ одной стороны
     * @param publicKey Публичный ключ другой стороны
     * @return Общий секрет (shared secret)
     */
    public static byte[] computeSharedSecret(PrivateKey privateKey, PublicKey publicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(publicKey, true);
            return keyAgreement.generateSecret();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вычислить общий ключ-токен", e);
        }
    }

    /**
     * Извлекает AES-256 ключ из ECDH shared secret через HKDF (RFC 5869).
     */
    public static byte[] deriveKeyFromSharedSecret(byte[] sharedSecret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
            byte[] prk = mac.doFinal(sharedSecret);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update("roomster-key".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            mac.update((byte) 1);
            return Arrays.copyOf(mac.doFinal(), 32);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось извлечь ключ из shared secret", e);
        }
    }

    /**
     * Шифрует данные с использованием shared secret от ECDH.
     * Использует AES-256-GCM с ключом, производным от shared secret через HKDF.
     * 
     * @param data Данные для шифрования
     * @param sharedSecret Общий секрет от ECDH
     * @return Зашифрованные данные
     */
    public static byte[] encryptWithSharedSecret(byte[] data, byte[] sharedSecret) {
        // Упрощенная версия: используем первые 32 байта shared secret как AES ключ
        // В production лучше использовать HKDF для извлечения ключа
        byte[] keyBytes = deriveKeyFromSharedSecret(sharedSecret);
        SecretKey key = new SecretKeySpec(keyBytes, AES_ALGORITHM);
        
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            SecureRandom random = new SecureRandom();
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            random.nextBytes(nonce);
            
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            
            byte[] ciphertext = cipher.doFinal(data);
            
            // Возвращаем nonce + ciphertext (ciphertext уже содержит auth tag)
            byte[] result = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Не удалось выполнить шифрование с использованием общего ключа-токена", e);
        }
    }
    
    /**
     * Расшифровывает данные, зашифрованные с shared secret.
     * 
     * @param encrypted Зашифрованные данные (nonce + ciphertext + auth tag)
     * @param sharedSecret Общий секрет от ECDH
     * @return Расшифрованные данные
     */
    public static byte[] decryptWithSharedSecret(byte[] encrypted, byte[] sharedSecret) {
        if (encrypted.length < GCM_NONCE_LENGTH + (GCM_TAG_LENGTH / 8)) {
            throw new IllegalArgumentException("Зашифрованные данные слишком короткие");
        }
        
        // Извлекаем nonce и ciphertext
        byte[] nonce = Arrays.copyOf(encrypted, GCM_NONCE_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(encrypted, GCM_NONCE_LENGTH, encrypted.length);
        
        // Создаем ключ из shared secret
        byte[] keyBytes = deriveKeyFromSharedSecret(sharedSecret);
        SecretKey key = new SecretKeySpec(keyBytes, AES_ALGORITHM);
        
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            
            return cipher.doFinal(ciphertext);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new SecurityException("Не удалось расшифровать: данные могут быть повреждены или указан неверный секретный ключ", e);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось расшифровать данные с использованием общего ключа", e);
        }
    }
    
    /**
     * Преобразует публичный ключ в Base64 строку для передачи.
     */
    public static String publicKeyToBase64(PublicKey publicKey) {
        return java.util.Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
    
    /**
     * Создает публичный ключ из Base64 строки.
     */
    public static PublicKey publicKeyFromBase64(String base64, String algorithm) {
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось расшифровать открытый ключ из Base64", e);
        }
    }
    
    /**
     * Преобразует приватный ключ в Base64 строку для хранения (зашифрованную).
     */
    public static String privateKeyToBase64(PrivateKey privateKey) {
        return java.util.Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }
    
    /**
     * Создает приватный ключ из Base64 строки.
     */
    public static PrivateKey privateKeyFromBase64(String base64, String algorithm) {
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
            java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось расшифровать закрытый ключ из Base64", e);
        }
    }
}
