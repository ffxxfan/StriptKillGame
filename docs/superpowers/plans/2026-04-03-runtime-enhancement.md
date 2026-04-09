# Runtime Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix timer key collisions, add smart Agent response filtering, and close the vote lifecycle loop with AI voting and DM notification.

**Architecture:** PhaseTimerService keys change from `roomId` to `roomId:phaseType`. AgentOrchestrator gets a 3-layer response strategy (mention → name match → DM fallback). VoteService gains auto-close on completion + DM notification. AgentExecutor gets a new `executeAgentVote` method for LLM-driven AI voting.

**Tech Stack:** Java 21, Spring Boot 3, Spring AI 1.1.3, JUnit 5, Mockito, Maven

---

### Task 1: PhaseTimerService — key granularity + API changes

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/service/PhaseTimerService.java`

- [ ] **Step 1: Add timerKey helper and update internal maps**

Add a private helper method after the field declarations:

```java
private String timerKey(String roomId, PhaseType phaseType) {
    return roomId + ":" + phaseType.name();
}
```

- [ ] **Step 2: Update startPhaseTimer to use composite key**

In `startPhaseTimer`, replace all `roomId` references in map operations with `timerKey(roomId, phaseType)`:

```java
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

            messagingTemplate.convertAndSend("/topic/room." + roomId,
                    Map.of("type", "PHASE_TIMER_REMINDER",
                            "remainingSeconds", REMINDER_BEFORE_SECONDS,
                            "content", reminderMsg));

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

        dmExecutor.executeDmAction(roomId, null,
                timeoutMsg + "请立即使用 transitionPhase 工具（action=NEXT_PHASE）推进到下一个环节。");

        log.info("[PhaseTimer] expired, room={}, phase={}", roomId, phaseType);
    }, duration, TimeUnit.SECONDS);
    timers.put(key, timeoutFuture);

    log.info("[PhaseTimer] started, room={}, phase={}, duration={}s", roomId, phaseType, duration);
}
```

- [ ] **Step 3: Replace cancelTimer and startVoteTimer with new API**

Delete `startVoteTimer` and `cancelTimer(String roomId)`. Add these three methods:

```java
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
```

- [ ] **Step 4: Verify compilation**

Run: `./mvnw compile -q`
Expected: Compilation errors in `TransitionPhaseTool` (calls old `cancelTimer(roomId)` signature). This is expected — fixed in Task 2.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/PhaseTimerService.java
git commit -m "feat: change PhaseTimerService to composite key (roomId:phaseType)"
```

---

### Task 2: Update TransitionPhaseTool for new cancelTimer signature

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/TransitionPhaseTool.java`

- [ ] **Step 1: Update cancelTimer calls in executeNextPhase**

In `executeNextPhase` (around line 107), replace:

```java
phaseTimerService.cancelTimer(room.getRoomId());
```

with:

```java
phaseTimerService.cancelTimer(room.getRoomId(), ctx.getCurrentPhaseType());
```

- [ ] **Step 2: Update cancelTimer call in executeNextStage**

In `executeNextStage` (around line 183), replace:

```java
phaseTimerService.cancelTimer(room.getRoomId());
```

with:

```java
phaseTimerService.cancelAllTimers(room.getRoomId());
```

Note: NEXT_STAGE cancels ALL timers for the room since we're leaving the entire stage.

- [ ] **Step 3: Verify compilation and run existing tests**

Run: `./mvnw test -pl . -Dtest=TransitionPhaseToolTest -Dspring.profiles.active=test -q`
Expected: PASS (existing tests mock PhaseTimerService so signature changes don't affect them, but verify).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/TransitionPhaseTool.java
git commit -m "fix: update TransitionPhaseTool to use new cancelTimer signatures"
```

---

