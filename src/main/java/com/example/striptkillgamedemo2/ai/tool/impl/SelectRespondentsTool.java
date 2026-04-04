package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SelectRespondentsTool implements DmTool {

    private final AgentExecutor agentExecutor;

    @Data
    public static class Input {
        private String messageContent;
        /** Role names (e.g. "庄主白峰") selected by the DM. */
        private List<String> selectedRoleNames;
    }

    @Override
    public String name() {
        return "selectRespondents";
    }

    @Override
    public String description() {
        return "根据消息内容选择1-2个最相关的AI角色名称进行回复。传入角色名称列表，选中的角色将自动触发发言。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        List<String> names = input.getSelectedRoleNames();

        if (names == null || names.isEmpty()) {
            return Map.of("respondents", List.of(), "triggered", 0);
        }

        List<String> selected = names.size() > 2 ? names.subList(0, 2) : names;

        LiveGameRoom room = ctx.getRoom();

        // Resolve role names to ObjectIds via script roles + room members
        List<ObjectId> aiRoleIds = selected.stream()
                .map(name -> resolveAiRoleId(name, ctx, room))
                .filter(Objects::nonNull)
                .toList();

        log.info("[selectRespondents] resolved {} names to {} roleIds: {}",
                selected.size(), aiRoleIds.size(), aiRoleIds);

        // Trigger sequentially so agents speak one at a time
        if (!aiRoleIds.isEmpty()) {
            agentExecutor.executeAgentRepliesSequentially(room.getRoomId(), aiRoleIds);
            // Mark that agents will speak — suppress DM's own text output
            ctx.setAgentDelegated(true);
        }

        log.info("[selectRespondents] message='{}', selected={}, triggered={}",
                input.getMessageContent(), selected, aiRoleIds.size());
        return Map.of("respondents", selected, "triggered", aiRoleIds.size());
    }

    /** Match a role name to an AI member's roleId (fuzzy: contains match). */
    private ObjectId resolveAiRoleId(String name, DmToolContext ctx, LiveGameRoom room) {
        String trimmed = name.trim();
        for (Role role : ctx.getScript().getRoles()) {
            if (role.getName() == null) continue;
            // Exact match or either side contains the other (handles LLM truncation)
            if (role.getName().equals(trimmed)
                    || role.getName().contains(trimmed)
                    || trimmed.contains(role.getName())) {
                boolean isAi = room.getMembers().stream()
                        .anyMatch(m -> m.isAi() && Objects.equals(m.getRoleId(), role.getId()));
                if (isAi) return role.getId();
            }
        }
        log.warn("[selectRespondents] could not resolve role name '{}', available roles: {}",
                name, ctx.getScript().getRoles().stream().map(Role::getName).toList());
        return null;
    }
}
