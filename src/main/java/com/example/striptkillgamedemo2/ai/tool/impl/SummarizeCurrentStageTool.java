package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.memory.MemoryManager;
import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DM tool to summarize the current stage before advancing.
 * Archives key findings (lies detected, critical evidence, suspects) into MemoryManager
 * so that later stages and the final review have access to per-stage intelligence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummarizeCurrentStageTool implements DmTool {

    private final MemoryManager memoryManager;
    private final LiveGameRoomService liveGameRoomService;
    private final ObjectMapper objectMapper;

    @Data
    public static class Input {
        /** DM 对本幕重点的文字总结：谁说了谎、谁发现了关键证据、目前的怀疑对象等 */
        private String summary;
    }

    @Override
    public String name() {
        return "summarizeCurrentStage";
    }

    @Override
    public String description() {
        return "归档当前幕的重点摘要。在每幕结束时调用，记录本幕关键信息：" +
                "谁说了谎、谁发现了关键证据、目前的怀疑对象、重要的讨论转折点。" +
                "summary 字段填写你对本幕的内部分析总结，该内容不会发送给玩家，仅用于后续推理和最终复盘。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        LiveGameRoom room = ctx.getRoom();
        int stageNumber = room.getCurrentStage();

        if (input.getSummary() == null || input.getSummary().isBlank()) {
            return Map.of("success", false, "error", "summary 不能为空");
        }

        // Retrieve current stage messages for compression
        List<GameMessage> stageMessages = deserializeMessages(
                liveGameRoomService.getMessages(new ObjectId(room.getRoomId())));

        // Trigger async compression with DM's summary as additional context
        memoryManager.compressStageWithDmSummary(
                room.getRoomId(), stageNumber, stageMessages, input.getSummary());

        log.info("[summarizeCurrentStage] room={}, stage={}, summaryLength={}",
                room.getRoomId(), stageNumber, input.getSummary().length());

        return Map.of("success", true,
                "stage", stageNumber,
                "message", "第" + (stageNumber + 1) + "幕摘要已归档");
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
