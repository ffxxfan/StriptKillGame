package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.dto.ChatMessageDTO;
import com.example.striptkillgamedemo2.entity.mongo.GameRecord;
import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.repository.GameRecordRepository;
import com.example.striptkillgamedemo2.repository.RoleRepository;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameChatService {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final RoleRepository roleRepository;
    private final GameRecordRepository gameRecordRepository;
    private final GameSummaryService gameSummaryService;
    private final ObjectMapper objectMapper;

    private static final String MESSAGES_KEY_PREFIX = "game:messages:";

    public ChatMessageDTO sendMessage(ObjectId roomId, ObjectId senderRoleId, String content) {
        Role role = roleRepository.findById(senderRoleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        GameMessage message = GameMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .gameRoomId(roomId)
                .senderRoleId(senderRoleId)
                .isAi(false)
                .senderRoleName(role.getName())
                .senderAvatar(role.getAvatar())
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        storeMessage(roomId, message);

        ChatMessageDTO dto = toChatDTO(message);
        messagingTemplate.convertAndSend("/topic/room." + roomId.toHexString(), dto);

        return dto;
    }

    public void flushMessages(ObjectId roomId, GameRoom room) {
        String key = MESSAGES_KEY_PREFIX + roomId.toHexString();

        try {
            List<String> summarized = gameSummaryService.summarize(roomId);

            GameRecord record = GameRecord.builder()
                    .roomId(roomId)
                    .fullChatLog(summarized)
                    .startTime(room.getStartTime())
                    .endTime(room.getEndTime())
                    .build();

            gameRecordRepository.save(record);
            log.info("Game record saved for room {}", roomId);
        } finally {
            redisTemplate.delete(key);
            log.info("Redis messages flushed for room {}", roomId);
        }
    }

    public ObjectId findRoleIdForUser(GameRoom room, ObjectId userId) {
        return room.getMembers().stream()
                .filter(m -> m.getUserId() != null && m.getUserId().equals(userId))
                .map(Member::getRoleId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("你不在该房间中或未选择角色"));
    }

    private void storeMessage(ObjectId roomId, GameMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(MESSAGES_KEY_PREFIX + roomId.toHexString(), json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message", e);
            throw new RuntimeException("消息序列化失败", e);
        }
    }

    private ChatMessageDTO toChatDTO(GameMessage message) {
        return ChatMessageDTO.builder()
                .messageId(message.getMessageId())
                .senderRoleId(message.getSenderRoleId().toHexString())
                .senderRoleName(message.getSenderRoleName())
                .senderAvatar(message.getSenderAvatar())
                .isAi(message.isAi())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .build();
    }
}
