package com.example.chat.controller;

import com.example.chat.model.User;
import com.example.chat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * REST контроллер для обмена публичными ключами пользователей.
 * Используется для E2E шифрования.
 */
@RestController
@RequestMapping("/api/keys")
public class KeyExchangeController {
    private static final Logger logger = LoggerFactory.getLogger(KeyExchangeController.class);
    
    @Autowired
    private UserService userService;
    
    /**
     * Получает публичный ключ пользователя.
     * GET /api/keys/users/{username}/public-key
     */
    @GetMapping("/users/{username}/public-key")
    public ResponseEntity<?> getPublicKey(@PathVariable String username) {
        logger.info("Getting public key for user: {}", username);
        
        User user = userService.findByUsername(username);
        if (user == null) {
            logger.warn("User not found: {}", username);
            return ResponseEntity.notFound().build();
        }
        
        if (user.getPublicKey() == null || user.getPublicKey().isEmpty()) {
            logger.warn("Public key not set for user: {}", username);
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> response = new HashMap<>();
        response.put("publicKey", user.getPublicKey());
        response.put("algorithm", user.getPublicKeyAlgorithm() != null ? user.getPublicKeyAlgorithm() : "EC");
        response.put("username", username);
        
        logger.debug("Returning public key for user: {}", username);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Устанавливает публичный ключ пользователя.
     * POST /api/keys/users/{username}/public-key
     * Body: {"publicKey": "...", "algorithm": "EC"}
     */
    @PostMapping("/users/{username}/public-key")
    public ResponseEntity<?> setPublicKey(
            @PathVariable String username,
            @RequestBody Map<String, String> body
    ) {
        logger.info("Setting public key for user: {}", username);
        
        String publicKey = body.get("publicKey");
        String algorithm = body.get("algorithm");
        
        if (publicKey == null || publicKey.isEmpty()) {
            logger.warn("Public key is empty for user: {}", username);
            return ResponseEntity.badRequest().body("Public key is required");
        }
        
        User user = userService.findByUsername(username);
        if (user == null) {
            logger.warn("User not found: {}", username);
            return ResponseEntity.notFound().build();
        }
        
        // Устанавливаем публичный ключ
        user.setPublicKey(publicKey);
        user.setPublicKeyAlgorithm(algorithm != null ? algorithm : "EC");
        
        // Сохраняем пользователя
        userService.saveUser(user);
        
        logger.info("Public key set successfully for user: {}", username);
        return ResponseEntity.ok(Map.of("message", "Public key set successfully"));
    }
    
    /**
     * Обновляет публичный ключ пользователя (то же самое, что POST).
     * PUT /api/keys/users/{username}/public-key
     */
    @PutMapping("/users/{username}/public-key")
    public ResponseEntity<?> updatePublicKey(
            @PathVariable String username,
            @RequestBody Map<String, String> body
    ) {
        return setPublicKey(username, body);
    }
}
