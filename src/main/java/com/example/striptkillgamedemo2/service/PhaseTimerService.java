package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
import com.example.striptkillgamedemo2.config.AiEngineProperties;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhaseTimerService {

    private final AiEngineProperties properties;
    private final LiveGameRoomService liveGameRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final DmExecutor dmExecutor;

    /** Reminder threshold — warn when this many seconds remain. */
    private static final int REMINDER_BEFORE_SECONDS = 60;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> reminders = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

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
        cancelTimer(roomId);

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
                reminders.remove(roomId);
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
            reminders.put(roomId, reminderFuture);
        }

        // Schedule timeout
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
            timers.remove(roomId);
            String timeoutMsg = phaseLabel(phaseType) + "时间已到。";

            // Notify players
            messagingTemplate.convertAndSend("/topic/room." + roomId,
                    Map.of("type", "PHASE_TIMER_EXPIRED",
                            "phaseType", phaseType.name(),
                            "content", timeoutMsg));

            // Notify DM Agent to transition
            dmExecutor.executeDmAction(roomId, null,
                    timeoutMsg + "请立即使用 transitionPhase 工具（action=NEXT_PHASE）推进到下一个环节。");

            log.info("[PhaseTimer] expired, room={}, phase={}", roomId, phaseType);
        }, duration, TimeUnit.SECONDS);
        timers.put(roomId, timeoutFuture);

        log.info("[PhaseTimer] started, room={}, phase={}, duration={}s", roomId, phaseType, duration);
    }

    public void startVoteTimer(String roomId) {
        cancelTimer(roomId);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            timers.remove(roomId);
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                    Map.of("type", "VOTE_CLOSED", "reason", "投票超时自动关闭"));
        }, properties.getVoteTimeoutSeconds(), TimeUnit.SECONDS);
        timers.put(roomId, future);
    }

    public void cancelTimer(String roomId) {
        ScheduledFuture<?> existing = timers.remove(roomId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }
        ScheduledFuture<?> reminder = reminders.remove(roomId);
        if (reminder != null && !reminder.isDone()) {
            reminder.cancel(false);
        }
    }

    private int getDefaultDuration(PhaseType phaseType) {
        return switch (phaseType) {
            case FREE_CHAT -> properties.getFreeChatTimeoutSeconds();
            case TURN_BASED -> properties.getTurnTimeoutSeconds();
            case VOTE -> properties.getVoteTimeoutSeconds();
            default -> properties.getFreeChatTimeoutSeconds();
        };
    }

    private String phaseLabel(PhaseType phaseType) {
        return switch (phaseType) {
            case FREE_CHAT -> "自由讨论";
            case TURN_BASED -> "轮流发言";
            case VOTE -> "投票";
            default -> phaseType.name();
        };
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
