package com.example.striptkillgamedemo2.ai.prompt;

import com.example.striptkillgamedemo2.config.AiEngineProperties;
import com.example.striptkillgamedemo2.entity.mongo.*;
import com.example.striptkillgamedemo2.entity.redis.GameClueInstance;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * 提示词构建器。
 *
 * <p>负责从 Markdown 模板和游戏运行时数据构建发送给 LLM 的系统提示词。
 * 模板文件位于 {@code src/main/resources/prompts/}，通过变量替换注入动态内容。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>{@link #buildDmPrompt} — 构建 DM 提示词，拥有所有角色秘密和线索的完整访问权限</li>
 *   <li>{@link #buildAgentPrompt} — 构建 AI 代理提示词，沙箱化处理仅展示该角色可见的线索和消息</li>
 *   <li>{@link #buildCompressionPrompt} — 构建记忆压缩提示词</li>
 *   <li>{@link #buildReviewPrompt} — 构建终局复盘提示词</li>
 * </ul>
 *
 * @see com.example.striptkillgamedemo2.ai.executor.DmExecutor
 * @see com.example.striptkillgamedemo2.ai.executor.AgentExecutor
 */
public class PromptBuilder {

    private final AiEngineProperties properties;

    /**
     * 构建 DM（主持人）系统提示词。
     *
     * <p>DM 拥有对所有角色秘密和线索的完整访问权限，提示词包含剧本真相、
     * 角色列表、搜证次数表、阶段信息和历史摘要等。</p>
     *
     * @param room            游戏房间运行时状态
     * @param script          剧本数据
     * @param memoryFragments 历史幕次摘要片段
     * @param recentMessages  最近的聊天消息
     * @return 完整的 DM 系统提示词
     */
    public String buildDmPrompt(LiveGameRoom room, Script script,
                                 List<String> memoryFragments,
                                 List<GameMessage> recentMessages) {
        String template = loadTemplate("prompts/dm-system.md", script.getDmConfig());

        ScriptStage currentStage = getCurrentStage(script, room.getCurrentStage());
        StagePhase currentPhase = getCurrentPhase(currentStage, room.getCurrentPhaseIndex());

        Map<String, String> vars = new HashMap<>();
        vars.put("scriptTitle", script.getTitle());
        vars.put("currentStage", String.valueOf(room.getCurrentStage() + 1));
        vars.put("stageTitle", currentStage != null ? currentStage.getStageTitle() : "未知");
        vars.put("phaseType", currentPhase != null ? currentPhase.getType().name() : "FREE_CHAT");
        vars.put("phaseInstruction", currentPhase != null && currentPhase.getDmInstruction() != null
                ? currentPhase.getDmInstruction() : "");
        vars.put("roleList", buildRoleList(script.getRoles(), room));
        vars.put("searchPowerTable", buildSearchPowerTable(script.getRoles()));
        vars.put("fullScriptTruth", buildFullScriptTruth(script));
        vars.put("memoryFragments", String.join("\n", memoryFragments));

        int totalStages = script.getStages() != null ? script.getStages().size() : 1;
        boolean isFirstStage = room.getCurrentStage() == 0;
        boolean isLastStage = room.getCurrentStage() >= totalStages - 1;
        vars.put("isFirstStage", String.valueOf(isFirstStage));
        vars.put("isLastStage", String.valueOf(isLastStage));
        vars.put("totalStages", String.valueOf(totalStages));

        // Load stage-specific instructions
        String stageTemplatePath;
        if (isFirstStage) {
            stageTemplatePath = "prompts/dm-stage-first.md";
        } else if (isLastStage) {
            stageTemplatePath = "prompts/dm-stage-last.md";
        } else {
            stageTemplatePath = "prompts/dm-stage-normal.md";
        }
        String stageInstructions = replaceVars(loadClasspathTemplate(stageTemplatePath), vars);
        vars.put("stageSpecificInstructions", stageInstructions);

        String prompt = replaceVars(template, vars);

        if (!recentMessages.isEmpty()) {
            prompt += "\n\n## 最近对话\n" + formatMessages(recentMessages);
        }
        return prompt;
    }

    /**
     * 构建 AI 代理系统提示词。
     *
     * <p>沙箱化处理：仅包含该角色可见的线索和消息，隔离其他角色的秘密信息。</p>
     *
     * @param room              游戏房间运行时状态
     * @param script            剧本数据
     * @param targetRole        目标 AI 代理的角色
     * @param allClueInstances  所有线索实例
     * @param memoryFragments   历史幕次摘要片段
     * @param recentMessages    最近的聊天消息
     * @return 沙箱化的代理系统提示词
     */
    public String buildAgentPrompt(LiveGameRoom room, Script script,
                                    Role targetRole,
                                    List<GameClueInstance> allClueInstances,
                                    List<String> memoryFragments,
                                    List<GameMessage> recentMessages) {
        String template = loadClasspathTemplate("prompts/agent-system.md");

        ScriptStage currentStage = getCurrentStage(script, room.getCurrentStage());
        StagePhase currentPhase = getCurrentPhase(currentStage, room.getCurrentPhaseIndex());

        // Sandbox: filter clues to only what this role can see
        String roleIdHex = targetRole.getId().toHexString();
        List<GameClueInstance> visibleClues = allClueInstances.stream()
                .filter(c -> c.isFound() &&
                        (c.isPublic() || c.getOwnerRoleIds().stream()
                                .anyMatch(id -> id.toHexString().equals(roleIdHex))))
                .toList();

        // Sandbox: filter messages to only public or addressed to this role
        ObjectId roleId = targetRole.getId();
        List<GameMessage> visibleMessages = recentMessages.stream()
                .filter(m -> m.getReceiverRoleIds() == null ||
                        m.getReceiverRoleIds().isEmpty() ||
                        m.getReceiverRoleIds().contains(roleId))
                .toList();

        Map<String, String> vars = new HashMap<>();
        vars.put("scriptTitle", script.getTitle());
        vars.put("roleName", targetRole.getName());
        vars.put("rolePrompt", targetRole.getPrompt() != null ? targetRole.getPrompt() : "");
        vars.put("roleSecret", targetRole.getSecret() != null ? targetRole.getSecret() : "无特殊秘密");
        vars.put("discoveredClues", formatClues(visibleClues, script.getClues()));
        vars.put("otherRoles", buildOtherRoles(script.getRoles(), targetRole.getId(), room));
        vars.put("currentStage", String.valueOf(room.getCurrentStage() + 1));
        vars.put("stageTitle", currentStage != null ? currentStage.getStageTitle() : "未知");
        vars.put("phaseType", currentPhase != null ? currentPhase.getType().name() : "FREE_CHAT");
        vars.put("phaseInstruction", currentPhase != null && currentPhase.getDmInstruction() != null
                ? currentPhase.getDmInstruction() : "");
        vars.put("memoryFragments", String.join("\n", memoryFragments));

        int totalStages = script.getStages() != null ? script.getStages().size() : 1;
        boolean isLastStage = room.getCurrentStage() >= totalStages - 1;
        vars.put("isLastStage", String.valueOf(isLastStage));

        String prompt = replaceVars(template, vars);

        if (!visibleMessages.isEmpty()) {
            prompt += "\n\n## 最近对话\n" + formatMessages(visibleMessages);
        }
        return prompt;
    }

    /**
     * 构建记忆压缩提示词，用于将一幕的聊天记录压缩为结构化摘要。
     *
     * @param stageNumber 幕次编号
     * @param messages    本幕的聊天消息列表
     * @return 压缩提示词
     */
    public String buildCompressionPrompt(int stageNumber, List<GameMessage> messages) {
        String template = loadClasspathTemplate("prompts/compression.md");
        Map<String, String> vars = new HashMap<>();
        vars.put("stageNumber", String.valueOf(stageNumber));
        vars.put("messages", formatMessages(messages));
        return replaceVars(template, vars);
    }

    /**
     * 构建终局复盘提示词。
     *
     * @param script          剧本数据
     * @param memoryFragments 所有幕次的历史摘要片段
     * @param cluePoolSummary 线索池摘要
     * @param voteRecords     投票记录摘要
     * @return 复盘提示词
     */
    public String buildReviewPrompt(Script script, List<String> memoryFragments,
                                     String cluePoolSummary, String voteRecords) {
        String template = loadClasspathTemplate("prompts/review-system.md");
        Map<String, String> vars = new HashMap<>();
        vars.put("fullScriptTruth", buildFullScriptTruth(script));
        vars.put("memoryFragments", String.join("\n", memoryFragments));
        vars.put("cluePoolSummary", cluePoolSummary);
        vars.put("voteRecords", voteRecords);
        return replaceVars(template, vars);
    }

    /**
     * 估算文本的 token 数量。
     *
     * @param text 文本内容
     * @return 估算的 token 数
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) (text.length() / properties.getCharsPerToken());
    }

    // --- Internal helpers (package-private for testing) ---

    String loadTemplate(String classpathPath, String override) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        return loadClasspathTemplate(classpathPath);
    }

    String loadClasspathTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt template: {}", path, e);
            throw new IllegalStateException("Prompt template not found: " + path, e);
        }
    }

    String replaceVars(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    private String buildRoleList(List<Role> roles, LiveGameRoom room) {
        return roles.stream()
                .map(r -> {
                    String label = "- " + r.getName() + (r.isNpc() ? " (NPC)" : "");
                    if (room != null && room.getEliminatedRoleIds() != null
                            && r.getId() != null
                            && room.getEliminatedRoleIds().contains(r.getId().toHexString())) {
                        label += " (已出局)";
                    }
                    return label;
                })
                .collect(Collectors.joining("\n"));
    }

    private String buildSearchPowerTable(List<Role> roles) {
        return roles.stream()
                .filter(r -> !r.isNpc())
                .map(r -> "- " + r.getName() + "：剩余搜证次数 " + r.getSearchPower())
                .collect(Collectors.joining("\n"));
    }

    private String buildFullScriptTruth(Script script) {
        StringBuilder sb = new StringBuilder();
        for (Role role : script.getRoles()) {
            sb.append("### ").append(role.getName()).append("\n");
            if (role.getSecret() != null) {
                sb.append("秘密：").append(role.getSecret()).append("\n");
            }
            sb.append("\n");
        }
        if (script.getClues() != null) {
            sb.append("### 线索清单\n");
            for (Clue clue : script.getClues()) {
                sb.append("- ").append(clue.getTitle()).append("：").append(clue.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildOtherRoles(List<Role> roles, ObjectId excludeRoleId, LiveGameRoom room) {
        return roles.stream()
                .filter(r -> !Objects.equals(r.getId(), excludeRoleId))
                .map(r -> {
                    String label = "- " + r.getName() + (r.isNpc() ? " (NPC)" : "");
                    if (room != null && room.getEliminatedRoleIds() != null
                            && r.getId() != null
                            && room.getEliminatedRoleIds().contains(r.getId().toHexString())) {
                        label += " (已出局)";
                    }
                    return label;
                })
                .collect(Collectors.joining("\n"));
    }

    private String formatClues(List<GameClueInstance> instances, List<Clue> clueTemplates) {
        if (instances.isEmpty()) return "暂无线索";
        return instances.stream().map(inst -> {
            Clue template = clueTemplates.stream()
                    .filter(c -> Objects.equals(c.getId(), inst.getClueId()))
                    .findFirst().orElse(null);
            if (template == null) return "- 未知线索";
            return "- " + template.getTitle() + "：" + template.getContent();
        }).collect(Collectors.joining("\n"));
    }

    private String formatMessages(List<GameMessage> messages) {
        return messages.stream()
                .map(m -> "[" + m.getSenderRoleName() + "] " + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    private ScriptStage getCurrentStage(Script script, int stageIndex) {
        if (script.getStages() == null || stageIndex >= script.getStages().size()) return null;
        return script.getStages().get(stageIndex);
    }

    private StagePhase getCurrentPhase(ScriptStage stage, int phaseIndex) {
        if (stage == null || stage.getPhases() == null || phaseIndex >= stage.getPhases().size()) return null;
        return stage.getPhases().get(phaseIndex);
    }
}
