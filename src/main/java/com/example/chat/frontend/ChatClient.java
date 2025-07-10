package com.example.chat.frontend;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.*;
import javafx.scene.control.Alert;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.input.MouseEvent;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.layout.Priority;
import javafx.stage.StageStyle;

public class ChatClient extends Application {
    private static WebSocketClient wsClient;
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static ChatView chatView;
    private static String token;
    private static String currentUser;
    private NavigationController navigationController;
    private BorderPane rootPane;
    private NavigationBar navigationBar;
    private String currentToken;

    // Вспомогательные классы для десериализации
    private static class HistoryPayload {
        public String type;
        public List<Map<String, Object>> messages;
    }
    private static class MessagePayload {
        public String type;
        public Map<String, Object> message;
    }
    private static class UsersPayload {
        public String type;
        public List<String> users;
    }

    @Override
    public void start(Stage primaryStage) {
        navigationController = new NavigationController();
        rootPane = new BorderPane();
        navigationBar = new NavigationBar(navigationController, this::goHome, this::onRoomSelected);
        rootPane.setTop(null);
        rootPane.setBottom(navigationBar);

        // Кастомный title bar
        HBox customTitleBar = new HBox();
        customTitleBar.getStyleClass().add("custom-title-bar");
        customTitleBar.setMinHeight(36);
        customTitleBar.setAlignment(Pos.CENTER_LEFT);
        customTitleBar.setSpacing(10);
        customTitleBar.setPadding(new Insets(0, 0, 0, 10));
        // Кнопки управления окном
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button minButton = new Button("—");
        minButton.getStyleClass().add("window-btn");
        minButton.setOnAction(e -> primaryStage.setIconified(true));
        Button closeButton = new Button("✕");
        closeButton.getStyleClass().addAll("window-btn", "close-btn");
        closeButton.setOnAction(e -> primaryStage.close());
        Button restoreButton = new Button("🗗");
        restoreButton.getStyleClass().add("window-btn");
        restoreButton.setOnAction(e -> {
            if (primaryStage.isMaximized()) {
                primaryStage.setMaximized(false);
            } else {
                primaryStage.setMaximized(true);
            }
        });
        customTitleBar.getChildren().addAll(restoreButton, spacer, minButton, closeButton);
        // Перетаскивание окна
        final double[] offset = new double[2];
        customTitleBar.setOnMousePressed((MouseEvent event) -> {
            offset[0] = event.getSceneX();
            offset[1] = event.getSceneY();
        });
        customTitleBar.setOnMouseDragged((MouseEvent event) -> {
            primaryStage.setX(event.getScreenX() - offset[0]);
            primaryStage.setY(event.getScreenY() - offset[1]);
        });
        // Новый основной layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setTop(customTitleBar);
        mainLayout.setCenter(rootPane);
        mainLayout.setBottom(navigationBar);
        // rootPane теперь только для navigationController
        rootPane.setTop(null);
        rootPane.setBottom(null);
        rootPane.setCenter(navigationController);
        // StackPane для анимаций
        javafx.scene.layout.StackPane stackPane = new javafx.scene.layout.StackPane(mainLayout);
        // Загружаем CSS стили
        Scene scene = new Scene(stackPane, 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setScene(scene);
        primaryStage.show();
        showLoginScreen();
    }

    private void showLoginScreen() {
        LoginView loginView = new LoginView(this::onLoginSuccess);
        navigationController.navigateTo(loginView.getNode(this));
    }

    private void onLoginSuccess(String receivedToken) {
        token = receivedToken;
        currentToken = receivedToken;
        // Извлекаем имя пользователя из токена (в реальном приложении это должно приходить с сервера)
        currentUser = extractUsernameFromToken(token);
        navigationBar.updateUser(currentUser);
        navigationBar.updateToken(currentToken);
        
        RoomListView roomListView = new RoomListView(this::onRoomSelected, token);
        navigationController.navigateTo(roomListView.getNode(this));
    }

    private String extractUsernameFromToken(String token) {
        try {
            // JWT: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payloadJson);
            return node.has("sub") ? node.get("sub").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void onRoomSelected(String room) {
        connectWebSocket(token, room);
        chatView = new ChatView(this::sendMessage, room, currentUser);
        navigationController.navigateTo(chatView.getNode(this));
    }

    private void goHome() {
        // Сброс токена и закрытие WebSocket
        token = null;
        currentUser = null;
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.close();
            wsClient = null;
        }
        if (navigationController != null) {
            navigationController.clearHistory();
        }
        LoginView loginView = new LoginView(this::onLoginSuccess);
        navigationController.setRoot(loginView.getNode(this));
    }

    private void connectWebSocket(String token, String room) {
        try {
            // Создаем WebSocket клиент с кастомными заголовками для передачи токена
            wsClient = new WebSocketClient(new URI("ws://localhost:8082")) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Map<String, Object> joinMessage = new HashMap<>();
                    joinMessage.put("type", "join");
                    joinMessage.put("room", room);
                    try {
                        send(objectMapper.writeValueAsString(joinMessage));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    try {
                        // Определяем тип сообщения
                        Map<String, Object> typeProbe = objectMapper.readValue(
                            message, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                        );
                        String type = (String) typeProbe.get("type");
                        
                        if ("auth_success".equals(type)) {
                        } else if ("history".equals(type)) {
                            HistoryPayload payload = objectMapper.readValue(message, HistoryPayload.class);
                            Platform.runLater(() -> chatView.updateMessages(payload.messages));
                        } else if ("message".equals(type)) {
                            MessagePayload payload = objectMapper.readValue(message, MessagePayload.class);
                            Platform.runLater(() -> chatView.addMessage(payload.message));
                        } else if ("users".equals(type)) {
                            UsersPayload payload = objectMapper.readValue(message, UsersPayload.class);
                            Platform.runLater(() -> chatView.updateUsers(payload.users));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (code == 1008) {
                        Platform.runLater(() -> {
                            // Показываем сообщение об ошибке аутентификации
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Authentication Error");
                            alert.setHeaderText("Connection Failed");
                            alert.setContentText("Your session has expired. Please log in again.");
                            alert.showAndWait();
                            goHome(); // Возвращаемся на экран входа
                        });
                    }
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }
            };
            
            // Добавляем заголовок с токеном
            wsClient.addHeader("Authorization", "Bearer " + token);
            wsClient.connect();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(String message, String recipient) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "message");
            msg.put("room", chatView.getRoom());
            msg.put("message", message);
            if (recipient != null && !recipient.isEmpty()) {
                msg.put("recipient", recipient);
            }
            String jsonMessage = objectMapper.writeValueAsString(msg);
            wsClient.send(jsonMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}