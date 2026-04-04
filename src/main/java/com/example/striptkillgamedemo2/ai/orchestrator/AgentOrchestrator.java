package com.example.striptkillgamedemo2.ai.orchestrator;

import com.example.striptkillgamedemo2.ai.event.ChatMessageEvent;
import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.mongo.StagePhase;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.ScriptCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AgentExecutor agentExecutor;
    private final DmExecutor dmExecutor;
    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\S+)");

    @EventListener
    public void onChatMessage(ChatMessageEvent event) {
        // Don't respond to AI messages to prevent loops
        if (event.isFromAi()) return;

        String roomId = event.getRoomId();
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null || room.getScriptId() == null) return;

        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        String content = event.getContent();

        // Check for @DM mention or search/vote keywords
        if (isDmRequest(content)) {
            dmExecutor.executeDmAction(roomId, event.getSenderRoleId(),
                    "玩家消息：" + content);
            return;
        }

        // Get current phase
        PhaseType currentPhase = getCurrentPhaseType(script, room);

        switch (currentPhase) {
            case TURN_BASED, FINAL_STATEMENT -> handleTurnBased(roomId, room, script, event);
            case FREE_CHAT -> handleFreeChat(roomId, room, script, content, event.getSenderRoleId());
            case INVESTIGATION -> {
                // During investigation, only search-related messages route to DM
                if (isDmRequest(content)) {
                    dmExecutor.executeDmAction(roomId, event.getSenderRoleId(),
                            "玩家消息：" + content);
                }
                // Other messages during investigation are ignored by AI agents
            }
            case SCRIPT_READING -> {
                // Silent reading phase — no AI agent responses
            }
            case PRIVATE_TALK -> {
                // Placeholder: route only to participants of the private talk
                // Full implementation deferred to batch B
                handleFreeChat(roomId, room, script, content, event.getSenderRoleId());
            }
            case VOTE -> {
                // No AI agents respond during voting
            }
        }
    }

    private void handleTurnBased(String roomId, LiveGameRoom room, Script script,
                                  ChatMessageEvent event) {
        StagePhase phase = getCurrentStagePhase(script, room);
        if (phase == null || phase.getSpeakOrder() == null) return;

        List<String> order = phase.getSpeakOrder();
        String currentSpeaker = room.getCurrentSpeakerRoleId();

        // Find next speaker
        int currentIndex = -1;
        if (currentSpeaker != null) {
            currentIndex = order.indexOf(currentSpeaker);
        }

        int nextIndex = currentIndex + 1;
        if (nextIndex < order.size()) {
            String nextRoleId = order.get(nextIndex);
            room.setCurrentSpeakerRoleId(nextRoleId);
            liveGameRoomService.save(room);

            // If next speaker is AI, trigger agent
            boolean isAi = room.getMembers().stream()
                    .anyMatch(m -> m.isAi() && m.getRoleId() != null
                            && m.getRoleId().toHexString().equals(nextRoleId));
            if (isAi) {
                agentExecutor.executeAgentReply(roomId, new ObjectId(nextRoleId));
            }
        } else {
            // All speakers done — trigger DM to decide next phase
            room.setCurrentSpeakerRoleId(null);
            liveGameRoomService.save(room);
            dmExecutor.executeDmAction(roomId, event.getSenderRoleId(),
                    "所有角色发言完毕，请决定下一步。使用 transitionPhase 工具：NEXT_PHASE 进入下一环节，或 NEXT_STAGE 推进到下一幕。");
        }
    }

    private void handleFreeChat(String roomId, LiveGameRoom room, Script script,
                                 String content, ObjectId senderRoleId) {
        // Layer 1: @mention — check for explicit @RoleName mentions
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String mentionedName = matcher.group(1);

            if ("DM".equalsIgnoreCase(mentionedName) || "主持人".equals(mentionedName)) {
                dmExecutor.executeDmAction(roomId, senderRoleId, "玩家消息：" + content);
                return;
            }

            for (Role role : script.getRoles()) {
                if (role.getName().equals(mentionedName)) {
                    boolean isAi = room.getMembers().stream()
                            .anyMatch(m -> m.isAi() && Objects.equals(m.getRoleId(), role.getId()));
                    if (isAi) {
                        agentExecutor.executeAgentReply(roomId, role.getId());
                        return;
                    }
                }
            }
        }

        // Layer 2: role name in text — find AI roles whose name appears in the message
        List<ObjectId> namedIds = findNamedAiRoles(content, script, room);
        if (!namedIds.isEmpty()) {
            List<ObjectId> limited = namedIds.size() > 2 ? namedIds.subList(0, 2) : namedIds;
            agentExecutor.executeAgentRepliesSequentially(roomId, limited);
            return;
        }

        // Layer 3: DM decides — ask DM to pick 1-2 relevant respondents
        List<String> candidateNames = script.getRoles().stream()
                .filter(role -> room.getMembers().stream()
                        .anyMatch(m -> m.isAi() && !m.isDm() && Objects.equals(m.getRoleId(), role.getId())))
                .map(Role::getName)
                .toList();
        if (!candidateNames.isEmpty()) {
            dmExecutor.executeDmAction(roomId, senderRoleId,
                    "玩家说：「" + content + "」。请使用 selectRespondents 工具从以下角色中选择 1-2 个最相关的回复：" + candidateNames);
        }
    }

    List<ObjectId> findNamedAiRoles(String content, Script script, LiveGameRoom room) {
        List<ObjectId> matched = new ArrayList<>();
        for (Role role : script.getRoles()) {
            if (role.getName() != null && content.contains(role.getName())) {
                boolean isAi = room.getMembers().stream()
                        .anyMatch(m -> m.isAi() && Objects.equals(m.getRoleId(), role.getId()));
                if (isAi) {
                    matched.add(role.getId());
                }
            }
        }
        return matched;
    }

    boolean isDmRequest(String content) {
        if (content.contains("@DM") || content.contains("@主持人")) return true;
        if (content.contains("搜证") || content.contains("搜索") || content.contains("调查")) return true;
        if (content.contains("投票") || content.contains("表决")) return true;
        if (content.contains("自由讨论") || content.contains("开放讨论")) return true;
        return false;
    }

    private PhaseType getCurrentPhaseType(Script script, LiveGameRoom room) {
        StagePhase phase = getCurrentStagePhase(script, room);
        return phase != null ? phase.getType() : PhaseType.FREE_CHAT;
    }

    private StagePhase getCurrentStagePhase(Script script, LiveGameRoom room) {
        if (script.getStages() == null || room.getCurrentStage() >= script.getStages().size()) return null;
        ScriptStage stage = script.getStages().get(room.getCurrentStage());
        if (stage.getPhases() == null || room.getCurrentPhaseIndex() >= stage.getPhases().size()) return null;
        return stage.getPhases().get(room.getCurrentPhaseIndex());
    }
}
