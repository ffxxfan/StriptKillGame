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
/**
 * AI 代理编排器 — 消息路由的中央枢纽。
 *
 * <p>监听 {@link ChatMessageEvent}，通过三层响应策略决定哪些 AI 代理需要回复：</p>
 * <ol>
 *   <li><b>Layer 1: @mention</b> — 精确匹配 {@code @角色名}，触发对应代理</li>
 *   <li><b>Layer 2: 名称匹配</b> — 消息中包含角色名子串，触发最多 2 个代理</li>
 *   <li><b>Layer 3: DM 裁决</b> — DM 使用 {@code selectRespondents} 工具选择代理（仅限人类消息）</li>
 * </ol>
 *
 * <p>附加功能：</p>
 * <ul>
 *   <li>AI-to-AI 轮次计数器 — 防止 AI 之间无限对话</li>
 *   <li>空闲计时器 — 自由讨论阶段长时间无人发言时触发 DM 推进</li>
 *   <li>阶段规则执行 — 通过 {@link PhaseRuleEnforcer} 检查发言权限</li>
 *   <li>自我介绍追踪 — 追踪真人玩家是否完成自我介绍</li>
 * </ul>
 *
 * @see com.example.striptkillgamedemo2.ai.executor.AgentExecutor
 * @see com.example.striptkillgamedemo2.ai.executor.DmExecutor
 */
public class AgentOrchestrator {

    private final AgentExecutor agentExecutor;
    private final DmExecutor dmExecutor;
    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;
    private final AiEngineProperties aiProperties;
    private final PhaseRuleEnforcer phaseRuleEnforcer;

    /** @mention 正则匹配模式 */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\S+)");

    /** 每个房间的 AI-to-AI 对话轮次计数器，人类消息重置计数 */
    private final ConcurrentHashMap<String, AtomicInteger> aiRoundCounters = new ConcurrentHashMap<>();

    /** 每个房间的空闲计时器，超时后触发 DM 推进 */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> idleTimers = new ConcurrentHashMap<>();
    /** 空闲计时器调度线程池 */
    private final ScheduledExecutorService idleScheduler = Executors.newScheduledThreadPool(1);

    /**
     * 聊天消息事件处理器 — 编排器的核心入口。
     *
     * <p>接收所有聊天消息，执行阶段规则检查，然后根据当前阶段类型
     * 分发到对应的处理逻辑（轮流发言、自由讨论、搜证等）。</p>
     *
     * @param event 聊天消息事件
     */
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

    /**
     * 处理轮流发言阶段的消息。按预定发言顺序推进，AI 角色自动触发回复。
     *
     * @param roomId 房间 ID
     * @param room   游戏房间运行时状态
     * @param script 剧本数据
     * @param event  聊天消息事件
     */
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

    /**
     * 处理自由讨论阶段的消息。通过三层策略匹配并触发 AI 代理回复。
     *
     * @param roomId       房间 ID
     * @param room         游戏房间运行时状态
     * @param script       剧本数据
     * @param content      消息内容
     * @param senderRoleId 发送者角色 ID
     * @param fromAi       是否来自 AI
     */
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

    /**
     * 在消息内容中查找被提及的 AI 角色（名称子串匹配）。
     *
     * @param content 消息内容
     * @param script  剧本数据
     * @param room    游戏房间运行时状态
     * @return 匹配到的 AI 角色 ID 列表
     */
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

    /**
     * 判断消息内容是否为 DM 请求（包含 @DM、搜证、投票等关键词）。
     *
     * @param content 消息内容
     * @return 是否为 DM 请求
     */
    boolean isDmRequest(String content) {
        if (content.contains("@DM") || content.contains("@主持人")) return true;
        if (content.contains("搜证") || content.contains("搜索") || content.contains("调查")) return true;
        if (content.contains("投票") || content.contains("表决")) return true;
        if (content.contains("自由讨论") || content.contains("开放讨论")) return true;
        return false;
    }

    /**
     * 计算有效的 AI 最大对话轮次。根据真人玩家数量动态调整，玩家越多轮次越少。
     *
     * @param room 游戏房间运行时状态
     * @return 有效的最大轮次数
     */
    int calculateEffectiveMaxRounds(LiveGameRoom room) {
        int configMax = aiProperties.getMaxAiChatRounds();
        long humanCount = room.getMembers().stream()
                .filter(m -> !m.isAi() && !m.isDm())
                .count();
        return (int) Math.max(2, configMax - (humanCount - 1));
    }

    /**
     * 重置（或启动）房间的空闲计时器。在任何消息活动时调用。
     *
     * <p>超时模式下使用 30 秒静默检测自动推进，正常模式使用配置的空闲超时时间。</p>
     *
     * @param roomId 房间 ID
     * @param room   游戏房间运行时状态
     * @param script 剧本数据
     */
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

    /**
     * 从外部信号（用户活动）重置空闲计时器。
     *
     * @param roomId 房间 ID
     */
    public void resetIdleTimer(String roomId) {
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null || room.getScriptId() == null) return;
        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        if (getCurrentPhaseType(script, room) == PhaseType.FREE_CHAT) {
            resetIdleTimer(roomId, room, script);
        }
    }

    /**
     * 取消指定房间的空闲计时器。
     *
     * @param roomId 房间 ID
     */
    public void cancelIdleTimer(String roomId) {
        ScheduledFuture<?> existing = idleTimers.remove(roomId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }
    }

    /**
     * 重置指定房间的 AI 对话轮次计数器。
     *
     * @param roomId 房间 ID
     */
    public void resetRoundCounter(String roomId) {
        AtomicInteger counter = aiRoundCounters.get(roomId);
        if (counter != null) counter.set(0);
    }

    /**
     * 清理房间相关资源（取消计时器、移除轮次计数器）。
     *
     * @param roomId 房间 ID
     */
    public void cleanupRoom(String roomId) {
        cancelIdleTimer(roomId);
        aiRoundCounters.remove(roomId);
    }

    /** 应用关闭时清理调度线程池。 */
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
