package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;

import java.util.List;

/**
 * AI Agent service interface.
 * Generates AI character responses during gameplay.
 * Current implementation is a no-op stub; replace with Spring AI integration later.
 */
public interface AiAgentService {

    /**
     * Generate an AI response for the given role based on conversation context and current stage.
     *
     * @param role     the AI role that should respond
     * @param context  recent chat messages for context
     * @param stage    the current script stage
     * @return the generated reply content, or null to skip
     */
    String generateReply(Role role, List<GameMessage> context, ScriptStage stage);
}
