package com.example.chat.service;

import com.example.chat.model.User;
import com.example.chat.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private MongoTemplate mongoTemplate;

    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public void saveUser(User user) {
        userRepository.save(user);
    }
    
    public void deleteUserAndMessages(String username) {
        logger.info("Deleting user and all messages for: {}", username);
        
        // Удаляем пользователя
        User user = userRepository.findByUsername(username);
        if (user != null) {
            userRepository.delete(user);
            logger.debug("User deleted: {}", username);
        } else {
            logger.warn("User not found for deletion: {}", username);
        }
        
        // Удаляем все сообщения этого пользователя
        Query query = new Query(Criteria.where("username").is(username));
        long deletedMessages = mongoTemplate.remove(query, Message.class).getDeletedCount();
        logger.debug("Deleted {} messages from user: {}", deletedMessages, username);
        
        // Удаляем все приватные сообщения, адресованные этому пользователю
        Query recipientQuery = new Query(Criteria.where("recipient").is(username));
        long deletedPrivateMessages = mongoTemplate.remove(recipientQuery, Message.class).getDeletedCount();
        logger.debug("Deleted {} private messages to user: {}", deletedPrivateMessages, username);
        
        logger.info("User deletion completed for: {} ({} messages, {} private messages)", 
                   username, deletedMessages, deletedPrivateMessages);
    }

    public List<String> getAllUsernames() {
        return userRepository.findAll().stream()
                .map(User::getUsername)
                .toList();
    }
}

interface UserRepository extends MongoRepository<User, String> {
    User findByUsername(String username);
}