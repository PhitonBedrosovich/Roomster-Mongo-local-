# Roomster Server — Быстрый старт через Docker

## Требования
- [Docker Desktop](https://www.docker.com/products/docker-desktop) (Windows/Mac/Linux)
- (опционально) [Git](https://git-scm.com/) для клонирования репозитория

## Как запустить

1. **Скачайте проект**  
   - Через Git:
     ```
     git clone <ссылка_на_ваш_репозиторий>
     cd <папка_проекта>
     ```
   - Или скачайте архив и распакуйте.

2. **Постройте и запустите все сервисы одной командой:**
   ```
   docker-compose up -d
   ```
   Это автоматически соберёт backend, поднимет MongoDB и Redis.

3. **Проверьте работу**
   - Сервер будет доступен на порту 8081:  
     http://localhost:8081

4. **Зарегистрируйте пользователя (пример через curl):**
   ```bash
   curl -X POST http://localhost:8081/api/auth/register \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"friend\",\"password\":\"anypassword\"}"
   ```
   Или зарегистрируйтесь через клиентское приложение.

5. **Остановить сервер:**
   ```
   docker-compose down
   ```

## Важно
- Все данные MongoDB и Redis сохраняются в volume Docker — при повторном запуске данные сохраняются.
- Для сброса данных используйте:
  ```
  docker-compose down -v
  ```

## Восстановление пользователей
- Если нужно восстановить пользователей или коллекции, используйте mongorestore (см. инструкции выше). 