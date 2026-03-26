package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void userEntityShouldHaveRequiredFields() {
        User user = new User();
        user.setId(new ObjectId());
        user.setUsername("testuser");
        user.setPassword("hashedpassword");
        user.setNickname("Test User");
        user.setAvatarUrl("http://example.com/avatar.png");
        user.setCreatedAt(LocalDateTime.now());

        assertNotNull(user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("hashedpassword", user.getPassword());
        assertEquals("Test User", user.getNickname());
        assertEquals("http://example.com/avatar.png", user.getAvatarUrl());
        assertNotNull(user.getCreatedAt());
    }
}