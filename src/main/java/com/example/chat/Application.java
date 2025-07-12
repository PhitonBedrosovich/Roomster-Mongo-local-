package com.example.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication(scanBasePackages = {"com.example.chat"})
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    public static void main(String[] args) {
        logger.info("Starting Chat Application...");
        ApplicationContext context = SpringApplication.run(Application.class, args);
        logger.info("Chat Application started successfully!");
        
        // Проверяем, какие бины загружены
        logger.debug("Loaded beans:");
        String[] beanNames = context.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            if (beanName.contains("Controller") || beanName.contains("auth")) {
                logger.debug("  - {}", beanName);
            }
        }
    }
}