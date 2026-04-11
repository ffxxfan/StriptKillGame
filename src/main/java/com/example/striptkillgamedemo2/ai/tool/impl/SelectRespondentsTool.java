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
/**
 * DM 工具：选择回复者。
 *
 * <p>DM 根据消息内容选择 1-2 个最相关的 AI 角色进行回复。
 * 选中的角色会被顺序触发发言，DM 自身的文本输出被抑制（{@code agentDelegated=true}）。</p>
 *
 * <p>角色名称匹配支持模糊匹配（contains），以适应 LLM 可能截断角色名的情况。
 * 已出局角色会被跳过。</p>
 */
public class SelectRespondentsTool implements DmTool {

    private final AgentExecutor agentExecutor;

    /**
     * 工具输入参数。
     */
    @Data
    public static class Input {
        /** 原始消息内容 */
        private String messageContent;
        /** DM 选择的角色名称列表（如 "庄主白峰"） */
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
            agentExecutor.executeAgentRepliesSequentially(room.getRoomId(), aiRoleIds, false);
            // Mark that agents will speak — suppress DM's own text output
            ctx.setAgentDelegated(true);
        }

        log.info("[selectRespondents] message='{}', selected={}, triggered={}",
                input.getMessageContent(), selected, aiRoleIds.size());
        return Map.of("respondents", selected, "triggered", aiRoleIds.size());
    }

    /**
     * 将角色名称模糊匹配到 AI 成员的角色 ID。
     *
     * @param name 角色名称
     * @param ctx  DM 工具上下文
     * @param room 游戏房间运行时状态
     * @return 匹配到的角色 ID，未匹配到或已出局则返回 {@code null}
     */
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
                if (isAi) {
                    // Skip eliminated roles
                    if (room.getEliminatedRoleIds().contains(role.getId().toHexString())) {
                        log.info("[selectRespondents] skipping eliminated role '{}'", name);
                        return null;
                    }
                    return role.getId();
                }
            }
        }
        log.warn("[selectRespondents] could not resolve role name '{}', available roles: {}",
                name, ctx.getScript().getRoles().stream().map(Role::getName).toList());
        return null;
    }
}
