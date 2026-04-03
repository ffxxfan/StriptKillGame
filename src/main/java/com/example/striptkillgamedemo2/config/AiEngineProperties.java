package com.example.striptkillgamedemo2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "game.ai")
public class AiEngineProperties {
    private double charsPerToken = 3.5;
    private double tokenThresholdRatio = 0.7;
    private int maxContextTokens = 100000;
    private int slidingWindowSize = 20;
    private int turnTimeoutSeconds = 60;
    private int voteTimeoutSeconds = 120;
    private int freeChatTimeoutSeconds = 300;
    private int scriptReadingTimeoutSeconds = 180;
    private int investigationTimeoutSeconds = 240;
    private int privateTalkTimeoutSeconds = 180;
    private int finalStatementTimeoutSeconds = 120;
}
