package com.example.chat.controller;

import com.example.chat.config.JwtUtil;
import com.example.chat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

@RestController
@RequestMapping("/api")
public class RoomController {
    private static final Logger logger = LoggerFactory.getLogger(RoomController.class);

    private static final Set<String> rooms = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        rooms.addAll(Arrays.asList("Флуд", "Спорт", "Музыка", "Программирование", "Игры", "Новости"));
    }

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;  // ← сюда, внутрь класса

    @GetMapping("/rooms")
    public ResponseEntity<?> getRooms() {
        logger.info("Getting list of rooms");
        Map<String, Object> response = new HashMap<>();
        response.put("rooms", new ArrayList<>(rooms));
        logger.debug("Returning {} rooms", rooms.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rooms")
    public ResponseEntity<?> createRoom(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        logger.info("Creating room: {}", name);
        if (name == null || name.trim().isEmpty()) {
            logger.warn("Room creation failed: name is empty");
            return ResponseEntity.badRequest().body("Room name is required");
        }
        name = name.trim();
        if (rooms.contains(name)) {
            logger.warn("Room creation failed: room '{}' already exists", name);
            return ResponseEntity.status(409).body("Room already exists");
        }
        rooms.add(name);
        logger.info("Room '{}' created successfully", name);
        return ResponseEntity.status(201).body("Room created");
    }

    @DeleteMapping("/rooms/{roomName}")
    public ResponseEntity<?> deleteRoom(@PathVariable String roomName) {
        logger.info("Deleting room: {}", roomName);
        if (roomName == null || roomName.trim().isEmpty()) {
            logger.warn("Room deletion failed: name is empty");
            return ResponseEntity.badRequest().body("Room name is required");
        }
        roomName = roomName.trim();
        if (!rooms.contains(roomName)) {
            logger.warn("Room deletion failed: room '{}' not found", roomName);
            return ResponseEntity.status(404).body("Room not found");
        }
        rooms.remove(roomName);
        logger.info("Room '{}' deleted successfully", roomName);
        return ResponseEntity.ok("Room deleted");
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        String token = authHeader.substring(7);
        if (jwtUtil.isTokenExpired(token)) {
            return ResponseEntity.status(401).body("Token expired");
        }
        logger.info("Getting list of all users");
        List<String> users = userService.getAllUsernames();
        return ResponseEntity.ok(Map.of("users", users));
    }
}