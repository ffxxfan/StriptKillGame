package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SelectRespondentsTool implements DmTool {

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
        return "根据消息内容选择1-2个最相关的角色进行回复。返回应当回复的角色ID列表。";
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
            return Map.of("respondents", List.of());
        }

        // Return up to 2 candidates (LLM decides; this tool just validates/passes through)
        List<String> selected = candidates.size() > 2 ? candidates.subList(0, 2) : candidates;

        log.debug("[selectRespondents] message='{}', selected={}", input.getMessageContent(), selected);
        return Map.of("respondents", selected);
    }
}
