# Phase Model Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend PhaseType from 3 to 7 values with `defaultRequired` attribute, add NEXT_STAGE skip validation, and add fallback stage compression when DM forgets to summarize.

**Architecture:** PhaseType enum gains a `defaultRequired` boolean constructor param. TransitionPhaseTool gets two new guards: required-phase check before NEXT_STAGE, and automatic fallback compression via MemoryManager. All downstream switch statements (PhaseTimerService, AgentOrchestrator) are extended for new phase types.

**Tech Stack:** Java 21, Spring Boot 3, Spring AI 1.1.3, JUnit 5, Mockito, Maven

---

### Task 1: Extend PhaseType Enum

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/entity/enums/PhaseType.java`
- Modify: `src/test/java/com/example/striptkillgamedemo2/entity/enums/ClueTypeTest.java` (pattern reference)
- Create: `src/test/java/com/example/striptkillgamedemo2/entity/enums/PhaseTypeTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/example/striptkillgamedemo2/entity/enums/PhaseTypeTest.java`:

```java
package com.example.striptkillgamedemo2.entity.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseTypeTest {

    @Test
    void shouldHaveSevenPhaseTypes() {
        assertEquals(7, PhaseType.values().length);
    }

    @Test
    void voteIsDefaultRequired() {
        assertTrue(PhaseType.VOTE.isDefaultRequired());
    }

    @Test
    void nonVotePhasesAreNotDefaultRequired() {
        assertFalse(PhaseType.SCRIPT_READING.isDefaultRequired());
        assertFalse(PhaseType.TURN_BASED.isDefaultRequired());
        assertFalse(PhaseType.FREE_CHAT.isDefaultRequired());
        assertFalse(PhaseType.INVESTIGATION.isDefaultRequired());
        assertFalse(PhaseType.PRIVATE_TALK.isDefaultRequired());
        assertFalse(PhaseType.FINAL_STATEMENT.isDefaultRequired());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl . -Dtest=PhaseTypeTest -Dspring.profiles.active=test -q`
Expected: Compilation error — `isDefaultRequired()` not found, new enum values not defined.

- [ ] **Step 3: Implement PhaseType with defaultRequired**

Replace `src/main/java/com/example/striptkillgamedemo2/entity/enums/PhaseType.java`:

```java
package com.example.striptkillgamedemo2.entity.enums;

public enum PhaseType {
    SCRIPT_READING(false),
    TURN_BASED(false),
    FREE_CHAT(false),
    INVESTIGATION(false),
    PRIVATE_TALK(false),
    FINAL_STATEMENT(false),
    VOTE(true);

    private final boolean defaultRequired;

    PhaseType(boolean defaultRequired) {
        this.defaultRequired = defaultRequired;
    }

    public boolean isDefaultRequired() {
        return defaultRequired;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl . -Dtest=PhaseTypeTest -Dspring.profiles.active=test -q`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/enums/PhaseType.java \
       src/test/java/com/example/striptkillgamedemo2/entity/enums/PhaseTypeTest.java
git commit -m "feat: extend PhaseType enum with 7 values and defaultRequired attribute"
```

---

### Task 2: Update AiEngineProperties with new timeout defaults

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/config/AiEngineProperties.java`

- [ ] **Step 1: Add new timeout fields**

Add these fields to `AiEngineProperties.java` after the existing `freeChatTimeoutSeconds` field:

```java
private int scriptReadingTimeoutSeconds = 180;
private int investigationTimeoutSeconds = 240;
private int privateTalkTimeoutSeconds = 180;
private int finalStatementTimeoutSeconds = 120;
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/config/AiEngineProperties.java
git commit -m "feat: add timeout config for new phase types"
```

---

### Task 3: Update PhaseTimerService for new phase types

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/service/PhaseTimerService.java`

- [ ] **Step 1: Extend getDefaultDuration switch**

Replace the `getDefaultDuration` method in `PhaseTimerService.java`:

```java
private int getDefaultDuration(PhaseType phaseType) {
    return switch (phaseType) {
        case SCRIPT_READING -> properties.getScriptReadingTimeoutSeconds();
        case TURN_BASED -> properties.getTurnTimeoutSeconds();
        case FREE_CHAT -> properties.getFreeChatTimeoutSeconds();
        case INVESTIGATION -> properties.getInvestigationTimeoutSeconds();
        case PRIVATE_TALK -> properties.getPrivateTalkTimeoutSeconds();
        case FINAL_STATEMENT -> properties.getFinalStatementTimeoutSeconds();
        case VOTE -> properties.getVoteTimeoutSeconds();
    };
}
```

- [ ] **Step 2: Extend phaseLabel switch**

Replace the `phaseLabel` method in `PhaseTimerService.java`:

```java
private String phaseLabel(PhaseType phaseType) {
    return switch (phaseType) {
        case SCRIPT_READING -> "阅读剧本";
        case TURN_BASED -> "轮流发言";
        case FREE_CHAT -> "自由讨论";
        case INVESTIGATION -> "搜证";
        case PRIVATE_TALK -> "密谈";
        case FINAL_STATEMENT -> "最终陈述";
        case VOTE -> "投票";
    };
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/PhaseTimerService.java
git commit -m "feat: extend PhaseTimerService switches for new phase types"
```

---

### Task 4: Update AgentOrchestrator for new phase routing

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java`
- Modify: `src/test/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestratorTest.java`

- [ ] **Step 1: Write failing test for investigation routing**

Add to `AgentOrchestratorTest.java`:

```java
@Test
void isDmRequest_detectsInvestigationKeywords() {
    assertTrue(orchestrator.isDmRequest("我要去花园搜证"));
    assertTrue(orchestrator.isDmRequest("我想调查书房"));
    assertTrue(orchestrator.isDmRequest("我要搜索卧室"));
}
```

- [ ] **Step 2: Run test to verify it passes (keywords already detected)**

Run: `mvn test -pl . -Dtest=AgentOrchestratorTest -Dspring.profiles.active=test -q`
Expected: PASS — existing `isDmRequest` already matches 搜证/搜索/调查 keywords.

- [ ] **Step 3: Update onChatMessage to handle new phase types**

Replace the phase-handling logic in `onChatMessage` method (after the `isDmRequest` check):

```java
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
```

- [ ] **Step 4: Remove the old if-else chain**

Delete the old `if (currentPhase == PhaseType.TURN_BASED) ... else if ...` block that was replaced by the switch in Step 3.

- [ ] **Step 5: Verify compilation and existing tests pass**

Run: `mvn test -pl . -Dtest=AgentOrchestratorTest -Dspring.profiles.active=test -q`
Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java \
       src/test/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestratorTest.java
git commit -m "feat: extend AgentOrchestrator routing for new phase types"
```

---

### Task 5: Update AuthorizeSearchTool allowedPhases

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/AuthorizeSearchTool.java`

- [ ] **Step 1: Change allowedPhases from FREE_CHAT to INVESTIGATION**

In `AuthorizeSearchTool.java`, replace:

```java
@Override
public Set<PhaseType> allowedPhases() {
    return Set.of(PhaseType.FREE_CHAT);
}
```

with:

```java
@Override
public Set<PhaseType> allowedPhases() {
    return Set.of(PhaseType.INVESTIGATION);
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/AuthorizeSearchTool.java
git commit -m "feat: restrict authorizeSearch to INVESTIGATION phase"
```

---

### Task 6: Add MemoryManager.hasSummary()

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/memory/MemoryManager.java`

- [ ] **Step 1: Add hasSummary method**

Add this method to `MemoryManager.java` after the `getMemoryFragments` method:

```java
public boolean hasSummary(String roomId, int stageNumber) {
    String key = MEMORY_KEY_PREFIX + roomId + MEMORY_KEY_INFIX + stageNumber;
    return Boolean.TRUE.equals(redisTemplate.hasKey(key));
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/memory/MemoryManager.java
git commit -m "feat: add MemoryManager.hasSummary() for fallback check"
```

---

### Task 7: Add NEXT_STAGE skip validation and fallback compression to TransitionPhaseTool

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/TransitionPhaseTool.java`
- Create: `src/test/java/com/example/striptkillgamedemo2/ai/tool/impl/TransitionPhaseToolTest.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/example/striptkillgamedemo2/ai/tool/impl/TransitionPhaseToolTest.java`:

```java
package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.memory.MemoryManager;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.mongo.StagePhase;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.PhaseTimerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransitionPhaseToolTest {

    private TransitionPhaseTool tool;
    private LiveGameRoomService liveGameRoomService;
    private MemoryManager memoryManager;
    private SimpMessagingTemplate messagingTemplate;
    private PhaseTimerService phaseTimerService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        liveGameRoomService = mock(LiveGameRoomService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        phaseTimerService = mock(PhaseTimerService.class);
        memoryManager = mock(MemoryManager.class);
        objectMapper = new ObjectMapper();
        tool = new TransitionPhaseTool(liveGameRoomService, messagingTemplate,
                phaseTimerService, memoryManager, objectMapper);
    }

    @Test
    void nextStage_blockedWhenRequiredPhaseNotCompleted() {
        // Stage has 3 phases: FREE_CHAT(0), INVESTIGATION(1), VOTE(2)
        // Current phase index is 1 (INVESTIGATION), so VOTE at index 2 is still ahead
        LiveGameRoom room = LiveGameRoom.builder()
                .roomId("room1")
                .currentStage(0)
                .currentPhaseIndex(1)
                .build();

        ScriptStage stage = new ScriptStage();
        stage.setPhases(List.of(
                StagePhase.builder().type(PhaseType.FREE_CHAT).build(),
                StagePhase.builder().type(PhaseType.INVESTIGATION).build(),
                StagePhase.builder().type(PhaseType.VOTE).build()
        ));

        Script script = new Script();
        script.setStages(List.of(stage, new ScriptStage())); // 2 stages

        DmToolContext ctx = DmToolContext.builder()
                .room(room).script(script)
                .currentPhaseType(PhaseType.INVESTIGATION)
                .build();

        TransitionPhaseTool.Input input = new TransitionPhaseTool.Input();
        input.setAction("NEXT_STAGE");
        input.setReason("test");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(input, ctx);

        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("VOTE"));
    }

    @Test
    void nextStage_allowedWhenNoRequiredPhasesRemain() {
        // Stage has 2 phases: FREE_CHAT(0), INVESTIGATION(1)
        // Current phase index is 1, no required phases remain
        LiveGameRoom room = LiveGameRoom.builder()
                .roomId("room1")
                .currentStage(0)
                .currentPhaseIndex(1)
                .build();

        ScriptStage stage0 = new ScriptStage();
        stage0.setPhases(List.of(
                StagePhase.builder().type(PhaseType.FREE_CHAT).build(),
                StagePhase.builder().type(PhaseType.INVESTIGATION).build()
        ));
        ScriptStage stage1 = new ScriptStage();
        stage1.setStageTitle("第二幕");
        stage1.setPhases(List.of(
                StagePhase.builder().type(PhaseType.FREE_CHAT).timeLimitSeconds(0).build()
        ));

        Script script = new Script();
        script.setStages(List.of(stage0, stage1));

        DmToolContext ctx = DmToolContext.builder()
                .room(room).script(script)
                .currentPhaseType(PhaseType.INVESTIGATION)
                .build();

        when(memoryManager.hasSummary("room1", 0)).thenReturn(true);
        when(liveGameRoomService.getMessages(any())).thenReturn(List.of());

        TransitionPhaseTool.Input input = new TransitionPhaseTool.Input();
        input.setAction("NEXT_STAGE");
        input.setReason("test");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(input, ctx);

        assertTrue((Boolean) result.get("success"));
        assertEquals("STAGE_ADVANCE", result.get("event"));
    }

    @Test
    void nextStage_triggersFallbackCompressionWhenNoSummary() {
        LiveGameRoom room = LiveGameRoom.builder()
                .roomId("room1")
                .currentStage(0)
                .currentPhaseIndex(1)
                .build();

        ScriptStage stage0 = new ScriptStage();
        stage0.setPhases(List.of(
                StagePhase.builder().type(PhaseType.FREE_CHAT).build(),
                StagePhase.builder().type(PhaseType.INVESTIGATION).build()
        ));
        ScriptStage stage1 = new ScriptStage();
        stage1.setStageTitle("第二幕");
        stage1.setPhases(List.of(
                StagePhase.builder().type(PhaseType.FREE_CHAT).timeLimitSeconds(0).build()
        ));

        Script script = new Script();
        script.setStages(List.of(stage0, stage1));

        DmToolContext ctx = DmToolContext.builder()
                .room(room).script(script)
                .currentPhaseType(PhaseType.INVESTIGATION)
                .build();

        when(memoryManager.hasSummary("room1", 0)).thenReturn(false);
        when(liveGameRoomService.getMessages(any())).thenReturn(List.of());

        TransitionPhaseTool.Input input = new TransitionPhaseTool.Input();
        input.setAction("NEXT_STAGE");
        input.setReason("test");

        tool.execute(input, ctx);

        verify(memoryManager).compressStage(eq("room1"), eq(0), anyList());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest=TransitionPhaseToolTest -Dspring.profiles.active=test -q`
Expected: Compilation error — TransitionPhaseTool constructor doesn't accept MemoryManager/ObjectMapper yet.

- [ ] **Step 3: Add new dependencies to TransitionPhaseTool**

In `TransitionPhaseTool.java`, add these imports:

```java
import com.example.striptkillgamedemo2.ai.memory.MemoryManager;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.types.ObjectId;
import java.util.Objects;
```

Add these fields to the class (after existing fields):

```java
private final MemoryManager memoryManager;
private final ObjectMapper objectMapper;
```

Note: `@RequiredArgsConstructor` will auto-generate the updated constructor.

- [ ] **Step 4: Add required-phase validation to executeNextStage**

At the beginning of `executeNextStage`, after `int nextStage = currentStage + 1;`, add:

```java
// Validate no required phases are being skipped
if (stages != null && currentStage < stages.size()) {
    ScriptStage currentStageObj = stages.get(currentStage);
    if (currentStageObj.getPhases() != null) {
        List<String> requiredSkipped = currentStageObj.getPhases().stream()
                .skip(room.getCurrentPhaseIndex() + 1)
                .filter(p -> p.getType().isDefaultRequired())
                .map(p -> p.getType().name())
                .toList();
        if (!requiredSkipped.isEmpty()) {
            return Map.of("success", false,
                    "error", "以下必须环节未完成，不能跳过: " + requiredSkipped,
                    "hint", "请先使用 NEXT_PHASE 推进完成这些环节");
        }
    }
}
```

- [ ] **Step 5: Add fallback compression before stage advance**

In `executeNextStage`, just before `// Reset phase index for new stage`, add:

```java
// Fallback compression if DM forgot to call summarizeCurrentStage
if (!memoryManager.hasSummary(room.getRoomId(), currentStage)) {
    log.info("[transitionPhase] fallback compression for room={}, stage={}",
            room.getRoomId(), currentStage);
    List<GameMessage> msgs = deserializeMessages(
            liveGameRoomService.getMessages(new ObjectId(room.getRoomId())));
    memoryManager.compressStage(room.getRoomId(), currentStage, msgs);
}
```

- [ ] **Step 6: Add deserializeMessages helper method**

Add at the bottom of the class (before the closing brace):

```java
private List<GameMessage> deserializeMessages(List<String> jsonMessages) {
    return jsonMessages.stream()
            .map(json -> {
                try {
                    return objectMapper.readValue(json, GameMessage.class);
                } catch (Exception e) {
                    log.warn("Failed to deserialize message", e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest=TransitionPhaseToolTest -Dspring.profiles.active=test -q`
Expected: All 3 tests PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/TransitionPhaseTool.java \
       src/test/java/com/example/striptkillgamedemo2/ai/tool/impl/TransitionPhaseToolTest.java
git commit -m "feat: add NEXT_STAGE skip validation and fallback compression"
```

---

### Task 8: Update dm-system.md prompt

**Files:**
- Modify: `src/main/resources/prompts/dm-system.md`

- [ ] **Step 1: Update the tool usage rules table**

Replace the tool table section in `dm-system.md` with:

```markdown
## 工具使用规则（严格遵守）
工具只能在对应环节使用，**禁止跨环节调用**：

| 工具 | 允许环节 | 说明 |
|------|----------|------|
| pushStageContent | 仅在新幕开始时 | 通知玩家新幕开启 |
| transitionPhase | 任意环节 | **统一流程推进工具**。NEXT_PHASE=推进到下一环节，NEXT_STAGE=推进到下一幕（会校验必须环节是否已完成） |
| summarizeCurrentStage | 每幕结束时 | 归档本幕重点（谎言、证据、嫌疑人），在 NEXT_STAGE 前调用。即使忘记调用，系统也会自动兜底压缩，但你的分析更有价值 |
| authorizeSearch | 仅在 INVESTIGATION | 不得主动发起搜证，仅在玩家请求时授权 |
| initiateVote | 仅在 FREE_CHAT 且讨论充分后 | 不得在其他环节或刚开始讨论时发起 |
| selectRespondents | 仅在 FREE_CHAT | 选择AI角色回复 |
| assignTurn | 仅在 TURN_BASED 或 FINAL_STATEMENT | 指定发言顺序 |
```

- [ ] **Step 2: Add phase type reference**

After the tool table, add:

```markdown
### 环节类型说明
| 环节 | 说明 |
|------|------|
| SCRIPT_READING | 阅读剧本阶段，玩家静默阅读，不触发讨论 |
| TURN_BASED | 轮流发言，按 speakOrder 顺序 |
| FREE_CHAT | 自由讨论，可发起投票或选择回复角色 |
| INVESTIGATION | 搜证阶段，玩家请求搜证时你授权 |
| PRIVATE_TALK | 密谈阶段，指定角色间的私密交流 |
| FINAL_STATEMENT | 最终陈述，投票前的最后发言机会 |
| VOTE | 投票环节（必须完成，不可跳过） |
```

- [ ] **Step 3: Update the flow rules**

Replace the 流程推进原则 section with:

```markdown
### 流程推进原则
1. 每个环节有时间限制，系统会在到期前提醒你，到期后你**必须**使用 transitionPhase 推进
2. VOTE 环节为必须完成环节，NEXT_STAGE 会拒绝跳过未完成的必须环节
3. 不得跳过未完成的环节，除非时间已到
4. 推进时务必填写 reason 字段，记录转换原因
5. 每幕结束前尽量调用 summarizeCurrentStage 归档重点
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/prompts/dm-system.md
git commit -m "docs: update dm-system prompt for new phase types and validation"
```

---

### Task 9: Verify full compilation and run all tests

- [ ] **Step 1: Run full compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run all tests**

Run: `mvn test -Dspring.profiles.active=test -q`
Expected: All tests PASS. Watch for switch exhaustiveness errors in any file that switches on PhaseType.

- [ ] **Step 3: Fix any remaining switch exhaustiveness issues**

If compilation fails on any switch statement that doesn't cover new PhaseType values, add the missing branches. Known locations to check:
- `PhaseTimerService.getDefaultDuration()` (handled in Task 3)
- `PhaseTimerService.phaseLabel()` (handled in Task 3)
- Any other file with `switch (phaseType)` — search with: `grep -r "switch.*phaseType" src/main/`

- [ ] **Step 4: Final commit if any fixes were needed**

```bash
git add -A
git commit -m "fix: resolve remaining switch exhaustiveness for new PhaseType values"
```
