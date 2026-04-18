package com.example.chat.frontend;

import com.example.chat.frontend.crypto.*;
import com.example.chat.frontend.crypto.PersistentKeyStore;
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
import java.util.Arrays;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import com.example.chat.frontend.PasswordToggleField;
import javafx.scene.control.TextField;
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
import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Toolkit;

public class ChatClient extends Application {
    private static final Logger logger = LoggerFactory.getLogger(ChatClient.class);
    // Поля намеренно НЕ static: каждый экземпляр Application имеет своё соединение
    private WebSocketClient wsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatView chatView;
    private String token;
    private String currentUser;
    private NavigationController navigationController;
    private BorderPane rootPane;
    private NavigationBar navigationBar;
    private String currentToken;

    // Криптографические компоненты
    private SensitiveDataDetector sensitiveDataDetector;
    private KeyExchangeHandler keyExchangeHandler;
    // Пароль keystore хранится в памяти на время сессии для автосохранения
    private char[] keystorePassword;
    // Ссылка на главное окно — нужна чтобы диалоги открывались поверх него
    private Stage primaryStage;
    @SuppressWarnings("unchecked")
    private final TrayIcon[] trayIconRef = new TrayIcon[1];

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
        this.primaryStage = primaryStage;
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

        // ── Системный трей ────────────────────────────────────────────────────
        if (SystemTray.isSupported()) {
            // Разрешаем JVM жить без окна (чтобы трей остался после скрытия окна)
            javafx.application.Platform.setImplicitExit(false);

            SystemTray tray = SystemTray.getSystemTray();

            // Иконка — берём из ресурсов, если нет — стандартная Java
            java.net.URL iconUrl = getClass().getResource("/tray_icon.png");
            Image trayImage = iconUrl != null
                    ? Toolkit.getDefaultToolkit().getImage(iconUrl)
                    : Toolkit.getDefaultToolkit().getImage(
                    getClass().getResource("/styles.css").getPath()
                    .replace("styles.css", "tray_icon.png"));
            // Запасной вариант — белый квадрат 16x16
            if (trayImage == null) {
                trayImage = new java.awt.image.BufferedImage(16, 16,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
            }

            PopupMenu popup = new PopupMenu();

            MenuItem showItem = new MenuItem("Открыть Roomster");
            showItem.addActionListener(e -> javafx.application.Platform.runLater(() -> {
                primaryStage.show();
                primaryStage.toFront();
            }));

            MenuItem exitItem = new MenuItem("Закрыть");
            exitItem.addActionListener(e -> {
                javafx.application.Platform.runLater(() -> primaryStage.close());
                tray.remove(trayIconRef[0]);
                javafx.application.Platform.exit();
                System.exit(0);
            });

            popup.add(showItem);
            popup.addSeparator();
            popup.add(exitItem);

            TrayIcon trayIcon = new TrayIcon(trayImage, "Roomster", popup);
            trayIcon.setImageAutoSize(true);
            // Двойной клик по иконке — показать окно
            trayIcon.addActionListener(e -> javafx.application.Platform.runLater(() -> {
                primaryStage.show();
                primaryStage.toFront();
            }));

            trayIconRef[0] = trayIcon;

            try {
                tray.add(trayIcon);
            } catch (AWTException ex) {
                System.err.println("Не удалось добавить иконку в трей: " + ex.getMessage());
            }

            // Кнопка ✕ в titlebar — скрывает окно в трей вместо закрытия
            closeButton.setOnAction(e -> primaryStage.hide());
        }
        // ─────────────────────────────────────────────────────────────────────

        showLoginScreen();
    }

    private void showLoginScreen() {
        LoginView loginView = new LoginView(this::onLoginSuccess);
        navigationController.navigateTo(loginView.getNode(this));
    }

