package com.example.chat.frontend;

import com.example.chat.frontend.crypto.KeyStore;
import com.example.chat.frontend.service.NotificationService;
import com.example.chat.frontend.service.RoomService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.FontPosture;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.concurrent.CompletableFuture;

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
    private Label e2eIndicator; // Индикатор E2E защиты

    public ChatView(BiConsumer<String, String> onSendMessage, String room, String currentUser, String token) {
        this.onSendMessage = onSendMessage;
        this.messageList = new ListView<>();
        this.userList = new ListView<>();
        this.room = room;
        this.currentUser = currentUser;
        this.token = token;
        createUI();
        loadAllUsers();
        // --- Добавлено: слушатель на смену темы ---
        messageList.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getRoot().getStyleClass().addListener((javafx.collections.ListChangeListener<String>) c -> {
                    messageList.refresh();
                });
            }
        });
    }

    private void createUI() {
        root = new VBox(15);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("chat-container");

        // Заголовок комнаты
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        roomLabel = new Label("Room: " + room);
        roomLabel.getStyleClass().add("title");
        roomLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        
        userCountLabel = new Label("0 users online");
        userCountLabel.getStyleClass().add("subtitle");
        
        // Индикатор E2E защиты
        e2eIndicator = new Label("🔒 E2E Encrypted");
        e2eIndicator.getStyleClass().add("e2e-indicator");
        e2eIndicator.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        e2eIndicator.setStyle("-fx-text-fill: #4caf50;"); // Зеленый цвет для индикации безопасности
        
        headerBox.getChildren().addAll(roomLabel, e2eIndicator, userCountLabel);

        // Основной контент
        BorderPane contentPane = new BorderPane();
        
        // Список сообщений (центр)
        VBox messageContainer = new VBox(10);
        messageContainer.getStyleClass().add("message-container");
        
        Label messageLabel = new Label("Messages");
        messageLabel.getStyleClass().add("subtitle");
        
        messageList.setPrefHeight(400);
        messageList.setCellFactory(param -> new MessageListCell());
        
        messageContainer.getChildren().addAll(messageLabel, messageList);
        contentPane.setCenter(messageContainer);

        // Список пользователей (право)
        VBox userContainer = new VBox(10);
        userContainer.setPrefWidth(200);
        userContainer.getStyleClass().add("user-container");
        
        Label userLabel = new Label("Online Users");
        userLabel.getStyleClass().add("subtitle");
        
        userList.setPrefHeight(400);
        userList.setCellFactory(param -> new UserListCell());
        
        userContainer.getChildren().addAll(userLabel, userList);
        contentPane.setRight(userContainer);

        // Поле ввода сообщений
        VBox inputContainer = new VBox(10);
        inputContainer.getStyleClass().add("input-container");
        
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        
        messageField = new TextField();
        messageField.setPromptText("Type a message...");
        messageField.setPrefHeight(40);
        messageField.setPrefWidth(400);
        
        recipientCombo = new ComboBox<>();
        recipientCombo.setPromptText("Send to all");
        recipientCombo.setPrefWidth(150);
        recipientCombo.setPrefHeight(40);
        
        sendButton = new Button("Send");
        sendButton.getStyleClass().addAll("success", "primary");
        sendButton.setPrefWidth(100);
        sendButton.setPrefHeight(40);
        
        inputBox.getChildren().addAll(messageField, recipientCombo, sendButton);
        inputContainer.getChildren().add(inputBox);

        // Обработчики событий
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());
        
        // Двойной клик на пользователя для приватного сообщения
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

    public Node getNode(ChatClient app) {
        return root;
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        String recipient = recipientCombo.getValue();
        onSendMessage.accept(message, recipient);
        messageField.clear();
        recipientCombo.setValue(null);
    }

    public void updateMessages(List<Map<String, Object>> messages) {
        messageList.getItems().clear();
        for (Map<String, Object> msg : messages) {
            String recipient = (String) msg.get("recipient");
            String username = (String) msg.get("username");
            boolean isPrivate = recipient != null && !recipient.isEmpty();
            // Приватные сообщения видит только отправитель и получатель
            if (!isPrivate || username.equals(currentUser) || recipient.equals(currentUser)) {
                MessageItem messageItem = createMessageItem(msg);
                messageList.getItems().add(messageItem);
            }
        }
        scrollToBottom();
    }

    public void addMessage(Map<String, Object> msg) {
        String recipient = (String) msg.get("recipient");
        String username = (String) msg.get("username");
        boolean isPrivate = recipient != null && !recipient.isEmpty();
        if (!isPrivate || username.equals(currentUser) || recipient.equals(currentUser)) {
            MessageItem messageItem = createMessageItem(msg);
            messageList.getItems().add(messageItem);
            scrollToBottom();
            // Уведомление о новом сообщении (если не от текущего пользователя)
            if (!username.equals(currentUser)) {
                String content = (String) msg.get("content");
                String notificationText = msg.containsKey("recipient") ? 
                    "Private message from " + username : 
                    username + ": " + content;
                NotificationService.showNotification("New Message", notificationText, NotificationService.NotificationType.INFO);
            }
        }
    }

    public void updateUsers(List<String> users) {
        userList.getItems().clear();
        for (String username : users) {
            UserItem userItem = new UserItem(username, username.equals(currentUser));
            userList.getItems().add(userItem);
        }
        // Обновляем комбобокс получателей только если он пуст (чтобы не затирать полный список)
        if (recipientCombo.getItems().isEmpty()) {
            recipientCombo.getItems().addAll(users.stream().filter(user -> !user.equals(currentUser)).toList());
        }
        userCountLabel.setText(users.size() + " users online");
        
        // Обновляем индикатор E2E в зависимости от наличия ключа
        updateE2EIndicator();
    }
    
    /**
     * Обновляет индикатор E2E защиты в зависимости от наличия ключа комнаты.
     */
    private void updateE2EIndicator() {
        if (e2eIndicator == null) return;
        
        boolean hasKey = KeyStore.hasRoomKey(room);
        if (hasKey) {
            e2eIndicator.setText("🔒 E2E Encrypted");
            e2eIndicator.setStyle("-fx-text-fill: #4caf50;"); // Зеленый
        } else {
            e2eIndicator.setText("⚠️ Waiting for key...");
            e2eIndicator.setStyle("-fx-text-fill: #ff9800;"); // Оранжевый
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
        String username = (String) msg.get("username");
        String content = (String) msg.get("content");
        String timestamp = (String) msg.get("timestamp");
        String recipient = (String) msg.get("recipient");
        
        boolean isOwn = username.equals(currentUser);
        boolean isPrivate = recipient != null && !recipient.isEmpty();
        
        return new MessageItem(username, content, timestamp, isOwn, isPrivate, recipient);
    }

    private void scrollToBottom() {
        if (!messageList.getItems().isEmpty()) {
            messageList.scrollTo(messageList.getItems().size() - 1);
        }
    }

    public String getRoom() {
        return room;
    }

    // Внутренние классы для отображения сообщений и пользователей
    public static class MessageItem {
        private final String username;
        private final String content;
        private final String timestamp;
        private final boolean isOwn;
        private final boolean isPrivate;
        private final String recipient;

        public MessageItem(String username, String content, String timestamp, boolean isOwn, boolean isPrivate, String recipient) {
            this.username = username;
            this.content = content;
            this.timestamp = timestamp;
            this.isOwn = isOwn;
            this.isPrivate = isPrivate;
            this.recipient = recipient;
        }

        public String getUsername() { return username; }
        public String getContent() { return content; }
        public String getTimestamp() { return timestamp; }
        public boolean isOwn() { return isOwn; }
        public boolean isPrivate() { return isPrivate; }
        public String getRecipient() { return recipient; }
    }

    public static class UserItem {
        private final String username;
        private final boolean isCurrentUser;

        public UserItem(String username, boolean isCurrentUser) {
            this.username = username;
            this.isCurrentUser = isCurrentUser;
        }

        public String getUsername() { return username; }
        public boolean isCurrentUser() { return isCurrentUser; }
    }

    private static class MessageListCell extends ListCell<MessageItem> {
        @Override
        protected void updateItem(MessageItem item, boolean empty) {
            super.updateItem(item, empty);
            setTextFill(null);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                getStyleClass().clear();
            } else {
                VBox container = new VBox(5);
                container.setPadding(new Insets(8));

                HBox headerBox = new HBox(10);
                headerBox.setAlignment(Pos.CENTER_LEFT);

                Label usernameLabel = new Label(item.getUsername());
                usernameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                usernameLabel.setStyle("");

                Label timeLabel = new Label(item.getTimestamp());
                timeLabel.setFont(Font.font("Segoe UI", 10));
                timeLabel.getStyleClass().add("subtitle");
                timeLabel.setStyle("");

                Label contentLabel = new Label(item.getContent());
                contentLabel.setWrapText(true);
                contentLabel.setMaxWidth(400);
                contentLabel.setFont(Font.font("Segoe UI", FontPosture.ITALIC, 15));
                contentLabel.setStyle("");

                Label privateLabel = null;
                if (item.isPrivate()) {
                    privateLabel = new Label("🔒 Private");
                    privateLabel.getStyleClass().add("warning");
                    privateLabel.setStyle("-fx-text-fill: #e53935;");
                }

                String theme = "light";
                if (getScene() != null && getScene().getRoot().getStyleClass().contains("dark-theme")) {
                    theme = "dark";
                }
                if (theme.equals("dark")) {
                    if (item.isOwn()) {
                        contentLabel.setTextFill(javafx.scene.paint.Color.web("#fff"));
                        usernameLabel.setTextFill(javafx.scene.paint.Color.web("#e9ecef"));
                        timeLabel.setTextFill(javafx.scene.paint.Color.web("#51cf66"));
                    } else {
                        contentLabel.setTextFill(javafx.scene.paint.Color.web("#51cf66"));
                        usernameLabel.setTextFill(javafx.scene.paint.Color.web("#e9ecef"));
                        timeLabel.setTextFill(javafx.scene.paint.Color.web("#51cf66"));
                    }
                } else {
                    if (item.isOwn()) {
                        contentLabel.setTextFill(javafx.scene.paint.Color.web("#000"));
                        usernameLabel.setTextFill(javafx.scene.paint.Color.web("#212529"));
                        timeLabel.setTextFill(javafx.scene.paint.Color.web("#007bff"));
                    } else {
                        contentLabel.setTextFill(javafx.scene.paint.Color.web("#007bff"));
                        usernameLabel.setTextFill(javafx.scene.paint.Color.web("#212529"));
                        timeLabel.setTextFill(javafx.scene.paint.Color.web("#007bff"));
                    }
                }
                if (privateLabel != null) privateLabel.setTextFill(javafx.scene.paint.Color.web("#e53935"));

                if (item.isPrivate() && privateLabel != null) {
                    headerBox.getChildren().addAll(usernameLabel, privateLabel, timeLabel);
                } else {
                    headerBox.getChildren().addAll(usernameLabel, timeLabel);
                }
                container.getChildren().addAll(headerBox, contentLabel);
                getStyleClass().clear();
                getStyleClass().add("message-item");
                setGraphic(container);
                setText(null);
            }
        }
    }

    private static class UserListCell extends ListCell<UserItem> {
        @Override
        protected void updateItem(UserItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                getStyleClass().clear();
            } else {
                HBox container = new HBox(10);
                container.setAlignment(Pos.CENTER_LEFT);
                container.setPadding(new Insets(5));
                
                Label usernameLabel = new Label(item.getUsername());
                if (item.isCurrentUser()) {
                    usernameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                    usernameLabel.setText(usernameLabel.getText() + " (You)");
                }
                
                container.getChildren().add(usernameLabel);
                
                getStyleClass().clear();
                getStyleClass().add("user-item");
                if (item.isCurrentUser()) {
                    getStyleClass().add("online");
                }
                
                setGraphic(container);
                setText(null);
            }
        }
    }
}