package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.entity.mongo.User;
import com.example.striptkillgamedemo2.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import org.bson.types.ObjectId;

import java.util.Collections;
import java.util.Optional;

/**
 * 用户服务：提供用户 CRUD 和 Spring Security UserDetailsService 实现
 */
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Spring Security 通过用户名加载用户详情
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    /**
     * 根据用户名查找用户实体
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 根据用户 ID 查找用户实体
     */
    public Optional<User> findById(ObjectId id) {
        return userRepository.findById(id);
    }

    /**
     * 检查用户名是否已存在
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * 保存用户（注册或更新）
     */
    public User save(User user) {
        return userRepository.save(user);
    }
}
