package com.example.striptkillgamedemo2.ai.memory;

import com.example.striptkillgamedemo2.ai.prompt.PromptBuilder;
import com.example.striptkillgamedemo2.config.AiEngineProperties;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * AI 记忆管理器。
 *
 * <p>负责游戏对话的上下文管理，核心功能包括：</p>
 * <ul>
 *   <li>幕次压缩 — 每幕结束时调用 LLM 将聊天记录压缩为结构化摘要（{@link StageSummary}），存入 Redis</li>
 *   <li>上下文窗口 — 通过滑动窗口控制发送给 LLM 的历史消息数量，防止超出 token 限制</li>
 *   <li>记忆检索 — 为后续幕次和最终复盘提供历史摘要片段</li>
 * </ul>
 *
 * @see StageSummary
 * @see com.example.striptkillgamedemo2.ai.prompt.PromptBuilder
 */
public class MemoryManager {

    private static final String MEMORY_KEY_PREFIX = "game:";
    private static final String MEMORY_KEY_INFIX = ":memory:";

    private final ChatModel chatModel;
    private final PromptBuilder promptBuilder;
    private final AiEngineProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 异步压缩指定幕次的聊天记录为结构化摘要。
     *
     * @param roomId        房间 ID
     * @param stageNumber   幕次编号
     * @param stageMessages 本幕的聊天消息列表
     */
    @Async("aiExecutor")
    public void compressStage(String roomId, int stageNumber, List<GameMessage> stageMessages) {
        compressStageWithDmSummary(roomId, stageNumber, stageMessages, null);
    }

    /**
     * 压缩幕次消息为摘要，可附带 DM 的内部分析。
     *
     * <p>DM 的分析（如发现的谎言、关键证据、嫌疑人等）会被追加到 LLM 提示词中，
     * 使压缩输出能捕获纯聊天记录可能遗漏的 DM 层面的情报。</p>
     *
     * @param roomId        房间 ID
     * @param stageNumber   幕次编号
     * @param stageMessages 本幕的聊天消息列表
     * @param dmSummary     DM 的内部分析总结，可为 {@code null}
     */
    @Async("aiExecutor")
    public void compressStageWithDmSummary(String roomId, int stageNumber,
                                            List<GameMessage> stageMessages, String dmSummary) {
        if (stageMessages.isEmpty() && (dmSummary == null || dmSummary.isBlank())) {
            log.debug("No messages or summary to compress for room {} stage {}", roomId, stageNumber);
            return;
        }
        try {
            String compressionPrompt = promptBuilder.buildCompressionPrompt(stageNumber, stageMessages);
            if (dmSummary != null && !dmSummary.isBlank()) {
                compressionPrompt += "\n\n## DM 内部分析\n" + dmSummary;
            }
            String response = chatModel.call(new Prompt(compressionPrompt))
                    .getResult().getOutput().getText();
            String json = extractJson(response);
            // Validate parseable
            objectMapper.readValue(json, StageSummary.class);
            String key = MEMORY_KEY_PREFIX + roomId + MEMORY_KEY_INFIX + stageNumber;
            redisTemplate.opsForValue().set(key, json, 12, TimeUnit.HOURS);
            log.info("Stage {} compressed for room {} (dmSummary={})", stageNumber, roomId,
                    dmSummary != null ? "yes" : "no");
        } catch (Exception e) {
            log.error("Failed to compress stage {} for room {}", stageNumber, roomId, e);
        }
    }

    /**
     * 判断当前提示词是否需要压缩（token 数是否超过阈值）。
     *
     * @param currentPrompt 当前提示词文本
     * @return 是否需要压缩
     */
    public boolean shouldCompress(String currentPrompt) {
        int tokens = promptBuilder.estimateTokens(currentPrompt);
        int threshold = (int) (properties.getMaxContextTokens() * properties.getTokenThresholdRatio());
        return tokens > threshold;
    }

    /**
     * 获取指定房间从第 0 幕到 {@code upToStage}（不含）的所有历史摘要片段。
     *
     * @param roomId    房间 ID
     * @param upToStage 截止幕次（不含）
     * @return 历史摘要片段列表
     */
    public List<String> getMemoryFragments(String roomId, int upToStage) {
        List<String> fragments = new ArrayList<>();
        for (int i = 0; i < upToStage; i++) {
            String key = MEMORY_KEY_PREFIX + roomId + MEMORY_KEY_INFIX + i;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                fragments.add("### 第" + (i + 1) + "幕摘要\n" + json);
            }
        }
        return fragments;
    }

    /**
     * 检查指定幕次是否已有压缩摘要。
     *
     * @param roomId      房间 ID
     * @param stageNumber 幕次编号
     * @return 是否存在摘要
     */
    public boolean hasSummary(String roomId, int stageNumber) {
        String key = MEMORY_KEY_PREFIX + roomId + MEMORY_KEY_INFIX + stageNumber;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 获取滑动窗口范围内的最近消息。
     *
     * <p>如果总消息数不超过窗口大小，返回全部消息；否则返回最后 N 条。</p>
     *
     * @param allMessages 全部消息列表
     * @return 滑动窗口内的消息子列表
     */
    public List<GameMessage> getRecentMessages(List<GameMessage> allMessages) {
        int windowSize = properties.getSlidingWindowSize();
        if (allMessages.size() <= windowSize) {
            return allMessages;
        }
        return allMessages.subList(allMessages.size() - windowSize, allMessages.size());
    }

    /**
     * 从 LLM 响应文本中提取 JSON 内容。
     *
     * <p>优先识别 {@code ```json} 代码块，其次识别普通 {@code ```} 代码块，
     * 如果都不存在则返回整个响应文本。</p>
     *
     * @param response LLM 响应文本
     * @return 提取的 JSON 字符串
     */
    public String extractJson(String response) {
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) return response.substring(start, end).trim();
        }
        if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.indexOf("```", start);
            if (end > start) return response.substring(start, end).trim();
        }
        return response.trim();
    }
}
