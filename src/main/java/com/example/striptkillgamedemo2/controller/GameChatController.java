package com.example.striptkillgamedemo2.controller;

import com.example.striptkillgamedemo2.ai.event.ChatMessageEvent;
import com.example.striptkillgamedemo2.ai.orchestrator.AgentOrchestrator;
import com.example.striptkillgamedemo2.dto.ChatMessageRequest;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.GameChatService;
import com.example.striptkillgamedemo2.service.GameRoomService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

/**
 * 游戏聊天 WebSocket 控制器。
 * <p>
 * 通过 STOMP 处理房间内的聊天发送以及用户活动心跳。聊天消息会被广播到房间主题，并发布
 * {@link ChatMessageEvent} 驱动 AI 代理/DM 做出响应；心跳用于重置 AI 的空闲计时器。
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class GameChatController {

    private final GameChatService gameChatService;
    private final GameRoomService gameRoomService;
    private final ApplicationEventPublisher eventPublisher;
    private final AgentOrchestrator agentOrchestrator;

    /**
     * 处理玩家在房间内发送的聊天消息。
     * <p>
     * 校验发送者在房间中且房间处于 {@link GameRoomStatus#PLAYING} 状态，随后保存并广播
     * 消息，并发布 {@link ChatMessageEvent} 以便 AI 引擎介入。
     * </p>
     *
     * @param roomId         目标房间 ID
     * @param request        聊天消息内容
     * @param headerAccessor STOMP 消息头访问器，用于取认证信息
     */
    @MessageMapping("/chat.{roomId}")
    public void handleChatMessage(
            @DestinationVariable String roomId,
            @Payload ChatMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) headerAccessor.getUser();
        if (auth == null) {
            log.warn("Unauthenticated message attempt for room {}", roomId);
            return;
        }

        Claims claims = (Claims) auth.getPrincipal();
        ObjectId userId = new ObjectId(claims.getSubject());

        LiveGameRoom room = gameRoomService.getRoom(roomId);
        gameRoomService.validateMembership(room, userId);

        if (room.getStatus() != GameRoomStatus.PLAYING) {
            log.warn("Message attempt in non-playing room {} by user {}", roomId, userId);
            return;
        }

        ObjectId senderRoleId = gameRoomService.findRoleIdForUser(room, userId);
        gameChatService.sendMessage(roomId, senderRoleId, request.getContent(), room);

        // 发布事件供 AI 引擎处理
        eventPublisher.publishEvent(new ChatMessageEvent(
                this, roomId, senderRoleId, request.getContent(), false));
    }

    /**
     * 处理用户活动心跳。
     * <p>
     * 当前端滚动剧本/聊天等行为时上报，用于重置 AI 的空闲计时器，避免误触发 idle 流程。
     * </p>
     *
     * @param roomId         房间 ID
     * @param headerAccessor STOMP 消息头访问器
     */
    @MessageMapping("/room.{roomId}.activity")
    public void handleUserActivity(
            @DestinationVariable String roomId,
            SimpMessageHeaderAccessor headerAccessor) {

        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) headerAccessor.getUser();
        if (auth == null) return;

        // 用户仍在主动浏览 —— 重置空闲计时器
        agentOrchestrator.resetIdleTimer(roomId);
    }
}
