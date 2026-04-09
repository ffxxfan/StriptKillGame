package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerRoundRobinSpeechTool implements DmTool {

    private final AgentExecutor agentExecutor;
    private final DmExecutor dmExecutor;

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

        log.info("[triggerRoundRobinSpeech] triggering {} AI agents in room={}, instruction='{}'",
                aiRoleIds.size(), roomId, instruction);

        // Synchronously execute each agent on the current thread
        for (ObjectId roleId : aiRoleIds) {
            agentExecutor.executeAgentReplySync(roomId, roleId, instruction);
        }

        log.info("[triggerRoundRobinSpeech] all {} agents completed in room={}", aiRoleIds.size(), roomId);

        // Async callback: re-trigger DM to continue the flow
        dmExecutor.executeDmAction(roomId, null,
                "所有AI角色已完成「" + instruction + "」。请继续推进流程。" +
                "如需等待真人玩家发言请提醒他们，否则请使用 transitionPhase 推进到下一环节。");

        // Suppress DM's current text output — agents already spoke
        ctx.setAgentDelegated(true);

        return Map.of("success", true, "triggered", aiRoleIds.size(), "instruction", instruction);
    }
}
