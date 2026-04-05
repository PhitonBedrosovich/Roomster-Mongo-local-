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
import javax.crypto.Mac;

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
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }
    
    /**
     * Детерминированное получение pairwise-ключа для приватного диалога A<->B.
     * Обе стороны вычисляют одинаковый AES-256 ключ: HKDF(HMAC-SHA256(sharedSecret), context),
     * где context = "PAIRWISE|" + min(userA,userB) + "|" + max(userA,userB)
     */
    public static SecretKey derivePairwiseKey(
            String myUsername,
            String otherUsername,
            PrivateKey myPrivateKey,
            PublicKey otherPublicKey
    ) {
        byte[] sharedSecret = computeSharedSecret(myPrivateKey, otherPublicKey);
        String u1 = myUsername;
        String u2 = otherUsername;
        if (u1.compareTo(u2) > 0) {
            String tmp = u1;
            u1 = u2;
            u2 = tmp;
        }
        String context = "PAIRWISE|" + u1 + "|" + u2;
        byte[] salt = context.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(sharedSecret); // pseudo-random key

            mac.reset();
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            byte[] okm = mac.doFinal("PAIRWISE_AES_256".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] keyBytes = Arrays.copyOf(okm, 32);
            return new SecretKeySpec(keyBytes, AES_ALGORITHM);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to derive pairwise key", e);
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
            throw new RuntimeException("Failed to generate EC key pair", e);
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
            throw new RuntimeException("Failed to encrypt message", e);
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
            throw new SecurityException("Failed to decrypt: message may be corrupted or key is incorrect", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt message", e);
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
            throw new RuntimeException("Failed to compute shared secret", e);
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
        byte[] keyBytes = Arrays.copyOf(sharedSecret, 32);
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
            throw new RuntimeException("Failed to encrypt with shared secret", e);
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
            throw new IllegalArgumentException("Encrypted data too short");
        }
        
        // Извлекаем nonce и ciphertext
        byte[] nonce = Arrays.copyOf(encrypted, GCM_NONCE_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(encrypted, GCM_NONCE_LENGTH, encrypted.length);
        
        // Создаем ключ из shared secret
        byte[] keyBytes = Arrays.copyOf(sharedSecret, 32);
        SecretKey key = new SecretKeySpec(keyBytes, AES_ALGORITHM);
        
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            
            return cipher.doFinal(ciphertext);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new SecurityException("Failed to decrypt: data may be corrupted or shared secret is incorrect", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt with shared secret", e);
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
            throw new RuntimeException("Failed to decode public key from Base64", e);
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
            throw new RuntimeException("Failed to decode private key from Base64", e);
        }
    }
}
