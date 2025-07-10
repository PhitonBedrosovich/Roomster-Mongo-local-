package com.example.chat.controller;

import com.example.chat.model.User;
import com.example.chat.service.UserService;
import com.example.chat.config.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public AuthController() {
        System.out.println("AuthController initialized!");
    }

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok("AuthController is working!");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            System.out.println("Registering user: " + user.getUsername());
            // Проверка уникальности username
            if (userService.findByUsername(user.getUsername()) != null) {
                return ResponseEntity.badRequest().body("Username already exists");
            }
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userService.saveUser(user);
            System.out.println("User registered successfully: " + user.getUsername());
            return ResponseEntity.ok("User registered");
        } catch (Exception e) {
            System.out.println("Registration error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Username already exists");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {
        try {
            System.out.println("Login attempt for user: " + user.getUsername());
            
            User existingUser = userService.findByUsername(user.getUsername());
            System.out.println("Found user: " + (existingUser != null ? existingUser.getUsername() : "null"));
            
            if (existingUser != null && passwordEncoder.matches(user.getPassword(), existingUser.getPassword())) {
                System.out.println("Password matches for user: " + existingUser.getUsername());
                
                String token = jwtUtil.generateToken(existingUser.getUsername());
                
                System.out.println("JWT token generated for user: " + existingUser.getUsername());
                System.out.println("Token: " + token);
                System.out.println("Token expires at: " + jwtUtil.getExpirationDateFromToken(token));
                
                Map<String, String> response = new HashMap<>();
                response.put("token", token);
                response.put("username", existingUser.getUsername());
                response.put("expiresAt", jwtUtil.getExpirationDateFromToken(token).toString());
                return ResponseEntity.ok(response);
            }
            
            System.out.println("Login failed for user: " + user.getUsername());
            return ResponseEntity.status(401).body("Invalid credentials");
        } catch (Exception e) {
            System.out.println("Login error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            if (token == null) {
                return ResponseEntity.badRequest().body("Token is required");
            }

            if (jwtUtil.isTokenExpired(token)) {
                return ResponseEntity.status(401).body("Token expired");
            }

            String username = jwtUtil.getUsernameFromToken(token);
            User user = userService.findByUsername(username);
            
            if (user == null) {
                return ResponseEntity.status(401).body("User not found");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("username", username);
            response.put("expiresAt", jwtUtil.getExpirationDateFromToken(token).toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("Token validation error: " + e.getMessage());
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
    public ResponseEntity<?> deleteUser(@PathVariable String username) {
        try {
            System.out.println("Deleting user: " + username);
            userService.deleteUserAndMessages(username);
            System.out.println("User and messages deleted successfully: " + username);
            return ResponseEntity.ok("User and all messages deleted");
        } catch (Exception e) {
            System.out.println("Delete user error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }
}