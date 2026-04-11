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
 * 剧本缓存服务。
 *
 * <p>将 {@link Script} 对象缓存在 Redis 中，避免游戏过程中频繁读取 MongoDB。
 * 剧本内容在游戏会话期间不可变，因此使用 24 小时的 TTL 是安全的。</p>
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

    /**
     * 获取剧本，优先从 Redis 缓存读取，缓存未命中时从 MongoDB 加载并回填缓存。
     *
     * @param scriptId 剧本 ID
     * @return 剧本对象
     * @throws IllegalArgumentException 如果剧本不存在
     */
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

    /**
     * 将剧本缓存到 Redis。
     *
     * @param script 剧本对象
     */
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

    /**
     * 从 Redis 中移除剧本缓存。
     *
     * @param scriptId 剧本 ID
     */
    public void evictScript(ObjectId scriptId) {
        redisTemplate.delete(KEY_PREFIX + scriptId.toHexString());
    }
}
