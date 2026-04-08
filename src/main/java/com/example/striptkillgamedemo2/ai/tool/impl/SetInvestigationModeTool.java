package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SetInvestigationModeTool implements DmTool {

    private static final Set<String> VALID_MODES = Set.of("PUBLIC", "PRIVATE");

    private final LiveGameRoomService liveGameRoomService;

    @Data
    public static class Input {
        private String mode; // "PUBLIC" | "PRIVATE"
    }

    @Override
    public String name() {
        return "setInvestigationMode";
    }

    @Override
    public String description() {
        return "设置当前搜证阶段的模式。PUBLIC=搜证结果全场可见，PRIVATE=搜证结果仅搜证者可见。" +
                "必须在 INVESTIGATION 阶段开始时调用。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Set<PhaseType> allowedPhases() {
        return Set.of(PhaseType.INVESTIGATION);
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        String mode = input.getMode();

        if (mode == null || !VALID_MODES.contains(mode.toUpperCase())) {
            return Map.of("success", false,
                    "error", "无效的模式: " + mode + "，合法值: PUBLIC, PRIVATE");
        }

        LiveGameRoom room = ctx.getRoom();
        room.setInvestigationMode(mode.toUpperCase());
        liveGameRoomService.save(room);

        log.info("[setInvestigationMode] room={}, mode={}", room.getRoomId(), mode);
        return Map.of("success", true, "mode", mode.toUpperCase());
    }
}
