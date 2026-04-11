package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.entity.redis.VoteRecord;
import com.example.striptkillgamedemo2.entity.redis.VoteSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;

@Slf4j
@Service
/**
 * 投票服务。
 *
 * <p>管理游戏内投票的完整生命周期：</p>
 * <ul>
 *   <li>{@link #castVote} — 提交投票（玩家或 AI），记录投票并检查是否全员投完</li>
 *   <li>{@link #closeVote} — 关闭投票会话，广播结果</li>
 *   <li>{@link #closeVoteAndNotifyDm} — 关闭投票并通知 DM 处理结果（含平票检测）</li>
 *   <li>{@link #eliminateRole} — 标记角色出局，检查是否所有人类都已出局</li>
 * </ul>
 *
 * <p>使用 {@code @Lazy} 注入 {@link DmExecutor} 和 {@link GameFlowService} 以打破循环依赖。</p>
 */
public class VoteService {

    private final LiveGameRoomService liveGameRoomService;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final DmExecutor dmExecutor;
    private final GameFlowService gameFlowService;

    /**
     * 构造投票服务。使用 {@code @Lazy} 注入以打破循环依赖。
     */
    public VoteService(LiveGameRoomService liveGameRoomService,
                       StringRedisTemplate redisTemplate,
                       SimpMessagingTemplate messagingTemplate,
                       ObjectMapper objectMapper,
                       @Lazy DmExecutor dmExecutor,
                       @Lazy GameFlowService gameFlowService) {
        this.liveGameRoomService = liveGameRoomService;
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.dmExecutor = dmExecutor;
        this.gameFlowService = gameFlowService;
    }

    private static final String VOTES_KEY_PREFIX = "game:";
    private static final String VOTES_KEY_SUFFIX = ":votes";

    /**
     * 提交投票。
     *
     * <p>记录投票选择，存储投票记录到 Redis，广播投票进度，
     * 并向真人玩家发送私人投票确认。全员投完后自动关闭投票。</p>
     *
     * @param roomId      房间 ID
     * @param voterRoleId 投票者角色 ID
     * @param choice      投票选择
     * @return 是否全员投票完毕
     * @throws IllegalStateException 没有进行中的投票
     */
    public boolean castVote(String roomId, ObjectId voterRoleId, String choice) {
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null || room.getActiveVote() == null) {
            throw new IllegalStateException("没有进行中的投票");
        }

        VoteSession vote = room.getActiveVote();
        vote.getResults().put(voterRoleId.toHexString(), choice);
        liveGameRoomService.save(room);

        // Store vote record
        VoteRecord record = VoteRecord.builder()
                .id(new ObjectId())
                .gameRoomId(new ObjectId(roomId))
                .voterUserId(voterRoleId)
                .stageNumber(room.getCurrentStage())
                .votedRoleId(null)
                .voteCategory(vote.getTitle())
                .voteReason(choice)
                .voteWeight(1)
                .timestamp(LocalDateTime.now())
                .build();

        try {
            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForList().rightPush(VOTES_KEY_PREFIX + roomId + VOTES_KEY_SUFFIX, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize vote record", e);
        }

        // Broadcast update
        long totalMembers = room.getMembers().stream()
                .filter(m -> m.getRoleId() != null && !m.isDm())
                .count();

        long votedCount = vote.getResults().size();

        messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                Map.of("type", "VOTE_UPDATE",
                        "voteId", vote.getVoteId(),
                        "votedCount", votedCount,
                        "total", totalMembers));

        // Send private vote confirmation to human player
        String voterId = voterRoleId.toHexString();
        String userId = room.getMembers().stream()
                .filter(m -> !m.isAi() && m.getRoleId() != null
                        && m.getRoleId().toHexString().equals(voterId)
                        && m.getUserId() != null)
                .map(m -> m.getUserId().toHexString())
                .findFirst().orElse(null);
        if (userId != null) {
            messagingTemplate.convertAndSendToUser(userId,
                    "/queue/room." + roomId + ".private",
                    Map.of("type", "PRIVATE_VOTE", "choice", choice,
                            "label", "仅你可见"));
        }

        boolean allVoted = votedCount >= totalMembers;
        if (allVoted) {
            closeVoteAndNotifyDm(roomId);
        }
        return allVoted;
    }

    /**
     * 关闭投票会话，清除活跃投票状态并广播投票结果。
     *
     * @param roomId 房间 ID
     * @return 投票结果映射（角色 ID → 选择），无活跃投票时返回空 Map
     */
    public Map<String, String> closeVote(String roomId) {
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null || room.getActiveVote() == null) {
            return Map.of();
        }

        Map<String, String> results = room.getActiveVote().getResults();
        String voteId = room.getActiveVote().getVoteId();

        room.setActiveVote(null);
        room.setVoteSubPhase("RESULT");
        liveGameRoomService.save(room);

        messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                Map.of("type", "VOTE_CLOSED", "voteId", voteId, "results", results));

        log.info("Vote {} closed in room {}: {}", voteId, roomId, results);
        return results;
    }

    /**
     * 关闭投票并通知 DM 处理结果。
     *
     * <p>包含平票检测：若出现平票，提示 DM 让平票角色轮流发言后重新投票。</p>
     *
     * @param roomId 房间 ID
     */
    public void closeVoteAndNotifyDm(String roomId) {
        Map<String, String> results = closeVote(roomId);
        if (results.isEmpty()) return;

        String resultSummary = results.entrySet().stream()
                .map(e -> e.getKey() + " → " + e.getValue())
                .collect(Collectors.joining(", "));

        // Check for tie
        Map<String, Long> voteCounts = results.values().stream()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        long maxVotes = voteCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<String> topChoices = voteCounts.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();

        String tieInfo = "";
        if (topChoices.size() > 1) {
            tieInfo = " 注意：出现平票（" + String.join("、", topChoices) + " 各 " + maxVotes + " 票）。" +
                    "请让平票角色轮流发言，然后再次发起投票。最多重投2次，超过后请强制裁定AI的票。";
        }

        dmExecutor.executeDmAction(roomId, null,
                "投票已结束，结果如下：" + resultSummary + "。" + tieInfo +
                "请宣布投票结果，然后使用 transitionPhase 工具推进流程。");

        log.info("[VoteService] vote closed and DM notified, room={}, results={}", roomId, results);
    }

    /**
     * 标记角色出局。如果所有真人玩家都已出局，自动结束游戏。
     *
     * @param roomId    房间 ID
     * @param roleIdHex 出局角色 ID（十六进制字符串）
     */
    public void eliminateRole(String roomId, String roleIdHex) {
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null) return;

        room.getEliminatedRoleIds().add(roleIdHex);
        liveGameRoomService.save(room);

        log.info("[VoteService] role {} eliminated in room {}", roleIdHex, roomId);

        // Check if all human players are eliminated
        boolean allHumansEliminated = room.getMembers().stream()
                .filter(m -> !m.isAi() && !m.isDm() && m.getRoleId() != null)
                .allMatch(m -> room.getEliminatedRoleIds().contains(m.getRoleId().toHexString()));

        if (allHumansEliminated) {
            log.info("[VoteService] all humans eliminated in room {}, ending game", roomId);
            gameFlowService.endGame(roomId);
        }
    }
}
