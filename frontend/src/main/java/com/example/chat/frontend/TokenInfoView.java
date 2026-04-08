package com.example.chat.frontend;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.Consumer;

public class TokenInfoView {
    private VBox root;
    private String currentToken;
    private Label tokenInfoLabel;
    private Label statusLabel;
    private NavigationController navigationController;
    private Consumer<String> onRoomSelected;

    public TokenInfoView(String token, NavigationController navigationController, Consumer<String> onRoomSelected) {
        this.currentToken = token;
        this.navigationController = navigationController;
        this.onRoomSelected = onRoomSelected;
        createUI();
        validateToken();
    }

    private void createUI() {
        root = new VBox(10);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));

        Label titleLabel = new Label("JWT Token Information");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        tokenInfoLabel = new Label();
        tokenInfoLabel.setWrapText(true);
        tokenInfoLabel.setMaxWidth(600);

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-weight: bold;");

        Button refreshButton = new Button("🔄 Refresh Token Info");
        refreshButton.setOnAction(e -> validateToken());

        Button backButton = new Button("← Back to Chat");
        backButton.setOnAction(e -> {
            // Возвращаемся к предыдущему экрану
            if (navigationController != null) {
                navigationController.goBack();
            }
        });

        Button roomsButton = new Button("🏠 Go to Rooms");
        roomsButton.setOnAction(e -> {
            // Переходим к списку комнат
            if (navigationController != null && onRoomSelected != null) {
                // Создаем новый RoomListView и переходим к нему
                RoomListView roomListView = new RoomListView(onRoomSelected, currentToken);
                navigationController.navigateTo(roomListView.getNode(null));
            }
        });

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(refreshButton, backButton, roomsButton);

        root.getChildren().addAll(titleLabel, tokenInfoLabel, statusLabel, buttonBox);
    }

    private void validateToken() {
        try {
            // Показываем индикатор загрузки
            statusLabel.setText("🔄 Validating token...");
            statusLabel.setStyle("-fx-text-fill: blue; -fx-font-weight: bold;");

            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();
            String body = mapper.writeValueAsString(Map.of("token", currentToken));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://212.34.128.37:8081/api/auth/validate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> responseBody = mapper.readValue(
                        response.body(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                );

                String username = (String) responseBody.get("username");
                String expiresAt = (String) responseBody.get("expiresAt");

                tokenInfoLabel.setText(String.format(
                        "👤 Username: %s\n" +
                                "🔑 Token: %s\n" +
                                "⏰ Expires at: %s\n" +
                                "✅ Status: Valid",
                        username,
                        currentToken.substring(0, Math.min(50, currentToken.length())) + "...",
                        expiresAt
                ));

                statusLabel.setText("✅ Token is valid");
                statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

            } else {
                tokenInfoLabel.setText("❌ Token validation failed\nResponse: " + response.body());
                statusLabel.setText("❌ Token is invalid or expired");
                statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            }
        } catch (Exception e) {
            tokenInfoLabel.setText("❌ Error validating token: " + e.getMessage());
            statusLabel.setText("❌ Error occurred");
            statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        }
    }

    public Node getNode() {
        return root;
    }
}