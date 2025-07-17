# Используем официальный образ OpenJDK для Java 21
FROM openjdk:21-jdk-slim

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем jar-файл приложения (замените имя jar при необходимости)
COPY target/Roomster-0.0.1-SNAPSHOT.jar app.jar

# Открываем порт 8081 для HTTP (и 8082 для WebSocket, если нужно)
EXPOSE 8081 8082

# Запускаем Spring Boot приложение
ENTRYPOINT ["java", "-jar", "app.jar"] 