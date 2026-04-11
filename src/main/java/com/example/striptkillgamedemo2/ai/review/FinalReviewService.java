package com.example.striptkillgamedemo2.ai.review;

import com.example.striptkillgamedemo2.ai.memory.MemoryManager;
import com.example.striptkillgamedemo2.ai.prompt.PromptBuilder;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.redis.GameClueInstance;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.ScriptCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 游戏终局复盘服务。
 *
 * <p>在游戏结束时异步调用 LLM，综合所有幕次的记忆摘要、线索发现情况和投票记录，
 * 生成完整的游戏复盘报告（{@link GameReviewResult}），并通过 WebSocket 广播至房间内所有玩家。</p>
 *
 * @see GameReviewResult
 * @see com.example.striptkillgamedemo2.ai.memory.MemoryManager
 */
public class FinalReviewService {

    private final ChatModel chatModel;
    private final PromptBuilder promptBuilder;
    private final MemoryManager memoryManager;
    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 异步生成游戏复盘报告。
     *
     * <p>收集记忆摘要、线索池和投票记录，构建复盘提示词调用 LLM，
     * 解析结果后通过 {@code GAME_END} 类型消息广播至房间。</p>
     *
     * @param roomId 房间 ID
     * @param room   游戏房间运行时状态
     */
    @Async("aiExecutor")
    public void generateReview(String roomId, LiveGameRoom room) {
        try {
            if (room.getScriptId() == null) {
                log.warn("No script for room {}, skipping review", roomId);
                return;
            }

            Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));

            // Gather all memory fragments
            int totalStages = script.getStages() != null ? script.getStages().size() : 0;
            List<String> memoryFragments = memoryManager.getMemoryFragments(roomId, totalStages);

            // Build clue pool summary
            String cluePoolSummary = buildCluePoolSummary(room, script);

            // Build vote records summary
            String voteRecords = buildVoteRecords(roomId);

            // Build review prompt
            String prompt = promptBuilder.buildReviewPrompt(script, memoryFragments, cluePoolSummary, voteRecords);

            // Call LLM
            String response = chatModel.call(new Prompt(prompt))
                    .getResult().getOutput().getText();

            // Parse result
            String json = memoryManager.extractJson(response);
            GameReviewResult result = objectMapper.readValue(json, GameReviewResult.class);

            // Broadcast to room
            messagingTemplate.convertAndSend("/topic/room." + roomId,
                    Map.of("type", "GAME_END", "summary", result));

            log.info("Game review generated for room {}", roomId);
        } catch (Exception e) {
            log.error("Failed to generate game review for room {}", roomId, e);
            messagingTemplate.convertAndSend("/topic/room." + roomId,
                    Map.of("type", "GAME_END", "summary",
                            Map.of("narrative", "游戏结束，复盘生成失败")));
        }
    }

    /**
     * 构建线索池摘要，统计已发现/总计线索及其公开/私有状态。
     *
     * @param room   游戏房间运行时状态
     * @param script 剧本数据
     * @return 线索池摘要文本
     */
    private String buildCluePoolSummary(LiveGameRoom room, Script script) {
        if (room.getClueInstances() == null || room.getClueInstances().isEmpty()) {
            return "本局无线索被发现";
        }

        long found = room.getClueInstances().stream().filter(GameClueInstance::isFound).count();
        long total = script.getClues() != null ? script.getClues().size() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("已发现 ").append(found).append("/").append(total).append(" 条线索：\n");

        for (GameClueInstance ci : room.getClueInstances()) {
            if (ci.isFound()) {
                String title = script.getClues().stream()
                        .filter(c -> c.getId().toHexString().equals(ci.getClueId().toHexString()))
                        .map(c -> c.getTitle())
                        .findFirst().orElse("未知线索");
                sb.append("- ").append(title)
                        .append(ci.isPublic() ? " (公开)" : " (私有)")
                        .append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 构建投票记录摘要。
     *
     * @param roomId 房间 ID
     * @return 投票记录文本
     */
    private String buildVoteRecords(String roomId) {
        List<String> records = redisTemplate.opsForList()
                .range("game:" + roomId + ":votes", 0, -1);
        if (records == null || records.isEmpty()) {
            return "本局无投票记录";
        }
        return records.stream().collect(Collectors.joining("\n"));
    }
}
