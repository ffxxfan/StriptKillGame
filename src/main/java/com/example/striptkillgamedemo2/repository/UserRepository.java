package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.User;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * 用户 MongoDB 仓储接口。
 * <p>
 * 提供基于用户名的查询以及存在性校验，用于登录/注册流程。
 * </p>
 */
public interface UserRepository extends MongoRepository<User, ObjectId> {

    /**
     * 根据用户名查找用户。
     *
     * @param username 登录用户名
     * @return 匹配到的用户，若不存在则为 {@link Optional#empty()}
     */
    Optional<User> findByUsername(String username);

    /**
     * 判断用户名是否已被注册。
     *
     * @param username 待校验的用户名
     * @return {@code true} 表示用户名已存在
     */
    boolean existsByUsername(String username);
}
