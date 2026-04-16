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
        Label titleLabel = new Label("Комнаты чата");
        titleLabel.getStyleClass().add("title");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));

        Label subtitleLabel = new Label("Выберите комнату или создайте новую");
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

        joinButton = new Button("Войти в комнату");
        joinButton.getStyleClass().addAll("primary", "success");
        joinButton.setPrefWidth(120);
        joinButton.setPrefHeight(40);

        deleteButton = new Button("Удалить комнату");
        deleteButton.getStyleClass().addAll("danger");
        deleteButton.setPrefWidth(120);
        deleteButton.setPrefHeight(40);

        buttonContainer.getChildren().addAll(joinButton, deleteButton);

        // Создание новой комнаты
        VBox createContainer = new VBox(10);
        createContainer.setAlignment(Pos.CENTER);
        createContainer.setMaxWidth(400);

        Label createLabel = new Label("Создать новую комнату");
        createLabel.getStyleClass().add("subtitle");

        HBox inputContainer = new HBox(10);
        inputContainer.setAlignment(Pos.CENTER);

        newRoomField = new TextField();
        newRoomField.setPromptText("Название комнаты");
        newRoomField.setPrefHeight(40);
        newRoomField.setPrefWidth(200);

        createButton = new Button("Создать");
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
                showStatus("Не удалось загрузить комнаты: " + error, false);
                NotificationService.showNotification("Ошибка", "Не удалось загрузить комнаты", NotificationService.NotificationType.ERROR);
            }
        );
    }

    private void joinSelectedRoom() {
        String selected = roomList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            NotificationService.showNotification("Успех", "Вход в комнату: " + selected, NotificationService.NotificationType.INFO);
            onRoomSelected.accept(selected);
        } else {
            showStatus("Пожалуйста, выберите комнату", false);
        }
    }

    private void createNewRoom() {
        String roomName = newRoomField.getText().trim();
        if (roomName.isEmpty()) {
            showStatus("Введите название комнаты", false);
            return;
        }
        setLoading(true);
        RoomService.createRoom(roomName, token,
            success -> {
                setLoading(false);
                if (success) {
                    showStatus("Комната успешно создана!", true);
                    newRoomField.clear();
                    loadRooms();
                    NotificationService.showNotification("Успех", "Комната '" + roomName + "' создана!", NotificationService.NotificationType.SUCCESS);
                } else {
                    showStatus("Failed to create room", false);
                }
            },
            error -> {
                setLoading(false);
                showStatus("Ошибка: " + error, false);
                NotificationService.showNotification("Ошибка", "Ошибка сервера: " + error, NotificationService.NotificationType.ERROR);
            }
        );
    }

    private void deleteSelectedRoom() {
        String selected = roomList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("Выберите комнату для удаления", false);
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Удаление комнаты");
        alert.setHeaderText("Вы уверены?");
        alert.setContentText("Удалить комнату '" + selected + "'? Это действие необратимо.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                setLoading(true);
                RoomService.deleteRoom(selected, token,
                    success -> {
                        setLoading(false);
                        if (success) {
                            showStatus("Комната успешно удалена!", true);
                            loadRooms();
                            NotificationService.showNotification("Успех", "Комната '" + selected + "' удалена!", NotificationService.NotificationType.SUCCESS);
                        } else {
                            showStatus("Не удалось удалить комнату", false);
                        }
                    },
                    error -> {
                        setLoading(false);
                        showStatus("Ошибка: " + error, false);
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