### Task 3: AgentOrchestrator — 3-layer response strategy

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java`
- Modify: `src/test/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestratorTest.java`

- [ ] **Step 1: Write tests for role name matching**

Add to `AgentOrchestratorTest.java`:

```java
@Test
void findNamedAiRoles_matchesRoleNameInText() {
    // Setup script with roles
    Role role1 = new Role();
    role1.setId(new ObjectId());
    role1.setName("苏婉");

    Role role2 = new Role();
    role2.setId(new ObjectId());
    role2.setName("林默");

    Script script = new Script();
    script.setRoles(List.of(role1, role2));

    // Setup room with AI members
    Member m1 = Member.builder().ai(true).roleId(role1.getId()).build();
    Member m2 = Member.builder().ai(true).roleId(role2.getId()).build();
    LiveGameRoom room = LiveGameRoom.builder()
            .roomId("aabbccddeeff001122334455")
            .members(List.of(m1, m2))
            .build();

    List<ObjectId> result = orchestrator.findNamedAiRoles("我觉得苏婉很可疑", script, room);
    assertEquals(1, result.size());
    assertEquals(role1.getId(), result.get(0));
}

@Test
void findNamedAiRoles_returnsEmptyWhenNoMatch() {
    Role role1 = new Role();
    role1.setId(new ObjectId());
    role1.setName("苏婉");

    Script script = new Script();
    script.setRoles(List.of(role1));

    Member m1 = Member.builder().ai(true).roleId(role1.getId()).build();
    LiveGameRoom room = LiveGameRoom.builder()
            .roomId("aabbccddeeff001122334455")
            .members(List.of(m1))
            .build();

    List<ObjectId> result = orchestrator.findNamedAiRoles("今天天气不错", script, room);
    assertTrue(result.isEmpty());
}
```

Note: These tests require adding `import org.bson.types.ObjectId;`, `import com.example.striptkillgamedemo2.entity.mongo.*;`, `import com.example.striptkillgamedemo2.entity.redis.*;`, and `import java.util.List;` to the test file.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=AgentOrchestratorTest -Dspring.profiles.active=test -q`
Expected: Compilation error — `findNamedAiRoles` method not found.

- [ ] **Step 3: Add findNamedAiRoles method**

Add to `AgentOrchestrator.java`:

```java
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
```

Add `import java.util.ArrayList;` to the imports.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=AgentOrchestratorTest -Dspring.profiles.active=test -q`
Expected: All tests PASS.

- [ ] **Step 5: Refactor handleFreeChat with 3-layer strategy**

Replace the entire `handleFreeChat` method:

```java
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
        namedIds.stream().limit(2).forEach(id -> agentExecutor.executeAgentReply(roomId, id));
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
```

- [ ] **Step 6: Verify all tests pass**

Run: `./mvnw test -pl . -Dtest=AgentOrchestratorTest -Dspring.profiles.active=test -q`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java \
       src/test/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestratorTest.java
git commit -m "feat: 3-layer Agent response strategy (mention, name match, DM fallback)"
```

---

### Task 4: VoteService — auto-close + DM notification

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/service/VoteService.java`

- [ ] **Step 1: Add DmExecutor dependency**

Add to VoteService's fields (the class uses `@RequiredArgsConstructor`):

```java
private final DmExecutor dmExecutor;
```

Add import:

```java
import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
```

- [ ] **Step 2: Modify castVote to auto-close and notify DM**

Replace the `castVote` method:

```java
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

    // Check if all members have voted
    long totalMembers = room.getMembers().stream()
            .filter(m -> m.getRoleId() != null && !m.isDm())
            .count();

    long votedCount = vote.getResults().size();

    // Broadcast update
    messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
            Map.of("type", "VOTE_UPDATE",
                    "voteId", vote.getVoteId(),
                    "votedCount", votedCount,
                    "total", totalMembers));

    boolean allVoted = votedCount >= totalMembers;
    if (allVoted) {
        closeVoteAndNotifyDm(roomId);
    }

    return allVoted;
}
```

- [ ] **Step 3: Add closeVoteAndNotifyDm method**

Add after `closeVote`:

```java
public void closeVoteAndNotifyDm(String roomId) {
    Map<String, String> results = closeVote(roomId);
    if (results.isEmpty()) return;

    // Format results for DM
    String resultSummary = results.entrySet().stream()
            .map(e -> e.getKey() + " → " + e.getValue())
            .collect(Collectors.joining(", "));

    dmExecutor.executeDmAction(roomId, null,
            "投票已结束，结果如下：" + resultSummary +
            "。请宣布投票结果，然后使用 transitionPhase 工具推进流程。");

    log.info("[VoteService] vote closed and DM notified, room={}, results={}", roomId, results);
}
```

Add import: `import java.util.stream.Collectors;` (check if already present).

- [ ] **Step 4: Verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/VoteService.java
git commit -m "feat: VoteService auto-close on completion with DM notification"
```

