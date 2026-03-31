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
public class MemoryManager {

    private static final String MEMORY_KEY_PREFIX = "game:";
    private static final String MEMORY_KEY_INFIX = ":memory:";

    private final ChatModel chatModel;
    private final PromptBuilder promptBuilder;
    private final AiEngineProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Async("aiExecutor")
    public void compressStage(String roomId, int stageNumber, List<GameMessage> stageMessages) {
        if (stageMessages.isEmpty()) {
            log.debug("No messages to compress for room {} stage {}", roomId, stageNumber);
            return;
        }
        try {
            String compressionPrompt = promptBuilder.buildCompressionPrompt(stageNumber, stageMessages);
            String response = chatModel.call(new Prompt(compressionPrompt))
                    .getResult().getOutput().getText();
            String json = extractJson(response);
            // Validate parseable
            objectMapper.readValue(json, StageSummary.class);
            String key = MEMORY_KEY_PREFIX + roomId + MEMORY_KEY_INFIX + stageNumber;
            redisTemplate.opsForValue().set(key, json, 12, TimeUnit.HOURS);
            log.info("Stage {} compressed for room {}", stageNumber, roomId);
        } catch (Exception e) {
            log.error("Failed to compress stage {} for room {}", stageNumber, roomId, e);
        }
    }

    public boolean shouldCompress(String currentPrompt) {
        int tokens = promptBuilder.estimateTokens(currentPrompt);
        int threshold = (int) (properties.getMaxContextTokens() * properties.getTokenThresholdRatio());
        return tokens > threshold;
    }

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

    public List<GameMessage> getRecentMessages(List<GameMessage> allMessages) {
        int windowSize = properties.getSlidingWindowSize();
        if (allMessages.size() <= windowSize) {
            return allMessages;
        }
        return allMessages.subList(allMessages.size() - windowSize, allMessages.size());
    }

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
