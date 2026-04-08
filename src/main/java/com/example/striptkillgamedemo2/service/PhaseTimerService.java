package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
import com.example.striptkillgamedemo2.config.AiEngineProperties;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class PhaseTimerService {

    private final AiEngineProperties properties;
    private final LiveGameRoomService liveGameRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final DmExecutor dmExecutor;
    private final VoteService voteService;

    public PhaseTimerService(AiEngineProperties properties,
                             LiveGameRoomService liveGameRoomService,
                             SimpMessagingTemplate messagingTemplate,
                             @Lazy DmExecutor dmExecutor,
                             VoteService voteService) {
        this.properties = properties;
        this.liveGameRoomService = liveGameRoomService;
        this.messagingTemplate = messagingTemplate;
        this.dmExecutor = dmExecutor;
        this.voteService = voteService;
    }

    /** Reminder threshold — warn when this many seconds remain. */
    private static final int REMINDER_BEFORE_SECONDS = 60;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> reminders = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private String timerKey(String roomId, PhaseType phaseType) {
        return roomId + ":" + phaseType.name();
    }

    /**
     * Unified phase timer. Starts a countdown for the given phase type.
     * Duration is taken from StagePhase.timeLimitSeconds; if 0, falls back to config defaults.
     *
     * Behaviour:
     *   1. Broadcasts PHASE_TIMER_START to frontend (so UI can show countdown)
     *   2. Schedules a reminder at (duration - 60s) to warn players and DM
     *   3. Schedules a timeout at duration to tell DM to transition
     */
    public void startPhaseTimer(String roomId, PhaseType phaseType, int durationSeconds) {
        String key = timerKey(roomId, phaseType);
        cancelTimerByKey(key);

        int duration = durationSeconds > 0 ? durationSeconds : getDefaultDuration(phaseType);
        if (duration <= 0) return;

        // Broadcast timer start to frontend
        messagingTemplate.convertAndSend("/topic/room." + roomId,
                Map.of("type", "PHASE_TIMER_START",
                        "phaseType", phaseType.name(),
                        "durationSeconds", duration));

        // Schedule reminder (if duration is long enough)
        if (duration > REMINDER_BEFORE_SECONDS) {
            int reminderDelay = duration - REMINDER_BEFORE_SECONDS;
            ScheduledFuture<?> reminderFuture = scheduler.schedule(() -> {
                reminders.remove(key);
                String reminderMsg = phaseLabel(phaseType) + "还剩 " + REMINDER_BEFORE_SECONDS + " 秒，请准备收尾。";

                // Notify players
                messagingTemplate.convertAndSend("/topic/room." + roomId,
                        Map.of("type", "PHASE_TIMER_REMINDER",
                                "remainingSeconds", REMINDER_BEFORE_SECONDS,
                                "content", reminderMsg));

                // Notify DM Agent
                dmExecutor.executeDmAction(roomId, null,
                        reminderMsg + "请适时引导讨论收尾，准备使用 transitionPhase 工具推进流程。");

                log.info("[PhaseTimer] reminder sent, room={}, phase={}, remaining={}s",
                        roomId, phaseType, REMINDER_BEFORE_SECONDS);
            }, reminderDelay, TimeUnit.SECONDS);
            reminders.put(key, reminderFuture);
        }

        // Schedule timeout
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
            timers.remove(key);
            String timeoutMsg = phaseLabel(phaseType) + "时间已到。";

            messagingTemplate.convertAndSend("/topic/room." + roomId,
                    Map.of("type", "PHASE_TIMER_EXPIRED",
                            "phaseType", phaseType.name(),
                            "content", timeoutMsg));

            if (phaseType == PhaseType.VOTE) {
                voteService.closeVoteAndNotifyDm(roomId);
            } else if (phaseType == PhaseType.FREE_CHAT) {
                // Enter overtime mode — set flag and let idle timer handle transition
                LiveGameRoom room = liveGameRoomService.get(roomId);
                if (room != null) {
                    room.setPhaseOvertime(true);
                    liveGameRoomService.save(room);
                }
                dmExecutor.executeDmAction(roomId, null,
                        timeoutMsg + "如果还有人在讨论，请等待讨论自然结束。如果30秒内无人发言，系统将自动结束本环节。");
            } else {
                dmExecutor.executeDmAction(roomId, null,
                        timeoutMsg + "请立即使用 transitionPhase 工具（action=NEXT_PHASE）推进到下一个环节。");
            }

            log.info("[PhaseTimer] expired, room={}, phase={}", roomId, phaseType);
        }, duration, TimeUnit.SECONDS);
        timers.put(key, timeoutFuture);

        log.info("[PhaseTimer] started, room={}, phase={}, duration={}s", roomId, phaseType, duration);
    }

    public void cancelTimer(String roomId, PhaseType phaseType) {
        String key = timerKey(roomId, phaseType);
        cancelTimerByKey(key);
    }

    public void cancelAllTimers(String roomId) {
        String prefix = roomId + ":";
        timers.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .toList()
                .forEach(this::cancelTimerByKey);
        reminders.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .toList()
                .forEach(k -> {
                    ScheduledFuture<?> f = reminders.remove(k);
                    if (f != null && !f.isDone()) f.cancel(false);
                });
    }

    private void cancelTimerByKey(String key) {
        ScheduledFuture<?> existing = timers.remove(key);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }
        ScheduledFuture<?> reminder = reminders.remove(key);
        if (reminder != null && !reminder.isDone()) {
            reminder.cancel(false);
        }
    }

    private int getDefaultDuration(PhaseType phaseType) {
        return switch (phaseType) {
            case SCRIPT_READING -> properties.getScriptReadingTimeoutSeconds();
            case TURN_BASED -> properties.getTurnTimeoutSeconds();
            case FREE_CHAT -> properties.getFreeChatTimeoutSeconds();
            case INVESTIGATION -> properties.getInvestigationTimeoutSeconds();
            case VOTE -> properties.getVoteTimeoutSeconds();
        };
    }

    private String phaseLabel(PhaseType phaseType) {
        return switch (phaseType) {
            case SCRIPT_READING -> "阅读剧本";
            case TURN_BASED -> "轮流发言";
            case FREE_CHAT -> "自由讨论";
            case INVESTIGATION -> "搜证";
            case VOTE -> "投票";
        };
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
