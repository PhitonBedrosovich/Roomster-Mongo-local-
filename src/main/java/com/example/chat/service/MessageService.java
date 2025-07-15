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

@Service
public class MessageService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RedisTemplate<String, List<Message>> redisTemplate;

    public List<Message> getRoomMessages(String room, LocalDateTime registeredAt) {
        String key = "room:messages:" + room + ":" + registeredAt.toString();
        System.out.println("getRoomMessages called for room: " + room + ", registeredAt: " + registeredAt);
        List<Message> messages = null;
        try {
            messages = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            System.out.println("[WARN] Redis недоступен, кэширование отключено: " + e.getMessage());
        }
        if (messages == null) {
            System.out.println("Redis cache MISS for key: " + key);
            Query query = new Query(Criteria.where("room").is(room)
                    .and("createdAt").gte(registeredAt)).limit(50);
            messages = mongoTemplate.find(query, Message.class);
            System.out.println("Loaded from MongoDB, count: " + messages.size());
            try {
                redisTemplate.opsForValue().set(key, messages, CACHE_TTL);
            } catch (Exception e) {
                System.out.println("[WARN] Redis недоступен, не удалось записать кэш: " + e.getMessage());
            }
        } else {
            System.out.println("Redis cache HIT for key: " + key);
        }
        return messages;
    }

    public void invalidateRoomCache(String room, LocalDateTime registeredAt) {
        String key = "room:messages:" + room + ":" + registeredAt.toString();
        System.out.println("Redis cache INVALIDATE for key: " + key);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            System.out.println("[WARN] Redis недоступен, не удалось сбросить кэш: " + e.getMessage());
        }
    }
} 