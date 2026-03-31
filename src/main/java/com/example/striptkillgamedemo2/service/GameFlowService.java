package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.ai.memory.MemoryManager;
import com.example.striptkillgamedemo2.ai.review.FinalReviewService;
import com.example.striptkillgamedemo2.dto.StageContentDTO;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.GameRecord;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.mongo.StagePhase;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.repository.GameRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameFlowService {

    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;
    private final GameSummaryService gameSummaryService;
    private final GameRecordRepository gameRecordRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FinalReviewService finalReviewService;
    private final MemoryManager memoryManager;
    private final ObjectMapper objectMapper;

    public LiveGameRoom startGame(String roomId) {
        LiveGameRoom room = getPlayableRoom(roomId);

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

        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));

        room.setStatus(GameRoomStatus.PLAYING);
        room.setStartTime(LocalDateTime.now());
        room.setCurrentStage(0);

        // Initialize phase tracking
        room.setCurrentPhaseIndex(0);
        room.setCurrentSpeakerRoleId(null);
        if (script.getStages() != null && !script.getStages().isEmpty()) {
            ScriptStage firstStage = script.getStages().get(0);
            if (firstStage.getPhases() != null && !firstStage.getPhases().isEmpty()) {
                StagePhase firstPhase = firstStage.getPhases().get(0);
                if (firstPhase.getType() == PhaseType.TURN_BASED
                        && firstPhase.getSpeakOrder() != null
                        && !firstPhase.getSpeakOrder().isEmpty()) {
                    room.setCurrentSpeakerRoleId(firstPhase.getSpeakOrder().get(0));
                }
            }
        }

        liveGameRoomService.save(room);

        String stageTitle = (script.getStages() != null && !script.getStages().isEmpty())
                ? script.getStages().get(0).getStageTitle() : "第一幕";

        messagingTemplate.convertAndSend("/topic/room." + roomId,
                Map.of("type", "SYSTEM", "content", "游戏开始！当前阶段: " + stageTitle));

        log.info("Game started in room {}", roomId);
        return room;
    }

    /** Returns stage content for a specific user's role. */
    public StageContentDTO getStageContent(String roomId, ObjectId userId) {
        LiveGameRoom room = getPlayableRoom(roomId);
        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        List<ScriptStage> stages = script.getStages();

        int stageIndex = room.getCurrentStage();
        ScriptStage stage = stages.get(stageIndex);

        // Find this user's roleId
        String roleIdHex = room.getMembers().stream()
                .filter(m -> m.getUserId() != null
                        && userId.toHexString().equals(m.getUserId().toHexString()))
                .map(m -> m.getRoleId() != null ? m.getRoleId().toHexString() : null)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        String content = (roleIdHex != null && stage.getContentMap() != null)
                ? stage.getContentMap().get(roleIdHex)
                : null;

        return StageContentDTO.builder()
                .stageNumber(stage.getStageNumber())
                .stageTitle(stage.getStageTitle())
                .content(content)
                .audioUrl(stage.getAudioUrl())
                .totalStages(stages.size())
                .isLastStage(stageIndex >= stages.size() - 1)
                .build();
    }

    public LiveGameRoom advanceStage(String roomId) {
        LiveGameRoom room = getPlayableRoom(roomId);

        if (room.getStatus() != GameRoomStatus.PLAYING) {
            throw new IllegalStateException("游戏未在进行中");
        }

        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        int nextStage = room.getCurrentStage() + 1;

        if (script.getStages() == null || nextStage >= script.getStages().size()) {
            return endGame(roomId);
        }

        // Compress completed stage messages
        List<GameMessage> stageMessages = deserializeMessages(
                liveGameRoomService.getMessages(new ObjectId(roomId)));
        memoryManager.compressStage(roomId, room.getCurrentStage(), stageMessages);

        liveGameRoomService.advanceStage(new ObjectId(roomId), nextStage);
        room.setCurrentStage(nextStage);

        ScriptStage stage = script.getStages().get(nextStage);
        messagingTemplate.convertAndSend("/topic/room." + roomId,
                Map.of("type", "SYSTEM", "content", "进入新阶段: " + stage.getStageTitle()));

        log.info("Room {} advanced to stage {}", roomId, nextStage);
        return room;
    }

    public LiveGameRoom endGame(String roomId) {
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null) {
            log.info("Room {} not found in Redis (already ended), skipping endGame", roomId);
            return null;
        }
        if (room.getStatus() == GameRoomStatus.FINISHED) {
            log.info("Room {} already finished, skipping endGame", roomId);
            return room;
        }

        boolean wasPlaying = room.getStatus() == GameRoomStatus.PLAYING;

        room.setStatus(GameRoomStatus.FINISHED);
        room.setEndTime(LocalDateTime.now());
        liveGameRoomService.save(room);

        if (wasPlaying) {
            persistGameRecord(roomId, room);
            // Trigger async AI review (must happen before eviction)
            finalReviewService.generateReview(roomId, room);
        }

        // Evict all Redis keys for this room
        liveGameRoomService.evict(new ObjectId(roomId));
        if (room.getScriptId() != null) {
            scriptCacheService.evictScript(new ObjectId(room.getScriptId()));
        }

        // Unbind all human members from active room
        room.getMembers().stream()
                .filter(m -> !m.isAi() && m.getUserId() != null)
                .forEach(m -> liveGameRoomService.unbindUserRoom(m.getUserId()));

        messagingTemplate.convertAndSend("/topic/room." + roomId,
                Map.of("type", "SYSTEM", "content", "游戏结束！"));

        log.info("Game ended in room {}", roomId);
        return room;
    }

    private void persistGameRecord(String roomId, LiveGameRoom room) {
        try {
            ObjectId rid = new ObjectId(roomId);
            List<String> summary = gameSummaryService.summarize(rid);
            String scriptTitle = null;
            if (room.getScriptId() != null) {
                try {
                    scriptTitle = scriptCacheService.getScript(new ObjectId(room.getScriptId())).getTitle();
                } catch (Exception ignored) {}
            }
            GameRecord record = GameRecord.builder()
                    .roomId(rid)
                    .scriptTitle(scriptTitle)
                    .fullChatLog(summary)
                    .startTime(room.getStartTime())
                    .endTime(room.getEndTime())
                    .build();
            gameRecordRepository.save(record);
            log.info("GameRecord saved for room {}", roomId);
        } catch (Exception e) {
            log.error("Failed to persist GameRecord for room {}", roomId, e);
        }
    }

    private List<GameMessage> deserializeMessages(List<String> jsonMessages) {
        return jsonMessages.stream()
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, GameMessage.class);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize message", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private LiveGameRoom getPlayableRoom(String roomId) {
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在或已结束: " + roomId);
        }
        return room;
    }
}
