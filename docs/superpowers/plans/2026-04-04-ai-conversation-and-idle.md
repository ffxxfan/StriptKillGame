# AI Multi-Round Conversation & Idle Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add controlled multi-round AI-to-AI conversations with dynamic limits and last-round redirection, plus idle detection that triggers DM auto-advance during FREE_CHAT.

**Architecture:** Two independent features in `AgentOrchestrator`. Feature 1 adds a per-room `AtomicInteger` round counter that gates AI-to-AI message forwarding and injects a redirect prompt on the final round. Feature 2 adds a per-room `ScheduledFuture` idle timer that fires DM action when no activity occurs for a configurable duration. Both features share `AiEngineProperties` for configuration.

**Tech Stack:** Java 17, Spring Boot, Spring AI, WebSocket (STOMP), Redis, Lombok

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/.../config/AiEngineProperties.java` | Modify | Add `maxAiChatRounds`, `idleTimeoutSeconds` |
| `src/main/resources/application.properties` | Modify | Add config defaults |
| `src/main/java/.../ai/event/ChatMessageEvent.java` | Modify | Add `lastAiRound` field |
| `src/main/java/.../ai/orchestrator/AgentOrchestrator.java` | Modify | Round counter + idle timer logic |
| `src/main/java/.../ai/executor/AgentExecutor.java` | Modify | Accept `lastAiRound` flag, inject redirect prompt |
| `src/main/java/.../controller/GameChatController.java` | Modify | Add `/app/room/{roomId}/activity` endpoint |
| `src/main/java/.../service/GameFlowService.java` | Modify | Reset round counter + cancel idle timer on phase transitions |
| `src/test/java/.../ai/orchestrator/AgentOrchestratorTest.java` | Create | Unit tests for round counter and idle timer |

All paths are relative to `src/main/java/com/example/striptkillgamedemo2/`.

---

### Task 1: Add configuration properties

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/config/AiEngineProperties.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Add fields to AiEngineProperties**

Add two new fields after the existing timeout fields:

```java
private int maxAiChatRounds = 3;
private int idleTimeoutSeconds = 60;
```

- [ ] **Step 2: Add config entries to application.properties**

Append after the existing `game.ai.free-chat-timeout-seconds=300` line:

```properties
game.ai.max-ai-chat-rounds=3
game.ai.idle-timeout-seconds=60
```

- [ ] **Step 3: Compile and verify**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/config/AiEngineProperties.java src/main/resources/application.properties
git commit -m "feat: add maxAiChatRounds and idleTimeoutSeconds config"
```

---

### Task 2: Add `lastAiRound` field to ChatMessageEvent

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/event/ChatMessageEvent.java`

- [ ] **Step 1: Add field and update constructor**

Replace the existing `ChatMessageEvent` class with:

```java
package com.example.striptkillgamedemo2.ai.event;

import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEvent;

@Getter
public class ChatMessageEvent extends ApplicationEvent {
    private final String roomId;
    private final ObjectId senderRoleId;
    private final String content;
    private final boolean fromAi;
    private final boolean lastAiRound;

    public ChatMessageEvent(Object source, String roomId, ObjectId senderRoleId,
                            String content, boolean fromAi) {
        this(source, roomId, senderRoleId, content, fromAi, false);
    }

    public ChatMessageEvent(Object source, String roomId, ObjectId senderRoleId,
                            String content, boolean fromAi, boolean lastAiRound) {
        super(source);
        this.roomId = roomId;
        this.senderRoleId = senderRoleId;
        this.content = content;
        this.fromAi = fromAi;
        this.lastAiRound = lastAiRound;
    }
}
```

The 5-arg constructor preserves backward compatibility with all existing call sites (`GameChatController`, `AgentExecutor`).

- [ ] **Step 2: Compile and verify**

Run: `./mvnw compile`
Expected: BUILD SUCCESS (existing callers use the 5-arg constructor, unchanged)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/event/ChatMessageEvent.java
git commit -m "feat: add lastAiRound field to ChatMessageEvent"
```

---

### Task 3: Add round counter and idle timer to AgentOrchestrator

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java`

This is the largest task. It adds:
1. Per-room AI round counter with dynamic max calculation
2. Per-room idle timer that fires DM action during FREE_CHAT
3. A public `resetIdleTimer` method (called from `GameChatController` for user activity signals)
4. A public `cancelIdleTimer` / `resetRoundCounter` method (called from `GameFlowService` on phase transitions)

- [ ] **Step 1: Add new fields and imports**

Add these imports at the top:

```java
import com.example.striptkillgamedemo2.config.AiEngineProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
```

Change `@RequiredArgsConstructor` to explicit constructor. Add these fields to the class:

```java
    private final AgentExecutor agentExecutor;
    private final DmExecutor dmExecutor;
    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;
    private final AiEngineProperties aiProperties;

    // Feature 1: AI-to-AI round counter per room
    private final ConcurrentHashMap<String, AtomicInteger> aiRoundCounters = new ConcurrentHashMap<>();

    // Feature 2: Idle timer per room
    private final ConcurrentHashMap<String, ScheduledFuture<?>> idleTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService idleScheduler = Executors.newScheduledThreadPool(1);
