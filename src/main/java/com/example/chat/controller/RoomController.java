package com.example.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    // Потокобезопасное множество комнат
    private static final Set<String> rooms = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        // Добавим стандартные комнаты
        rooms.addAll(Arrays.asList("General", "Sports", "Music", "Programming", "Gaming", "News"));
    }

    @GetMapping
    public ResponseEntity<?> getRooms() {
        Map<String, Object> response = new HashMap<>();
        response.put("rooms", new ArrayList<>(rooms));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Room name is required");
        }
        name = name.trim();
        if (rooms.contains(name)) {
            return ResponseEntity.status(409).body("Room already exists");
        }
        rooms.add(name);
        return ResponseEntity.status(201).body("Room created");
    }

    @DeleteMapping("/{roomName}")
    public ResponseEntity<?> deleteRoom(@PathVariable String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Room name is required");
        }
        roomName = roomName.trim();
        if (!rooms.contains(roomName)) {
            return ResponseEntity.status(404).body("Room not found");
        }
        rooms.remove(roomName);
        return ResponseEntity.ok("Room deleted");
    }
} 