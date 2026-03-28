package com.example.striptkillgamedemo2.controller;

import com.example.striptkillgamedemo2.dto.ChatMessageRequest;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
import com.example.striptkillgamedemo2.service.GameChatService;
import com.example.striptkillgamedemo2.service.GameRoomService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
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
        ObjectId rid = new ObjectId(roomId);

        // Room membership and status check
        GameRoom room = gameRoomService.getRoom(rid);
        gameRoomService.validateMembership(room, userId);

        if (room.getStatus() != GameRoomStatus.PLAYING) {
            log.warn("Message attempt in non-playing room {} by user {}", roomId, userId);
            return;
        }

        ObjectId senderRoleId = gameChatService.findRoleIdForUser(room, userId);
        gameChatService.sendMessage(rid, senderRoleId, request.getContent());
    }
}
