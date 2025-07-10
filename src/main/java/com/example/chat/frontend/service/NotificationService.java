package com.example.chat.frontend.service;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.FadeTransition;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.geometry.Insets;

import java.awt.*;
import java.awt.TrayIcon.MessageType;

public class NotificationService {
    private static boolean systemTraySupported = false;
    private static TrayIcon trayIcon = null;

    // Глобальные настройки
    private static boolean enableNotifications = true;
    private static boolean enableSound = true;

    public static void setEnableNotifications(boolean enabled) {
        enableNotifications = enabled;
    }
    public static boolean isEnableNotifications() {
        return enableNotifications;
    }
    public static void setEnableSound(boolean enabled) {
        enableSound = enabled;
    }
    public static boolean isEnableSound() {
        return enableSound;
    }
    
    static {
        if (SystemTray.isSupported()) {
            systemTraySupported = true;
            try {
                SystemTray tray = SystemTray.getSystemTray();
                Image image = Toolkit.getDefaultToolkit().createImage("icon.png");
                trayIcon = new TrayIcon(image, "Chat App");
                trayIcon.setImageAutoSize(true);
                tray.add(trayIcon);
            } catch (Exception e) {
                systemTraySupported = false;
            }
        }
    }

    public static void showNotification(String title, String message, NotificationType type) {
        Platform.runLater(() -> {
            if (!enableNotifications) return;
            // Визуальное уведомление
            showVisualNotification(title, message, type);
            
            // Системное уведомление
            if (systemTraySupported && trayIcon != null) {
                trayIcon.displayMessage(title, message, MessageType.INFO);
            }
            // Звук уведомления
            if (enableSound) {
                playNotificationSound();
            }
        });
    }

    private static void showVisualNotification(String title, String message, NotificationType type) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        VBox content = new VBox(8);
        content.setPadding(new Insets(16));
        content.getStyleClass().addAll("notification", type.getCssClass());

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("title");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(300);

        content.getChildren().addAll(titleLabel, messageLabel);
        popup.getContent().add(content);

        // Показываем уведомление в правом верхнем углу
        Stage primaryStage = getPrimaryStage();
        if (primaryStage != null) {
            popup.show(primaryStage, 
                primaryStage.getX() + primaryStage.getWidth() - 320, 
                primaryStage.getY() + 20);
        }

        // Автоматически скрываем через 5 секунд
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(5), content);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> popup.hide());
        fadeOut.play();
    }

    private static void playNotificationSound() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception ignored) {}
    }

    private static Stage getPrimaryStage() {
        return javafx.stage.Window.getWindows().stream()
                .filter(window -> window instanceof Stage)
                .map(window -> (Stage) window)
                .filter(stage -> stage.getOwner() == null) // Primary stage не имеет owner
                .findFirst()
                .orElse(null);
    }

    public enum NotificationType {
        INFO("notification"),
        SUCCESS("notification"),
        WARNING("notification warning"),
        ERROR("notification error");

        private final String cssClass;

        NotificationType(String cssClass) {
            this.cssClass = cssClass;
        }

        public String getCssClass() {
            return cssClass;
        }
    }
} 