    private void onLoginSuccess(String receivedToken) {
        token = receivedToken;
        currentToken = receivedToken;
        currentUser = extractUsernameFromToken(token);
        navigationBar.updateUser(currentUser);
        navigationBar.updateToken(currentToken);

        // ВАЖНО: сначала показываем диалог keystore и загружаем сохранённые ключи,
        // и только ПОСЛЕ этого инициализируем крипто.
        // Если сделать наоборот — initializeCryptography сгенерирует новые ключи
        // и загрузит новый публичный ключ на сервер, после чего старые ключи из
        // keystore станут несовместимы с тем что хранится на сервере.
        showKeystorePasswordDialog(currentUser, () -> {
            // Keystore уже загружен (или пропущен) — теперь безопасно инициализировать крипто
            initializeCryptography(currentUser);
            RoomListView roomListView = new RoomListView(this::onRoomSelected, token);
            navigationController.navigateTo(roomListView.getNode(this));
        });
    }

    /**
     * Показывает диалог для ввода пароля keystore.
     * Если keystore существует — расшифровывает и загружает ключи.
     * Если нет — предлагает создать новый с этим паролем.
     * После успеха вызывает onDone.
     */
    private void showKeystorePasswordDialog(String username, Runnable onDone) {
        Platform.runLater(() -> {

            boolean exists = PersistentKeyStore.keystoreExists(username);

            while (true) {
                Dialog<char[]> dialog = new Dialog<>();
                dialog.initOwner(primaryStage);

                dialog.setTitle("Key Storage");
                dialog.setHeaderText(exists
                        ? "🔑 Введите пароль хранилища ключей"
                        : "🔑 Создайте пароль хранилища ключей");

                ButtonType okButton = new ButtonType("Применить", ButtonBar.ButtonData.OK_DONE);
                ButtonType skipButton = new ButtonType("Пропустить", ButtonBar.ButtonData.CANCEL_CLOSE);
                dialog.getDialogPane().getButtonTypes().setAll(okButton, skipButton);

                PasswordToggleField pwField = new PasswordToggleField("Пароль");

                Label errorLabel = new Label();
                errorLabel.setStyle("-fx-text-fill: red;");

                VBox box = new VBox(10, pwField, errorLabel);
                box.setPadding(new Insets(10));
                dialog.getDialogPane().setContent(box);

                dialog.setResultConverter(btn -> btn == okButton ? pwField.toCharArray() : null);

                Optional<char[]> result = dialog.showAndWait();

                if (result.isEmpty()) {
                    logger.info("User skipped keystore");
                    onDone.run();
                    return;
                }

                char[] password = result.get();

                try {
                    if (exists) {
                        PersistentKeyStore.load(username, password);
                        logger.info("Keystore loaded");
                    } else {
                        PersistentKeyStore.save(username, password);
                        logger.info("Keystore created");
                    }

                    keystorePassword = password;
                    onDone.run();
                    return;

                } catch (SecurityException e) {
                    errorLabel.setText("Неправильный пароль!");
                } catch (Exception e) {
                    errorLabel.setText("Ошибка: " + e.getMessage());
                }

                Arrays.fill(password, '\0');
            }
        });
    }

