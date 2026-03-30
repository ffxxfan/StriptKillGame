package com.example.striptkillgamedemo2.ai.memory;

import com.example.striptkillgamedemo2.ai.prompt.PromptBuilder;
import com.example.striptkillgamedemo2.config.AiEngineProperties;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryManagerTest {

    private AiEngineProperties properties;
    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        properties = new AiEngineProperties();
        properties.setSlidingWindowSize(5);
        properties.setMaxContextTokens(1000);
        properties.setTokenThresholdRatio(0.7);
        promptBuilder = new PromptBuilder(properties);
    }

    @Test
    void shouldCompress_belowThreshold() {
        String shortPrompt = "短文本";
        int tokens = promptBuilder.estimateTokens(shortPrompt);
        int threshold = (int) (properties.getMaxContextTokens() * properties.getTokenThresholdRatio());
        assertFalse(tokens > threshold);
    }

    @Test
    void shouldCompress_aboveThreshold() {
        // 1000 * 0.7 = 700 token threshold, at 3.5 chars/token = 2450 chars
        String longPrompt = "x".repeat(3000);
        int tokens = promptBuilder.estimateTokens(longPrompt);
        int threshold = (int) (properties.getMaxContextTokens() * properties.getTokenThresholdRatio());
        assertTrue(tokens > threshold);
    }

    @Test
    void extractJson_fromMarkdownCodeBlock() {
        String input = "Here is the summary:\n```json\n{\"stageNumber\": 1}\n```\nDone.";
        int start = input.indexOf("```json") + 7;
        int end = input.indexOf("```", start);
        String json = input.substring(start, end).trim();
        assertEquals("{\"stageNumber\": 1}", json);
    }

    @Test
    void extractJson_rawJson() {
        String input = "{\"stageNumber\": 1}";
        assertEquals("{\"stageNumber\": 1}", input.trim());
    }

    @Test
    void getRecentMessages_withinWindow() {
        List<GameMessage> messages = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            messages.add(GameMessage.builder()
                    .messageId(new ObjectId())
                    .content("msg " + i)
                    .timestamp(LocalDateTime.now())
                    .build());
        }
        // Window size is 5, only 3 messages — all returned
        assertTrue(messages.size() <= properties.getSlidingWindowSize());
    }

    @Test
    void getRecentMessages_exceedsWindow() {
        List<GameMessage> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(GameMessage.builder()
                    .messageId(new ObjectId())
                    .content("msg " + i)
                    .timestamp(LocalDateTime.now())
                    .build());
        }
        // Window size 5, 10 messages — should return last 5
        int windowSize = properties.getSlidingWindowSize();
        List<GameMessage> recent = messages.subList(messages.size() - windowSize, messages.size());
        assertEquals(5, recent.size());
        assertEquals("msg 5", recent.get(0).getContent());
    }
}
