package com.example.chat.frontend;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

public class ProfileView {
    private final String username;
    private final Consumer<String> onBackToRooms;
    private VBox root;

    public ProfileView(String username, Consumer<String> onBackToRooms) {
        this.username = username;
        this.onBackToRooms = onBackToRooms;
        createUI();
    }

    private void createUI() {
        root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("card");

        // Заголовок
        Label titleLabel = new Label("User Profile");
        titleLabel.getStyleClass().addAll("title", "profile-title");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));

        // Аватар
        Circle avatar = new Circle(50);
        avatar.getStyleClass().add("profile-avatar");
        avatar.setStyle("-fx-fill: #007bff;");

        // Информация о пользователе
        VBox userInfo = new VBox(15);
        userInfo.setAlignment(Pos.CENTER);
        userInfo.setMaxWidth(400);

        Label usernameLabel = new Label(username);
        usernameLabel.getStyleClass().addAll("profile-label", "profile-username", "profile-username-underline");
        usernameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));

        Label statusLabel = new Label("Online");
        statusLabel.getStyleClass().addAll("profile-label", "profile-status");

        // Настройки
        VBox settingsContainer = new VBox(15);
        settingsContainer.setAlignment(Pos.CENTER);
        settingsContainer.setMaxWidth(400);

        Label settingsLabel = new Label("Settings");
        settingsLabel.getStyleClass().addAll("profile-label", "profile-title");
        settingsLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));

        // Переключатели настроек
        HBox notificationSetting = new HBox(15);
        notificationSetting.setAlignment(Pos.CENTER_LEFT);
        Label notificationLabel = new Label("Enable Notifications:");
        CheckBox notificationCheckBox = new CheckBox();
        notificationCheckBox.setSelected(com.example.chat.frontend.service.NotificationService.isEnableNotifications());
        // Связь с NotificationService
        notificationCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            com.example.chat.frontend.service.NotificationService.setEnableNotifications(newVal);
        });
        notificationSetting.getChildren().addAll(notificationLabel, notificationCheckBox);

        HBox soundSetting = new HBox(15);
        soundSetting.setAlignment(Pos.CENTER_LEFT);
        Label soundLabel = new Label("Sound Notifications:");
        CheckBox soundCheckBox = new CheckBox();
        soundCheckBox.setSelected(com.example.chat.frontend.service.NotificationService.isEnableSound());
        // Связь с NotificationService
        soundCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            com.example.chat.frontend.service.NotificationService.setEnableSound(newVal);
        });
        soundSetting.getChildren().addAll(soundLabel, soundCheckBox);

        HBox autoScrollSetting = new HBox(15);
        autoScrollSetting.setAlignment(Pos.CENTER_LEFT);
        Label autoScrollLabel = new Label("Auto-scroll to new messages:");
        CheckBox autoScrollCheckBox = new CheckBox();
        autoScrollCheckBox.setSelected(true);
        autoScrollSetting.getChildren().addAll(autoScrollLabel, autoScrollCheckBox);

        settingsContainer.getChildren().addAll(settingsLabel, notificationSetting, soundSetting, autoScrollSetting);

        // Кнопки
        HBox buttonContainer = new HBox(15);
        buttonContainer.setAlignment(Pos.CENTER);

        Button backButton = new Button("Back to Rooms");
        backButton.getStyleClass().add("profile-back-btn");
        backButton.setPrefWidth(150);
        backButton.setPrefHeight(40);

        Button changePasswordButton = new Button("Change Password");
        changePasswordButton.getStyleClass().add("profile-danger-btn");
        changePasswordButton.setPrefWidth(150);
        changePasswordButton.setPrefHeight(40);

        buttonContainer.getChildren().addAll(backButton, changePasswordButton);

        // Обработчики событий
        backButton.setOnAction(e -> onBackToRooms.accept(""));
        changePasswordButton.setOnAction(e -> showChangePasswordDialog());

        userInfo.getChildren().addAll(usernameLabel, statusLabel);
        root.getChildren().addAll(titleLabel, usernameLabel, avatar, userInfo, settingsContainer, buttonContainer);
    }

    private void showChangePasswordDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Enter new password");

        // Устанавливаем кнопки
        ButtonType changeButtonType = new ButtonType("Change", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(changeButtonType, ButtonType.CANCEL);

        // Создаем поля ввода
        PasswordField oldPasswordField = new PasswordField();
        oldPasswordField.setPromptText("Current Password");
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New Password");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm New Password");

        VBox content = new VBox(10);
        content.getChildren().addAll(
            new Label("Current Password:"), oldPasswordField,
            new Label("New Password:"), newPasswordField,
            new Label("Confirm Password:"), confirmPasswordField
        );
        dialog.getDialogPane().setContent(content);

        // Обработка результата
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == changeButtonType) {
                if (!newPasswordField.getText().equals(confirmPasswordField.getText())) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Passwords don't match");
                    alert.setContentText("Please make sure the new passwords match.");
                    alert.showAndWait();
                    return null;
                }
                if (oldPasswordField.getText().isEmpty() || newPasswordField.getText().isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Fields required");
                    alert.setContentText("Please fill in all fields.");
                    alert.showAndWait();
                    return null;
                }
                // Запрос на смену пароля
                com.example.chat.frontend.service.AuthService.changePassword(
                    username,
                    oldPasswordField.getText(),
                    newPasswordField.getText(),
                    () -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Success");
                        alert.setHeaderText("Password Changed");
                        alert.setContentText("Your password has been changed successfully.");
                        alert.showAndWait();
                    },
                    error -> {
                        String userMessage = error;
                        if (error != null) {
                            // Убираем техническую часть, если есть
                            if (userMessage.contains(":")) {
                                userMessage = userMessage.substring(userMessage.indexOf(":") + 1).trim();
                            }
                            if (userMessage.contains("Current password is incorrect")) {
                                userMessage = "Current password is incorrect.";
                            } else if (userMessage.contains("User not found")) {
                                userMessage = "User not found.";
                            } else if (userMessage.toLowerCase().contains("connect") || userMessage.toLowerCase().contains("server")) {
                                userMessage = "Server error. Please try again later.";
                            } else if (userMessage.length() > 100) {
                                userMessage = "Password change failed. Please try again.";
                            }
                        }
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText("Password Change Failed");
                        alert.setContentText(userMessage);
                        alert.showAndWait();
                    }
                );
                return "processing";
            }
            return null;
        });

        dialog.showAndWait();
    }

    public Node getNode(ChatClient app) {
        return root;
    }
} 