package com.example.striptkillgamedemo2.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * WebSocket STOMP 连接鉴权拦截器
 *
 * 在 CONNECT 命令时，从 nativeHeaders.Authorization 提取 Bearer Token，
 * 调用 JwtService 验证，成功则将认证信息设入消息头，失败则拒绝连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 从 STOMP Header 中提取 Authorization
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if (authHeaders == null || authHeaders.isEmpty()) {
                log.warn("WebSocket CONNECT 缺少 Authorization Header");
                throw new MessageDeliveryException("未提供认证 Token");
            }

            String authHeader = authHeaders.get(0);
            if (!authHeader.startsWith("Bearer ")) {
                log.warn("WebSocket CONNECT Authorization 格式错误");
                throw new MessageDeliveryException("认证 Token 格式错误");
            }

            String token = authHeader.substring(7);
            Claims claims = jwtService.validateAccessToken(token);

            if (claims == null) {
                log.warn("WebSocket CONNECT JWT 验证失败");
                throw new MessageDeliveryException("认证 Token 无效或已过期");
            }

            // 验证成功，设置认证信息
            // Override getName() to return userId (claims.getSubject()) so that
            // convertAndSendToUser(userId, ...) can match this STOMP session.
            String userId = claims.getSubject();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            claims,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    ) {
                        @Override
                        public String getName() {
                            return userId;
                        }
                    };
            accessor.setUser(authentication);
            log.info("WebSocket CONNECT 鉴权成功, userId={}", claims.getSubject());
        }

        return message;
    }
}
