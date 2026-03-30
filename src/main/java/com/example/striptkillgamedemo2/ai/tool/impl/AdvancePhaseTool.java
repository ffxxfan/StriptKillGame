package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdvancePhaseTool implements DmTool {

    private final LiveGameRoomService liveGameRoomService;
    private final SimpMessagingTemplate messagingTemplate;

    @Data
    public static class Input {
        private String roomId;
    }

    @Override
    public String name() {
        return "advancePhase";
    }

    @Override
    public String description() {
        return "推进到当前阶段的下一个环节。如果当前阶段所有环节已完成，则推进到下一幕。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        LiveGameRoom room = ctx.getRoom();
        int currentStage = room.getCurrentStage();

        // Determine total phases in current stage
        List<ScriptStage> stages = ctx.getScript().getStages();
        int totalPhases = 0;
        if (stages != null && currentStage < stages.size()) {
            ScriptStage stage = stages.get(currentStage);
            if (stage.getPhases() != null) {
                totalPhases = stage.getPhases().size();
            }
        }

        int nextPhaseIndex = room.getCurrentPhaseIndex() + 1;

        if (totalPhases > 0 && nextPhaseIndex >= totalPhases) {
            // All phases done — signal stage complete
            room.setCurrentPhaseIndex(nextPhaseIndex);
            liveGameRoomService.save(room);

            messagingTemplate.convertAndSend(
                    "/topic/room." + room.getRoomId(),
                    Map.of("type", "STAGE_COMPLETE", "stage", currentStage)
            );

            log.info("[advancePhase] roomId={}, STAGE_COMPLETE stage={}", room.getRoomId(), currentStage);
            return Map.of("success", true, "event", "STAGE_COMPLETE", "completedStage", currentStage);
        } else {
            room.setCurrentPhaseIndex(nextPhaseIndex);
            liveGameRoomService.save(room);

            messagingTemplate.convertAndSend(
                    "/topic/room." + room.getRoomId(),
                    Map.of("type", "PHASE_ADVANCE", "phaseIndex", nextPhaseIndex)
            );

            log.info("[advancePhase] roomId={}, phaseIndex={}", room.getRoomId(), nextPhaseIndex);
            return Map.of("success", true, "currentPhaseIndex", nextPhaseIndex);
        }
    }
}
