package com.example.striptkillgamedemo2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 引擎配置属性。
 * <p>
 * 映射 {@code application.properties} 中 {@code game.ai.*} 前缀的参数，控制上下文压缩、
 * 各游戏阶段超时时长、以及 AI-to-AI 聊天的最大轮数等行为。
 * </p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "game.ai")
public class AiEngineProperties {
    /** Token 粗估：每个字符近似占用的 Token 数量（用于不需严格 tokenizer 的估算）。 */
    private double charsPerToken = 3.5;
    /** Token 阈值比例：达到 {@code maxContextTokens} 的该比例时触发上下文压缩。 */
    private double tokenThresholdRatio = 0.7;
    /** LLM 可接受的最大上下文 Token 数。 */
    private int maxContextTokens = 100000;
    /** 滑动窗口大小（条），保留最近的消息数。 */
    private int slidingWindowSize = 20;
    /** 轮流发言阶段每人的发言超时时长（秒）。 */
    private int turnTimeoutSeconds = 60;
    /** 投票阶段超时时长（秒）。 */
    private int voteTimeoutSeconds = 120;
    /** 自由讨论阶段超时时长（秒）。 */
    private int freeChatTimeoutSeconds = 300;
    /** 读本阶段超时时长（秒）。 */
    private int scriptReadingTimeoutSeconds = 180;
    /** 调查取证阶段超时时长（秒）。 */
    private int investigationTimeoutSeconds = 240;
    /** 私聊阶段超时时长（秒）。 */
    private int privateTalkTimeoutSeconds = 180;
    /** 最终陈述阶段超时时长（秒）。 */
    private int finalStatementTimeoutSeconds = 120;
    /** AI 与 AI 之间连续对话的最大轮数，防止无限循环。 */
    private int maxAiChatRounds = 3;
    /** 无人发言的空闲超时时长（秒），超时后由 DM 介入。 */
    private int idleTimeoutSeconds = 60;
}
