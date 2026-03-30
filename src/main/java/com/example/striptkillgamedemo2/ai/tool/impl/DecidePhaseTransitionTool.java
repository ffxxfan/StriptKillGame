package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class DecidePhaseTransitionTool implements DmTool {

    private static final Set<String> VALID_ACTIONS = Set.of(
            "ENTER_FREE_CHAT", "SKIP_TO_VOTE_CHECK", "ADVANCE_STAGE"
    );

    @Data
    public static class Input {
        /** ENTER_FREE_CHAT | SKIP_TO_VOTE_CHECK | ADVANCE_STAGE */
        private String action;
        private String reason;
    }

    @Override
    public String name() {
        return "decidePhaseTransition";
    }

    @Override
    public String description() {
        return "决定阶段转换。action可选：ENTER_FREE_CHAT、SKIP_TO_VOTE_CHECK、ADVANCE_STAGE。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        String action = input.getAction();

        if (action == null || !VALID_ACTIONS.contains(action)) {
            return Map.of("success", false, "error", "无效的 action: " + action +
                    "，合法值: " + VALID_ACTIONS);
        }

        log.info("[decidePhaseTransition] action={}, reason={}", action, input.getReason());
        return Map.of("success", true, "action", action, "reason", input.getReason());
    }
}
