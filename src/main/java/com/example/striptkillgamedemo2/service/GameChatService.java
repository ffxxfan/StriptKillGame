package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.dto.ChatMessageDTO;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameChatService {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScriptCacheService scriptCacheService;
    private final ObjectMapper objectMapper;

    private static final String MESSAGES_KEY_PREFIX = "game:messages:";

    public ChatMessageDTO sendMessage(String roomId, ObjectId senderRoleId, String content, LiveGameRoom room) {
        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        Role curRole = script.getRoles().stream()
                .filter(r -> Objects.equals(r.getId(), senderRoleId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        GameMessage message = GameMessage.builder()
                .messageId(new ObjectId())
                .gameRoomId(new ObjectId(roomId))
                .senderRoleId(senderRoleId)
                .isAi(false)
                .senderRoleName(curRole.getName())
                .senderAvatar(curRole.getAvatar())
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        storeMessage(roomId, message);

        ChatMessageDTO dto = toChatDTO(message);
        messagingTemplate.convertAndSend("/topic/room." + roomId, dto);
        return dto;
    }

    public ChatMessageDTO sendAiMessage(String roomId, ObjectId senderRoleId, String content, LiveGameRoom room) {
        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        Role curRole = script.getRoles().stream()
                .filter(r -> Objects.equals(r.getId(), senderRoleId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        GameMessage message = GameMessage.builder()
                .messageId(new ObjectId())
                .gameRoomId(new ObjectId(roomId))
                .senderRoleId(senderRoleId)
                .isAi(true)
                .senderRoleName(curRole.getName())
                .senderAvatar(curRole.getAvatar())
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        storeMessage(roomId, message);

        ChatMessageDTO dto = toChatDTO(message);
        messagingTemplate.convertAndSend("/topic/room." + roomId, dto);
        return dto;
    }

    /**
     * Store an AI message to Redis WITHOUT broadcasting via WebSocket.
     * Used when chunks have already been streamed to the client.
     */
    public ChatMessageDTO storeAiMessage(String roomId, ObjectId senderRoleId, String content, LiveGameRoom room) {
        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        Role curRole = script.getRoles().stream()
                .filter(r -> Objects.equals(r.getId(), senderRoleId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        GameMessage message = GameMessage.builder()
                .messageId(new ObjectId())
                .gameRoomId(new ObjectId(roomId))
                .senderRoleId(senderRoleId)
                .isAi(true)
                .senderRoleName(curRole.getName())
                .senderAvatar(curRole.getAvatar())
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        storeMessage(roomId, message);
        return toChatDTO(message);
    }

    private void storeMessage(String roomId, GameMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(MESSAGES_KEY_PREFIX + roomId, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message", e);
            throw new RuntimeException("消息序列化失败", e);
        }
    }

    private ChatMessageDTO toChatDTO(GameMessage message) {
        return ChatMessageDTO.builder()
                .messageId(message.getMessageId().toHexString())
                .senderRoleId(message.getSenderRoleId().toHexString())
                .senderRoleName(message.getSenderRoleName())
                .senderAvatar(message.getSenderAvatar())
                .isAi(message.isAi())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .build();
    }
}
