package com.example.chat.frontend;

import com.example.chat.frontend.service.RoomService;
import com.example.chat.frontend.service.NotificationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.function.Consumer;

public class RoomListView {
    private final Consumer<String> onRoomSelected;
    private final String token;
    private VBox root;
    private ListView<String> roomList;
    private Button joinButton;
    private Button createButton;
    private Button deleteButton;
    private TextField newRoomField;
    private Label statusLabel;
    private ProgressIndicator loadingIndicator;

    public RoomListView(Consumer<String> onRoomSelected, String token) {
        this.onRoomSelected = onRoomSelected;
        this.token = token;
        createUI();
        loadRooms();
    }

    private void createUI() {
        root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("card");

        // Заголовок
        Label titleLabel = new Label("Chat Rooms");
        titleLabel.getStyleClass().add("title");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));

        Label subtitleLabel = new Label("Select a room to join or create a new one");
        subtitleLabel.getStyleClass().add("subtitle");

        // Список комнат
        VBox roomContainer = new VBox(10);
        roomContainer.setAlignment(Pos.CENTER);
        roomContainer.setMaxWidth(400);

        roomList = new ListView<>();
        roomList.setPrefHeight(200);
        roomList.getStyleClass().add("room-list");
        // Добавляем слушатель выбора
        roomList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateButtonStates());

        // Кнопки управления
        HBox buttonContainer = new HBox(15);
        buttonContainer.setAlignment(Pos.CENTER);

        joinButton = new Button("Join Room");
        joinButton.getStyleClass().addAll("primary", "success");
        joinButton.setPrefWidth(120);
        joinButton.setPrefHeight(40);

        deleteButton = new Button("Delete Room");
        deleteButton.getStyleClass().addAll("danger");
        deleteButton.setPrefWidth(120);
        deleteButton.setPrefHeight(40);

        buttonContainer.getChildren().addAll(joinButton, deleteButton);

        // Создание новой комнаты
        VBox createContainer = new VBox(10);
        createContainer.setAlignment(Pos.CENTER);
        createContainer.setMaxWidth(400);

        Label createLabel = new Label("Create New Room");
        createLabel.getStyleClass().add("subtitle");

        HBox inputContainer = new HBox(10);
        inputContainer.setAlignment(Pos.CENTER);

        newRoomField = new TextField();
        newRoomField.setPromptText("Room name");
        newRoomField.setPrefHeight(40);
        newRoomField.setPrefWidth(200);

        createButton = new Button("Create");
        createButton.getStyleClass().addAll("success", "primary");
        createButton.setPrefWidth(100);
        createButton.setPrefHeight(40);

        inputContainer.getChildren().addAll(newRoomField, createButton);

        // Статус
        statusLabel = new Label();
        statusLabel.getStyleClass().add("notification");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        createContainer.getChildren().addAll(createLabel, inputContainer, statusLabel);

        // Индикатор загрузки
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        loadingIndicator.setPrefSize(40, 40);

        // Обработчики событий
        joinButton.setOnAction(e -> joinSelectedRoom());
        deleteButton.setOnAction(e -> deleteSelectedRoom());
        createButton.setOnAction(e -> createNewRoom());
        newRoomField.setOnAction(e -> createNewRoom());

        // Двойной клик для входа в комнату
        roomList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                joinSelectedRoom();
            }
        });

        roomContainer.getChildren().addAll(roomList, buttonContainer, loadingIndicator);
        root.getChildren().addAll(titleLabel, subtitleLabel, roomContainer, createContainer);
    }

    public Node getNode(ChatClient app) {
        return root;
    }

    private void loadRooms() {
        setLoading(true);
        RoomService.getRooms(token,
            rooms -> {
                setLoading(false);
                roomList.getItems().clear();
                roomList.getItems().addAll(rooms);
                updateButtonStates();
            },
            error -> {
                setLoading(false);
                showStatus("Failed to load rooms: " + error, false);
                NotificationService.showNotification("Error", "Failed to load rooms", NotificationService.NotificationType.ERROR);
            }
        );
    }

    private void joinSelectedRoom() {
        String selected = roomList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            NotificationService.showNotification("Success", "Joining room: " + selected, NotificationService.NotificationType.INFO);
            onRoomSelected.accept(selected);
        } else {
            showStatus("Please select a room first", false);
        }
    }

    private void createNewRoom() {
        String roomName = newRoomField.getText().trim();
        if (roomName.isEmpty()) {
            showStatus("Please enter a room name", false);
            return;
        }
        setLoading(true);
        RoomService.createRoom(roomName, token,
            success -> {
                setLoading(false);
                if (success) {
                    showStatus("Room created successfully!", true);
                    newRoomField.clear();
                    loadRooms();
                    NotificationService.showNotification("Success", "Room '" + roomName + "' created!", NotificationService.NotificationType.SUCCESS);
                } else {
                    showStatus("Failed to create room", false);
                }
            },
            error -> {
                setLoading(false);
                showStatus("Error: " + error, false);
                NotificationService.showNotification("Error", "Server error: " + error, NotificationService.NotificationType.ERROR);
            }
        );
    }

    private void deleteSelectedRoom() {
        String selected = roomList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("Please select a room to delete", false);
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Room");
        alert.setHeaderText("Are you sure?");
        alert.setContentText("Do you want to delete room '" + selected + "'? This action cannot be undone.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                setLoading(true);
                RoomService.deleteRoom(selected, token,
                    success -> {
                        setLoading(false);
                        if (success) {
                            showStatus("Room deleted successfully!", true);
                            loadRooms();
                            NotificationService.showNotification("Success", "Room '" + selected + "' deleted!", NotificationService.NotificationType.SUCCESS);
                        } else {
                            showStatus("Failed to delete room", false);
                        }
                    },
                    error -> {
                        setLoading(false);
                        showStatus("Error: " + error, false);
                    }
                );
            }
        });
    }

    private void showStatus(String message, boolean isSuccess) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().clear();
        statusLabel.getStyleClass().addAll("notification", isSuccess ? "success" : "error");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void setLoading(boolean loading) {
        joinButton.setDisable(loading);
        deleteButton.setDisable(loading);
        createButton.setDisable(loading);
        newRoomField.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }

    private void updateButtonStates() {
        boolean hasSelection = roomList.getSelectionModel().getSelectedItem() != null;
        joinButton.setDisable(!hasSelection);
        deleteButton.setDisable(!hasSelection);
    }
} 