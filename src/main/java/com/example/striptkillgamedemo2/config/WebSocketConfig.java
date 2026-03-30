package com.example.striptkillgamedemo2.config;

import com.example.striptkillgamedemo2.security.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置：STOMP 协议 + JWT 鉴权拦截器
 *
 * - 端点：/ws（支持 SockJS 回退）
 * - 消息代理：/topic（广播）, /queue（点对点）
 * - 应用目的地前缀：/app
 * - 入站 Channel 注册 JWT 鉴权拦截器
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用简单的内存消息代理，广播和点对点
        registry.enableSimpleBroker("/topic", "/queue");
        // 应用目的地前缀
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 原生 WebSocket 端点
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
        // SockJS 回退端点
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 注册 JWT 鉴权拦截器，在 CONNECT 时校验 Token
        registration.interceptors(webSocketAuthInterceptor);
    }
}
