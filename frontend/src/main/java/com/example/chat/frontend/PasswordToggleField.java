package com.example.chat.frontend;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

/**
 * Поле пароля с кнопкой-глазиком 👁 / 🙈.
 *
 * Работает через StackPane: оба поля (PasswordField + TextField) лежат
 * друг на друге с одинаковыми размерами, текст связан двусторонним bindом.
 * Переключение — через opacity + mouseTransparent (не setVisible/setManaged,
 * которые ломают layout внутри Dialog).
 *
 * Использование:
 *   PasswordToggleField f = new PasswordToggleField("Пароль");
 *   f.setPrefHeight(45);
 *   f.setPrefWidth(280);
 *   container.getChildren().add(f);   // сам является HBox-узлом
 *   String text = f.getText();
 *   char[] arr  = f.toCharArray();
 *   f.clear();
 *   f.textProperty().addListener(...);
 */
public class PasswordToggleField extends HBox {

    private final PasswordField passwordField = new PasswordField();
    private final TextField     visibleField  = new TextField();
    private final StringProperty sharedText   = new SimpleStringProperty("");
    private boolean showing = false;

    public PasswordToggleField(String promptText) {
        super(0);
        setAlignment(Pos.CENTER_LEFT);

        // --- настройка полей ---
        passwordField.setPromptText(promptText);
        visibleField .setPromptText(promptText);

        // Убираем собственные рамки — рамка будет у контейнера HBox
        String fieldStyle = "-fx-background-color: transparent;" +
                "-fx-border-color: transparent;" +
                "-fx-border-width: 0;" +
                "-fx-background-radius: 0;" +
                "-fx-background-insets: 0;";
        passwordField.setStyle(fieldStyle);
        visibleField .setStyle(fieldStyle);

        // --- двусторонний bind через sharedText ---
        // PasswordField -> sharedText
        passwordField.textProperty().addListener((obs, o, n) -> {
            if (!showing) sharedText.set(n);
        });
        // TextField -> sharedText
        visibleField.textProperty().addListener((obs, o, n) -> {
            if (showing) sharedText.set(n);
        });
        // sharedText -> оба поля
        sharedText.addListener((obs, o, n) -> {
            if (!passwordField.getText().equals(n)) passwordField.setText(n);
            if (!visibleField .getText().equals(n)) visibleField .setText(n);
        });

        // --- StackPane: оба поля друг на друге ---
        StackPane stack = new StackPane(passwordField, visibleField);
        HBox.setHgrow(stack, Priority.ALWAYS);
        HBox.setHgrow(passwordField, Priority.ALWAYS);
        HBox.setHgrow(visibleField,  Priority.ALWAYS);
        passwordField.setMaxWidth(Double.MAX_VALUE);
        visibleField .setMaxWidth(Double.MAX_VALUE);

        // visibleField изначально "невидим" через opacity
        visibleField.setOpacity(0);
        visibleField.setMouseTransparent(true);

        // --- кнопка глазик ---
        Button eyeBtn = new Button("👁");
        eyeBtn.getStyleClass().setAll("eye-button"); // сбрасываем .button, ставим свой класс
        eyeBtn.setFocusTraversable(false);

        eyeBtn.setOnAction(e -> {
            showing = !showing;
            if (showing) {
                // показать текст
                visibleField.setOpacity(1);
                visibleField.setMouseTransparent(false);
                passwordField.setOpacity(0);
                passwordField.setMouseTransparent(true);
                visibleField.requestFocus();
                visibleField.positionCaret(visibleField.getText().length());
                eyeBtn.setText("🙈");
            } else {
                // скрыть текст
                passwordField.setOpacity(1);
                passwordField.setMouseTransparent(false);
                visibleField.setOpacity(0);
                visibleField.setMouseTransparent(true);
                passwordField.requestFocus();
                passwordField.positionCaret(passwordField.getText().length());
                eyeBtn.setText("👁");
            }
        });

        // --- внешняя рамка через CSS-класс (тема подхватывается автоматически) ---
        getStyleClass().add("password-toggle-box");

        getChildren().addAll(stack, eyeBtn);
    }

    // ── API ──────────────────────────────────────────────────────────────────

    public String getText() { return sharedText.get(); }

    public char[] toCharArray() { return getText().toCharArray(); }

    public void clear() { sharedText.set(""); }

    public boolean isEmpty() { return getText().isEmpty(); }

    public StringProperty textProperty() { return sharedText; }

    /** Устанавливает высоту внутренних полей (не переопределяет final Region.setPrefHeight) */
    public void setFieldHeight(double h) {
        passwordField.setPrefHeight(h);
        visibleField .setPrefHeight(h);
    }

    // setPrefWidth наследуется от HBox и работает автоматически

    /** Совместимость с setOnAction (Enter в поле пароля) */
    public void setOnAction(javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        passwordField.setOnAction(handler);
        visibleField .setOnAction(handler);
    }
}