---

### Task 5: PhaseTimerService — vote timeout calls VoteService

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/service/PhaseTimerService.java`

- [ ] **Step 1: Add VoteService dependency**

Add field to PhaseTimerService:

```java
private final VoteService voteService;
```

- [ ] **Step 2: Add vote-specific timeout handling in startPhaseTimer**

In the timeout callback inside `startPhaseTimer`, after the existing `dmExecutor.executeDmAction(...)` call, add a special case for VOTE phase:

Replace the timeout scheduling block with:

```java
// Schedule timeout
ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
    timers.remove(key);
    String timeoutMsg = phaseLabel(phaseType) + "时间已到。";

    // Notify players
    messagingTemplate.convertAndSend("/topic/room." + roomId,
            Map.of("type", "PHASE_TIMER_EXPIRED",
                    "phaseType", phaseType.name(),
                    "content", timeoutMsg));

    if (phaseType == PhaseType.VOTE) {
        // Vote timeout: close vote and notify DM with results
        voteService.closeVoteAndNotifyDm(roomId);
    } else {
        // Non-vote timeout: tell DM to transition
        dmExecutor.executeDmAction(roomId, null,
                timeoutMsg + "请立即使用 transitionPhase 工具（action=NEXT_PHASE）推进到下一个环节。");
    }

    log.info("[PhaseTimer] expired, room={}, phase={}", roomId, phaseType);
}, duration, TimeUnit.SECONDS);
timers.put(key, timeoutFuture);
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/PhaseTimerService.java
git commit -m "feat: vote timeout triggers VoteService.closeVoteAndNotifyDm"
```

---

### Task 6: AgentExecutor — LLM-driven AI voting

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/executor/AgentExecutor.java`

- [ ] **Step 1: Add VoteService dependency**

Add field:

```java
private final VoteService voteService;
```

Add import:

```java
import com.example.striptkillgamedemo2.service.VoteService;
```

- [ ] **Step 2: Add executeAgentVote method**

Add after `executeAgentRepliesSequentially`:

