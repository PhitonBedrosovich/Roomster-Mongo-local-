package com.example.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication(scanBasePackages = {"com.example.chat"})
public class Application {
    public static void main(String[] args) {
        System.out.println("Starting Chat Application...");
        ApplicationContext context = SpringApplication.run(Application.class, args);
        System.out.println("Chat Application started successfully!");
        
        // Проверяем, какие бины загружены
        System.out.println("Loaded beans:");
        String[] beanNames = context.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            if (beanName.contains("Controller") || beanName.contains("auth")) {
                System.out.println("  - " + beanName);
            }
        }
    }
}