    private void showKeystoreDialogLoop(Dialog<char[]> dialog, ButtonType okButton,
                                        Label errorLabel, PasswordToggleField pwField,
                                        String username, boolean exists, Runnable onDone) {
        dialog.showAndWait().ifPresentOrElse(password -> {
            if (password == null) {
                // Пользователь нажал Skip
                logger.info("User skipped keystore — keys won't be persisted");
                onDone.run();
                return;
            }

            if (exists) {
                // Пытаемся загрузить
                try {
                    PersistentKeyStore.load(username, password);
                    logger.info("Keystore loaded successfully for {}", username);
                    keystorePassword = password; // запоминаем для автосохранения
                    onDone.run();
                } catch (SecurityException e) {
                    // Неверный пароль — показываем снова
                    Arrays.fill(password, '\0');
                    errorLabel.setText("Неверный пароль, попробуйте еще раз");
                    errorLabel.setVisible(true);
                    pwField.clear();
                    showKeystoreDialogLoop(dialog, okButton, errorLabel, pwField, username, exists, onDone);
                } catch (Exception e) {
                    logger.error("Failed to load keystore", e);
                    Arrays.fill(password, '\0');
                    errorLabel.setText("Не удалось загрузить хранилище ключей: " + e.getMessage());
                    errorLabel.setVisible(true);
                    pwField.clear();
                    showKeystoreDialogLoop(dialog, okButton, errorLabel, pwField, username, exists, onDone);
                }
            } else {
                // Сохраняем новый keystore с текущими (только что сгенерированными) ключами
                try {
                    PersistentKeyStore.save(username, password);
                    logger.info("New keystore created for {}", username);
                    keystorePassword = password; // запоминаем для автосохранения
                    onDone.run();
                } catch (Exception e) {
                    logger.error("Failed to create keystore", e);
                    Arrays.fill(password, '\0');
                    errorLabel.setText("Не удалось создать хранилище ключей: " + e.getMessage());
                    errorLabel.setVisible(true);
                    pwField.clear();
                    showKeystoreDialogLoop(dialog, okButton, errorLabel, pwField, username, exists, onDone);
                }
            }
        }, () -> {
            // Dialog closed without result (Skip)
            logger.info("Keystore dialog closed — keys won't be persisted");
            onDone.run();
        });
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
            } else {
                // Ключи уже есть (загружены из keystore) — обновляем публичный ключ на сервере,
                // т.к. после перезапуска сервер мог потерять его из памяти/БД.
                String publicKeyBase64 = KeyStore.exportPublicKeyBase64();
                if (publicKeyBase64 != null) {
                    RoomService.setPublicKeyAsync(username, publicKeyBase64, "EC", token)
                            .thenAccept(success -> {
                                if (success) {
                                    logger.info("Public key re-uploaded to server after keystore load");
                                } else {
                                    logger.warn("Failed to re-upload public key to server");
                                }
                            });
                }
            }

