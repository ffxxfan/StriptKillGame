package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.repository.ScriptRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Caches Script objects in Redis to avoid repeated MongoDB reads during gameplay.
 * Script content is immutable during a game session, so a 7-day TTL is safe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptCacheService {

    private static final String KEY_PREFIX = "script:";
    private static final long TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;
    private final ScriptRepository scriptRepository;
    private final ObjectMapper objectMapper;

    public Script getScript(ObjectId scriptId) {
        String key = KEY_PREFIX + scriptId.toHexString();
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            try {
                return objectMapper.readValue(json, Script.class);
            } catch (JsonProcessingException e) {
                log.warn("Redis script deserialization failed for {}, falling back to MongoDB", scriptId, e);
            }
        }
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("剧本不存在: " + scriptId));
        cacheScript(script);
        return script;
    }

    public void cacheScript(Script script) {
        try {
            String json = objectMapper.writeValueAsString(script);
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + script.getId().toHexString(),
                    json,
                    TTL_HOURS, TimeUnit.HOURS
            );
            log.debug("Script {} cached in Redis", script.getId().toHexString());
        } catch (JsonProcessingException e) {
            log.error("Failed to cache script {} in Redis", script.getId(), e);
        }
    }

    public void evictScript(ObjectId scriptId) {
        redisTemplate.delete(KEY_PREFIX + scriptId.toHexString());
    }
}
