package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
import com.example.striptkillgamedemo2.config.AiEngineProperties;
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

    private final ConcurrentHashMap<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public void startTurnTimer(String roomId, String roleId) {
        cancelTimer(roomId);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            log.info("Turn timeout for role {} in room {}", roleId, roomId);
            timers.remove(roomId);
            dmExecutor.executeDmAction(roomId, null,
                    "角色 " + roleId + " 发言超时，请推进到下一位发言者。使用 assignTurn 工具。");
        }, properties.getTurnTimeoutSeconds(), TimeUnit.SECONDS);
        timers.put(roomId, future);
    }

    public void startFreeChatTimer(String roomId) {
        cancelTimer(roomId);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            log.info("Free chat timeout in room {}", roomId);
            timers.remove(roomId);
            dmExecutor.executeDmAction(roomId, null,
                    "自由讨论时间结束。请使用 endFreeChat 工具结束讨论，并决定是否发起投票。");
        }, properties.getFreeChatTimeoutSeconds(), TimeUnit.SECONDS);
        timers.put(roomId, future);
    }

    public void startVoteTimer(String roomId) {
        cancelTimer(roomId);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            log.info("Vote timeout in room {}", roomId);
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
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
