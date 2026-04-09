package com.example.striptkillgamedemo2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 响应 DTO。
 * <p>
 * 登录、注册或刷新 Token 接口的返回体，同时携带 Access Token 与 Refresh Token。
 * Access Token 用于常规业务接口鉴权，Refresh Token 用于换取新的 Access Token。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    /** 访问令牌（JWT），随请求头 {@code Authorization: Bearer} 提交。 */
    private String accessToken;
    /** 刷新令牌（JWT），用于在 Access Token 过期后换取新的 Access Token。 */
    private String refreshToken;
    /** Access Token 有效期（秒）。 */
    private long expiresIn;
}
