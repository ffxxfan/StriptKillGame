package com.example.striptkillgamedemo2.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JWT 服务：负责 Token 的生成、解析、黑名单管理
 *
 * Redis Key 规则：
 * - auth:blacklist:{jti}  → 登出/改密时写入，TTL 为 Access Token 剩余有效期
 * - auth:refresh:{userId} → 登录时写入 Refresh Token，TTL 为 30 天
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String REFRESH_PREFIX = "auth:refresh:";

    // ======================== Token 生成 ========================

    /**
     * 生成 Access Token
     *
     * @param userId   用户 ID（ObjectId hex string）
     * @param username 用户名，存入 claims
     * @return JWT Access Token 字符串
     */
    public String generateAccessToken(String userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration() * 1000);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成 Refresh Token，并存入 Redis
     *
     * @param userId 用户 ID（ObjectId hex string）
     * @return JWT Refresh Token 字符串
     */
    public String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration() * 1000);

        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();

        // 存入 Redis，覆盖旧值（单设备登录模型）
        redisTemplate.opsForValue().set(
                REFRESH_PREFIX + userId,
                token,
                jwtProperties.getRefreshTokenExpiration(),
                TimeUnit.SECONDS
        );

        return token;
    }

    // ======================== Token 解析 ========================

    /**
     * 解析并验证 Token，返回 Claims
     *
     * @param token JWT 字符串
     * @return 解析后的 Claims
     * @throws JwtException 如果 Token 无效或过期
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Access Token：签名有效 + 类型为 access + 不在黑名单中
     *
     * @param token JWT Access Token
     * @return 有效则返回 Claims，否则返回 null
     */
    public Claims validateAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            // 检查 token 类型
            if (!"access".equals(claims.get("type", String.class))) {
                log.warn("Token 类型不是 access");
                return null;
            }
            // 检查黑名单
            if (isBlacklisted(claims.getId())) {
                log.warn("Token 已在黑名单中, jti={}", claims.getId());
                return null;
            }
            return claims;
        } catch (JwtException e) {
            log.warn("Access Token 验证失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证 Refresh Token：签名有效 + 类型为 refresh + 与 Redis 中存储的一致
     *
     * @param token JWT Refresh Token
     * @return 有效则返回 Claims，否则返回 null
     */
    public Claims validateRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            // 检查 token 类型
            if (!"refresh".equals(claims.get("type", String.class))) {
                log.warn("Token 类型不是 refresh");
                return null;
            }
            // 检查 Redis 中是否存在且一致
            String userId = claims.getSubject();
            String storedToken = redisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
            if (!token.equals(storedToken)) {
                log.warn("Refresh Token 与 Redis 中存储的不一致, userId={}", userId);
                return null;
            }
            return claims;
        } catch (JwtException e) {
            log.warn("Refresh Token 验证失败: {}", e.getMessage());
            return null;
        }
    }

    // ======================== 黑名单管理 ========================

    /**
     * 将 Access Token 加入黑名单（登出/改密时调用）
     *
     * @param claims Access Token 的 Claims
     */
    public void blacklistAccessToken(Claims claims) {
        String jti = claims.getId();
        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainingMillis > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + jti,
                    "1",
                    remainingMillis,
                    TimeUnit.MILLISECONDS
            );
            log.info("Access Token 已加入黑名单, jti={}, 剩余有效期={}ms", jti, remainingMillis);
        }
    }

    /**
     * 检查 Token 是否在黑名单中
     */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

    /**
     * 删除指定用户的 Refresh Token（登出/改密时调用）
     *
     * @param userId 用户 ID（hex string）
     */
    public void removeRefreshToken(String userId) {
        redisTemplate.delete(REFRESH_PREFIX + userId);
        log.info("已删除用户 Refresh Token, userId={}", userId);
    }

    // ======================== 工具方法 ========================

    /**
     * 获取 Access Token 有效期（秒）
     */
    public long getAccessTokenExpiration() {
        return jwtProperties.getAccessTokenExpiration();
    }

    /**
     * 从 Base64 编码的密钥字符串构建 HMAC-SHA 签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
