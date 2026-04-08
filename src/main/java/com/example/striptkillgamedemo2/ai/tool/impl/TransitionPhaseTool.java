package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.memory.MemoryManager;
import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.mongo.StagePhase;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.PhaseRuleEnforcer;
import com.example.striptkillgamedemo2.service.PhaseTimerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Unified phase/stage transition tool — replaces advancePhase, decidePhaseTransition,
 * endFreeChat, and skipVote with a single state-machine interface.
 *
 * Actions:
 *   NEXT_PHASE  — advance to the next phase within the current stage
 *   NEXT_STAGE  — advance to the next stage (skips remaining phases in current stage)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransitionPhaseTool implements DmTool {

    private static final Set<String> VALID_ACTIONS = Set.of("NEXT_PHASE", "NEXT_STAGE");

    private final LiveGameRoomService liveGameRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PhaseTimerService phaseTimerService;
    private final PhaseRuleEnforcer phaseRuleEnforcer;
    private final MemoryManager memoryManager;
    private final ObjectMapper objectMapper;

    @Data
    public static class Input {
        /** NEXT_PHASE | NEXT_STAGE */
        private String action;
        /** 转换原因（可选，便于复盘追溯） */
        private String reason;
    }

    @Override
    public String name() {
        return "transitionPhase";
    }

    @Override
    public String description() {
        return "统一的流程推进工具。action 可选：" +
                "NEXT_PHASE（推进到当前幕的下一个环节，如结束自由讨论进入投票）、" +
                "NEXT_STAGE（推进到下一幕，跳过当前幕剩余环节）。" +
                "reason 为可选的转换原因说明。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        String action = input.getAction();

        if (action == null || !VALID_ACTIONS.contains(action)) {
            return Map.of("success", false,
                    "error", "无效的 action: " + action + "，合法值: " + VALID_ACTIONS);
        }

        return switch (action) {
            case "NEXT_PHASE" -> executeNextPhase(ctx, input.getReason());
            case "NEXT_STAGE" -> executeNextStage(ctx, input.getReason());
            default -> Map.of("success", false, "error", "未知 action");
        };
    }

    private Object executeNextPhase(DmToolContext ctx, String reason) {
        LiveGameRoom room = ctx.getRoom();
        List<ScriptStage> stages = ctx.getScript().getStages();
        int currentStage = room.getCurrentStage();

        int totalPhases = 0;
        if (stages != null && currentStage < stages.size()) {
            ScriptStage stage = stages.get(currentStage);
            if (stage.getPhases() != null) {
                totalPhases = stage.getPhases().size();
            }
        }

        int nextPhaseIndex = room.getCurrentPhaseIndex() + 1;

        // Cancel any active timer for the ending phase
        phaseTimerService.cancelTimer(room.getRoomId(), ctx.getCurrentPhaseType());

        // Clear phase-scoped state from previous phase
        phaseRuleEnforcer.resetPhaseState(room);

        if (totalPhases > 0 && nextPhaseIndex >= totalPhases) {
            // All phases in this stage are done — signal stage complete
            room.setCurrentPhaseIndex(nextPhaseIndex);
            room.setCurrentSpeakerRoleId(null);
            liveGameRoomService.save(room);

            messagingTemplate.convertAndSend("/topic/room." + room.getRoomId(),
                    Map.of("type", "STAGE_COMPLETE", "stage", currentStage));

            log.info("[transitionPhase] NEXT_PHASE → STAGE_COMPLETE, room={}, stage={}, reason={}",
                    room.getRoomId(), currentStage, reason);
            return Map.of("success", true, "event", "STAGE_COMPLETE",
                    "completedStage", currentStage,
                    "hint", "当前幕所有环节已完成，请调用 summarizeCurrentStage 归档本幕要点，然后使用 NEXT_STAGE 推进到下一幕");
        }

        // Advance to next phase
        room.setCurrentPhaseIndex(nextPhaseIndex);
        room.setCurrentSpeakerRoleId(null);

        // Determine next phase info
        StagePhase nextPhase = stages.get(currentStage).getPhases().get(nextPhaseIndex);
        PhaseType nextType = nextPhase.getType();

        // Initialize phase-specific state
        if (nextType == PhaseType.VOTE) {
            room.setVoteSubPhase("STATEMENT");
        }

        liveGameRoomService.save(room);

        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("type", "PHASE_ADVANCE");
        signal.put("phaseIndex", nextPhaseIndex);
        signal.put("phaseType", nextType.name());
        if (nextPhase.getTimeLimitSeconds() > 0) {
            signal.put("timeLimitSeconds", nextPhase.getTimeLimitSeconds());
        }
        messagingTemplate.convertAndSend("/topic/room." + room.getRoomId(), signal);

        // Start phase timer with reminder
        startPhaseTimer(room.getRoomId(), nextPhase);

        log.info("[transitionPhase] NEXT_PHASE → phaseIndex={}, type={}, room={}, reason={}",
                nextPhaseIndex, nextType, room.getRoomId(), reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("currentPhaseIndex", nextPhaseIndex);
        result.put("phaseType", nextType.name());
        if (nextPhase.getTimeLimitSeconds() > 0) {
            result.put("timeLimitSeconds", nextPhase.getTimeLimitSeconds());
        }
        return result;
    }

    private Object executeNextStage(DmToolContext ctx, String reason) {
        LiveGameRoom room = ctx.getRoom();
        List<ScriptStage> stages = ctx.getScript().getStages();
        int currentStage = room.getCurrentStage();
        int nextStage = currentStage + 1;

        // Validate no required phases are being skipped
        if (stages != null && currentStage < stages.size()) {
            ScriptStage currentStageObj = stages.get(currentStage);
            if (currentStageObj.getPhases() != null) {
                List<String> requiredSkipped = currentStageObj.getPhases().stream()
                        .skip(room.getCurrentPhaseIndex() + 1)
                        .filter(p -> p.getType().isDefaultRequired())
                        .map(p -> p.getType().name())
                        .toList();
                if (!requiredSkipped.isEmpty()) {
                    return Map.of("success", false,
                            "error", "以下必须环节未完成，不能跳过: " + requiredSkipped,
                            "hint", "请先使用 NEXT_PHASE 推进完成这些环节");
                }
            }
        }

        // Cancel any active timer
        phaseTimerService.cancelAllTimers(room.getRoomId());

        if (stages == null || nextStage >= stages.size()) {
            // No more stages — game should end
            messagingTemplate.convertAndSend("/topic/room." + room.getRoomId(),
                    Map.of("type", "SYSTEM", "content", "所有幕已完成，游戏即将结束。"));

            log.info("[transitionPhase] NEXT_STAGE → GAME_ENDING, room={}, reason={}", room.getRoomId(), reason);
            return Map.of("success", true, "event", "GAME_ENDING",
                    "hint", "已无后续幕次，请做最终总结后结束游戏");
        }

        // Fallback compression if DM forgot to call summarizeCurrentStage
        if (!memoryManager.hasSummary(room.getRoomId(), currentStage)) {
            log.info("[transitionPhase] fallback compression for room={}, stage={}",
                    room.getRoomId(), currentStage);
            List<GameMessage> msgs = deserializeMessages(
                    liveGameRoomService.getMessages(new ObjectId(room.getRoomId())));
            memoryManager.compressStage(room.getRoomId(), currentStage, msgs);
        }

        // Reset phase index for new stage
        room.setCurrentStage(nextStage);
        room.setCurrentPhaseIndex(0);
        room.setCurrentSpeakerRoleId(null);
        phaseRuleEnforcer.resetPhaseState(room);
        liveGameRoomService.save(room);

        ScriptStage newStage = stages.get(nextStage);

        messagingTemplate.convertAndSend("/topic/room." + room.getRoomId(),
                Map.of("type", "STAGE_ADVANCE", "stage", nextStage,
                        "stageTitle", newStage.getStageTitle()));

        // Start timer for first phase of new stage if applicable
        if (newStage.getPhases() != null && !newStage.getPhases().isEmpty()) {
            startPhaseTimer(room.getRoomId(), newStage.getPhases().get(0));
        }

        log.info("[transitionPhase] NEXT_STAGE → stage={}, title={}, room={}, reason={}",
                nextStage, newStage.getStageTitle(), room.getRoomId(), reason);

        return Map.of("success", true, "event", "STAGE_ADVANCE",
                "currentStage", nextStage, "stageTitle", newStage.getStageTitle(),
                "hint", "请调用 pushStageContent 通知玩家新幕开启，然后发表过渡旁白");
    }

    private void startPhaseTimer(String roomId, StagePhase phase) {
        if (phase.getTimeLimitSeconds() > 0) {
            phaseTimerService.startPhaseTimer(roomId, phase.getType(), phase.getTimeLimitSeconds());
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
}
