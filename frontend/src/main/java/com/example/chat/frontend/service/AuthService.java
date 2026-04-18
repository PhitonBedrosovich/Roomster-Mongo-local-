package com.example.chat.frontend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class AuthService {
    private static final String BASE_URL = "https://roomster.duckdns.org";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static CompletableFuture<String> loginAsync(String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, String> responseBody = objectMapper.readValue(
                        response.body(), 
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}
                    );
                    if (responseBody.containsKey("token")) {
                        return responseBody.get("token");
                    } else {
                        throw new RuntimeException("Неизвестный ответ сервера");
                    }
                } else {
                    throw new RuntimeException(response.body() != null ? response.body() : "Login failed");
                }
            } catch (Exception e) {
                throw new RuntimeException("Ошибка подключения к серверу: " + e.getMessage(), e);
            }
        });
    }

    public static CompletableFuture<String> registerAsync(String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, String> responseBody = objectMapper.readValue(
                        response.body(), 
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}
                    );
                    if (responseBody.containsKey("token")) {
                        return responseBody.get("token");
                    } else {
                        throw new RuntimeException("Неизвестный ответ сервера");
                    }
                } else {
                    throw new RuntimeException(response.body() != null ? response.body() : "Регистрация не удалась");
                }
            } catch (Exception e) {
                throw new RuntimeException("Ошибка подключения к серверу: " + e.getMessage(), e);
            }
        });
    }

    public static CompletableFuture<Boolean> changePasswordAsync(String username, String oldPassword, String newPassword) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = objectMapper.writeValueAsString(Map.of(
                    "username", username,
                    "oldPassword", oldPassword,
                    "newPassword", newPassword
                ));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/auth/change-password"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return true;
                } else {
                    throw new RuntimeException(response.body() != null ? response.body() : "Сменить пароль не удалось");
                }
            } catch (Exception e) {
                throw new RuntimeException("Ошибка подключения к серверу: " + e.getMessage(), e);
            }
        });
    }

    public static void login(String username, String password, Consumer<String> onSuccess, Consumer<String> onError) {
        loginAsync(username, password)
                .thenAccept(token -> Platform.runLater(() -> onSuccess.accept(token)))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> onError.accept(throwable.getMessage()));
                    return null;
                });
    }

    public static void register(String username, String password, Consumer<String> onSuccess, Consumer<String> onError) {
        registerAsync(username, password)
                .thenAccept(token -> Platform.runLater(() -> onSuccess.accept(token)))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> onError.accept(throwable.getMessage()));
                    return null;
                });
    }

    public static void changePassword(String username, String oldPassword, String newPassword, Runnable onSuccess, Consumer<String> onError) {
        changePasswordAsync(username, oldPassword, newPassword)
            .thenAccept(success -> Platform.runLater(onSuccess))
            .exceptionally(throwable -> {
                Platform.runLater(() -> onError.accept(throwable.getMessage()));
                return null;
            });
    }
} 