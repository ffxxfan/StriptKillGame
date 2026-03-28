package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.dto.*;
import com.example.striptkillgamedemo2.entity.mongo.User;
import com.example.striptkillgamedemo2.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：封装登录、登出、注册、改密、刷新 Token 的业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户登录：验证用户名密码，生成并返回 Token 对
     *
     * @throws AuthenticationException 如果用户名或密码错误
     */
    public TokenResponse login(LoginRequest request) {
        // 使用 Spring Security AuthenticationManager 验证凭证
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // 查找用户实体获取 userId
        User user = userService.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String userId = user.getId().toHexString();
        String accessToken = jwtService.generateAccessToken(userId, user.getUsername());
        String refreshToken = jwtService.generateRefreshToken(userId);

        log.info("用户登录成功: {}", user.getUsername());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenExpiration())
                .build();
    }

    /**
     * 用户注册：创建新用户，密码使用 BCrypt 加密
     *
     * @throws IllegalArgumentException 如果用户名已存在
     */
    public UserInfoResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userService.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在: " + request.getUsername());
        }

        // 构建用户实体
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname() != null ? request.getNickname() : request.getUsername())
                .build();

        User savedUser = userService.save(user);
        log.info("用户注册成功: {}", savedUser.getUsername());

        return toUserInfoResponse(savedUser);
    }

    /**
     * 用户登出：将当前 Access Token 加入黑名单，删除 Refresh Token
     *
     * @param accessTokenClaims 当前请求的 Access Token Claims
     */
    public void logout(Claims accessTokenClaims) {
        String userId = accessTokenClaims.getSubject();
        // Access Token 加入黑名单
        jwtService.blacklistAccessToken(accessTokenClaims);
        // 删除 Redis 中的 Refresh Token
        jwtService.removeRefreshToken(userId);

        log.info("用户登出成功, userId={}", userId);
    }

    /**
     * 修改密码：验证旧密码，更新为新密码，使所有现有 Token 失效
     *
     * @param accessTokenClaims 当前请求的 Access Token Claims
     * @throws IllegalArgumentException 如果旧密码不正确
     */
    public void changePassword(Claims accessTokenClaims, ChangePasswordRequest request) {
        String userId = accessTokenClaims.getSubject();
        String username = accessTokenClaims.get("username", String.class);

        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("旧密码不正确");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userService.save(user);

        // 使所有现有 Token 失效
        jwtService.blacklistAccessToken(accessTokenClaims);
        jwtService.removeRefreshToken(userId);

        log.info("用户修改密码成功, username={}", username);
    }

    /**
     * 刷新 Token：验证 Refresh Token，生成新的 Token 对（Refresh Token 轮换）
     *
     * @throws IllegalArgumentException 如果 Refresh Token 无效
     */
    public TokenResponse refresh(RefreshTokenRequest request) {
        Claims claims = jwtService.validateRefreshToken(request.getRefreshToken());
        if (claims == null) {
            throw new IllegalArgumentException("Refresh Token 无效或已过期");
        }

        String userId = claims.getSubject();
        // 通过 userId 反查用户（Refresh Token 中不含 username）
        org.bson.types.ObjectId objectId = new org.bson.types.ObjectId(userId);
        User user = userService.findById(objectId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String accessToken = jwtService.generateAccessToken(userId, user.getUsername());
        // Refresh Token 轮换：生成新的 Refresh Token 替换旧的
        String refreshToken = jwtService.generateRefreshToken(userId);

        log.info("Token 刷新成功, userId={}", userId);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenExpiration())
                .build();
    }

    /**
     * 获取当前用户信息
     */
    public UserInfoResponse getUserInfo(Claims accessTokenClaims) {
        String username = accessTokenClaims.get("username", String.class);
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return toUserInfoResponse(user);
    }

    // ======================== 工具方法 ========================

    /**
     * 将 User 实体转为 UserInfoResponse DTO
     */
    private UserInfoResponse toUserInfoResponse(User user) {
        return UserInfoResponse.builder()
                .id(user.getId() != null ? user.getId().toHexString() : null)
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }

}
