package com.example.striptkillgamedemo2.ai.orchestrator;

import com.example.striptkillgamedemo2.ai.event.ChatMessageEvent;
import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
import com.example.striptkillgamedemo2.config.AiEngineProperties;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.mongo.StagePhase;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.PhaseRuleEnforcer;
import com.example.striptkillgamedemo2.service.ScriptCacheService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final AiEngineProperties aiProperties;
    private final PhaseRuleEnforcer phaseRuleEnforcer;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\S+)");

    // Feature 1: AI-to-AI round counter per room
    private final ConcurrentHashMap<String, AtomicInteger> aiRoundCounters = new ConcurrentHashMap<>();

    // Feature 2: Idle timer per room
    private final ConcurrentHashMap<String, ScheduledFuture<?>> idleTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService idleScheduler = Executors.newScheduledThreadPool(1);

    @EventListener
    public void onChatMessage(ChatMessageEvent event) {
        String roomId = event.getRoomId();
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null || room.getScriptId() == null) return;

        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        String content = event.getContent();
        boolean fromAi = event.isFromAi();

        // Reset idle timer on any message (human, AI, or DM)
        PhaseType currentPhase = getCurrentPhaseType(script, room);

        // Phase rule enforcement — check before any routing
        String senderRoleIdHex = event.getSenderRoleId() != null ? event.getSenderRoleId().toHexString() : null;
        if (senderRoleIdHex != null) {
            PhaseRuleEnforcer.SpeakCheck check = phaseRuleEnforcer.checkCanSpeak(
                    room, currentPhase, senderRoleIdHex, fromAi);
            if (check == PhaseRuleEnforcer.SpeakCheck.BLOCKED_SILENT) {
                log.info("[PhaseRule] silently blocked, room={}, role={}, phase={}", roomId, senderRoleIdHex, currentPhase);
                return;
            }
            if (check == PhaseRuleEnforcer.SpeakCheck.BLOCKED_DM_REMIND) {
                String remindMsg = buildRemindMessage(currentPhase, senderRoleIdHex, room);
                dmExecutor.executeDmAction(roomId, event.getSenderRoleId(), remindMsg);
                return;
            }
        }

        if (currentPhase == PhaseType.FREE_CHAT) {
            resetIdleTimer(roomId, room, script);
        }

        // Human message resets AI round counter
        if (!fromAi) {
            aiRoundCounters.computeIfAbsent(roomId, k -> new AtomicInteger(0)).set(0);
        }

        // Track self-introduction completion for human players
        if (!fromAi && senderRoleIdHex != null
                && room.getAwaitingIntroRoleIds() != null
                && room.getAwaitingIntroRoleIds().remove(senderRoleIdHex)) {
            liveGameRoomService.save(room);
            if (room.getAwaitingIntroRoleIds().isEmpty()) {
                log.info("[Intro] all human players have introduced in room={}", roomId);
                dmExecutor.executeDmAction(roomId, null,
                        "所有真人玩家已完成自我介绍。请使用 transitionPhase（NEXT_PHASE）进入下一环节。");
                return;
            }
        }

        // AI message: increment round counter, drop if over limit
        if (fromAi) {
            AtomicInteger counter = aiRoundCounters.computeIfAbsent(roomId, k -> new AtomicInteger(0));
            int currentRound = counter.incrementAndGet();
            int effectiveMax = calculateEffectiveMaxRounds(room);

            if (currentRound > effectiveMax) {
                log.info("[AI-Round] room={} round={} exceeds max={}, dropping", roomId, currentRound, effectiveMax);
                return;
            }
        }

        // DM requests only from human players
        if (!fromAi && isDmRequest(content)) {
            dmExecutor.executeDmAction(roomId, event.getSenderRoleId(),
                    "玩家消息：" + content);
            return;
        }

        // Mark AI as spoken in TURN_BASED
        if (fromAi && currentPhase == PhaseType.TURN_BASED && senderRoleIdHex != null) {
            phaseRuleEnforcer.markSpoken(room, senderRoleIdHex);
            liveGameRoomService.save(room);
        }

        // Mark AI as spoken in VOTE STATEMENT sub-phase
        if (fromAi && currentPhase == PhaseType.VOTE
                && "STATEMENT".equals(room.getVoteSubPhase()) && senderRoleIdHex != null) {
            phaseRuleEnforcer.markSpoken(room, senderRoleIdHex);
            liveGameRoomService.save(room);
        }

        switch (currentPhase) {
            case TURN_BASED -> {
                if (!fromAi) handleTurnBased(roomId, room, script, event);
            }
            case FREE_CHAT -> handleFreeChat(roomId, room, script, content,
                    event.getSenderRoleId(), fromAi);
            case INVESTIGATION -> {
                if (isDmRequest(content)) {
                    String senderInfo = buildSenderInfo(event.getSenderRoleId(), script, fromAi);
                    dmExecutor.executeDmAction(roomId, event.getSenderRoleId(),
                            senderInfo + "的搜证请求：" + content +
                            "\n请直接使用 authorizeSearch 工具为该角色授权搜证，" +
                            "roleId 为「" + (senderRoleIdHex != null ? senderRoleIdHex : "unknown") + "」，" +
                            "不要反问玩家身份。");
                }
                // No AI-to-AI chain in INVESTIGATION
            }
            case SCRIPT_READING -> { }
            case VOTE -> { }
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
                                 String content, ObjectId senderRoleId, boolean fromAi) {
        // Compute redirect flag at trigger time: will the next AI response(s) hit the round limit?
        AtomicInteger counter = aiRoundCounters.computeIfAbsent(roomId, k -> new AtomicInteger(0));
        int effectiveMax = calculateEffectiveMaxRounds(room);

        // Layer 1: @mention
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String mentionedName = matcher.group(1);

            if (!fromAi && ("DM".equalsIgnoreCase(mentionedName) || "主持人".equals(mentionedName))) {
                dmExecutor.executeDmAction(roomId, senderRoleId, "玩家消息：" + content);
                return;
            }

            for (Role role : script.getRoles()) {
                if (role.getName().equals(mentionedName)) {
                    if (Objects.equals(role.getId(), senderRoleId)) continue;
                    boolean isAi = room.getMembers().stream()
                            .anyMatch(m -> m.isAi() && Objects.equals(m.getRoleId(), role.getId()));
                    if (isAi) {
                        if (phaseRuleEnforcer.isTargetingEliminatedAi(room, role.getId().toHexString())) {
                            dmExecutor.executeDmAction(roomId, senderRoleId,
                                    "玩家 @了已出局的角色「" + mentionedName + "」，请提醒该角色已被投出。");
                            return;
                        }
                        boolean lastRound = counter.get() + 1 >= effectiveMax;
                        agentExecutor.executeAgentReply(roomId, role.getId(), lastRound);
                        return;
                    }
                }
            }
        }

        // Layer 2: role name in text
        List<ObjectId> namedIds = findNamedAiRoles(content, script, room);
        namedIds.removeIf(id -> Objects.equals(id, senderRoleId));
        if (!namedIds.isEmpty()) {
            List<ObjectId> limited = namedIds.size() > 2 ? namedIds.subList(0, 2) : namedIds;
            boolean lastRound = counter.get() + limited.size() >= effectiveMax;
            agentExecutor.executeAgentRepliesSequentially(roomId, limited, lastRound);
            return;
        }

        // Layer 3: DM decides — only for human messages
        if (fromAi) return;

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

    private String buildSenderInfo(ObjectId senderRoleId, Script script, boolean fromAi) {
        if (senderRoleId == null) return fromAi ? "AI角色" : "玩家";
        return script.getRoles().stream()
                .filter(r -> Objects.equals(r.getId(), senderRoleId))
                .map(r -> (fromAi ? "AI角色" : "玩家") + "「" + r.getName() + "」")
                .findFirst()
                .orElse(fromAi ? "AI角色" : "玩家");
    }

    private String buildRemindMessage(PhaseType phase, String roleIdHex, LiveGameRoom room) {
        if (room.getEliminatedRoleIds().contains(roleIdHex)) {
            return "提醒：该玩家已被投出局，其发言不影响游戏流程。请温和提醒。";
        }
        return switch (phase) {
            case SCRIPT_READING -> "有玩家在阅读剧本阶段发言了，请提醒他们保持安静阅读。";
            default -> "当前环节不允许该操作，请提醒玩家。";
        };
    }

    boolean isDmRequest(String content) {
        if (content.contains("@DM") || content.contains("@主持人")) return true;
        if (content.contains("搜证") || content.contains("搜索") || content.contains("调查")) return true;
        if (content.contains("投票") || content.contains("表决")) return true;
        if (content.contains("自由讨论") || content.contains("开放讨论")) return true;
        return false;
    }

    int calculateEffectiveMaxRounds(LiveGameRoom room) {
        int configMax = aiProperties.getMaxAiChatRounds();
        long humanCount = room.getMembers().stream()
                .filter(m -> !m.isAi() && !m.isDm())
                .count();
        return (int) Math.max(2, configMax - (humanCount - 1));
    }

    /** Reset (or start) the idle timer for a room. Called on any activity. */
    public void resetIdleTimer(String roomId, LiveGameRoom room, Script script) {
        cancelIdleTimer(roomId);

        // In overtime mode, use 30s silence detection that auto-transitions
        int timeout;
        boolean isOvertime = room.isPhaseOvertime();
        if (isOvertime) {
            timeout = 30;
        } else {
            timeout = aiProperties.getIdleTimeoutSeconds();
        }
        if (timeout <= 0) return;

        ScheduledFuture<?> future = idleScheduler.schedule(() -> {
            LiveGameRoom currentRoom = liveGameRoomService.get(roomId);
            if (currentRoom == null) return;
            Script currentScript = scriptCacheService.getScript(new ObjectId(currentRoom.getScriptId()));
            PhaseType phase = getCurrentPhaseType(currentScript, currentRoom);
            if (phase != PhaseType.FREE_CHAT) return;

            if (currentRoom.isPhaseOvertime()) {
                // Overtime + silence → auto-transition
                log.info("[IdleTimer] overtime silence detected, auto-ending FREE_CHAT for room={}", roomId);
                dmExecutor.executeDmAction(roomId, null,
                        "自由讨论已超时且30秒内无人发言，请立即使用 transitionPhase 工具（action=NEXT_PHASE）结束本环节。");
            } else {
                log.info("[IdleTimer] fired for room={}, triggering DM", roomId);
                dmExecutor.executeDmAction(roomId, null,
                        "自由讨论中所有参与者已沉默超过一分钟。请主动推进游戏进程：可以发起新话题、总结讨论要点、或使用 transitionPhase 工具推进到下一环节。");
            }
        }, timeout, TimeUnit.SECONDS);

        idleTimers.put(roomId, future);
    }

    /** Reset idle timer from external signal (user activity). */
    public void resetIdleTimer(String roomId) {
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null || room.getScriptId() == null) return;
        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        if (getCurrentPhaseType(script, room) == PhaseType.FREE_CHAT) {
            resetIdleTimer(roomId, room, script);
        }
    }

    public void cancelIdleTimer(String roomId) {
        ScheduledFuture<?> existing = idleTimers.remove(roomId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }
    }

    public void resetRoundCounter(String roomId) {
        AtomicInteger counter = aiRoundCounters.get(roomId);
        if (counter != null) counter.set(0);
    }

    public void cleanupRoom(String roomId) {
        cancelIdleTimer(roomId);
        aiRoundCounters.remove(roomId);
    }

    @PreDestroy
    public void shutdown() {
        idleScheduler.shutdownNow();
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
