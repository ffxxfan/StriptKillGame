package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SelectRespondentsTool implements DmTool {

    private final AgentExecutor agentExecutor;

    @Data
    public static class Input {
        private String messageContent;
        private List<String> candidateRoleIds;
    }

    @Override
    public String name() {
        return "selectRespondents";
    }

    @Override
    public String description() {
        return "根据消息内容选择1-2个最相关的AI角色进行回复。选中的角色将自动触发发言。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        List<String> candidates = input.getCandidateRoleIds();

        if (candidates == null || candidates.isEmpty()) {
            return Map.of("respondents", List.of(), "triggered", 0);
        }

        List<String> selected = candidates.size() > 2 ? candidates.subList(0, 2) : candidates;

        LiveGameRoom room = ctx.getRoom();
        int triggered = 0;

        for (String roleIdHex : selected) {
            // Only trigger AI members
            boolean isAi = room.getMembers().stream()
                    .anyMatch(m -> m.isAi() && m.getRoleId() != null
                            && m.getRoleId().toHexString().equals(roleIdHex));
            if (isAi) {
                agentExecutor.executeAgentReply(room.getRoomId(), new ObjectId(roleIdHex));
                triggered++;
                log.info("[selectRespondents] triggered agent reply for role {}", roleIdHex);
            }
        }

        log.info("[selectRespondents] message='{}', selected={}, triggered={}",
                input.getMessageContent(), selected, triggered);
        return Map.of("respondents", selected, "triggered", triggered);
    }
}
