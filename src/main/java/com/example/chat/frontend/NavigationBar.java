package com.example.chat.frontend;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.Label;

public class NavigationBar extends BorderPane {
    private String currentUser;
    private String currentToken;
    private java.util.function.Consumer<String> onRoomSelected;
    
    public NavigationBar(NavigationController navigationController, Runnable goHomeAction, java.util.function.Consumer<String> onRoomSelected) {
        this.onRoomSelected = onRoomSelected;
        setPadding(new Insets(15, 20, 15, 20));
        getStyleClass().add("nav-bar");

        // Навигационные кнопки
        Button backButton = new Button("←");
        backButton.getStyleClass().addAll("secondary", "back-btn");
        backButton.setPrefWidth(50);
        backButton.setPrefHeight(35);
        backButton.setTooltip(new javafx.scene.control.Tooltip("Back"));

        Button forwardButton = new Button("→");
        forwardButton.getStyleClass().addAll("secondary", "forward-btn");
        forwardButton.setPrefWidth(50);
        forwardButton.setPrefHeight(35);
        forwardButton.setTooltip(new javafx.scene.control.Tooltip("Forward"));

        Button homeButton = new Button("🏠");
        homeButton.getStyleClass().addAll("secondary", "home-btn");
        homeButton.setPrefWidth(50);
        homeButton.setPrefHeight(35);
        homeButton.setTooltip(new javafx.scene.control.Tooltip("Home"));

        Button profileButton = new Button("👤");
        profileButton.getStyleClass().add("secondary");
        profileButton.getStyleClass().addAll("secondary", "profile-btn");
        profileButton.setPrefWidth(50);
        profileButton.setPrefHeight(35);
        profileButton.setTooltip(new javafx.scene.control.Tooltip("Profile"));

        backButton.setOnAction(e -> navigationController.goBack());
        forwardButton.setOnAction(e -> navigationController.goForward());
        homeButton.setOnAction(e -> goHomeAction.run());
        profileButton.setOnAction(e -> showProfile(navigationController));

        // Переключатель темы (левый верхний угол)
        ThemeToggle themeToggle = new ThemeToggle();
        themeToggle.getStyleClass().add("theme-toggle");

        // Логотип и название приложения
        Label logoLabel = new Label("\uD83C\uDFE0");
        logoLabel.getStyleClass().add("app-logo");
        Label appNameLabel = new Label("Roomster");
        appNameLabel.getStyleClass().add("app-title");
        HBox logoBox = new HBox(8, logoLabel, appNameLabel);
        logoBox.setAlignment(Pos.CENTER_LEFT);
        logoBox.getStyleClass().add("logo-box");

        // Центральная панель с навигационными кнопками
        HBox centerPanel = new HBox(10);
        centerPanel.setAlignment(Pos.CENTER);
        centerPanel.getStyleClass().add("center-panel");
        centerPanel.getChildren().addAll(backButton, forwardButton, homeButton);

        // Правая панель с кнопкой профиля
        HBox rightPanel = new HBox(10);
        rightPanel.setAlignment(Pos.CENTER_RIGHT);
        rightPanel.getStyleClass().add("right-panel");
        rightPanel.getChildren().add(profileButton);

        // Устанавливаем компоненты в BorderPane
        setLeft(themeToggle);
        setCenter(centerPanel);
        setRight(rightPanel);
        
        // Отладочная информация
    }

    private void showProfile(NavigationController navigationController) {
        if (currentUser != null) {
            ProfileView profileView = new ProfileView(currentUser, room -> {
                // Возвращаемся к списку комнат
                if (navigationController != null && currentToken != null && onRoomSelected != null) {
                    RoomListView roomListView = new RoomListView(onRoomSelected, currentToken);
                    navigationController.navigateTo(roomListView.getNode(null));
                }
            });
            navigationController.navigateTo(profileView.getNode(null));
        }
    }

    public void updateUser(String username) {
        this.currentUser = username;
    }

    public void updateToken(String token) {
        this.currentToken = token;
    }
} 