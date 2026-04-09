package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssignTurnTool implements DmTool {

    private final LiveGameRoomService liveGameRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AgentExecutor agentExecutor;

    @Data
    public static class Input {
        private String roomId;
        private String roleId;
    }

    @Override
    public String name() {
        return "assignTurn";
    }

    @Override
    public String description() {
        return "指定下一位发言角色。用于轮次制环节中切换发言者。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        LiveGameRoom room = ctx.getRoom();

        room.setCurrentSpeakerRoleId(input.getRoleId());
        liveGameRoomService.save(room);

        messagingTemplate.convertAndSend(
                "/topic/room." + room.getRoomId(),
                Map.of("type", "TURN_CHANGE", "roleId", input.getRoleId())
        );

        // If the assigned role is an AI agent, trigger it to speak
        boolean isAi = room.getMembers().stream()
                .anyMatch(m -> m.isAi() && Objects.equals(m.getRoleId(), new ObjectId(input.getRoleId())));
        if (isAi) {
            // Skip eliminated roles
            if (room.getEliminatedRoleIds().contains(input.getRoleId())) {
                log.info("[assignTurn] skipping eliminated AI role {}", input.getRoleId());
            } else {
                agentExecutor.executeAgentRepliesSequentially(
                        room.getRoomId(), List.of(new ObjectId(input.getRoleId())));
                ctx.setAgentDelegated(true);
                log.info("[assignTurn] triggered AI agent for role {}", input.getRoleId());
            }
        }

        log.info("[assignTurn] roomId={}, nextSpeaker={}", room.getRoomId(), input.getRoleId());
        return Map.of("success", true, "currentSpeaker", input.getRoleId());
    }
}
