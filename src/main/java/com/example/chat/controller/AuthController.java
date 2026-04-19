package com.example.chat.controller;

import com.example.chat.model.User;
import com.example.chat.service.UserService;
import com.example.chat.config.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final int BLOCK_MINUTES = 5;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public AuthController() {
        logger.info("AuthController initialized!");
    }

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok("AuthController is working!");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            logger.info("Registering user: {}", user.getUsername());
            if (userService.findByUsername(user.getUsername()) != null) {
                logger.warn("Registration failed: username {} already exists", user.getUsername());
                return ResponseEntity.badRequest().body("Username already exists");
            }
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userService.saveUser(user);
            logger.info("User registered successfully: {}", user.getUsername());
            return ResponseEntity.ok("User registered");
        } catch (Exception e) {
            logger.error("Registration error for user {}: {}", user.getUsername(), e.getMessage(), e);
            return ResponseEntity.badRequest().body("Username already exists");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user, HttpServletRequest request) {
        try {
            String ip = request.getRemoteAddr();
            String blockKey = "login:block:" + ip;
            String attemptsKey = "login:attempts:" + ip;

            // Проверяем заблокирован ли IP
            if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
                logger.warn("Blocked login attempt from IP: {}", ip);
                return ResponseEntity.status(429)
                        .body("Too many failed attempts. Try again in 5 minutes.");
            }

            logger.info("Login attempt for user: {}", user.getUsername());

            User existingUser = userService.findByUsername(user.getUsername());
            logger.debug("Found user: {}", existingUser != null ? existingUser.getUsername() : "null");

            if (existingUser != null && passwordEncoder.matches(user.getPassword(), existingUser.getPassword())) {
                logger.info("Password matches for user: {}", existingUser.getUsername());

                // Успешный вход — сбрасываем счётчик
                redisTemplate.delete(attemptsKey);

                String token = jwtUtil.generateToken(existingUser.getUsername());

                logger.debug("JWT token generated for user: {}", existingUser.getUsername());
                logger.debug("Token expires at: {}", jwtUtil.getExpirationDateFromToken(token));

                Map<String, String> response = new HashMap<>();
                response.put("token", token);
                response.put("username", existingUser.getUsername());
                response.put("expiresAt", jwtUtil.getExpirationDateFromToken(token).toString());
                return ResponseEntity.ok(response);
            }

            // Неудачная попытка — увеличиваем счётчик
            Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
            redisTemplate.expire(attemptsKey, BLOCK_MINUTES, TimeUnit.MINUTES);

            if (attempts != null && attempts >= MAX_ATTEMPTS) {
                redisTemplate.opsForValue().set(blockKey, "1", BLOCK_MINUTES, TimeUnit.MINUTES);
                redisTemplate.delete(attemptsKey);
                logger.warn("IP {} blocked after {} failed attempts", ip, MAX_ATTEMPTS);
                return ResponseEntity.status(429)
                        .body("Too many failed attempts. Try again in 5 minutes.");
            }

            logger.warn("Login failed for user: {}, attempts: {}", user.getUsername(), attempts);
            return ResponseEntity.status(401)
                    .body("Invalid credentials. Attempts left: " + (MAX_ATTEMPTS - attempts));

        } catch (Exception e) {
            logger.error("Login error for user {}: {}", user.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            if (token == null) {
                logger.warn("Token validation failed: token is null");
                return ResponseEntity.badRequest().body("Token is required");
            }

            if (jwtUtil.isTokenExpired(token)) {
                logger.warn("Token validation failed: token expired");
                return ResponseEntity.status(401).body("Token expired");
            }

            String username = jwtUtil.getUsernameFromToken(token);
            User user = userService.findByUsername(username);

            if (user == null) {
                logger.warn("Token validation failed: user not found for username {}", username);
                return ResponseEntity.status(401).body("User not found");
            }

            logger.debug("Token validation successful for user: {}", username);
            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("username", username);
            response.put("expiresAt", jwtUtil.getExpirationDateFromToken(token).toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Token validation error: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body("Invalid token");
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (username == null || oldPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body("Missing required fields");
        }
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(404).body("User not found");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return ResponseEntity.status(401).body("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.saveUser(user);
        return ResponseEntity.ok("Password changed successfully");
    }

    @DeleteMapping("/user/{username}")
    public ResponseEntity<?> deleteUser(
            @PathVariable String username,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            String token = authHeader.substring(7);
            if (jwtUtil.isTokenExpired(token)) {
                return ResponseEntity.status(401).body("Token expired");
            }

            String tokenUsername = jwtUtil.getUsernameFromToken(token);
            if (!tokenUsername.equals(username)) {
                logger.warn("User {} tried to delete account of {}", tokenUsername, username);
                return ResponseEntity.status(403).body("Forbidden: you can only delete your own account");
            }

            logger.info("Deleting user: {}", username);
            userService.deleteUserAndMessages(username);
            logger.info("User and messages deleted successfully: {}", username);
            return ResponseEntity.ok("User and all messages deleted");

        } catch (Exception e) {
            logger.error("Delete user error for {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }
}