package com.example.chat.frontend;

import javafx.animation.FadeTransition;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import java.util.Stack;

public class NavigationController extends StackPane {
    private final Stack<Node> backStack = new Stack<>();
    private final Stack<Node> forwardStack = new Stack<>();
    private Node currentScreen;

    public NavigationController() {
        setPrefSize(800, 600); // Можно изменить размер
    }

    public void navigateTo(Node screen) {
        if (currentScreen != null) {
            backStack.push(currentScreen);
            getChildren().remove(currentScreen);
        }
        forwardStack.clear();
        currentScreen = screen;
        getChildren().add(screen);
        playFadeIn(screen);
    }

    public void goBack() {
        if (!backStack.isEmpty()) {
            if (currentScreen != null) {
                forwardStack.push(currentScreen);
                getChildren().remove(currentScreen);
            }
            currentScreen = backStack.pop();
            getChildren().add(currentScreen);
            playFadeIn(currentScreen);
        }
    }

    public void goForward() {
        if (!forwardStack.isEmpty()) {
            if (currentScreen != null) {
                backStack.push(currentScreen);
                getChildren().remove(currentScreen);
            }
            currentScreen = forwardStack.pop();
            getChildren().add(currentScreen);
            playFadeIn(currentScreen);
        }
    }

    public void goHome(Node homeScreen) {
        if (currentScreen != null) {
            getChildren().remove(currentScreen);
        }
        backStack.clear();
        forwardStack.clear();
        currentScreen = homeScreen;
        getChildren().add(homeScreen);
        playFadeIn(homeScreen);
    }

    public void clearHistory() {
        backStack.clear();
        forwardStack.clear();
    }

    public void setRoot(Node screen) {
        backStack.clear();
        forwardStack.clear();
        getChildren().clear();
        currentScreen = screen;
        getChildren().add(screen);
        playFadeIn(screen);
    }

    private void playFadeIn(Node node) {
        FadeTransition ft = new FadeTransition(Duration.millis(350), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }
} 