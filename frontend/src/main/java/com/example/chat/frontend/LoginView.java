package com.example.chat.frontend;

import com.example.chat.frontend.service.AuthService;
import com.example.chat.frontend.service.NotificationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.Consumer;

public class LoginView {
    private final Consumer<String> onLoginSuccess;
    private VBox root;
    private Label errorLabel;
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

        // Заголовок

        Label welcomeLabel = new Label("Welcome to Roomster");
        welcomeLabel.getStyleClass().add("app-title");
        welcomeLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 32));

        // Поля ввода
        VBox inputContainer = new VBox(15);
        inputContainer.setAlignment(Pos.CENTER);
        inputContainer.setMaxWidth(300);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setPrefHeight(45);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefHeight(45);

        // Кнопки
        HBox buttonContainer = new HBox(15);
        buttonContainer.setAlignment(Pos.CENTER);

        loginButton = new Button("Sign In");
        loginButton.getStyleClass().addAll("primary", "success");
        loginButton.setPrefWidth(120);
        loginButton.setPrefHeight(40);

        registerButton = new Button("Sign Up");
        registerButton.getStyleClass().addAll("secondary");
        registerButton.setPrefWidth(120);
        registerButton.setPrefHeight(40);

        buttonContainer.getChildren().addAll(loginButton, registerButton);

        // Индикатор загрузки
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        loadingIndicator.setPrefSize(40, 40);

        // Обработчики событий
        loginButton.setOnAction(e -> handleLogin(usernameField.getText(), passwordField.getText()));
        registerButton.setOnAction(e -> handleRegister(usernameField.getText(), passwordField.getText()));

        // Обработка Enter в полях
        usernameField.setOnAction(e -> handleLogin(usernameField.getText(), passwordField.getText()));
        passwordField.setOnAction(e -> handleLogin(usernameField.getText(), passwordField.getText()));

        // Метка ошибки
        errorLabel = new Label();
        errorLabel.getStyleClass().addAll("notification", "error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        inputContainer.getChildren().addAll(usernameField, passwordField, buttonContainer, errorLabel, loadingIndicator);
        root.getChildren().addAll(welcomeLabel, inputContainer);
    }

    public Node getNode(ChatClient app) {
        return root;
    }

    private void handleLogin(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password");
            return;
        }

        setLoading(true);
        AuthService.login(username, password, 
            token -> {
                setLoading(false);
                hideError();
                NotificationService.showNotification("Success", "Welcome back!", NotificationService.NotificationType.SUCCESS);
                onLoginSuccess.accept(token);
            },
            error -> {
                setLoading(false);
                String userMessage = error;
                if (error != null) {
                    // Убираем техническую часть, если есть
                    if (userMessage.contains(":")) {
                        userMessage = userMessage.substring(userMessage.indexOf(":") + 1).trim();
                    }
                    if (userMessage.toLowerCase().contains("invalid credentials")) {
                        userMessage = "Invalid username or password.";
                    } else if (userMessage.toLowerCase().contains("user not found")) {
                        userMessage = "User not found.";
                    } else if (userMessage.toLowerCase().contains("connect") || userMessage.toLowerCase().contains("server")) {
                        userMessage = "Server error. Please try again later.";
                    } else if (userMessage.length() > 100) {
                        userMessage = "Login failed. Please try again.";
                    }
                }
                showError(userMessage);
            }
        );
    }

    private void handleRegister(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password");
            return;
        }

        setLoading(true);
        AuthService.register(username, password,
            token -> {
                setLoading(false);
                hideError();
                NotificationService.showNotification("Success", "Account created successfully!", NotificationService.NotificationType.SUCCESS);
                onLoginSuccess.accept(token);
            },
            error -> {
                setLoading(false);
                showError(error);
            }
        );
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
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
            loginButton.setText("Signing In...");
            registerButton.setText("Signing Up...");
        } else {
            loginButton.setText("Sign In");
            registerButton.setText("Sign Up");
        }
    }
}