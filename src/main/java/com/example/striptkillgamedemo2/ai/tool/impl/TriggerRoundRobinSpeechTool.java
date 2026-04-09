package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class TriggerRoundRobinSpeechTool implements DmTool {

    @Data
    public static class Input {
        /** 发言指令，如"请进行自我介绍"或"这是最终陈述，请复盘和辩解" */
        private String instruction;
    }

    @Override
    public String name() {
        return "triggerRoundRobinSpeech";
    }

    @Override
    public String description() {
        return "触发所有存活AI角色轮流发言（如自我介绍、最终陈述）。传入发言指令，所有AI角色将依次发言，完成后自动回调DM继续流程。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        LiveGameRoom room = ctx.getRoom();
        String roomId = room.getRoomId();
        String instruction = input.getInstruction();

        // Collect all surviving AI role IDs (exclude DM and eliminated)
        List<ObjectId> aiRoleIds = room.getMembers().stream()
                .filter(m -> m.isAi() && !m.isDm())
                .map(Member::getRoleId)
                .filter(Objects::nonNull)
                .filter(roleId -> !room.getEliminatedRoleIds().contains(roleId.toHexString()))
                .toList();

        if (aiRoleIds.isEmpty()) {
            log.info("[triggerRoundRobinSpeech] no AI agents to trigger in room={}", roomId);
            return Map.of("success", true, "triggered", 0, "instruction", instruction);
        }

        log.info("[triggerRoundRobinSpeech] deferring {} AI agents in room={}, instruction='{}'",
                aiRoleIds.size(), roomId, instruction);

        // Defer execution: DmExecutor will run agents AFTER streaming DM's text,
        // so the opening narration appears before agent speech.
        ctx.setPendingRoundRobinInstruction(instruction);
        ctx.setPendingRoundRobinRoleIds(aiRoleIds);

        return Map.of("success", true, "triggered", aiRoleIds.size(), "instruction", instruction);
    }
}