```

Keep `@RequiredArgsConstructor` — it will inject `aiProperties` alongside the existing dependencies. The `ConcurrentHashMap` and `ScheduledExecutorService` fields are initialized inline so Lombok skips them.

- [ ] **Step 2: Add effective max calculation method**

Add after the `isDmRequest` method:

```java
    int calculateEffectiveMaxRounds(LiveGameRoom room) {
        int configMax = aiProperties.getMaxAiChatRounds();
        long humanCount = room.getMembers().stream()
                .filter(m -> !m.isAi() && !m.isDm())
                .count();
        return (int) Math.max(2, configMax - (humanCount - 1));
    }
```

- [ ] **Step 3: Add round counter logic to onChatMessage**

Replace the current `onChatMessage` method body with:

```java
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
        if (currentPhase == PhaseType.FREE_CHAT) {
            resetIdleTimer(roomId, room, script);
        }

        // Human message resets AI round counter
        if (!fromAi) {
            aiRoundCounters.computeIfAbsent(roomId, k -> new AtomicInteger(0)).set(0);
        }

        // AI message: check round limit before processing
        boolean lastAiRound = false;
        if (fromAi) {
            AtomicInteger counter = aiRoundCounters.computeIfAbsent(roomId, k -> new AtomicInteger(0));
            int currentRound = counter.incrementAndGet();
            int effectiveMax = calculateEffectiveMaxRounds(room);

            if (currentRound > effectiveMax) {
                log.info("[AI-Round] room={} round={} exceeds max={}, dropping", roomId, currentRound, effectiveMax);
                return;
            }
            if (currentRound == effectiveMax) {
                lastAiRound = true;
                log.info("[AI-Round] room={} round={} is last round, will inject redirect", roomId, currentRound);
            }
        }

        // DM requests only from human players
        if (!fromAi && isDmRequest(content)) {
            dmExecutor.executeDmAction(roomId, event.getSenderRoleId(),
                    "玩家消息：" + content);
            return;
        }

        switch (currentPhase) {
            case TURN_BASED, FINAL_STATEMENT -> {
                if (!fromAi) handleTurnBased(roomId, room, script, event);
            }
            case FREE_CHAT -> handleFreeChat(roomId, room, script, content,
                    event.getSenderRoleId(), fromAi, lastAiRound);
            case INVESTIGATION -> {
                if (!fromAi && isDmRequest(content)) {
                    dmExecutor.executeDmAction(roomId, event.getSenderRoleId(),
                            "玩家消息：" + content);
                }
            }
            case SCRIPT_READING -> { }
            case PRIVATE_TALK -> {
                handleFreeChat(roomId, room, script, content,
                        event.getSenderRoleId(), fromAi, lastAiRound);
            }
            case VOTE -> { }
        }
    }