            // Инициализируем обработчик обмена ключами
            keyExchangeHandler = new KeyExchangeHandler(
                    json -> {
                        if (wsClient != null && wsClient.isOpen()) {
                            wsClient.send(json);
                        }
                    },
                    username,
                    token,
                    () -> autoSaveKeystore(username)  // автосохранение при каждом новом ключе
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

    /**
     * Автоматически сохраняет keystore при получении нового ключа.
     * Вызывается из KeyExchangeHandler через колбэк.
     * Использует пароль сохранённый в памяти — пользователь ничего не видит.
     */
    /**
     * Автоматически сохраняет keystore при получении нового ключа.
     * Вызывается из KeyExchangeHandler через колбэк.
     * Также обновляет E2E индикатор в UI — потому что updateUsers приходит
     * не всегда после key_exchange, и без этого индикатор остаётся оранжевым.
     */
    private void autoSaveKeystore(String username) {
        if (keystorePassword == null || username == null) return;
        try {
            PersistentKeyStore.save(username, keystorePassword);
            logger.info("Keystore auto-saved for {}", username);
        } catch (Exception e) {
            logger.warn("Auto-save keystore failed for {}: {}", username, e.getMessage());
        }
        // Обновляем E2E индикатор в UI после получения нового ключа
        Platform.runLater(() -> {
            if (chatView != null) {
                chatView.updateE2EIndicator();
            }
        });
    }

    private void goHome() {
        // Сохраняем ключи на диск перед выходом (если keystore уже существует)
        if (currentUser != null && PersistentKeyStore.keystoreExists(currentUser)) {
            showSaveKeystoreDialog(currentUser);
        }

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
        if (keystorePassword != null) {
            Arrays.fill(keystorePassword, '\0');
            keystorePassword = null;
        }
        LoginView loginView = new LoginView(this::onLoginSuccess);
        navigationController.setRoot(loginView.getNode(this));
    }

    /**
     * Показывает диалог сохранения keystore при выходе.
     */
    private void showSaveKeystoreDialog(String username) {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.initOwner(primaryStage);
        dialog.setTitle("Сохранить ключи");
        dialog.setHeaderText("💾 Сохранить ключи шифрования перед выходом?");

        ButtonType saveBtn = new ButtonType("Сохранить", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType skipBtn = new ButtonType("Пропустить", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, skipBtn);

        PasswordToggleField pwField = new PasswordToggleField("Пароль хранилища");
        pwField.setPrefWidth(280);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e53935;");
        errorLabel.setVisible(false);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10,
                new Label("Введите пароль хранилища ключей, чтобы сохранить обновленные ключи (для комнаты и парные)"),
                pwField, errorLabel);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().lookupButton(saveBtn).setDisable(true);
        pwField.textProperty().addListener((obs, o, n) ->
                dialog.getDialogPane().lookupButton(saveBtn).setDisable(n == null || n.trim().isEmpty()));

        dialog.setResultConverter(btn -> btn == saveBtn ? pwField.toCharArray() : null);

        dialog.showAndWait().ifPresent(password -> {
            if (password == null) return;
            try {
                PersistentKeyStore.save(username, password);
                logger.info("Keystore saved on logout for {}", username);
            } catch (Exception e) {
                logger.error("Failed to save keystore on logout", e);
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    private void connectWebSocket(String token, String room) {
        try {
            // Закрываем старое соединение без блокировки UI-потока.
            // close() отправляет CLOSE-фрейм асинхронно — нам этого достаточно,
            // новый wsClient создаём сразу после, они не мешают друг другу.
            if (wsClient != null) {
                try {
                    wsClient.close();
                } catch (Exception e) {
                    logger.warn("Error closing previous WebSocket connection", e);
                }
                wsClient = null;
            }

            // Создаем WebSocket клиент с кастомными заголовками для передачи токена
            wsClient = new WebSocketClient(new URI("wss://roomster.duckdns.org/ws")) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Map<String, Object> joinMessage = new HashMap<>();
                    joinMessage.put("type", "join");
                    joinMessage.put("room", room);
                    try {
                        send(objectMapper.writeValueAsString(joinMessage));
                        // key_request НЕ отправляем здесь — сервер ещё не добавил нас в комнату.
                        // Запрос ключа делаем после получения "history" (join уже обработан сервером).
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
                            // Сервер отправляет history сразу после обработки join —
                            // значит мы уже в комнате и можно безопасно запросить ключ.
                            if (!KeyStore.hasRoomKey(room) && keyExchangeHandler != null) {
                                keyExchangeHandler.requestRoomKey(room);
                            }
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
                                try {
                                    keyExchangeHandler.handleKeyExchange(typeProbe);
                                } catch (SecurityException e) {
                                    Platform.runLater(() -> {
                                        Alert alert = new Alert(Alert.AlertType.ERROR);
                                        alert.setTitle("Угроза безопасности");
                                        alert.setHeaderText("⚠️ Обнаружена возможная MITM-атака!");
                                        alert.setContentText(e.getMessage());
                                        alert.showAndWait();
                                    });
                                }
                            }
                        } else if ("key_request".equals(type)) {
                            // Обрабатываем запрос ключа
                            if (keyExchangeHandler != null) {
                                try {
                                    keyExchangeHandler.handleKeyRequest(typeProbe);
                                } catch (SecurityException e) {
                                    Platform.runLater(() -> {
                                        Alert alert = new Alert(Alert.AlertType.ERROR);
                                        alert.setTitle("Угроза безопасности");
                                        alert.setHeaderText("⚠️ Обнаружена возможная MITM-атака!");
                                        alert.setContentText(e.getMessage());
                                        alert.showAndWait();
                                    });
                                }
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
                            alert.setTitle("Ошибка аутентификации");
                            alert.setHeaderText("Соединение не удалось");
                            alert.setContentText("Срок действия вашей сессии истек. Пожалуйста, войдите в систему снова");
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
                            alert.setTitle("Ключевое учреждение");
                            alert.setHeaderText("Настройка закрытого ключа шифрования...");
                            alert.setContentText("Пожалуйста, подождите немного и отправьте снова.");
                            alert.showAndWait();
                        });
                        return;
                    }

                    javax.crypto.SecretKey pairwiseKey = KeyStore.getPairwiseKey(recipient);
                    if (pairwiseKey == null) {
                        throw new IllegalStateException("Парный ключ недоступен для пользователя: " + recipient);
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
                            alert.setTitle("Ключ недоступен");
                            alert.setHeaderText("Ключ от комнаты отсутствует");
                            alert.setContentText("Запрос ключа от комнаты... Пожалуйста, подождите и попробуйте снова");
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
                    alert.setTitle("Ошибка шифрования");
                    alert.setHeaderText("Не удалось зашифровать сообщение");
                    alert.setContentText("Ошибка: " + e.getMessage());
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

        ButtonType continueButton = new ButtonType("Продолжить в любом случае");
        ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);

        if (Platform.isFxApplicationThread()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Обнаружены конфиденциальные данные");
            alert.setHeaderText("⚠️ Внимание: Обнаружена конфиденциальная информация");
            alert.setContentText(scanResult.getWarningMessage());
            alert.getButtonTypes().setAll(continueButton, cancelButton);
            return alert.showAndWait().map(bt -> bt == continueButton).orElse(false);
        }

        java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Обнаружены конфиденциальные данные");
            alert.setHeaderText("⚠️ Внимание: Обнаружена конфиденциальная информация.");
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
     * Если pairwise ключ ещё не установлен — запускает асинхронную установку
     * и повторяет расшифровку после её завершения (обновляет UI через updateSingleMessage).
     */
    private Map<String, Object> decryptMessage(Map<String, Object> message) {
        Map<String, Object> decrypted = new HashMap<>(message);
        String ciphertext = (String) message.get("content");
        String room = (String) message.get("room");
        String sender = (String) message.get("username");
        String recipient = (String) message.get("recipient");

        if (ciphertext == null) {
            return decrypted;
        }

        if (!EncryptedMessage.isValidTransportFormat(ciphertext)) {
            return decrypted;
        }

        try {
            javax.crypto.SecretKey key;
            boolean isPrivate = recipient != null && !recipient.isEmpty();

            if (isPrivate) {
                // Определяем peer: для получателя это отправитель, для отправителя — получатель
                String peerUsername;
                if (currentUser.equals(recipient)) {
                    peerUsername = sender;
                } else if (currentUser.equals(sender)) {
                    peerUsername = recipient;
                } else {
                    decrypted.put("content", "[Не могу расшифровать: не участник приватного сообщения]");
                    return decrypted;
                }

                if (!KeyStore.hasPairwiseKey(peerUsername)) {
                    // Ключ ещё не готов — устанавливаем асинхронно через ECDH.
                    // ECDH симметричен: sharedSecret(A_priv, B_pub) == sharedSecret(B_priv, A_pub),
                    // поэтому получателю не нужно ждать никакого key_exchange от отправителя.
                    if (keyExchangeHandler != null) {
                        final Map<String, Object> originalMessage = message;
                        keyExchangeHandler.establishPairwiseKeyAsync(peerUsername, () -> {
                            Map<String, Object> retried = decryptMessage(originalMessage);
                            Platform.runLater(() -> chatView.updateSingleMessage(retried));
                        });
                    }
                    decrypted.put("content", "[Устанавливается ключ шифрования...]");
                    return decrypted;
                }
                key = KeyStore.getPairwiseKey(peerUsername);

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