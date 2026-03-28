package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.entity.redis.GameClueInstance;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveGameRoomService {

    static final String ROOM_KEY_PREFIX     = "game:room:";
    static final String MESSAGES_KEY_PREFIX = "game:messages:";
    static final String STAGE_KEY_SUFFIX    = ":stage";
    static final String USER_ROOM_KEY_PREFIX = "user:";
    static final String USER_ROOM_KEY_SUFFIX = ":activeRoom";

    private static final long ACTIVE_TTL_HOURS = 12;
    private static final long IDLE_TTL_HOURS   = 2;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void init(LiveGameRoom room) {
        save(room);
        log.info("LiveGameRoom initialized for room {}", room.getRoomId());
    }

    public LiveGameRoom get(ObjectId roomId) {
        return get(roomId.toHexString());
    }

    public LiveGameRoom get(String roomId) {
        String json = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, LiveGameRoom.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize LiveGameRoom for room {}", roomId, e);
            return null;
        }
    }

    public void save(LiveGameRoom live) {
        live.setLastActiveAt(LocalDateTime.now());
        try {
            String json = objectMapper.writeValueAsString(live);
            redisTemplate.opsForValue().set(
                    ROOM_KEY_PREFIX + live.getRoomId(),
                    json,
                    ACTIVE_TTL_HOURS, TimeUnit.HOURS
            );
            log.debug("LiveGameRoom saved: room={}", live.getRoomId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize LiveGameRoom for room {}", live.getRoomId(), e);
            throw new RuntimeException("LiveGameRoom 序列化失败: " + live.getRoomId(), e);
        }
    }

    public void evict(ObjectId roomId) {
        String id = roomId.toHexString();
        redisTemplate.delete(ROOM_KEY_PREFIX + id);
        redisTemplate.delete(MESSAGES_KEY_PREFIX + id);
        redisTemplate.delete(ROOM_KEY_PREFIX + id + STAGE_KEY_SUFFIX);
        log.info("LiveGameRoom evicted for room {}", id);
    }

    public void evictWithUser(ObjectId roomId, ObjectId userId) {
        evict(roomId);
        unbindUserRoom(userId);
    }

    public void setIdleTtl(ObjectId roomId) {
        redisTemplate.expire(ROOM_KEY_PREFIX + roomId.toHexString(), IDLE_TTL_HOURS, TimeUnit.HOURS);
        log.info("Room {} idle TTL set to {}h", roomId, IDLE_TTL_HOURS);
    }

    public void bindUserRoom(ObjectId userId, String roomId) {
        redisTemplate.opsForValue().set(
                USER_ROOM_KEY_PREFIX + userId.toHexString() + USER_ROOM_KEY_SUFFIX,
                roomId,
                ACTIVE_TTL_HOURS, TimeUnit.HOURS
        );
    }

    public String getActiveRoomId(ObjectId userId) {
        return redisTemplate.opsForValue().get(
                USER_ROOM_KEY_PREFIX + userId.toHexString() + USER_ROOM_KEY_SUFFIX
        );
    }

    public void unbindUserRoom(ObjectId userId) {
        redisTemplate.delete(USER_ROOM_KEY_PREFIX + userId.toHexString() + USER_ROOM_KEY_SUFFIX);
    }

    public void addClueInstance(ObjectId roomId, GameClueInstance clue) {
        LiveGameRoom live = get(roomId);
        if (live == null) {
            log.warn("addClueInstance: LiveGameRoom not found for room {}", roomId);
            return;
        }
        live.getClueInstances().add(clue);
        save(live);
    }

    public void setMemberOnline(ObjectId roomId, ObjectId userId, boolean online) {
        LiveGameRoom live = get(roomId);
        if (live == null) return;

        live.getMembers().stream()
                .filter(m -> userId.toHexString().equals(
                        m.getUserId() != null ? m.getUserId().toHexString() : null))
                .findFirst()
                .ifPresent(m -> m.setOnline(online));

        save(live);

        boolean anyHumanOnline = live.getMembers().stream()
                .anyMatch(m -> !m.isAi() && m.isOnline());

        if (!anyHumanOnline) {
            redisTemplate.expire(ROOM_KEY_PREFIX + roomId.toHexString(), IDLE_TTL_HOURS, TimeUnit.HOURS);
            log.info("Room {} has no online humans, idle TTL set to {}h", roomId, IDLE_TTL_HOURS);
        }
    }

    public void advanceStage(ObjectId roomId, int newStage) {
        LiveGameRoom live = get(roomId);
        if (live == null) return;
        live.setCurrentStage(newStage);
        save(live);
    }

    public List<String> getMessages(ObjectId roomId) {
        List<String> msgs = redisTemplate.opsForList()
                .range(MESSAGES_KEY_PREFIX + roomId.toHexString(), 0, -1);
        return msgs != null ? msgs : List.of();
    }
}
