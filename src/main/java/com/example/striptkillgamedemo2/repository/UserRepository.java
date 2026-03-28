package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.User;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * 用户 MongoDB 仓储接口
 */
public interface UserRepository extends MongoRepository<User, ObjectId> {

    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 检查用户名是否已存在
     */
    boolean existsByUsername(String username);
}
