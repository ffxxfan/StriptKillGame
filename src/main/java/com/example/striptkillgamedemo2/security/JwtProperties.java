package com.example.striptkillgamedemo2.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性，从 application.properties 读取 jwt.* 前缀的配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    /** Base64 编码的签名密钥，至少 256 位 */
    private String secret;

    /** Access Token 有效期（秒），默认 2 小时 */
    private long accessTokenExpiration = 7200;

    /** Refresh Token 有效期（秒），默认 30 天 */
    private long refreshTokenExpiration = 2592000;
}
