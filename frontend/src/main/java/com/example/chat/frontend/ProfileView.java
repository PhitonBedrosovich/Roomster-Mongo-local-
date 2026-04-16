package com.example.chat.frontend;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Separator;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.util.function.Consumer;
import com.example.chat.frontend.PasswordToggleField;
import java.util.prefs.Preferences;

public class ProfileView {
    private final String username;
    private final Consumer<String> onBackToRooms;
    private VBox root;

    private static final String AVATAR_PREF_KEY = "avatar_path_";

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

        Label titleLabel = new Label("Профиль пользователя");
        titleLabel.getStyleClass().addAll("title", "profile-title");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));

        Label usernameLabel = new Label(username);
        usernameLabel.getStyleClass().addAll("profile-label", "profile-username", "profile-username-underline");
        usernameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));

        StackPane avatarPane = buildAvatarPane();

        Label statusLabel = new Label("В сети");
        statusLabel.getStyleClass().addAll("profile-label", "profile-status");

        VBox settingsContainer = new VBox(15);
        settingsContainer.setAlignment(Pos.CENTER);
        settingsContainer.setMaxWidth(400);

        Label settingsLabel = new Label("Настройки");
        settingsLabel.getStyleClass().addAll("profile-label", "profile-title");
        settingsLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));

        HBox notificationSetting = new HBox(15);
        notificationSetting.setAlignment(Pos.CENTER_LEFT);
        Label notificationLabel = new Label("Уведомления:");
        CheckBox notificationCheckBox = new CheckBox();
        notificationCheckBox.setSelected(com.example.chat.frontend.service.NotificationService.isEnableNotifications());
        notificationCheckBox.selectedProperty().addListener((obs, oldVal, newVal) ->
                com.example.chat.frontend.service.NotificationService.setEnableNotifications(newVal));
        notificationSetting.getChildren().addAll(notificationLabel, notificationCheckBox);

        HBox soundSetting = new HBox(15);
        soundSetting.setAlignment(Pos.CENTER_LEFT);
        Label soundLabel = new Label("Звук уведомлений:");
        CheckBox soundCheckBox = new CheckBox();
        soundCheckBox.setSelected(com.example.chat.frontend.service.NotificationService.isEnableSound());
        soundCheckBox.selectedProperty().addListener((obs, oldVal, newVal) ->
                com.example.chat.frontend.service.NotificationService.setEnableSound(newVal));
        soundSetting.getChildren().addAll(soundLabel, soundCheckBox);

        HBox autoScrollSetting = new HBox(15);
        autoScrollSetting.setAlignment(Pos.CENTER_LEFT);
        Label autoScrollLabel = new Label("Автопрокрутка к новым сообщениям:");
        CheckBox autoScrollCheckBox = new CheckBox();
        autoScrollCheckBox.setSelected(true);
        autoScrollSetting.getChildren().addAll(autoScrollLabel, autoScrollCheckBox);

        settingsContainer.getChildren().addAll(settingsLabel, notificationSetting, soundSetting, autoScrollSetting);

        HBox buttonContainer = new HBox(15);
        buttonContainer.setAlignment(Pos.CENTER);

        Button backButton = new Button("Назад к комнатам");
        backButton.getStyleClass().add("profile-back-btn");
        backButton.setPrefWidth(170);
        backButton.setPrefHeight(40);

        Button changePasswordButton = new Button("Сменить пароль");
        changePasswordButton.getStyleClass().add("profile-danger-btn");
        changePasswordButton.setPrefWidth(150);
        changePasswordButton.setPrefHeight(40);

        buttonContainer.getChildren().addAll(backButton, changePasswordButton);

        backButton.setOnAction(e -> onBackToRooms.accept(""));
        changePasswordButton.setOnAction(e -> showChangePasswordDialog());

        root.getChildren().addAll(titleLabel, usernameLabel, avatarPane, statusLabel, settingsContainer, buttonContainer);
    }

    private StackPane buildAvatarPane() {
        StackPane pane = new StackPane();
        pane.setAlignment(Pos.CENTER);
        pane.setMaxWidth(120);
        pane.setMaxHeight(120);

        Preferences prefs = Preferences.userRoot().node("roomster");
        String savedPath = prefs.get(AVATAR_PREF_KEY + username, null);

        Circle defaultCircle = new Circle(50);
        defaultCircle.getStyleClass().add("profile-avatar");
        defaultCircle.setStyle("-fx-fill: #007bff;");

        ImageView imageView = new ImageView();
        imageView.setFitWidth(100);
        imageView.setFitHeight(100);
        imageView.setPreserveRatio(false);
        Circle clip = new Circle(50, 50, 50);
        imageView.setClip(clip);
        imageView.setVisible(false);

        if (savedPath != null) {
            loadAvatarImage(imageView, defaultCircle, savedPath);
        }

        Button changeAvatarBtn = new Button("📷");
        changeAvatarBtn.setStyle(
                "-fx-background-color: rgba(0,0,0,0.55);" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;" +
                        "-fx-background-radius: 50%;" +
                        "-fx-min-width: 34px; -fx-min-height: 34px;" +
                        "-fx-max-width: 34px; -fx-max-height: 34px;" +
                        "-fx-cursor: hand; -fx-padding: 0;"
        );
        StackPane.setAlignment(changeAvatarBtn, Pos.BOTTOM_RIGHT);
        changeAvatarBtn.setTooltip(new Tooltip("Изменить фото профиля"));

        changeAvatarBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Выберите фото профиля");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
            );
            Stage stage = (Stage) root.getScene().getWindow();
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                String path = file.getAbsolutePath();
                prefs.put(AVATAR_PREF_KEY + username, path);
                loadAvatarImage(imageView, defaultCircle, path);
            }
        });

        pane.getChildren().addAll(defaultCircle, imageView, changeAvatarBtn);
        return pane;
    }

    private void loadAvatarImage(ImageView imageView, Circle defaultCircle, String path) {
        try {
            Image img = new Image(new FileInputStream(path), 100, 100, false, true);
            imageView.setImage(img);
            defaultCircle.setVisible(false);
            imageView.setVisible(true);
        } catch (Exception ex) {
            defaultCircle.setVisible(true);
            imageView.setVisible(false);
        }
    }

    private void showChangePasswordDialog() {
        // Создаём Stage вместо Dialog — как окно keystore, тема подхватывается
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Смена пароля");
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setResizable(false);

        // Заголовок
        Label titleLabel = new Label("🔑 Смена пароля");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Поля
        Label oldLabel = new Label("Текущий пароль:");
        PasswordToggleField oldPasswordField = new PasswordToggleField("Текущий пароль");
        oldPasswordField.setPrefWidth(280);

        Label newLabel = new Label("Новый пароль:");
        PasswordToggleField newPasswordField = new PasswordToggleField("Новый пароль");
        newPasswordField.setPrefWidth(280);

        Label confirmLabel = new Label("Подтвердите пароль:");
        PasswordToggleField confirmPasswordField = new PasswordToggleField("Подтвердите новый пароль");
        confirmPasswordField.setPrefWidth(280);

        // Метка ошибки
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e53935; -fx-font-size: 12px;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(280);

        // Кнопки
        Button changeBtn = new Button("Изменить");
        changeBtn.getStyleClass().addAll("button", "success");
        changeBtn.setPrefWidth(120);
        Button cancelBtn = new Button("Отмена");
        cancelBtn.getStyleClass().addAll("button", "secondary");
        cancelBtn.setPrefWidth(100);

        HBox btnBox = new HBox(12, changeBtn, cancelBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(10,
                titleLabel,
                new javafx.scene.control.Separator(),
                oldLabel, oldPasswordField,
                newLabel, newPasswordField,
                confirmLabel, confirmPasswordField,
                errorLabel,
                btnBox
        );
        content.setPadding(new Insets(20));
        content.setPrefWidth(320);

        javafx.scene.Scene scene = new javafx.scene.Scene(content);
        // Подключаем тот же CSS что и у главного окна
        if (root.getScene() != null) {
            scene.getStylesheets().addAll(root.getScene().getStylesheets());
            // Копируем тему (dark/light)
            String themeClass = root.getScene().getRoot().getStyleClass()
                    .stream().filter(c -> c.endsWith("-theme")).findFirst().orElse("light-theme");
            scene.getRoot().getStyleClass().add(themeClass);
        }
        stage.setScene(scene);

        cancelBtn.setOnAction(e -> stage.close());

        changeBtn.setOnAction(e -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);

            if (oldPasswordField.isEmpty() || newPasswordField.isEmpty() || confirmPasswordField.isEmpty()) {
                errorLabel.setText("Пожалуйста, заполните все поля.");
                errorLabel.setVisible(true); errorLabel.setManaged(true); return;
            }
            if (!newPasswordField.getText().equals(confirmPasswordField.getText())) {
                errorLabel.setText("Новые пароли не совпадают.");
                errorLabel.setVisible(true); errorLabel.setManaged(true); return;
            }

            changeBtn.setDisable(true);
            changeBtn.setText("Сохранение...");

            com.example.chat.frontend.service.AuthService.changePassword(
                    username,
                    oldPasswordField.getText(),
                    newPasswordField.getText(),
                    () -> javafx.application.Platform.runLater(() -> {
                        stage.close();
                        showAlert(Alert.AlertType.INFORMATION, "Успех", "Пароль изменён", "Ваш пароль успешно изменён.");
                    }),
                    error -> javafx.application.Platform.runLater(() -> {
                        changeBtn.setDisable(false);
                        changeBtn.setText("Изменить");
                        String msg = error;
                        if (msg != null) {
                            if (msg.contains(":")) msg = msg.substring(msg.indexOf(":") + 1).trim();
                            if (msg.contains("Current password is incorrect")) msg = "Неверный текущий пароль.";
                            else if (msg.contains("User not found")) msg = "Пользователь не найден.";
                            else if (msg.toLowerCase().contains("connect") || msg.toLowerCase().contains("server")) msg = "Ошибка сервера. Попробуйте позже.";
                            else if (msg.length() > 100) msg = "Не удалось изменить пароль. Попробуйте снова.";
                        }
                        errorLabel.setText(msg);
                        errorLabel.setVisible(true); errorLabel.setManaged(true);
                    })
            );
        });

        stage.showAndWait();
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public Node getNode(ChatClient app) {
        return root;
    }
}
