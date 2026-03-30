package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssignTurnTool implements DmTool {

    private final LiveGameRoomService liveGameRoomService;
    private final SimpMessagingTemplate messagingTemplate;

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

        log.info("[assignTurn] roomId={}, nextSpeaker={}", room.getRoomId(), input.getRoleId());
        return Map.of("success", true, "currentSpeaker", input.getRoleId());
    }
}
