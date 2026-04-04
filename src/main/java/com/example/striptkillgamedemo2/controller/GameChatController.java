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

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameChatController {

    private final GameChatService gameChatService;
    private final GameRoomService gameRoomService;
    private final ApplicationEventPublisher eventPublisher;
    private final AgentOrchestrator agentOrchestrator;

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

        // Publish event for AI engine
        eventPublisher.publishEvent(new ChatMessageEvent(
                this, roomId, senderRoleId, request.getContent(), false));
    }

    @MessageMapping("/room.{roomId}.activity")
    public void handleUserActivity(
            @DestinationVariable String roomId,
            SimpMessageHeaderAccessor headerAccessor) {

        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) headerAccessor.getUser();
        if (auth == null) return;

        // User is actively browsing (script/chat scroll) — reset idle timer
        agentOrchestrator.resetIdleTimer(roomId);
    }
}
