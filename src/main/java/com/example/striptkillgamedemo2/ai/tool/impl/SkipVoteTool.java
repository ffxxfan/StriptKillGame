package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class SkipVoteTool implements DmTool {

    @Data
    public static class Input {
        private String roomId;
        private String reason;
    }

    @Override
    public String name() {
        return "skipVote";
    }

    @Override
    public String description() {
        return "跳过投票环节，直接推进到下一幕。需提供跳过原因。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        log.info("[skipVote] roomId={}, reason={}", input.getRoomId(), input.getReason());
        return Map.of("success", true, "action", "SKIP_VOTE", "reason", input.getReason());
    }
}
