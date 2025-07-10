package com.example.chat.service;

import com.example.chat.model.User;
import com.example.chat.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {
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
        // Удаляем пользователя
        User user = userRepository.findByUsername(username);
        if (user != null) {
            userRepository.delete(user);
        }
        
        // Удаляем все сообщения этого пользователя
        Query query = new Query(Criteria.where("username").is(username));
        mongoTemplate.remove(query, Message.class);
        
        // Удаляем все приватные сообщения, адресованные этому пользователю
        Query recipientQuery = new Query(Criteria.where("recipient").is(username));
        mongoTemplate.remove(recipientQuery, Message.class);
    }
}

interface UserRepository extends MongoRepository<User, String> {
    User findByUsername(String username);
}