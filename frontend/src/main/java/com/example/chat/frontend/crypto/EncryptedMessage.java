package com.example.chat.frontend.crypto;

import java.util.Base64;

/**
 * Класс для представления зашифрованного сообщения.
 * Содержит nonce, ciphertext и authentication tag для AES-GCM.
 */
public class EncryptedMessage {
    private final byte[] nonce;
    private final byte[] ciphertext;
    private final byte[] authTag;
    
    public EncryptedMessage(byte[] nonce, byte[] ciphertext, byte[] authTag) {
        if (nonce == null || ciphertext == null || authTag == null) {
            throw new IllegalArgumentException("All fields must be non-null");
        }
        this.nonce = nonce.clone();
        this.ciphertext = ciphertext.clone();
        this.authTag = authTag.clone();
    }
    
    public byte[] getNonce() {
        return nonce.clone();
    }
    
    public byte[] getCiphertext() {
        return ciphertext.clone();
    }
    
    public byte[] getAuthTag() {
        return authTag.clone();
    }
    
    /**
     * Преобразует зашифрованное сообщение в строку для передачи через сеть.
     * Формат: Base64(nonce):Base64(ciphertext):Base64(authTag)
     */
    public String toTransportFormat() {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(nonce) + ":" +
               encoder.encodeToString(ciphertext) + ":" +
               encoder.encodeToString(authTag);
    }
    
    /**
     * Создает EncryptedMessage из строки транспортного формата.
     * Формат: Base64(nonce):Base64(ciphertext):Base64(authTag)
     */
    public static EncryptedMessage fromTransportFormat(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("Transport format string cannot be null or empty");
        }
        
        String[] parts = str.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid transport format. Expected format: Base64(nonce):Base64(ciphertext):Base64(authTag)");
        }
        
        Base64.Decoder decoder = Base64.getDecoder();
        try {
            byte[] nonce = decoder.decode(parts[0]);
            byte[] ciphertext = decoder.decode(parts[1]);
            byte[] authTag = decoder.decode(parts[2]);
            
            return new EncryptedMessage(nonce, ciphertext, authTag);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 encoding in transport format", e);
        }
    }
    
    /**
     * Проверяет, является ли строка валидным транспортным форматом.
     */
    public static boolean isValidTransportFormat(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        String[] parts = str.split(":");
        if (parts.length != 3) {
            return false;
        }
        
        Base64.Decoder decoder = Base64.getDecoder();
        try {
            decoder.decode(parts[0]);
            decoder.decode(parts[1]);
            decoder.decode(parts[2]);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
