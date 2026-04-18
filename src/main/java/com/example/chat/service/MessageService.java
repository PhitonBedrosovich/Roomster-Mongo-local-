package com.example.chat.service;

import com.example.chat.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class MessageService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RedisTemplate<String, List<Message>> redisTemplate;

    public List<Message> getRoomMessages(String room, LocalDateTime registeredAt, String username) {
        String key = "room:messages:" + room + ":" + registeredAt.toString() + ":" + username;
        List<Message> messages = null;
        try {
            messages = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            System.out.println("[WARN] Redis недоступен: " + e.getMessage());
        }
        if (messages == null) {
            // Публичные сообщения комнаты + приватные где пользователь отправитель или получатель
            Criteria criteria = new Criteria().andOperator(
                    Criteria.where("createdAt").gte(registeredAt),
                    new Criteria().orOperator(
                            // Публичные сообщения комнаты
                            Criteria.where("room").is(room).and("isPrivate").is(false),
                            // Приватные где я отправитель
                            Criteria.where("room").is(room).and("isPrivate").is(true).and("username").is(username),
                            // Приватные где я получатель
                            Criteria.where("room").is(room).and("isPrivate").is(true).and("recipient").is(username)
                    )
            );
            Query query = new Query(criteria).limit(50);
            messages = mongoTemplate.find(query, Message.class);
            try {
                redisTemplate.opsForValue().set(key, messages, CACHE_TTL);
            } catch (Exception e) {
                System.out.println("[WARN] Redis недоступен: " + e.getMessage());
            }
        }
        return messages;
    }

    public void invalidateRoomCache(String room) {
        // Удаляем все ключи для этой комнаты по паттерну
        String pattern = "room:messages:" + room + ":*";
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            System.out.println("[WARN] Redis недоступен, не удалось сбросить кэш: " + e.getMessage());
        }
    }
} 