package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DM tool that advances the game to a new stage and broadcasts a lightweight
 * STAGE_UPDATE signal via WebSocket. Script content is NOT pushed here —
 * clients fetch it on demand via GET /api/scripts/my-content.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushStageContentTool implements DmTool {

    private final SimpMessagingTemplate messagingTemplate;
    private final LiveGameRoomService liveGameRoomService;

    @Data
    public static class Input {
        /** Stage index to activate (0-based). If null, uses current stage. */
        private Integer stageIndex;
    }

    @Override
    public String name() {
        return "pushStageContent";
    }

    @Override
    public String description() {
        return "通知所有玩家新一幕已开启。此工具会更新房间的 currentStage 并广播 STAGE_UPDATE 信号，" +
                "玩家客户端收到信号后会自动刷新剧本内容。当游戏开始或进入新一幕时调用。" +
                "stageIndex 为 0 起始的幕序号，不传则使用当前幕。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        LiveGameRoom room = ctx.getRoom();
        List<ScriptStage> stages = ctx.getScript().getStages();

        int stageIndex = input.getStageIndex() != null ? input.getStageIndex() : room.getCurrentStage();

        if (stages == null || stageIndex < 0 || stageIndex >= stages.size()) {
            return Map.of("success", false, "error", "无效的幕序号: " + stageIndex);
        }

        // Update room's currentStage in Redis
        if (stageIndex != room.getCurrentStage()) {
            liveGameRoomService.advanceStage(new ObjectId(room.getRoomId()), stageIndex);
            room.setCurrentStage(stageIndex);
        }

        ScriptStage stage = stages.get(stageIndex);

        // Broadcast lightweight signal — no script content
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("type", "STAGE_UPDATE");
        signal.put("currentStage", stageIndex);
        signal.put("stageTitle", stage.getStageTitle());

        messagingTemplate.convertAndSend("/topic/room." + room.getRoomId(), signal);

        log.info("[pushStageContent] roomId={}, STAGE_UPDATE currentStage={}",
                room.getRoomId(), stageIndex);

        return Map.of("success", true, "currentStage", stageIndex,
                "stageTitle", stage.getStageTitle());
    }
}