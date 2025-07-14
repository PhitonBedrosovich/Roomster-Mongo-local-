package com.example.chat.frontend;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.util.Duration;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;
import java.util.prefs.Preferences;

public class ThemeToggle extends Button {
    private boolean isDarkTheme = false;
    private static final String PREF_KEY = "theme.dark";
    private static final Preferences prefs = Preferences.userNodeForPackage(ThemeToggle.class);

    public ThemeToggle() {
        // Восстанавливаем тему из Preferences
        isDarkTheme = prefs.getBoolean(PREF_KEY, false);
        createUI();
        // Применяем тему сразу после создания
        applyTheme(isDarkTheme ? "dark-theme" : "light-theme");
        setText(isDarkTheme ? "🌙" : "☼");
        if (isDarkTheme) {
            getStyleClass().remove("sun-theme");
        } else {
            if (!getStyleClass().contains("sun-theme")) getStyleClass().add("sun-theme");
        }
    }

    private void createUI() {
        setText("☼"); // Солнышко с лучами для светлой темы
        setTooltip(new Tooltip("Toggle Theme"));
        getStyleClass().add("theme-button");
        getStyleClass().add("sun-theme"); // по умолчанию солнце желтое
        setFont(Font.font(18));
        setPrefSize(40, 40);
        setStyle("-fx-padding: 0 0 0 2px;"); // небольшой отступ

        setOnAction(e -> toggleTheme());
    }

    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        prefs.putBoolean(PREF_KEY, isDarkTheme); // сохраняем выбор
        Node root = getScene().getRoot();
        StackPane stackPane = null;
        if (root instanceof StackPane) {
            stackPane = (StackPane) root;
        } else if (root.getParent() instanceof StackPane) {
            stackPane = (StackPane) root.getParent();
        }
        if (stackPane == null) {
            applyTheme(isDarkTheme ? "dark-theme" : "light-theme");
            setText(isDarkTheme ? "🌙" : "☼");
            if (isDarkTheme) {
                getStyleClass().remove("sun-theme");
            } else {
                if (!getStyleClass().contains("sun-theme")) getStyleClass().add("sun-theme");
            }
            return;
        }
        // Цвет новой темы
        String color = isDarkTheme ? "#212529" : "#f8f9fa";
        final Rectangle fadeRect = new Rectangle(stackPane.getWidth(), stackPane.getHeight());
        fadeRect.setFill(javafx.scene.paint.Color.web(color));
        fadeRect.setOpacity(0);
        fadeRect.setMouseTransparent(true);
        fadeRect.widthProperty().bind(stackPane.widthProperty());
        fadeRect.heightProperty().bind(stackPane.heightProperty());
        fadeRect.setViewOrder(-10000);
        stackPane.getChildren().add(fadeRect);
        final StackPane finalStackPane = stackPane;
        Timeline fadeIn = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(fadeRect.opacityProperty(), 0)),
            new KeyFrame(Duration.millis(400), new KeyValue(fadeRect.opacityProperty(), 1))
        );
        Timeline fadeOut = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(fadeRect.opacityProperty(), 1)),
            new KeyFrame(Duration.millis(400), new KeyValue(fadeRect.opacityProperty(), 0))
        );
        fadeIn.setOnFinished(e -> {
            applyTheme(isDarkTheme ? "dark-theme" : "light-theme");
            fadeOut.play();
        });
        fadeOut.setOnFinished(e -> finalStackPane.getChildren().remove(fadeRect));
        fadeIn.play();
        setText(isDarkTheme ? "🌙" : "☼");
        if (isDarkTheme) {
            getStyleClass().remove("sun-theme");
        } else {
            if (!getStyleClass().contains("sun-theme")) getStyleClass().add("sun-theme");
        }
    }

    private void applyTheme(String themeClass) {
        // Применяем тему к корневому элементу
        javafx.scene.Node root = getScene().getRoot();
        root.getStyleClass().removeAll("light-theme", "dark-theme");
        root.getStyleClass().add(themeClass);
    }

    public boolean isDarkTheme() {
        return isDarkTheme;
    }
} 