package com.example.chat.frontend;

import com.example.chat.frontend.crypto.*;
import com.example.chat.frontend.detection.SensitiveDataDetector;
import com.example.chat.frontend.detection.ScanResult;
import com.example.chat.frontend.service.RoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.security.KeyPair;
import java.util.*;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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
    private static final Logger logger = LoggerFactory.getLogger(ChatClient.class);
    private static WebSocketClient wsClient;
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static ChatView chatView;
    private static String token;
    private static String currentUser;
    private NavigationController navigationController;
    private BorderPane rootPane;
    private NavigationBar navigationBar;
    private String currentToken;
    
    // Криптографические компоненты
    private static SensitiveDataDetector sensitiveDataDetector;
    private static KeyExchangeHandler keyExchangeHandler;

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
        
        // Инициализируем криптографические компоненты
        initializeCryptography(currentUser);
        
        RoomListView roomListView = new RoomListView(this::onRoomSelected, token);
        navigationController.navigateTo(roomListView.getNode(this));
    }
    
    /**
     * Инициализирует криптографические компоненты при входе пользователя.
     */
    private void initializeCryptography(String username) {
        try {
            // Инициализируем детектор чувствительных данных
            sensitiveDataDetector = new SensitiveDataDetector();
            
            // Проверяем, есть ли уже identity ключи
            if (!KeyStore.hasIdentityKeys()) {
                // Генерируем новую пару ключей
                KeyPair identityKeyPair = CryptoService.generateECKeyPair();
                KeyStore.saveIdentityKeyPair(identityKeyPair, username);
                
                // Отправляем публичный ключ на сервер
                String publicKeyBase64 = CryptoService.publicKeyToBase64(identityKeyPair.getPublic());
                RoomService.setPublicKeyAsync(username, publicKeyBase64, "EC", token)
                    .thenAccept(success -> {
                        if (success) {
                            logger.info("Public key uploaded to server successfully");
                        } else {
                            logger.warn("Failed to upload public key to server");
                        }
                    });
            }
            
            // Инициализируем обработчик обмена ключами
            keyExchangeHandler = new KeyExchangeHandler(
                json -> {
                    if (wsClient != null && wsClient.isOpen()) {
                        wsClient.send(json);
                    }
                },
                username,
                token
            );
            
            logger.info("Cryptography initialized for user: {}", username);
        } catch (Exception e) {
            logger.error("Error initializing cryptography", e);
        }
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
        chatView = new ChatView(this::sendMessage, room, currentUser, token);
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
        // Очищаем криптографические данные
        KeyStore.clear();
        sensitiveDataDetector = null;
        keyExchangeHandler = null;
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
                        
                        // Проверяем наличие ключа комнаты и запрашиваем, если нет
                        if (!KeyStore.hasRoomKey(room) && keyExchangeHandler != null) {
                            keyExchangeHandler.requestRoomKey(room);
                        }
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
                            // Аутентификация успешна
                        } else if ("history".equals(type)) {
                            HistoryPayload payload = objectMapper.readValue(message, HistoryPayload.class);
                            // Расшифровываем историю сообщений
                            Platform.runLater(() -> {
                                List<Map<String, Object>> decryptedMessages = decryptMessages(payload.messages);
                                chatView.updateMessages(decryptedMessages);
                            });
                        } else if ("message".equals(type)) {
                            MessagePayload payload = objectMapper.readValue(message, MessagePayload.class);
                            // Расшифровываем сообщение
                            Platform.runLater(() -> {
                                Map<String, Object> decryptedMessage = decryptMessage(payload.message);
                                chatView.addMessage(decryptedMessage);
                            });
                        } else if ("users".equals(type)) {
                            UsersPayload payload = objectMapper.readValue(message, UsersPayload.class);
                            Platform.runLater(() -> {
                                chatView.updateUsers(payload.users);
                                // Если мы одни в комнате и ключа еще нет — создаем room key локально.
                                // Когда второй пользователь присоединится и запросит ключ, мы ему ответим.
                                try {
                                    if (payload.users != null
                                            && payload.users.size() == 1
                                            && payload.users.contains(currentUser)
                                            && !KeyStore.hasRoomKey(room)) {
                                        javax.crypto.SecretKey roomKey = CryptoService.generateAES256Key();
                                        KeyStore.saveRoomKey(room, roomKey);
                                        logger.info("Generated room key for room {} (single participant)", room);
                                        // Обновим индикатор в UI через updateUsers() при следующем событии,
                                        // либо пользователь увидит обновление при любом следующем обновлении списка.
                                    }
                                } catch (Exception e) {
                                    logger.error("Failed to generate room key on users update", e);
                                }
                            });
                        } else if ("key_exchange".equals(type)) {
                            // Обрабатываем обмен ключами
                            if (keyExchangeHandler != null) {
                                keyExchangeHandler.handleKeyExchange(typeProbe);
                            }
                        } else if ("key_request".equals(type)) {
                            // Обрабатываем запрос ключа
                            if (keyExchangeHandler != null) {
                                keyExchangeHandler.handleKeyRequest(typeProbe);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing WebSocket message", e);
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
            // 1. Сканирование на чувствительные данные
            if (sensitiveDataDetector != null) {
                ScanResult scanResult = sensitiveDataDetector.scan(message);
                if (scanResult.hasSensitiveData()) {
                    // Показываем предупреждение пользователю
                    boolean confirmed = showSensitiveDataWarning(scanResult);
                    if (!confirmed) {
                        logger.info("User cancelled message send due to sensitive data warning");
                        return; // Пользователь отменил отправку
                    }
                }
            }
            
            // 2. Шифрование сообщения
            String ciphertext;
            String room = chatView.getRoom();
            
            try {
                if (recipient != null && !recipient.isEmpty()) {
                    // Приватное сообщение: используем pairwise ключ
                    if (!KeyStore.hasPairwiseKey(recipient)) {
                        // Устанавливаем pairwise ключ, если его нет
                        if (keyExchangeHandler != null) {
                            keyExchangeHandler.establishPairwiseKey(recipient);
                        }
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Key Establishment");
                            alert.setHeaderText("Setting up private encryption key...");
                            alert.setContentText("Please wait a moment and send again.");
                            alert.showAndWait();
                        });
                        return;
                    }
                    
                    javax.crypto.SecretKey pairwiseKey = KeyStore.getPairwiseKey(recipient);
                    if (pairwiseKey == null) {
                        throw new IllegalStateException("Pairwise key not available for user: " + recipient);
                    }
                    
                    EncryptedMessage encrypted = CryptoService.encryptAESGCM(message, pairwiseKey);
                    ciphertext = encrypted.toTransportFormat();
                } else {
                    // Групповое сообщение: используем ключ комнаты
                    if (!KeyStore.hasRoomKey(room)) {
                        // Запрашиваем ключ комнаты
                        if (keyExchangeHandler != null) {
                            keyExchangeHandler.requestRoomKey(room);
                        }
                        // Показываем сообщение пользователю
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.setTitle("Key Not Available");
                            alert.setHeaderText("Room Key Missing");
                            alert.setContentText("Requesting room key... Please wait and try again.");
                            alert.showAndWait();
                        });
                        return;
                    }
                    
                    javax.crypto.SecretKey roomKey = KeyStore.getRoomKey(room);
                    EncryptedMessage encrypted = CryptoService.encryptAESGCM(message, roomKey);
                    ciphertext = encrypted.toTransportFormat();
                }
            } catch (Exception e) {
                logger.error("Error encrypting message", e);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Encryption Error");
                    alert.setHeaderText("Failed to Encrypt Message");
                    alert.setContentText("Error: " + e.getMessage());
                    alert.showAndWait();
                });
                return;
            }
            
            // 3. Отправка зашифрованного сообщения
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "message");
            msg.put("room", room);
            msg.put("message", ciphertext); // Отправляем ciphertext вместо plaintext
            if (recipient != null && !recipient.isEmpty()) {
                msg.put("recipient", recipient);
            }
            String jsonMessage = objectMapper.writeValueAsString(msg);
            wsClient.send(jsonMessage);
            
            logger.debug("Encrypted message sent successfully");
        } catch (Exception e) {
            logger.error("Error sending message", e);
            e.printStackTrace();
        }
    }
    
    /**
     * Показывает предупреждение о чувствительных данных и запрашивает подтверждение.
     */
    private boolean showSensitiveDataWarning(ScanResult scanResult) {
        // Важно: sendMessage() вызывается из UI-потока JavaFX.
        // Если здесь блокировать UI-поток и при этом пытаться показать Alert через Platform.runLater(),
        // получится deadlock. Поэтому:
        // - если мы уже в FX-потоке: показываем showAndWait() напрямую
        // - иначе: показываем через Platform.runLater() и ждем через CompletableFuture

        ButtonType continueButton = new ButtonType("Continue Anyway");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        if (Platform.isFxApplicationThread()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Sensitive Data Detected");
            alert.setHeaderText("⚠️ Warning: Sensitive Information Found");
            alert.setContentText(scanResult.getWarningMessage());
            alert.getButtonTypes().setAll(continueButton, cancelButton);
            return alert.showAndWait().map(bt -> bt == continueButton).orElse(false);
        }

        java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Sensitive Data Detected");
            alert.setHeaderText("⚠️ Warning: Sensitive Information Found");
            alert.setContentText(scanResult.getWarningMessage());
            alert.getButtonTypes().setAll(continueButton, cancelButton);
            boolean result = alert.showAndWait().map(bt -> bt == continueButton).orElse(false);
            future.complete(result);
        });
        try {
            return future.get();
        } catch (Exception e) {
            logger.error("Failed to show sensitive data warning", e);
            return false;
        }
    }

    /**
     * Расшифровывает одно сообщение.
     */
    private Map<String, Object> decryptMessage(Map<String, Object> message) {
        Map<String, Object> decrypted = new HashMap<>(message);
        String ciphertext = (String) message.get("content");
        String room = (String) message.get("room");
        String sender = (String) message.get("username");
        String recipient = (String) message.get("recipient");

        if (ciphertext == null) {
            return decrypted; // Нет контента для расшифровки
        }

        // Проверяем, является ли это зашифрованным сообщением
        if (!EncryptedMessage.isValidTransportFormat(ciphertext)) {
            // Не зашифровано, возвращаем как есть
            return decrypted;
        }

        try {
            javax.crypto.SecretKey key;
            boolean isPrivate = recipient != null && !recipient.isEmpty();

            if (isPrivate) {
                // Приватное сообщение
                if (currentUser.equals(recipient)) {
                    // Я получатель приватного сообщения -> ключ с отправителем
                    if (!KeyStore.hasPairwiseKey(sender)) {
                        if (keyExchangeHandler != null) {
                            keyExchangeHandler.establishPairwiseKey(sender);
                        }
                        decrypted.put("content", "[Расшифровка... Ключ устанавливается]");
                        return decrypted;
                    }
                    key = KeyStore.getPairwiseKey(sender);
                } else if (currentUser.equals(sender)) {
                    // Я отправитель приватного сообщения -> ключ с получателем
                    if (!KeyStore.hasPairwiseKey(recipient)) {
                        if (keyExchangeHandler != null) {
                            keyExchangeHandler.establishPairwiseKey(recipient);
                        }
                        decrypted.put("content", "[Расшифровка... Ключ устанавливается]");
                        return decrypted;
                    }
                    key = KeyStore.getPairwiseKey(recipient);
                } else {
                    // Мы не участник приватного сообщения
                    decrypted.put("content", "[Не могу расшифровать: не участник приватного сообщения]");
                    return decrypted;
                }
            } else {
                // Групповое сообщение
                if (!KeyStore.hasRoomKey(room)) {
                    if (keyExchangeHandler != null) {
                        keyExchangeHandler.requestRoomKey(room);
                    }
                    decrypted.put("content", "[Не могу расшифровать: ключ комнаты отсутствует]");
                    return decrypted;
                }
                key = KeyStore.getRoomKey(room);
            }

            // Расшифровываем
            EncryptedMessage encrypted = EncryptedMessage.fromTransportFormat(ciphertext);
            String plaintext = CryptoService.decryptAESGCM(encrypted, key);
            decrypted.put("content", plaintext);

        } catch (SecurityException e) {
            logger.warn("Failed to decrypt message: {}", e.getMessage());
            decrypted.put("content", "[Ошибка расшифровки: сообщение повреждено или ключ неверный]");
        } catch (Exception e) {
            logger.error("Error decrypting message", e);
            decrypted.put("content", "[Ошибка расшифровки]");
        }

        return decrypted;
    }
    
    /**
     * Расшифровывает список сообщений.
     */
    private List<Map<String, Object>> decryptMessages(List<Map<String, Object>> messages) {
        List<Map<String, Object>> decrypted = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            decrypted.add(decryptMessage(message));
        }
        return decrypted;
    }

    public static void main(String[] args) {
        launch(args);
    }
}