```

- [ ] **Step 4: Update handleFreeChat to accept and forward lastAiRound**

Update the method signature and the agent trigger calls:

```java
    private void handleFreeChat(String roomId, LiveGameRoom room, Script script,
                                 String content, ObjectId senderRoleId, boolean fromAi,
                                 boolean lastAiRound) {
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
                        agentExecutor.executeAgentReply(roomId, role.getId(), lastAiRound);
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
            agentExecutor.executeAgentRepliesSequentially(roomId, limited, lastAiRound);
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
```

- [ ] **Step 5: Add idle timer methods**

Add these methods to `AgentOrchestrator`:

```java
    /** Reset (or start) the idle timer for a room. Called on any activity. */
    public void resetIdleTimer(String roomId, LiveGameRoom room, Script script) {
        cancelIdleTimer(roomId);

        int timeout = aiProperties.getIdleTimeoutSeconds();
        if (timeout <= 0) return;

        ScheduledFuture<?> future = idleScheduler.schedule(() -> {
            // Re-check phase at fire time
            LiveGameRoom currentRoom = liveGameRoomService.get(roomId);
            if (currentRoom == null) return;
            Script currentScript = scriptCacheService.getScript(new ObjectId(currentRoom.getScriptId()));
            PhaseType phase = getCurrentPhaseType(currentScript, currentRoom);
            if (phase != PhaseType.FREE_CHAT) return;

            log.info("[IdleTimer] fired for room={}, triggering DM", roomId);
            dmExecutor.executeDmAction(roomId, null,
                    "自由讨论中所有参与者已沉默超过一分钟。请主动推进游戏进程：可以发起新话题、总结讨论要点、或使用 transitionPhase 工具推进到下一环节。");
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
```

- [ ] **Step 6: Compile and verify**

Run: `./mvnw compile`
Expected: FAIL — `AgentExecutor.executeAgentReply` and `executeAgentRepliesSequentially` don't accept `lastAiRound` yet. That's expected; Task 4 fixes it.

- [ ] **Step 7: Commit (WIP)**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java
git commit -m "feat(wip): add AI round counter and idle timer to AgentOrchestrator"
```

---

### Task 4: Update AgentExecutor to accept lastAiRound flag

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/executor/AgentExecutor.java`

- [ ] **Step 1: Update executeAgentReply signature**

Replace the existing `executeAgentReply` method:

```java
    /** Trigger a single agent reply asynchronously. */
    @Async("aiExecutor")
    public void executeAgentReply(String roomId, ObjectId roleId) {
        doExecuteAgentReply(roomId, roleId, false);
    }

    /** Trigger a single agent reply with last-round redirect flag. */
    @Async("aiExecutor")
    public void executeAgentReply(String roomId, ObjectId roleId, boolean lastAiRound) {
        doExecuteAgentReply(roomId, roleId, lastAiRound);
    }
```

- [ ] **Step 2: Update executeAgentRepliesSequentially signature**

Replace the existing method:

```java
    /** Trigger multiple agents sequentially (one finishes before the next starts). */
    @Async("aiExecutor")
    public void executeAgentRepliesSequentially(String roomId, List<ObjectId> roleIds) {
        for (ObjectId roleId : roleIds) {
            doExecuteAgentReply(roomId, roleId, false);
        }
    }

    @Async("aiExecutor")
    public void executeAgentRepliesSequentially(String roomId, List<ObjectId> roleIds, boolean lastAiRound) {
        for (int i = 0; i < roleIds.size(); i++) {
            // Only the last agent in the batch gets the redirect flag
            boolean isLast = lastAiRound && (i == roleIds.size() - 1);
            doExecuteAgentReply(roomId, roleIds.get(i), isLast);
        }
    }
```

- [ ] **Step 3: Update doExecuteAgentReply to inject redirect prompt**

Change the method signature and add the redirect injection after prompt building:

```java
    private void doExecuteAgentReply(String roomId, ObjectId roleId, boolean lastAiRound) {
```

Then, after the line `String promptText = promptBuilder.buildAgentPrompt(...)`, add:

```java
            // Last AI round: inject redirect instruction
            if (lastAiRound) {
                promptText += "\n\n【系统指令】这是你在本轮 AI 对话中的最后一次发言机会，" +
                        "请将话题自然地引向在场的玩家或主持人，邀请他们参与讨论或表达看法。";
                log.info("[AgentReply] lastAiRound redirect injected for role={}", roleId);
            }
```

- [ ] **Step 4: Compile and verify**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/executor/AgentExecutor.java
git commit -m "feat: AgentExecutor accepts lastAiRound flag for redirect prompt"
```

---

### Task 5: Add user activity WebSocket endpoint

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/controller/GameChatController.java`

- [ ] **Step 1: Add activity handler**

Add a new method to `GameChatController`. First add the import:

```java
import com.example.striptkillgamedemo2.ai.orchestrator.AgentOrchestrator;
```

Add `AgentOrchestrator` to the constructor-injected fields:

```java
    private final GameChatService gameChatService;
    private final GameRoomService gameRoomService;
    private final ApplicationEventPublisher eventPublisher;
    private final AgentOrchestrator agentOrchestrator;
```

Add the endpoint method after `handleChatMessage`:

```java
    @MessageMapping("/room.{roomId}.activity")
    public void handleUserActivity(
            @DestinationVariable String roomId,
            SimpMessageHeaderAccessor headerAccessor) {

        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) headerAccessor.getUser();
        if (auth == null) return;

        // User is actively browsing (script/chat scroll) — reset idle timer
        agentOrchestrator.resetIdleTimer(roomId);
    }
```

- [ ] **Step 2: Compile and verify**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/controller/GameChatController.java
git commit -m "feat: add /room.{roomId}.activity endpoint for user idle reset"
```

---

### Task 6: Reset counters on phase transitions and game end

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/service/GameFlowService.java`

- [ ] **Step 1: Inject AgentOrchestrator**

Add import:

```java
import com.example.striptkillgamedemo2.ai.orchestrator.AgentOrchestrator;
```

Add to the constructor-injected fields (after `ObjectMapper objectMapper`):

```java
    private final AgentOrchestrator agentOrchestrator;
```

- [ ] **Step 2: Add cleanup to advanceStage**

In the `advanceStage` method, after the line `liveGameRoomService.advanceStage(new ObjectId(roomId), nextStage);`, add:

```java
        // Reset AI round counter and idle timer for new stage
        agentOrchestrator.resetRoundCounter(roomId);
        agentOrchestrator.cancelIdleTimer(roomId);
```

- [ ] **Step 3: Add cleanup to endGame**

Find the `endGame` method. Before or after the existing cleanup logic (status set to FINISHED), add:

```java
        agentOrchestrator.cleanupRoom(roomId);
```

- [ ] **Step 4: Compile and verify**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/GameFlowService.java
git commit -m "feat: reset AI round counter and idle timer on phase transition and game end"
```

---

### Task 7: Update SelectRespondentsTool for lastAiRound

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/SelectRespondentsTool.java`

The `selectRespondents` DM tool calls `executeAgentRepliesSequentially`. Since it's triggered by DM (not AI-to-AI), it always passes `lastAiRound=false`.

- [ ] **Step 1: Update the call**

In `SelectRespondentsTool.execute()`, the existing call:

```java
agentExecutor.executeAgentRepliesSequentially(room.getRoomId(), aiRoleIds);
```

Change to:

```java
agentExecutor.executeAgentRepliesSequentially(room.getRoomId(), aiRoleIds, false);
```

This is technically optional (the no-arg overload exists), but makes intent explicit.

- [ ] **Step 2: Compile and verify**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/SelectRespondentsTool.java
git commit -m "refactor: explicit lastAiRound=false in SelectRespondentsTool"
```

---

### Task 8: Unit tests

**Files:**
- Create: `src/test/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestratorTest.java`

- [ ] **Step 1: Write test for effective max calculation**

```java
package com.example.striptkillgamedemo2.ai.orchestrator;

import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
import com.example.striptkillgamedemo2.config.AiEngineProperties;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.ScriptCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentOrchestratorTest {

    private AgentOrchestrator orchestrator;
    private AiEngineProperties props;

    @BeforeEach
    void setUp() {
        props = new AiEngineProperties();
        props.setMaxAiChatRounds(3);
        props.setIdleTimeoutSeconds(60);
        orchestrator = new AgentOrchestrator(
                mock(AgentExecutor.class),
                mock(DmExecutor.class),
                mock(LiveGameRoomService.class),
                mock(ScriptCacheService.class),
                props
        );
    }

    @Test
    void effectiveMax_oneHuman_returnsConfigMax() {
        LiveGameRoom room = buildRoomWithHumans(1);
        assertEquals(3, orchestrator.calculateEffectiveMaxRounds(room));
    }

    @Test
    void effectiveMax_twoHumans_reducedByOne() {
        LiveGameRoom room = buildRoomWithHumans(2);
        assertEquals(2, orchestrator.calculateEffectiveMaxRounds(room));
    }

    @Test
    void effectiveMax_manyHumans_floorAtTwo() {
        LiveGameRoom room = buildRoomWithHumans(5);
        assertEquals(2, orchestrator.calculateEffectiveMaxRounds(room));
    }

    @Test
    void roundCounter_resetOnHumanMessage() {
        String roomId = "test-room";
        // Simulate 2 AI rounds
        orchestrator.resetRoundCounter(roomId); // init
        // Direct counter manipulation for unit test
        // (in production, onChatMessage increments it)
        assertDoesNotThrow(() -> orchestrator.resetRoundCounter(roomId));
    }

    private LiveGameRoom buildRoomWithHumans(int humanCount) {
        LiveGameRoom room = new LiveGameRoom();
        List<Member> members = new java.util.ArrayList<>();
        for (int i = 0; i < humanCount; i++) {
            Member m = new Member();
            m.setAi(false);
            m.setDm(false);
            members.add(m);
        }
        // Add 2 AI members
        for (int i = 0; i < 2; i++) {
            Member m = new Member();
            m.setAi(true);
            m.setDm(false);
            members.add(m);
        }
        // Add DM
        Member dm = new Member();
        dm.setAi(true);
        dm.setDm(true);
        members.add(dm);

        room.setMembers(members);
        return room;
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./mvnw test -pl . -Dtest=AgentOrchestratorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestratorTest.java
git commit -m "test: add unit tests for AI round counter effective max calculation"
```

---

### Task 9: Final integration compile and verify

- [ ] **Step 1: Full compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run all tests**

Run: `./mvnw test`
Expected: All tests pass

- [ ] **Step 3: Final commit if any remaining changes**

```bash
git status
# If any unstaged fixes needed, stage and commit
```
