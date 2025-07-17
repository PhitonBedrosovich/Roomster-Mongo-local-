# Java Chat Application

Веб-приложение для обмена сообщениями в реальном времени, построенное на Java с использованием Spring Boot и WebSocket.

## Функциональность

- Регистрация и авторизация пользователей
- Создание и присоединение к чат-комнатам
- Обмен сообщениями в реальном времени
- Просмотр истории сообщений
- Список активных пользователей в комнате
- **Фильтрация сообщений по времени регистрации пользователя**
- **Удаление пользователей с очисткой всех их сообщений**
- **Приватные сообщения между пользователями**

## Технологии

- **Backend**: Java, Spring Boot, WebSocket
- **Frontend**: JavaFX
- **База данных**: MongoDB
- **Кэш**: Redis (кэширование истории сообщений комнат, опционально)
- **Сборка**: Maven

## Структура проекта

```
frontend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/example/chat/frontend/
    │   │        ├── ChatClient.java
    │   │        ├── ChatView.java
    │   │        ├── LoginView.java
    │   │        ├── NavigationBar.java
    │   │        ├── NavigationController.java
    │   │        ├── ProfileView.java
    │   │        ├── RoomListView.java
    │   │        ├── ThemeToggle.java
    │   │        ├── TokenInfoView.java
    │   │        └── service/
    │   │             ├── AuthService.java
    │   │             ├── NotificationService.java
    │   │             └── RoomService.java
    │   └── resources/
    │        └── styles.css
src/
└── main/
    ├── java/
    │   └── com/example/chat/
    │        ├── Application.java
    │        ├── config/
    │        │    ├── JwtUtil.java
    │        │    └── SecurityConfig.java
    │        ├── controller/
    │        │    ├── AuthController.java
    │        │    └── RoomController.java
    │        ├── model/
    │        │    ├── Message.java
    │        │    └── User.java
    │        ├── service/
    │        │    └── UserService.java
    │        └── websocket/
    │             ├── ChatWebSocketServer.java
    │             └── WebSocketConfig.java
    └── resources/
         ├── application.properties
         └── logback-spring.xml
logs/
target/
update_users.js
pom.xml
README.md
```

## Запуск

1. Убедитесь, что у вас установлена Java 21+ и Maven
2. Установите и запустите MongoDB
3. Клонируйте репозиторий
4. Запустите сервер:
   ```bash
   mvn spring-boot:run
   ```
5. Запустите клиент:
   ```bash
   mvn javafx:run
   ```

## Порт

Сервер запускается на порту 8081 (HTTP) и 8082 (WebSocket).

## API Endpoints

- `GET /api/auth/test` - Тест API
- `POST /api/auth/register` - Регистрация пользователя
- `POST /api/auth/login` - Вход в систему
- `DELETE /api/auth/user/{username}` - Удаление пользователя и всех его сообщений

## Тестирование API

### Тест сервера
```bash
curl http://localhost:8081/api/auth/test
```

### Регистрация пользователя
```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"testuser\",\"password\":\"password123\"}"
```

### Вход в систему
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"testuser\",\"password\":\"password123\"}"
```

### Удаление пользователя
```bash
curl -X DELETE http://localhost:8081/api/auth/user/testuser
```

### Тестирование через JavaScript (в браузере F12 → Console)

```javascript
// Регистрация
fetch('http://localhost:8081/api/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({username: 'testuser', password: 'password123'})
}).then(r => r.text()).then(console.log);

// Вход
fetch('http://localhost:8081/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({username: 'testuser', password: 'password123'})
}).then(r => r.json()).then(console.log);

