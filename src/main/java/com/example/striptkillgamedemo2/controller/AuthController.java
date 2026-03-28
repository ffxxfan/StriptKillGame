package com.example.striptkillgamedemo2.controller;

import com.example.striptkillgamedemo2.dto.*;
import com.example.striptkillgamedemo2.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器：处理登录、注册、登出、改密、刷新 Token、获取用户信息
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/login - 用户登录
     * 验证用户名密码，返回 Access Token + Refresh Token
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            TokenResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            log.warn("登录失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", 401, "message", "用户名或密码错误"));
        }
    }

    /**
     * POST /api/auth/register - 用户注册
     * 创建新用户，密码 BCrypt 加密存储
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            UserInfoResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("注册失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", 409, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/auth/logout - 用户登出
     * 将当前 Access Token 加入黑名单，删除 Refresh Token
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        authService.logout(claims);
        return ResponseEntity.ok(Map.of("message", "登出成功"));
    }

    /**
     * PUT /api/auth/password - 修改密码
     * 验证旧密码后更新，使所有现有 Token 失效
     */
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(Authentication authentication,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        try {
            Claims claims = (Claims) authentication.getPrincipal();
            authService.changePassword(claims, request);
            return ResponseEntity.ok(Map.of("message", "密码修改成功，请重新登录"));
        } catch (IllegalArgumentException e) {
            log.warn("修改密码失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/auth/refresh - 刷新 Token
     * 用 Refresh Token 换取新的 Access Token + Refresh Token（轮换）
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            TokenResponse response = authService.refresh(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("刷新 Token 失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", 401, "message", e.getMessage()));
        }
    }

    /**
     * GET /api/auth/info - 获取当前用户信息
     */
    @GetMapping("/info")
    public ResponseEntity<UserInfoResponse> getUserInfo(Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        UserInfoResponse response = authService.getUserInfo(claims);
        return ResponseEntity.ok(response);
    }
}