```java
/**
 * AI agent votes using LLM reasoning.
 * Sends the vote context to the agent's prompt, parses the chosen option,
 * and calls VoteService.castVote(). Falls back to random choice on parse failure.
 */
@Async("aiExecutor")
public void executeAgentVote(String roomId, ObjectId roleId, String voteTitle, List<String> options) {
    try {
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null || room.getActiveVote() == null) return;

        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        Role targetRole = script.getRoles().stream()
                .filter(r -> Objects.equals(r.getId(), roleId))
                .findFirst()
                .orElse(null);
        if (targetRole == null) return;

        // Build vote prompt
        List<String> memoryFragments = memoryManager.getMemoryFragments(roomId, room.getCurrentStage());
        List<GameMessage> allMessages = deserializeMessages(liveGameRoomService.getMessages(new ObjectId(roomId)));
        List<GameMessage> recentMessages = memoryManager.getRecentMessages(allMessages);

        String agentPrompt = promptBuilder.buildAgentPrompt(
                room, script, targetRole,
                room.getClueInstances(),
                memoryFragments, recentMessages);

        String voteInstruction = "\n\n## 投票任务\n" +
                "投票主题：" + voteTitle + "\n" +
                "选项：" + options + "\n" +
                "根据你的角色身份和已知信息，从以上选项中选择一个。" +
                "请只回复选项的完整文本，不要附加任何解释。";

        ChatResponse chatResponse = chatModel.call(new Prompt(agentPrompt + voteInstruction));
        String reply = "";
        if (chatResponse.getResults() != null
                && chatResponse.getResults().size() > 1
                && chatResponse.getResults().get(1).getOutput() != null
                && chatResponse.getResults().get(1).getOutput().getText() != null) {
            reply = stripThinkTags(chatResponse.getResults().get(1).getOutput().getText()).trim();
        }

        // Match reply to an option (exact or contains)
        String chosen = options.stream()
                .filter(opt -> reply.contains(opt) || opt.contains(reply))
                .findFirst()
                .orElse(null);

        // Fallback: random choice
        if (chosen == null) {
            chosen = options.get(new java.util.Random().nextInt(options.size()));
            log.warn("[AgentVote] could not parse AI reply '{}', falling back to random: {}", reply, chosen);
        }

        voteService.castVote(roomId, roleId, chosen);
        log.info("[AgentVote] role={}, chose='{}', room={}", targetRole.getName(), chosen, roomId);

    } catch (Exception e) {
        log.error("Agent vote failed for role {} in room {}", roleId, roomId, e);
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/executor/AgentExecutor.java
git commit -m "feat: add LLM-driven AI voting in AgentExecutor"
```

---

### Task 7: InitiateVoteTool — trigger AI voting + use new timer API

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/InitiateVoteTool.java`

- [ ] **Step 1: Add AgentExecutor and PhaseTimerService dependencies**

Add fields (class uses `@RequiredArgsConstructor`):

```java
private final AgentExecutor agentExecutor;
private final PhaseTimerService phaseTimerService;
```

Add imports:

```java
import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.service.PhaseTimerService;
import com.example.striptkillgamedemo2.entity.mongo.Member;
```

- [ ] **Step 2: Update execute method to trigger AI voting and start timer**

At the end of `execute`, before the return statement, add:

```java
// Start vote timer
phaseTimerService.startPhaseTimer(room.getRoomId(), PhaseType.VOTE,
        aiEngineProperties.getVoteTimeoutSeconds());

// Trigger AI agents to vote asynchronously
List<ObjectId> aiRoleIds = room.getMembers().stream()
        .filter(m -> m.isAi() && !m.isDm() && m.getRoleId() != null)
        .map(Member::getRoleId)
        .toList();
for (ObjectId aiRoleId : aiRoleIds) {
    agentExecutor.executeAgentVote(room.getRoomId(), aiRoleId,
            input.getTitle(), input.getOptions());
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/InitiateVoteTool.java
git commit -m "feat: InitiateVoteTool triggers AI voting and starts vote timer"
```

---

### Task 8: Full compilation + run all tests

- [ ] **Step 1: Run full compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run all unit tests**

Run: `./mvnw test -Dspring.profiles.active=test -Dtest='!StriptKillGameDemo2ApplicationTests' -q`
Expected: All tests PASS.

- [ ] **Step 3: Check for circular dependency issues**

Dependency chain to verify has no cycles:
- `PhaseTimerService` → `VoteService` (new) + `DmExecutor` (existing)
- `VoteService` → `DmExecutor` (new) + `LiveGameRoomService` (existing)
- `InitiateVoteTool` → `AgentExecutor` (new) + `PhaseTimerService` (new)
- `AgentExecutor` → `VoteService` (new)

None of these create a cycle. If Spring fails to start due to circular dependency, the error will appear in `StriptKillGameDemo2ApplicationTests`. Check by running: `./mvnw test -Dtest=StriptKillGameDemo2ApplicationTests -Dspring.profiles.active=test` (may fail due to missing API key, but circular dependency errors appear before that).

- [ ] **Step 4: Final commit if any fixes were needed**

```bash
git add -A
git commit -m "fix: resolve any remaining compilation or test issues"
```
