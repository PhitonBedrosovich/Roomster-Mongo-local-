package com.example.chat.frontend;

import com.example.chat.frontend.service.AuthService;
import com.example.chat.frontend.service.NotificationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import com.example.chat.frontend.PasswordToggleField;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.Consumer;

public class LoginView {
    private final Consumer<String> onLoginSuccess;
    private VBox root;
    private Label errorLabel;
    private Label successLabel;
    private Button loginButton;
    private Button registerButton;
    private ProgressIndicator loadingIndicator;

    public LoginView(Consumer<String> onLoginSuccess) {
        this.onLoginSuccess = onLoginSuccess;
        createUI();
    }

    private void createUI() {
        root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("card");

        Label welcomeLabel = new Label("Добро пожаловать в Roomster");
        welcomeLabel.getStyleClass().add("app-title");
        welcomeLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 32));

        VBox inputContainer = new VBox(15);
        inputContainer.setAlignment(Pos.CENTER);
        inputContainer.setMaxWidth(300);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Имя пользователя");
        usernameField.setPrefHeight(45);

        PasswordToggleField passwordField = new PasswordToggleField("Пароль");
        passwordField.setFieldHeight(45);

        HBox buttonContainer = new HBox(15);
        buttonContainer.setAlignment(Pos.CENTER);

        loginButton = new Button("Войти");
        loginButton.getStyleClass().addAll("primary", "success");
        loginButton.setPrefWidth(120);
        loginButton.setPrefHeight(40);

        registerButton = new Button("Зарегистрироваться");
        registerButton.getStyleClass().addAll("secondary");
        registerButton.setPrefWidth(120);
        registerButton.setPrefHeight(40);

        buttonContainer.getChildren().addAll(loginButton, registerButton);

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        loadingIndicator.setPrefSize(40, 40);

        loginButton.setOnAction(e -> handleLogin(usernameField.getText(), passwordField.getText()));
        registerButton.setOnAction(e -> handleRegister(usernameField.getText(), passwordField.getText()));

        usernameField.setOnAction(e -> handleLogin(usernameField.getText(), passwordField.getText()));
        passwordField.setOnAction(e -> handleLogin(usernameField.getText(), passwordField.getText()));

        errorLabel = new Label();
        errorLabel.getStyleClass().addAll("notification", "error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(300);

        successLabel = new Label();
        successLabel.getStyleClass().addAll("notification", "success");
        successLabel.setVisible(false);
        successLabel.setManaged(false);
        successLabel.setWrapText(true);
        successLabel.setMaxWidth(300);

        inputContainer.getChildren().addAll(usernameField, passwordField, buttonContainer, errorLabel, successLabel, loadingIndicator);
        root.getChildren().addAll(welcomeLabel, inputContainer);
    }

    public Node getNode(ChatClient app) {
        return root;
    }

    private void handleLogin(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            showError("Пожалуйста, введите имя пользователя и пароль");
            return;
        }

        setLoading(true);
        AuthService.login(username, password,
                token -> {
                    setLoading(false);
                    hideError();
                    NotificationService.showNotification("Успешный вход", "С возвращением!", NotificationService.NotificationType.SUCCESS);
                    onLoginSuccess.accept(token);
                },
                error -> {
                    setLoading(false);
                    String userMessage = error;
                    if (error != null) {
                        if (userMessage.contains(":")) {
                            userMessage = userMessage.substring(userMessage.indexOf(":") + 1).trim();
                        }
                        if (userMessage.toLowerCase().contains("invalid credentials")) {
                            userMessage = "Неверное имя пользователя или пароль.";
                        } else if (userMessage.toLowerCase().contains("user not found")) {
                            userMessage = "Пользователь не найден.";
                        } else if (userMessage.toLowerCase().contains("connect") || userMessage.toLowerCase().contains("server")) {
                            userMessage = "Ошибка сервера. Попробуйте позже.";
                        } else if (userMessage.length() > 100) {
                            userMessage = "Ошибка входа. Попробуйте снова.";
                        }
                    }
                    showError(userMessage);
                }
        );
    }

    private void handleRegister(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            showError("Пожалуйста, введите имя пользователя и пароль");
            return;
        }

        setLoading(true);
        AuthService.register(username, password,
                token -> {
                    setLoading(false);
                    hideError();
                    showSuccess("Регистрация прошла успешно! Добро пожаловать, " + username + "!");
                    NotificationService.showNotification("Успех", "Аккаунт успешно создан!", NotificationService.NotificationType.SUCCESS);
                    new Thread(() -> {
                        try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                        javafx.application.Platform.runLater(() -> onLoginSuccess.accept(token));
                    }).start();
                },
                error -> {
                    setLoading(false);
                    showError(friendlyRegisterError(error));
                }
        );
    }

    /** Переводит сырые ошибки сервера/сети в понятный пользователю текст */
    private String friendlyRegisterError(String raw) {
        if (raw == null) return "Не удалось зарегистрироваться. Попробуйте снова.";
        String lower = raw.toLowerCase();
        if (lower.contains("already") || lower.contains("exist")
                || lower.contains("duplicate") || lower.contains("409")
                || lower.contains("занят") || lower.contains("зарегистрирован")) {
            return "Пользователь с таким именем уже зарегистрирован. Попробуйте другое имя.";
        }
        if (lower.contains("connect") || lower.contains("подключени")
                || lower.contains("timeout") || lower.contains("refused")) {
            return "Нет связи с сервером. Проверьте интернет-соединение.";
        }
        if (lower.contains("password") || lower.contains("пароль")) {
            return "Пароль слишком короткий или не соответствует требованиям.";
        }
        if (lower.contains("username") || lower.contains("имя")) {
            return "Имя пользователя содержит недопустимые символы.";
        }
        if (lower.contains("runtimeexception") || lower.contains("exception")
                || lower.contains("java.") || lower.contains("at com.")) {
            return "Не удалось зарегистрироваться. Попробуйте снова.";
        }
        return raw.length() > 120 ? "Не удалось зарегистрироваться. Попробуйте снова." : raw;
    }

    private void showError(String message) {
        hideSuccess();
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void showSuccess(String message) {
        hideError();
        successLabel.setText(message);
        successLabel.setVisible(true);
        successLabel.setManaged(true);
    }

    private void hideSuccess() {
        successLabel.setVisible(false);
        successLabel.setManaged(false);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void setLoading(boolean loading) {
        loginButton.setDisable(loading);
        registerButton.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        if (loading) {
            loginButton.setText("Вход...");
            registerButton.setText("Регистрация...");
        } else {
            loginButton.setText("Войти");
            registerButton.setText("Регистрация");
        }
    }
}