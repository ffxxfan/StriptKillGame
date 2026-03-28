package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.repository.GameRoomRepository;
import com.example.striptkillgamedemo2.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameFlowService {

    private final GameRoomRepository gameRoomRepository;
    private final ScriptRepository scriptRepository;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameChatService gameChatService;

    public GameRoom startGame(ObjectId roomId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("房间不存在: " + roomId));

        if (room.getStatus() != GameRoomStatus.WAITING) {
            throw new IllegalStateException("房间状态不正确，无法开始游戏");
        }
        if (room.getScriptId() == null) {
            throw new IllegalStateException("请先选择剧本");
        }

        boolean hasPlayerWithRole = room.getMembers().stream()
                .anyMatch(m -> !m.isAi() && m.getRoleId() != null);
        if (!hasPlayerWithRole) {
            throw new IllegalStateException("请先选择角色");
        }

        Script script = scriptRepository.findById(room.getScriptId())
                .orElseThrow(() -> new IllegalStateException("剧本数据异常"));

        room.setStatus(GameRoomStatus.PLAYING);
        room.setStartTime(LocalDateTime.now());
        room.setCurrentStage(0);

        // Cache current stage in Redis
        redisTemplate.opsForValue().set(
                "game:room:" + roomId.toHexString() + ":stage", "0");

        // Stub: initialize clue pool (no-op for now)
        initCluePool(roomId, room.getScriptId(), 0);

        GameRoom saved = gameRoomRepository.save(room);

        // Broadcast game start
        String stageTitle = (script.getStages() != null && !script.getStages().isEmpty())
                ? script.getStages().get(0).getStageTitle()
                : "第一幕";

        messagingTemplate.convertAndSend("/topic/room." + roomId.toHexString(),
                Map.of("type", "SYSTEM", "content", "游戏开始！当前阶段: " + stageTitle));

        log.info("Game started in room {}", roomId);
        return saved;
    }

    public GameRoom advanceStage(ObjectId roomId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("房间不存在: " + roomId));

        if (room.getStatus() != GameRoomStatus.PLAYING) {
            throw new IllegalStateException("游戏未在进行中");
        }

        Script script = scriptRepository.findById(room.getScriptId())
                .orElseThrow(() -> new IllegalStateException("剧本数据异常"));

        int nextStage = room.getCurrentStage() + 1;

        if (script.getStages() == null || nextStage >= script.getStages().size()) {
            return endGame(roomId);
        }

        room.setCurrentStage(nextStage);
        redisTemplate.opsForValue().set(
                "game:room:" + roomId.toHexString() + ":stage", String.valueOf(nextStage));

        GameRoom saved = gameRoomRepository.save(room);

        ScriptStage stage = script.getStages().get(nextStage);
        messagingTemplate.convertAndSend("/topic/room." + roomId.toHexString(),
                Map.of("type", "SYSTEM", "content", "进入新阶段: " + stage.getStageTitle()));

        log.info("Room {} advanced to stage {}", roomId, nextStage);
        return saved;
    }

    public GameRoom endGame(ObjectId roomId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("房间不存在: " + roomId));

        room.setStatus(GameRoomStatus.FINISHED);
        room.setEndTime(LocalDateTime.now());
        GameRoom saved = gameRoomRepository.save(room);

        // Flush chat messages to GameRecord
        gameChatService.flushMessages(roomId, room);

        // Clean up Redis keys
        String roomKey = roomId.toHexString();
        redisTemplate.delete("game:room:" + roomKey + ":stage");
        redisTemplate.delete("game:room:" + roomKey + ":clues");

        messagingTemplate.convertAndSend("/topic/room." + roomKey,
                Map.of("type", "SYSTEM", "content", "游戏结束！"));

        log.info("Game ended in room {}", roomId);
        return saved;
    }

    /**
     * Initialize clue pool for a game stage.
     * Stub — no-op for now. Future: load Clue documents into Redis.
     */
    protected void initCluePool(ObjectId roomId, ObjectId scriptId, int stageNumber) {
        log.debug("initCluePool called for room={}, script={}, stage={} (no-op)", roomId, scriptId, stageNumber);
    }
}
