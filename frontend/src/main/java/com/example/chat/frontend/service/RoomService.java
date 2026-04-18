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
    private static final String BASE_URL = "https://roomster.duckdns.org";
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
                        return List.of("Флуд", "Спорт", "Музыка", "Разработка", "Игры", "Новости");
                    }
                } else {
                    return List.of("Флуд", "Спорт", "Музыка", "Разработка", "Игры", "Новости");
                }
            } catch (Exception e) {
                return List.of("Флуд", "Спорт", "Музыка", "Разработка", "Игры", "Новости");
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

    public static CompletableFuture<List<String>> getAllUsersAsync(String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/users"))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    Map<String, Object> map = objectMapper.readValue(response.body(), Map.class);
                    return (List<String>) map.get("users");
                }
                return List.of();
            } catch (Exception e) {
                return List.of();
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
    
    /**
     * Получает публичный ключ пользователя.
     */
    public static CompletableFuture<Map<String, String>> getPublicKeyAsync(String username, String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/keys/users/" + username + "/public-key"))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return objectMapper.readValue(response.body(), Map.class);
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        });
    }
    
    /**
     * Устанавливает публичный ключ пользователя.
     */
    public static CompletableFuture<Boolean> setPublicKeyAsync(String username, String publicKey, String algorithm, String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> body = new java.util.HashMap<>();
                body.put("publicKey", publicKey);
                body.put("algorithm", algorithm != null ? algorithm : "EC");
                
                String bodyJson = objectMapper.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/keys/users/" + username + "/public-key"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200 || response.statusCode() == 201;
            } catch (Exception e) {
                return false;
            }
        });
    }
} 