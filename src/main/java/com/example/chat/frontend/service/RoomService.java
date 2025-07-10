package com.example.chat.frontend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RoomService {
    private static final String BASE_URL = "http://localhost:8081";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static CompletableFuture<List<String>> getRoomsAsync(String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/rooms"))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> responseBody = objectMapper.readValue(
                        response.body(), 
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                    );
                    if (responseBody.containsKey("rooms")) {
                        @SuppressWarnings("unchecked")
                        List<String> rooms = (List<String>) responseBody.get("rooms");
                        return rooms;
                    } else {
                        // Fallback to default rooms if server doesn't provide them
                        return List.of("General", "Sports", "Music", "Programming", "Gaming", "News");
                    }
                } else {
                    return List.of("General", "Sports", "Music", "Programming", "Gaming", "News");
                }
            } catch (Exception e) {
                return List.of("General", "Sports", "Music", "Programming", "Gaming", "News");
            }
        });
    }

    public static CompletableFuture<Boolean> createRoomAsync(String roomName, String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = objectMapper.writeValueAsString(Map.of("name", roomName));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/rooms"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200 || response.statusCode() == 201;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public static CompletableFuture<Boolean> deleteRoomAsync(String roomName, String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/rooms/" + roomName))
                        .header("Authorization", "Bearer " + token)
                        .DELETE()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public static void getRooms(String token, Consumer<List<String>> onSuccess, Consumer<String> onError) {
        getRoomsAsync(token)
                .thenAccept(rooms -> Platform.runLater(() -> onSuccess.accept(rooms)))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> onError.accept(throwable.getMessage()));
                    return null;
                });
    }

    public static void createRoom(String roomName, String token, Consumer<Boolean> onSuccess, Consumer<String> onError) {
        createRoomAsync(roomName, token)
                .thenAccept(success -> Platform.runLater(() -> onSuccess.accept(success)))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> onError.accept(throwable.getMessage()));
                    return null;
                });
    }

    public static void deleteRoom(String roomName, String token, Consumer<Boolean> onSuccess, Consumer<String> onError) {
        deleteRoomAsync(roomName, token)
                .thenAccept(success -> Platform.runLater(() -> onSuccess.accept(success)))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> onError.accept(throwable.getMessage()));
                    return null;
                });
    }
} 