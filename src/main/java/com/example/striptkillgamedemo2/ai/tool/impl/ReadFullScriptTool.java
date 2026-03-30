package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.mongo.Clue;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ReadFullScriptTool implements DmTool {

    @Data
    public static class Input {
        /** "roles", "clues", or "all" */
        private String queryType;
    }

    @Override
    public String name() {
        return "readFullScript";
    }

    @Override
    public String description() {
        return "查阅剧本真相。queryType可选：roles（角色秘密）、clues（线索详情）、all（全部）。注意：不可直接将原文透露给玩家。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        Script script = ctx.getScript();
        String queryType = input.getQueryType() == null ? "all" : input.getQueryType().toLowerCase();

        if ("roles".equals(queryType)) {
            return Map.of("roles", summarizeRoles(script.getRoles()));
        } else if ("clues".equals(queryType)) {
            return Map.of("clues", summarizeClues(script.getClues()));
        } else {
            return Map.of(
                    "roles", summarizeRoles(script.getRoles()),
                    "clues", summarizeClues(script.getClues())
            );
        }
    }

    private List<Map<String, Object>> summarizeRoles(List<Role> roles) {
        if (roles == null) return List.of();
        return roles.stream()
                .map(r -> Map.<String, Object>of(
                        "id", r.getId() != null ? r.getId().toHexString() : "",
                        "name", r.getName() != null ? r.getName() : "",
                        "secret", r.getSecret() != null ? r.getSecret() : "",
                        "isNpc", r.isNpc()
                ))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> summarizeClues(List<Clue> clues) {
        if (clues == null) return List.of();
        return clues.stream()
                .map(c -> Map.<String, Object>of(
                        "id", c.getId() != null ? c.getId().toHexString() : "",
                        "title", c.getTitle() != null ? c.getTitle() : "",
                        "content", c.getContent() != null ? c.getContent() : "",
                        "locationTag", c.getLocationTag() != null ? c.getLocationTag() : List.of()
                ))
                .collect(Collectors.toList());
    }
}
