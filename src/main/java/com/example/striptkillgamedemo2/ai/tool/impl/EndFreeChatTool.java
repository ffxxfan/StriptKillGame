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
public class EndFreeChatTool implements DmTool {

    private final LiveGameRoomService liveGameRoomService;
    private final SimpMessagingTemplate messagingTemplate;

    @Data
    public static class Input {
        private String roomId;
    }

    @Override
    public String name() {
        return "endFreeChat";
    }

    @Override
    public String description() {
        return "结束自由讨论环节。调用后将进入投票决策阶段。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        LiveGameRoom room = ctx.getRoom();

        messagingTemplate.convertAndSend(
                "/topic/room." + room.getRoomId(),
                Map.of("type", "FREE_CHAT_ENDED", "roomId", room.getRoomId())
        );

        liveGameRoomService.save(room);

        log.info("[endFreeChat] roomId={}", room.getRoomId());
        return Map.of("success", true, "message", "自由讨论已结束，进入投票决策阶段");
    }
}