// Удаление пользователя
fetch('http://localhost:8081/api/auth/user/testuser', {
  method: 'DELETE'
}).then(r => r.text()).then(console.log);
```

## Изменения и дополнения (15.07.2025)

### ✅ Добавлена фильтрация сообщений по времени регистрации пользователя
- **Проблема**: Новые пользователи видели все старые сообщения, включая те, которые были отправлены до их регистрации
- **Решение**: 
  - Добавлено поле `registeredAt` в модель `User`
  - При подключении к комнате пользователь видит только сообщения, созданные после его регистрации
  - Обновлен `ChatWebSocketServer` для фильтрации истории сообщений

### ✅ Добавлено удаление пользователей с очисткой сообщений
- **Проблема**: При удалении пользователя его сообщения оставались в базе данных
- **Решение**:
  - Добавлен метод `deleteUserAndMessages()` в `UserService`
  - При удалении пользователя удаляются все его сообщения и приватные сообщения, адресованные ему
  - Добавлен REST API эндпоинт `DELETE /api/auth/user/{username}`

### ✅ Улучшена обработка ошибок и логирование
- **Проблема**: В коде использовались `System.out.println()` вместо proper logging
- **Решение**:
  - Заменены все `System.out.println()` на SLF4J + Logback
  - Добавлена конфигурация логирования в `logback-spring.xml`
  - Логи сохраняются в файлы с ротацией (10MB, 30 дней)
  - Разные уровни логирования для разных компонентов
  - Структурированные сообщения с контекстом

### ✅ Миграция базы данных
- Создан скрипт `update_users.js` для обновления существующих пользователей
- Все пользователи получили поле `registeredAt` с текущей датой

### 🔧 Технические улучшения
- Обновлена версия Java до 21
- Изменена база данных с H2 на MongoDB
- Добавлена поддержка JWT токенов для аутентификации
- Улучшено логирование для отслеживания фильтрации сообщений
- Добавлено кэширование истории сообщений комнат через Redis (Jackson JSON serialization)
- Поддержка масштабируемости backend через Redis

## Лицензия

MIT 

## Работа без Redis

Если Redis не установлен или не запущен, приложение продолжит работать в обычном режиме — история сообщений будет загружаться напрямую из MongoDB, а кэширование будет автоматически отключено. В консоли появятся предупреждения, но функциональность чата (регистрация, вход, просмотр истории, отправка сообщений) сохранится полностью **только при запуске одного экземпляра backend-сервера**.

> **Важно:**
> - Без Redis невозможно горизонтальное масштабирование real-time чата. Если вы запускаете несколько экземпляров backend-сервера (например, на разных машинах или портах), пользователи, подключённые к разным экземплярам, **не смогут обмениваться сообщениями друг с другом**.
> - Вход по логину/паролю и другие функции, связанные с базой данных, будут работать, но real-time обмен сообщениями между пользователями на разных серверах — **нет**.
> - Для полноценного real-time чата между всеми пользователями при масштабировании необходим Redis или другой брокер сообщений.

## Горизонтальное масштабирование WebSocket через Redis Pub/Sub

### Как это работает
- Каждый экземпляр backend-приложения запускает свой WebSocket-сервер.
- Все экземпляры подписаны на общий Redis Pub/Sub канал (`chat-messages`).
- Когда пользователь отправляет сообщение, оно публикуется в Redis.
- Redis рассылает сообщение всем экземплярам, и каждый экземпляр отправляет его своим локальным WebSocket-подключениям.
- Благодаря этому пользователи, подключённые к разным серверам, могут общаться друг с другом в реальном времени.

### Архитектура
```
[User A] --WS--> [Backend 1] --Pub--> [Redis] <--Sub-- [Backend 2] <--WS-- [User B]
```

### Как запустить в режиме масштабирования
1. Запустите Redis-сервер (порт по умолчанию 6379).
2. Запустите несколько экземпляров backend (Spring Boot) — можно на одной или разных машинах.
3. Запустите frontend (JavaFX).
4. Все экземпляры backend автоматически синхронизируют сообщения через Redis.

### Требования
- Redis должен быть доступен всем backend-инстансам.
- В `application.properties` должны быть корректно указаны параметры подключения к Redis:
  ```
  spring.redis.host=localhost
  spring.redis.port=6379
  ```

### Преимущества
- Масштабирование по горизонтали (добавляйте новые серверы без изменения кода).
- Сообщения мгновенно доставляются всем пользователям, независимо от того, к какому серверу они подключены. 