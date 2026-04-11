package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.config.AiEngineProperties;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.entity.redis.VoteSession;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.PhaseTimerService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
/**
 * DM 工具：发起投票。
 *
 * <p>创建投票会话，广播投票信令给所有玩家，并异步触发所有存活的 AI 代理进行投票。
 * 投票有超时限制，由 {@link PhaseTimerService} 管理。</p>
 *
 * <p>仅在 {@link PhaseType#FREE_CHAT} 和 {@link PhaseType#VOTE} 阶段可用。
 * 同一时间只能有一个活跃的投票会话。</p>
 *
 * @see VoteService
 */
public class InitiateVoteTool implements DmTool {

    private final LiveGameRoomService liveGameRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AiEngineProperties aiEngineProperties;
    private final AgentExecutor agentExecutor;
    private final PhaseTimerService phaseTimerService;

    /**
     * 构造投票工具。使用 {@code @Lazy} 注入 AgentExecutor 以打破循环依赖。
     */
    public InitiateVoteTool(LiveGameRoomService liveGameRoomService,
                            SimpMessagingTemplate messagingTemplate,
                            AiEngineProperties aiEngineProperties,
                            @Lazy AgentExecutor agentExecutor,
                            PhaseTimerService phaseTimerService) {
        this.liveGameRoomService = liveGameRoomService;
        this.messagingTemplate = messagingTemplate;
        this.aiEngineProperties = aiEngineProperties;
        this.agentExecutor = agentExecutor;
        this.phaseTimerService = phaseTimerService;
    }

    /**
     * 工具输入参数。
     */
    @Data
    public static class Input {
        /** 房间 ID */
        private String roomId;
        /** 投票标题 */
        private String title;
        /** 投票选项列表 */
        private List<String> options;
    }

    @Override
    public Set<PhaseType> allowedPhases() {
        return Set.of(PhaseType.FREE_CHAT, PhaseType.VOTE);
    }

    @Override
    public String name() {
        return "initiateVote";
    }

    @Override
    public String description() {
        return "发起投票。提供投票标题和选项列表，将广播投票信令给所有玩家。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        LiveGameRoom room = ctx.getRoom();

        // Check no active vote
        if (room.getActiveVote() != null) {
            return Map.of("success", false, "error", "已有进行中的投票，请等待结束后再发起");
        }

        VoteSession vote = VoteSession.builder()
                .voteId(new ObjectId().toHexString())
                .title(input.getTitle())
                .options(input.getOptions())
                .deadline(LocalDateTime.now().plusSeconds(aiEngineProperties.getVoteTimeoutSeconds()))
                .build();

        room.setActiveVote(vote);
        room.setVoteSubPhase("VOTING");
        liveGameRoomService.save(room);

        messagingTemplate.convertAndSend(
                "/topic/room." + room.getRoomId(),
                Map.of("type", "VOTE_OPEN", "vote", vote)
        );

        // Start vote timer
        phaseTimerService.startPhaseTimer(room.getRoomId(), PhaseType.VOTE,
                aiEngineProperties.getVoteTimeoutSeconds());

        // Trigger AI agents to vote asynchronously
        List<ObjectId> aiRoleIds = room.getMembers().stream()
                .filter(m -> m.isAi() && !m.isDm() && m.getRoleId() != null)
                .filter(m -> !room.getEliminatedRoleIds().contains(m.getRoleId().toHexString()))
                .map(Member::getRoleId)
                .toList();
        for (ObjectId aiRoleId : aiRoleIds) {
            agentExecutor.executeAgentVote(room.getRoomId(), aiRoleId,
                    input.getTitle(), input.getOptions());
        }

        log.info("[initiateVote] roomId={}, title={}, options={}", room.getRoomId(), input.getTitle(), input.getOptions());
        return Map.of("success", true, "voteId", vote.getVoteId(), "deadline", vote.getDeadline().toString());
    }
}
