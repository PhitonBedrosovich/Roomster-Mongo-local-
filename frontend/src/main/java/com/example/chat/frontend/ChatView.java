package com.example.chat.frontend;

import com.example.chat.frontend.crypto.KeyStore;
import com.example.chat.frontend.service.NotificationService;
import com.example.chat.frontend.service.RoomService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.FontPosture;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ChatView {
    private final BiConsumer<String, String> onSendMessage;
    private final ListView<MessageItem> messageList;
    private final ListView<UserItem> userList;
    private final String room;
    private final String currentUser;
    private final String token;
    private VBox root;
    private TextField messageField;
    private ComboBox<String> recipientCombo;
    private Button sendButton;
    private Label roomLabel;
    private Label userCountLabel;
    private Label e2eIndicator;

    // Форматтер для отображения времени в углу сообщения (ЧЧ:ММ)
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    // Форматтер для разбора timestamp с сервера
    private static final DateTimeFormatter[] PARSE_FMTS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
    };

    public ChatView(BiConsumer<String, String> onSendMessage, String room, String currentUser, String token) {
        this.onSendMessage = onSendMessage;
        this.messageList = new ListView<>();
        this.userList = new ListView<>();
        this.room = room;
        this.currentUser = currentUser;
        this.token = token;
        createUI();
        loadAllUsers();
        // Перерисовываем сообщения при смене темы
        messageList.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getRoot().getStyleClass().addListener(
                        (javafx.collections.ListChangeListener<String>) c -> messageList.refresh()
                );
            }
        });
    }

    private void createUI() {
        root = new VBox(15);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("chat-container");

        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        roomLabel = new Label("Комната: " + room);
        roomLabel.getStyleClass().add("title");
        roomLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));

        userCountLabel = new Label("0 онлайн");
        userCountLabel.getStyleClass().add("subtitle");

        e2eIndicator = new Label("🔒 E2E Шифрование");
        e2eIndicator.getStyleClass().add("e2e-indicator");
        e2eIndicator.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        e2eIndicator.setStyle("-fx-text-fill: #4caf50;");

        headerBox.getChildren().addAll(roomLabel, e2eIndicator, userCountLabel);

        BorderPane contentPane = new BorderPane();

        VBox messageContainer = new VBox(10);
        messageContainer.getStyleClass().add("message-container");

        Label messageLabel = new Label("Сообщения");
        messageLabel.getStyleClass().add("subtitle");

        messageList.setPrefHeight(400);
        messageList.setCellFactory(param -> new MessageListCell());

        messageContainer.getChildren().addAll(messageLabel, messageList);
        contentPane.setCenter(messageContainer);

        VBox userContainer = new VBox(10);
        userContainer.setPrefWidth(200);
        userContainer.getStyleClass().add("user-container");

        Label userLabel = new Label("Онлайн");
        userLabel.getStyleClass().add("subtitle");

        userList.setPrefHeight(400);
        userList.setCellFactory(param -> new UserListCell());

        userContainer.getChildren().addAll(userLabel, userList);
        contentPane.setRight(userContainer);

        VBox inputContainer = new VBox(10);
        inputContainer.getStyleClass().add("input-container");

        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);

        messageField = new TextField();
        messageField.setPromptText("Введите сообщение...");
        messageField.setPrefHeight(40);
        messageField.setPrefWidth(400);

        recipientCombo = new ComboBox<>();
        recipientCombo.setPromptText("Всем");
        recipientCombo.setPrefWidth(150);
        recipientCombo.setPrefHeight(40);

        sendButton = new Button("Отправить");
        sendButton.getStyleClass().addAll("success", "primary");
        sendButton.setPrefWidth(100);
        sendButton.setPrefHeight(40);

        inputBox.getChildren().addAll(messageField, recipientCombo, sendButton);
        inputContainer.getChildren().add(inputBox);

        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        userList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                UserItem selected = userList.getSelectionModel().getSelectedItem();
                if (selected != null && !selected.getUsername().equals(currentUser)) {
                    recipientCombo.setValue(selected.getUsername());
                    messageField.requestFocus();
                }
            }
        });

        root.getChildren().addAll(headerBox, contentPane, inputContainer);
    }

    public Node getNode(ChatClient app) { return root; }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;
        String recipient = recipientCombo.getValue();
        onSendMessage.accept(message, recipient);
        messageField.clear();
        recipientCombo.setValue(null);
    }

    public void updateMessages(List<Map<String, Object>> messages) {
        messageList.getItems().clear();
        String lastDate = null;
        for (Map<String, Object> msg : messages) {
            String recipient = (String) msg.get("recipient");
            String username  = (String) msg.get("username");
            boolean isPrivate = recipient != null && !recipient.isEmpty();
            if (!isPrivate || username.equals(currentUser) || recipient.equals(currentUser)) {
                String timestamp = (String) msg.get("timestamp");
                String dateStr = formatDateLabel(timestamp);
                if (dateStr != null && !dateStr.equals(lastDate)) {
                    messageList.getItems().add(new MessageItem(null, null, dateStr, false, false, null));
                    lastDate = dateStr;
                }
                messageList.getItems().add(createMessageItem(msg));
            }
        }
        scrollToBottom();
    }

    public void updateSingleMessage(Map<String, Object> updatedMsg) {
        String username   = (String) updatedMsg.get("username");
        String timestamp  = (String) updatedMsg.get("timestamp");
        String newContent = (String) updatedMsg.get("content");
        for (int i = 0; i < messageList.getItems().size(); i++) {
            MessageItem item = messageList.getItems().get(i);
            if (item.getUsername() != null && item.getUsername().equals(username)
                    && java.util.Objects.equals(item.getTimestamp(), timestamp)) {
                String recipient  = (String) updatedMsg.get("recipient");
                boolean isPrivate = recipient != null && !recipient.isEmpty();
                messageList.getItems().set(i, new MessageItem(
                        username, newContent, timestamp, item.isOwn(), isPrivate, recipient));
                break;
            }
        }
    }

    public void addMessage(Map<String, Object> msg) {
        String recipient = (String) msg.get("recipient");
        String username  = (String) msg.get("username");
        boolean isPrivate = recipient != null && !recipient.isEmpty();
        if (!isPrivate || username.equals(currentUser) || recipient.equals(currentUser)) {
            String timestamp = (String) msg.get("timestamp");
            String dateStr = formatDateLabel(timestamp);
            if (dateStr != null) {
                boolean needSeparator = true;
                for (int i = messageList.getItems().size() - 1; i >= 0; i--) {
                    MessageItem prev = messageList.getItems().get(i);
                    if (prev.getUsername() == null) {
                        if (dateStr.equals(prev.getTimestamp())) needSeparator = false;
                        break;
                    }
                    break;
                }
                if (needSeparator)
                    messageList.getItems().add(new MessageItem(null, null, dateStr, false, false, null));
            }
            messageList.getItems().add(createMessageItem(msg));
            scrollToBottom();
            if (!username.equals(currentUser)) {
                String content = (String) msg.get("content");
                String text = isPrivate ? "Личное сообщение от " + username : username + ": " + content;
                NotificationService.showNotification("Новое сообщение", text, NotificationService.NotificationType.INFO);
            }
        }
    }

    public void updateUsers(List<String> users) {
        userList.getItems().clear();
        for (String u : users)
            userList.getItems().add(new UserItem(u, u.equals(currentUser)));
        if (recipientCombo.getItems().isEmpty())
            recipientCombo.getItems().addAll(users.stream().filter(u -> !u.equals(currentUser)).toList());
        userCountLabel.setText(users.size() + " онлайн");
        updateE2EIndicator();
    }

    public void updateE2EIndicator() {
        if (e2eIndicator == null) return;
        boolean hasKey = KeyStore.hasRoomKey(room);
        if (hasKey) {
            e2eIndicator.setText("🔒 E2E Шифрование");
            e2eIndicator.setStyle("-fx-text-fill: #4caf50;");
        } else {
            e2eIndicator.setText("⚠️ Ожидание ключа...");
            e2eIndicator.setStyle("-fx-text-fill: #ff9800;");
        }
    }

    private void loadAllUsers() {
        RoomService.getAllUsersAsync(token).thenAccept(users -> {
            javafx.application.Platform.runLater(() -> {
                recipientCombo.getItems().clear();
                recipientCombo.getItems().addAll(users.stream().filter(u -> !u.equals(currentUser)).toList());
            });
        });
    }

    private MessageItem createMessageItem(Map<String, Object> msg) {
        String username  = (String) msg.get("username");
        String content   = (String) msg.get("content");
        String timestamp = (String) msg.get("timestamp");
        String recipient = (String) msg.get("recipient");
        boolean isOwn     = username.equals(currentUser);
        boolean isPrivate = recipient != null && !recipient.isEmpty();
        return new MessageItem(username, content, timestamp, isOwn, isPrivate, recipient);
    }

    private void scrollToBottom() {
        if (!messageList.getItems().isEmpty())
            messageList.scrollTo(messageList.getItems().size() - 1);
    }

    public String getRoom() { return room; }

    // ── Вспомогательные методы для форматирования времени ───────────────────

    /** Дата для разделителя: DD.MM.YYYY */
    private String formatDateLabel(String ts) {
        if (ts == null || ts.length() < 10) return null;
        try {
            String d = ts.substring(0, 10);
            String[] p = d.split("-");
            return p.length == 3 ? p[2] + "." + p[1] + "." + p[0] : d;
        } catch (Exception e) { return null; }
    }

    /** Время для отображения в углу сообщения: ЧЧ:ММ */
    static String formatTime(String ts) {
        if (ts == null || ts.length() < 16) return "";
        try {
            // Берём подстроку HH:mm напрямую из ISO-строки (быстро, без парсинга)
            String timePart = ts.substring(11, 16); // "HH:mm"
            return timePart;
        } catch (Exception e) { return ""; }
    }

    // ── Модели данных ────────────────────────────────────────────────────────

    public static class MessageItem {
        private final String username, content, timestamp, recipient;
        private final boolean isOwn, isPrivate;

        public MessageItem(String username, String content, String timestamp,
                           boolean isOwn, boolean isPrivate, String recipient) {
            this.username  = username;  this.content   = content;
            this.timestamp = timestamp; this.isOwn     = isOwn;
            this.isPrivate = isPrivate; this.recipient = recipient;
        }

        public String  getUsername()  { return username; }
        public String  getContent()   { return content; }
        public String  getTimestamp() { return timestamp; }
        public boolean isOwn()        { return isOwn; }
        public boolean isPrivate()    { return isPrivate; }
        public String  getRecipient() { return recipient; }
    }

    public static class UserItem {
        private final String username;
        private final boolean isCurrentUser;
        public UserItem(String username, boolean isCurrentUser) {
            this.username = username; this.isCurrentUser = isCurrentUser;
        }
        public String  getUsername()      { return username; }
        public boolean isCurrentUser()    { return isCurrentUser; }
    }

    // ── Ячейка сообщения ─────────────────────────────────────────────────────

    private static class MessageListCell extends ListCell<MessageItem> {
        @Override
        protected void updateItem(MessageItem item, boolean empty) {
            super.updateItem(item, empty);
            setStyle("");
            if (empty || item == null) {
                setText(null); setGraphic(null); getStyleClass().clear(); return;
            }

            boolean isDark = getScene() != null
                    && getScene().getRoot().getStyleClass().contains("dark-theme");

            // ── Разделитель даты — красивый бейдж по центру ─────────────
            if (item.getUsername() == null) {
                Label dateLbl = new Label("  " + item.getTimestamp() + "  ");
                dateLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
                String badgeBg   = isDark ? "rgba(255,255,255,0.08)" : "rgba(0,0,0,0.07)";
                String badgeFg   = isDark ? "#adb5bd" : "#6c757d";
                String badgeBdr  = isDark ? "#495057" : "#dee2e6";
                dateLbl.setStyle(
                        "-fx-text-fill: " + badgeFg + ";" +
                                "-fx-background-color: " + badgeBg + ";" +
                                "-fx-background-radius: 10px;" +
                                "-fx-border-color: " + badgeBdr + ";" +
                                "-fx-border-radius: 10px;" +
                                "-fx-border-width: 1px;" +
                                "-fx-padding: 2px 12px;"
                );
                // Линии по бокам от бейджа
                Region lineL = new Region();
                Region lineR = new Region();
                String lineStyle = "-fx-background-color: " + badgeBdr + "; -fx-pref-height: 1px; -fx-max-height: 1px;";
                lineL.setStyle(lineStyle); lineR.setStyle(lineStyle);
                HBox.setHgrow(lineL, Priority.ALWAYS);
                HBox.setHgrow(lineR, Priority.ALWAYS);

                HBox badge = new HBox(8, lineL, dateLbl, lineR);
                badge.setAlignment(Pos.CENTER);
                badge.setMaxWidth(Double.MAX_VALUE);
                badge.setPadding(new Insets(6, 16, 6, 16));

                getStyleClass().clear();
                setStyle("-fx-background-color: transparent;");
                setGraphic(badge); setText(null); return;
            }

            // ── Обычное сообщение ─────────────────────────────────────────
            // Цвета текста
            String colorUsername, colorContent, colorTime;
            if (isDark) {
                colorUsername = "#e9ecef";
                colorTime     = "#adb5bd";
                colorContent  = item.isOwn() ? "#ffffff"
                        : item.isPrivate() ? "#e0c3fc"
                          : "#51cf66";   // ← зелёный для чужих в тёмной теме
            } else {
                colorUsername = "#212529";
                colorTime     = "#6c757d";
                colorContent  = item.isOwn() ? "#212529"
                        : item.isPrivate() ? "#6f42c1"
                          : "#007bff";   // ← синий для чужих в светлой теме
            }

            // Имя пользователя
            Label usernameLabel = new Label(item.getUsername());
            usernameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            usernameLabel.setStyle("-fx-text-fill: " + colorUsername + ";");

            // Метка «приватное»
            Label privateLabel = null;
            if (item.isPrivate()) {
                privateLabel = new Label("🔒 Приватное");
                privateLabel.setStyle("-fx-text-fill: #e53935; -fx-font-size: 11px;");
            }

            // Текст сообщения
            Label contentLabel = new Label(item.getContent());
            contentLabel.setWrapText(true);
            contentLabel.setMaxWidth(Double.MAX_VALUE);
            contentLabel.setFont(Font.font("Segoe UI", FontPosture.ITALIC, 14));
            contentLabel.setStyle("-fx-text-fill: " + colorContent + ";");

            // Время — правый угол
            String timeStr = formatTime(item.getTimestamp());
            Label timeLabel = new Label(timeStr);
            timeLabel.setFont(Font.font("Segoe UI", 10));
            timeLabel.setStyle("-fx-text-fill: " + colorTime + ";");

            // Верхняя строка: имя [приватное] + spacer + время
            HBox headerBox = new HBox(6);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            if (item.isPrivate() && privateLabel != null) {
                headerBox.getChildren().addAll(usernameLabel, privateLabel, spacer, timeLabel);
            } else {
                headerBox.getChildren().addAll(usernameLabel, spacer, timeLabel);
            }

            VBox container = new VBox(3, headerBox, contentLabel);
            container.setPadding(new Insets(6, 10, 6, 10));

            getStyleClass().clear();
            getStyleClass().add("message-item");
            setGraphic(container);
            setText(null);
        }
    }

    // ── Ячейка пользователя ──────────────────────────────────────────────────

    private static class UserListCell extends ListCell<UserItem> {
        @Override
        protected void updateItem(UserItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null); setGraphic(null); getStyleClass().clear(); return;
            }

            boolean isDark = getScene() != null
                    && getScene().getRoot().getStyleClass().contains("dark-theme");

            Label lbl = new Label(item.isCurrentUser()
                    ? item.getUsername() + " (Вы)"
                    : item.getUsername());
            lbl.setFont(item.isCurrentUser()
                    ? Font.font("Segoe UI", FontWeight.BOLD, 12)
                    : Font.font("Segoe UI", 12));

            // Черный в темной теме (как просил), белый текст уже через CSS .user-item
            // Явно ставим цвет чтобы перебить наследование
            lbl.setStyle("-fx-text-fill: " + (isDark ? "#1a1a1a" : "#212529") + ";");

            HBox container = new HBox(10, lbl);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(5));

            getStyleClass().clear();
            getStyleClass().add("user-item");
            if (item.isCurrentUser()) getStyleClass().add("online");
            setGraphic(container);
            setText(null);
        }
    }
}
