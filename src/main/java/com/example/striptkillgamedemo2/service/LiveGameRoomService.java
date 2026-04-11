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
/**
 * 游戏房间运行时状态服务。
 *
 * <p>使用 Redis 管理游戏房间的运行时状态（{@link LiveGameRoom}），提供：</p>
 * <ul>
 *   <li>房间状态的 CRUD 操作（序列化为 JSON 存入 Redis）</li>
 *   <li>用户-房间绑定关系管理</li>
 *   <li>聊天消息的 Redis List 存取</li>
 *   <li>成员在线状态管理</li>
 *   <li>自动过期策略（活跃 12h / 空闲 2h TTL）</li>
 * </ul>
 *
 * @see LiveGameRoom
 */
public class LiveGameRoomService {

    static final String ROOM_KEY_PREFIX     = "game:room:";
    static final String MESSAGES_KEY_PREFIX = "game:messages:";
    static final String STAGE_KEY_SUFFIX    = ":stage";
    static final String USER_ROOM_KEY_PREFIX = "user:";
    static final String USER_ROOM_KEY_SUFFIX = ":activeRoom";

    /** 活跃房间的 TTL（小时） */
    private static final long ACTIVE_TTL_HOURS = 12;
    /** 空闲房间的 TTL（小时） */
    private static final long IDLE_TTL_HOURS   = 2;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 初始化游戏房间运行时状态，保存到 Redis。
     *
     * @param room 游戏房间对象
     */
    public void init(LiveGameRoom room) {
        save(room);
        log.info("LiveGameRoom initialized for room {}", room.getRoomId());
    }

    /**
     * 通过 ObjectId 获取游戏房间运行时状态。
     *
     * @param roomId 房间 ID
     * @return 房间对象，不存在时返回 {@code null}
     */
    public LiveGameRoom get(ObjectId roomId) {
        return get(roomId.toHexString());
    }

    /**
     * 通过字符串 ID 获取游戏房间运行时状态。
     *
     * @param roomId 房间 ID 字符串
     * @return 房间对象，不存在或反序列化失败时返回 {@code null}
     */
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

    /**
     * 保存游戏房间运行时状态到 Redis，同时更新最后活跃时间。
     *
     * @param live 游戏房间对象
     * @throws RuntimeException 序列化失败时抛出
     */
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

    /**
     * 从 Redis 中移除房间的所有相关键（房间状态、消息、幕次）。
     *
     * @param roomId 房间 ID
     */
    public void evict(ObjectId roomId) {
        String id = roomId.toHexString();
        redisTemplate.delete(ROOM_KEY_PREFIX + id);
        redisTemplate.delete(MESSAGES_KEY_PREFIX + id);
        redisTemplate.delete(ROOM_KEY_PREFIX + id + STAGE_KEY_SUFFIX);
        log.info("LiveGameRoom evicted for room {}", id);
    }

    /**
     * 移除房间并解绑用户的活跃房间关联。
     *
     * @param roomId 房间 ID
     * @param userId 用户 ID
     */
    public void evictWithUser(ObjectId roomId, ObjectId userId) {
        evict(roomId);
        unbindUserRoom(userId);
    }

    /**
     * 为房间设置空闲 TTL。
     *
     * @param roomId 房间 ID
     */
    public void setIdleTtl(ObjectId roomId) {
        redisTemplate.expire(ROOM_KEY_PREFIX + roomId.toHexString(), IDLE_TTL_HOURS, TimeUnit.HOURS);
        log.info("Room {} idle TTL set to {}h", roomId, IDLE_TTL_HOURS);
    }

    /**
     * 绑定用户与活跃房间的关联。
     *
     * @param userId 用户 ID
     * @param roomId 房间 ID
     */
    public void bindUserRoom(ObjectId userId, String roomId) {
        redisTemplate.opsForValue().set(
                USER_ROOM_KEY_PREFIX + userId.toHexString() + USER_ROOM_KEY_SUFFIX,
                roomId,
                ACTIVE_TTL_HOURS, TimeUnit.HOURS
        );
    }

    /**
     * 获取用户当前绑定的活跃房间 ID。
     *
     * @param userId 用户 ID
     * @return 活跃房间 ID，无绑定时返回 {@code null}
     */
    public String getActiveRoomId(ObjectId userId) {
        return redisTemplate.opsForValue().get(
                USER_ROOM_KEY_PREFIX + userId.toHexString() + USER_ROOM_KEY_SUFFIX
        );
    }

    /**
     * 解除用户与活跃房间的绑定。
     *
     * @param userId 用户 ID
     */
    public void unbindUserRoom(ObjectId userId) {
        redisTemplate.delete(USER_ROOM_KEY_PREFIX + userId.toHexString() + USER_ROOM_KEY_SUFFIX);
    }

    /**
     * 向房间添加一个线索实例。
     *
     * @param roomId 房间 ID
     * @param clue   线索实例
     */
    public void addClueInstance(ObjectId roomId, GameClueInstance clue) {
        LiveGameRoom live = get(roomId);
        if (live == null) {
            log.warn("addClueInstance: LiveGameRoom not found for room {}", roomId);
            return;
        }
        live.getClueInstances().add(clue);
        save(live);
    }

    /**
     * 设置成员的在线状态。若所有真人玩家都离线，自动设置空闲 TTL。
     *
     * @param roomId 房间 ID
     * @param userId 用户 ID
     * @param online 是否在线
     */
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

    /**
     * 推进房间到指定幕次。
     *
     * @param roomId   房间 ID
     * @param newStage 新的幕次索引
     */
    public void advanceStage(ObjectId roomId, int newStage) {
        LiveGameRoom live = get(roomId);
        if (live == null) return;
        live.setCurrentStage(newStage);
        save(live);
    }

    /**
     * 获取房间的所有聊天消息（JSON 字符串列表）。
     *
     * @param roomId 房间 ID
     * @return 消息 JSON 列表
     */
    public List<String> getMessages(ObjectId roomId) {
        List<String> msgs = redisTemplate.opsForList()
                .range(MESSAGES_KEY_PREFIX + roomId.toHexString(), 0, -1);
        return msgs != null ? msgs : List.of();
    }
}
