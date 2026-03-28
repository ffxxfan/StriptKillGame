package com.example.striptkillgamedemo2.service.impl;

import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.service.AiAgentService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultAiAgentService implements AiAgentService {

    @Override
    public String generateReply(Role role, List<GameMessage> context, ScriptStage stage) {
        // No-op stub — return null to skip AI response
        return null;
    }
}
