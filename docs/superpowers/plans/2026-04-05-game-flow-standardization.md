# Game Flow Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize the game flow state machine with backend-enforced phase rules, private messaging, elimination mechanics, and first/last stage special flows.

**Architecture:** New `PhaseRuleEnforcer` service gates all messages before routing. Existing tools (`TransitionPhaseTool`, `AuthorizeSearchTool`, etc.) are modified to respect new state fields. Private messages use STOMP user-directed channels. DM prompt drives soft decisions (narration, flow timing).

**Tech Stack:** Java 17, Spring Boot 3.5, Spring AI, Redis, MongoDB, Vue 3, STOMP.js, Pinia

---

### Task 1: Remove PRIVATE_TALK and FINAL_STATEMENT from PhaseType

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/entity/enums/PhaseType.java`
- Modify: `src/main/java/com/example/striptkillgamedemo2/service/PhaseTimerService.java`
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java`

- [ ] **Step 1: Update PhaseType enum**

Remove `PRIVATE_TALK` and `FINAL_STATEMENT` entries:

```java
package com.example.striptkillgamedemo2.entity.enums;

public enum PhaseType {
    SCRIPT_READING(false),
    TURN_BASED(false),
    FREE_CHAT(false),
    INVESTIGATION(false),
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

- [ ] **Step 2: Fix PhaseTimerService switch statements**

In `PhaseTimerService.java`, remove cases for removed types from `getDefaultDuration()` (lines 152-161) and `phaseLabel()` (lines 164-173):

```java
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
```

- [ ] **Step 3: Fix AgentOrchestrator switch statement**

In `AgentOrchestrator.java`, remove `PRIVATE_TALK` case from `onChatMessage()` switch (lines 91-109). The `PRIVATE_TALK` case was routing to `handleFreeChat` — remove it:

```java
switch (currentPhase) {
    case TURN_BASED -> {
        if (!fromAi) handleTurnBased(roomId, room, script, event);
    }
    case FREE_CHAT -> handleFreeChat(roomId, room, script, content,
            event.getSenderRoleId(), fromAi);
    case INVESTIGATION -> {
        if (!fromAi && isDmRequest(content)) {
            dmExecutor.executeDmAction(roomId, event.getSenderRoleId(),
                    "玩家消息：" + content);
        }
    }
    case SCRIPT_READING -> { }
    case VOTE -> { }
}
```

- [ ] **Step 4: Compile and verify**

Run: `./mvnw compile`
Expected: BUILD SUCCESS (no compilation errors from removed enum values)

- [ ] **Step 5: Run tests**

Run: `./mvnw test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/enums/PhaseType.java \
       src/main/java/com/example/striptkillgamedemo2/service/PhaseTimerService.java \
       src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java
git commit -m "refactor: remove PRIVATE_TALK and FINAL_STATEMENT from PhaseType"
```

---

### Task 2: Add new fields to LiveGameRoom

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/entity/redis/LiveGameRoom.java`

- [ ] **Step 1: Add new fields**

Add these fields after the existing `activeVote` field (line 50):

```java
@Builder.Default
private Set<String> spokenRoleIds = new HashSet<>();

@Builder.Default
private Set<String> eliminatedRoleIds = new HashSet<>();

private boolean phaseOvertime;

private String investigationMode; // "PUBLIC" | "PRIVATE"

private String voteSubPhase; // "STATEMENT" | "VOTING" | "RESULT"
```

Add the import at the top:

```java
import java.util.HashSet;
import java.util.Set;
```

- [ ] **Step 2: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/redis/LiveGameRoom.java
git commit -m "feat: add spokenRoleIds, eliminatedRoleIds, phaseOvertime, investigationMode, voteSubPhase to LiveGameRoom"
```

---

### Task 3: Add stages and visibility to Clue

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/entity/mongo/Clue.java`

- [ ] **Step 1: Add new fields**

Add after `locationTag` (line 45):

```java
private List<Integer> stages; // Stages where this clue can be discovered; null/empty = any stage
private String visibility;    // "PUBLIC" | "PRIVATE"; null defaults to PUBLIC
```

- [ ] **Step 2: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/mongo/Clue.java
git commit -m "feat: add stages and visibility fields to Clue"
```

---

### Task 4: Delete deprecated tools

**Files:**
- Delete: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/DecidePhaseTransitionTool.java`
- Delete: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/EndFreeChatTool.java`
- Delete: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/SkipVoteTool.java`
- Delete: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/AdvancePhaseTool.java`

- [ ] **Step 1: Delete the files**

```bash
git rm src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/DecidePhaseTransitionTool.java
git rm src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/EndFreeChatTool.java
git rm src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/SkipVoteTool.java
git rm src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/AdvancePhaseTool.java
```

- [ ] **Step 2: Compile and verify no references remain**

Run: `./mvnw compile`
Expected: BUILD SUCCESS (these tools are registered via `@Component` auto-scan, no explicit references elsewhere)

- [ ] **Step 3: Run tests**

Run: `./mvnw test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor: delete deprecated DM tools (AdvancePhase, DecidePhaseTransition, EndFreeChat, SkipVote)"
```

---

### Task 5: Create PhaseRuleEnforcer

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/service/PhaseRuleEnforcer.java`

- [ ] **Step 1: Write PhaseRuleEnforcer**

```java
package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PhaseRuleEnforcer {

    public enum SpeakCheck {
        ALLOWED,
        BLOCKED_SILENT,
        BLOCKED_DM_REMIND
    }

    /**
     * Check whether a role is allowed to speak in the current phase.
     *
     * @param room       current game room state
     * @param phase      current phase type
     * @param roleIdHex  hex string of the speaking role
     * @param isAi       true if speaker is an AI agent
     * @return SpeakCheck indicating whether to allow, silently drop, or remind via DM
     */
    public SpeakCheck checkCanSpeak(LiveGameRoom room, PhaseType phase,
                                     String roleIdHex, boolean isAi) {
        // Eliminated AI: always silent block
        if (isAi && room.getEliminatedRoleIds().contains(roleIdHex)) {
            log.debug("[PhaseRule] BLOCKED_SILENT: eliminated AI {}", roleIdHex);
            return SpeakCheck.BLOCKED_SILENT;
        }

        // Eliminated human: DM remind
        if (!isAi && room.getEliminatedRoleIds().contains(roleIdHex)) {
            log.debug("[PhaseRule] BLOCKED_DM_REMIND: eliminated player {}", roleIdHex);
            return SpeakCheck.BLOCKED_DM_REMIND;
        }

        return switch (phase) {
            case SCRIPT_READING -> isAi ? SpeakCheck.BLOCKED_SILENT : SpeakCheck.BLOCKED_DM_REMIND;

            case TURN_BASED -> {
                if (isAi && room.getSpokenRoleIds().contains(roleIdHex)) {
                    yield SpeakCheck.BLOCKED_SILENT;
                }
                yield SpeakCheck.ALLOWED;
            }

            case FREE_CHAT -> SpeakCheck.ALLOWED;

            case INVESTIGATION -> SpeakCheck.ALLOWED;

            case VOTE -> {
                if (isAi) {
                    // AI can only speak during STATEMENT sub-phase and if not already spoken
                    String subPhase = room.getVoteSubPhase();
                    if (!"STATEMENT".equals(subPhase)) {
                        yield SpeakCheck.BLOCKED_SILENT;
                    }
                    if (room.getSpokenRoleIds().contains(roleIdHex)) {
                        yield SpeakCheck.BLOCKED_SILENT;
                    }
                }
                yield SpeakCheck.ALLOWED;
            }
        };
    }

    /**
     * Check if a player is @mentioning an eliminated AI role.
     */
    public boolean isTargetingEliminatedAi(LiveGameRoom room, String targetRoleIdHex) {
        return room.getEliminatedRoleIds().contains(targetRoleIdHex);
    }

    /**
     * Mark a role as having spoken in the current phase (for TURN_BASED enforcement).
     */
    public void markSpoken(LiveGameRoom room, String roleIdHex) {
        room.getSpokenRoleIds().add(roleIdHex);
    }

    /**
     * Clear phase-scoped state on phase transition.
     */
    public void resetPhaseState(LiveGameRoom room) {
        room.getSpokenRoleIds().clear();
        room.setPhaseOvertime(false);
        room.setInvestigationMode(null);
        room.setVoteSubPhase(null);
    }
}
```

- [ ] **Step 2: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/PhaseRuleEnforcer.java
git commit -m "feat: add PhaseRuleEnforcer service for per-phase speaking rules"
```

---

### Task 6: Integrate PhaseRuleEnforcer into AgentOrchestrator

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java`

- [ ] **Step 1: Add PhaseRuleEnforcer dependency**

Add to the class fields (after existing `aiProperties` field):

```java
private final PhaseRuleEnforcer phaseRuleEnforcer;
```

Add the import:

```java
import com.example.striptkillgamedemo2.service.PhaseRuleEnforcer;
```

- [ ] **Step 2: Add rule check at top of onChatMessage**

Insert after `PhaseType currentPhase = getCurrentPhaseType(script, room);` (around line 63), before the idle timer reset:

```java
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
```

- [ ] **Step 3: Add buildRemindMessage helper**

Add as a private method:

```java
private String buildRemindMessage(PhaseType phase, String roleIdHex, LiveGameRoom room) {
    if (room.getEliminatedRoleIds().contains(roleIdHex)) {
        return "提醒：该玩家已被投出局，其发言不影响游戏流程。请温和提醒。";
    }
    return switch (phase) {
        case SCRIPT_READING -> "有玩家在阅读剧本阶段发言了，请提醒他们保持安静阅读。";
        default -> "当前环节不允许该操作，请提醒玩家。";
    };
}
```

- [ ] **Step 4: Mark spoken AI in TURN_BASED after agent reply event**

In the existing `handleTurnBased()` method, after the AI agent is triggered to speak, add marking logic. Currently when an AI speaks, the `ChatMessageEvent(fromAi=true)` fires back into `onChatMessage`. Add marking after the AI message is processed — in `onChatMessage`, after the ALLOWED check and before the switch, for AI messages in TURN_BASED:

```java
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
```

- [ ] **Step 5: Suppress AI chat chain in INVESTIGATION**

In the switch statement, update the INVESTIGATION case so AI messages are not routed to chat response chain but DM requests from AI (containing @DM) are allowed through:

```java
case INVESTIGATION -> {
    if (isDmRequest(content)) {
        dmExecutor.executeDmAction(roomId, event.getSenderRoleId(),
                (fromAi ? "AI角色搜证请求：" : "玩家消息：") + content);
    }
    // No AI-to-AI chain in INVESTIGATION
}
```

- [ ] **Step 6: Add eliminated-AI @mention check in handleFreeChat**

In `handleFreeChat`, Layer 1 (@mention), after finding the role, add elimination check:

```java
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
```

- [ ] **Step 7: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Run tests**

Run: `./mvnw test`
Expected: All tests pass

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java
git commit -m "feat: integrate PhaseRuleEnforcer into AgentOrchestrator message routing"
```

---

### Task 7: Add elimination check to AgentExecutor

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/executor/AgentExecutor.java`

- [ ] **Step 1: Add elimination check at start of doExecuteAgentReply**

After `if (room == null) return;` (line 140), add:

```java
// Skip eliminated agents
String roleIdHex = roleId.toHexString();
if (room.getEliminatedRoleIds() != null && room.getEliminatedRoleIds().contains(roleIdHex)) {
    log.info("[AgentReply] skipping eliminated role={}, room={}", roleIdHex, roomId);
    return;
}
```

- [ ] **Step 2: Add same check to executeAgentVote**

After `if (room == null || room.getActiveVote() == null) return;` (line 89), add:

```java
if (room.getEliminatedRoleIds() != null && room.getEliminatedRoleIds().contains(roleId.toHexString())) {
    log.info("[AgentVote] skipping eliminated role={}, room={}", roleId, roomId);
    return;
}
```

- [ ] **Step 3: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/executor/AgentExecutor.java
git commit -m "feat: skip eliminated AI agents in AgentExecutor"
```

---

### Task 8: Update TransitionPhaseTool with phase state management

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/TransitionPhaseTool.java`

- [ ] **Step 1: Add PhaseRuleEnforcer dependency**

Add to the class fields:

```java
private final PhaseRuleEnforcer phaseRuleEnforcer;
```

Add import:

```java
import com.example.striptkillgamedemo2.service.PhaseRuleEnforcer;
```

- [ ] **Step 2: Add phase state cleanup in executeNextPhase**

In `executeNextPhase()`, after cancelling the timer (line 107) and before advancing the phase index, add:

```java
// Clear phase-scoped state from previous phase
phaseRuleEnforcer.resetPhaseState(room);
```

And after determining `nextType` (line 132), add phase-specific initialization:

```java
// Initialize phase-specific state
if (nextType == PhaseType.VOTE) {
    room.setVoteSubPhase("STATEMENT");
}
```

Then re-save room (existing `liveGameRoomService.save(room)` on line 128 already handles this, but move the VOTE init before the save):

Actually, the save is at line 128, before we know nextType. Restructure: move the `liveGameRoomService.save(room)` to after the phase init:

Replace lines 126-128:
```java
// Advance to next phase
room.setCurrentPhaseIndex(nextPhaseIndex);
room.setCurrentSpeakerRoleId(null);
liveGameRoomService.save(room);
```

With:
```java
// Advance to next phase
room.setCurrentPhaseIndex(nextPhaseIndex);
room.setCurrentSpeakerRoleId(null);

// Determine next phase info
StagePhase nextPhase = stages.get(currentStage).getPhases().get(nextPhaseIndex);
PhaseType nextType = nextPhase.getType();

// Initialize phase-specific state
if (nextType == PhaseType.VOTE) {
    room.setVoteSubPhase("STATEMENT");
}

liveGameRoomService.save(room);
```

And remove the duplicate `StagePhase nextPhase` / `PhaseType nextType` lines that were on lines 131-132.

- [ ] **Step 3: Add phase state cleanup in executeNextStage**

In `executeNextStage()`, after `room.setCurrentSpeakerRoleId(null);` (line 207), add:

```java
phaseRuleEnforcer.resetPhaseState(room);
```

- [ ] **Step 4: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/TransitionPhaseTool.java
git commit -m "feat: TransitionPhaseTool clears phase state and initializes voteSubPhase on transition"
```

---

### Task 9: Update InitiateVoteTool

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/InitiateVoteTool.java`

- [ ] **Step 1: Expand allowedPhases**

Change line 57:

```java
@Override
public Set<PhaseType> allowedPhases() {
    return Set.of(PhaseType.FREE_CHAT, PhaseType.VOTE);
}
```

- [ ] **Step 2: Set voteSubPhase to VOTING**

In `execute()`, after `room.setActiveVote(vote);` (line 92), add:

```java
room.setVoteSubPhase("VOTING");
```

- [ ] **Step 3: Filter eliminated AI agents from voting**

Change the AI role ID collection (lines 105-108) to filter eliminated:

```java
List<ObjectId> aiRoleIds = room.getMembers().stream()
        .filter(m -> m.isAi() && !m.isDm() && m.getRoleId() != null)
        .filter(m -> !room.getEliminatedRoleIds().contains(m.getRoleId().toHexString()))
        .map(Member::getRoleId)
        .toList();
```

- [ ] **Step 4: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/InitiateVoteTool.java
git commit -m "feat: InitiateVoteTool allows VOTE phase, sets voteSubPhase, filters eliminated"
```

---

### Task 10: Update SelectRespondentsTool to filter eliminated

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/SelectRespondentsTool.java`

- [ ] **Step 1: Filter eliminated roles in resolveAiRoleId**

In `resolveAiRoleId()` (line 86), after the `isAi` check, add elimination check:

```java
private ObjectId resolveAiRoleId(String name, DmToolContext ctx, LiveGameRoom room) {
    String trimmed = name.trim();
    for (Role role : ctx.getScript().getRoles()) {
        if (role.getName() == null) continue;
        if (role.getName().equals(trimmed)
                || role.getName().contains(trimmed)
                || trimmed.contains(role.getName())) {
            boolean isAi = room.getMembers().stream()
                    .anyMatch(m -> m.isAi() && Objects.equals(m.getRoleId(), role.getId()));
            if (isAi) {
                // Skip eliminated roles
                if (room.getEliminatedRoleIds().contains(role.getId().toHexString())) {
                    log.info("[selectRespondents] skipping eliminated role '{}'", name);
                    return null;
                }
                return role.getId();
            }
        }
    }
    log.warn("[selectRespondents] could not resolve role name '{}', available roles: {}",
            name, ctx.getScript().getRoles().stream().map(Role::getName).toList());
    return null;
}
```

- [ ] **Step 2: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/SelectRespondentsTool.java
git commit -m "feat: SelectRespondentsTool filters eliminated roles"
```

---

### Task 11: Create SetInvestigationModeTool

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/SetInvestigationModeTool.java`

- [ ] **Step 1: Write the tool**

```java
package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SetInvestigationModeTool implements DmTool {

    private static final Set<String> VALID_MODES = Set.of("PUBLIC", "PRIVATE");

    private final LiveGameRoomService liveGameRoomService;

    @Data
    public static class Input {
        private String mode; // "PUBLIC" | "PRIVATE"
    }

    @Override
    public String name() {
        return "setInvestigationMode";
    }

    @Override
    public String description() {
        return "设置当前搜证阶段的模式。PUBLIC=搜证结果全场可见，PRIVATE=搜证结果仅搜证者可见。" +
                "必须在 INVESTIGATION 阶段开始时调用。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Set<PhaseType> allowedPhases() {
        return Set.of(PhaseType.INVESTIGATION);
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        String mode = input.getMode();

        if (mode == null || !VALID_MODES.contains(mode.toUpperCase())) {
            return Map.of("success", false,
                    "error", "无效的模式: " + mode + "，合法值: PUBLIC, PRIVATE");
        }

        LiveGameRoom room = ctx.getRoom();
        room.setInvestigationMode(mode.toUpperCase());
        liveGameRoomService.save(room);

        log.info("[setInvestigationMode] room={}, mode={}", room.getRoomId(), mode);
        return Map.of("success", true, "mode", mode.toUpperCase());
    }
}
```

- [ ] **Step 2: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/SetInvestigationModeTool.java
git commit -m "feat: add SetInvestigationModeTool for DM to choose PUBLIC/PRIVATE investigation"
```

---

### Task 12: Update AuthorizeSearchTool with stages filter and mode branching

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/AuthorizeSearchTool.java`

- [ ] **Step 1: Add stage-based clue filtering**

In `execute()`, update the clue filtering (lines 84-89) to also filter by stage:

```java
int currentStage = ctx.getRoom().getCurrentStage();

List<Clue> matchingClues = ctx.getScript().getClues() == null
        ? List.of()
        : ctx.getScript().getClues().stream()
                .filter(clue -> clue.getLocationTag() != null && clue.getLocationTag().contains(location))
                .filter(clue -> clue.getSearchableRoleIds() != null && clue.getSearchableRoleIds().contains(roleId))
                .filter(clue -> clue.getStages() == null || clue.getStages().isEmpty()
                        || clue.getStages().contains(currentStage))
                .toList();
```

- [ ] **Step 2: Branch on investigation mode**

Replace the broadcast section (lines 122-127) with mode-aware logic. Add `SimpMessagingTemplate` is already injected. Need to determine if searcher is AI:

```java
String investigationMode = room.getInvestigationMode();
boolean isPrivate = "PRIVATE".equals(investigationMode);
boolean isAiSearcher = room.getMembers().stream()
        .anyMatch(m -> m.isAi() && m.getRoleId() != null
                && m.getRoleId().toHexString().equals(roleId));

if (!foundTitles.isEmpty()) {
    if (!isPrivate) {
        // PUBLIC mode: broadcast to all
        messagingTemplate.convertAndSend(
                "/topic/room." + room.getRoomId(),
                Map.of("type", "CLUE_FOUND", "roleId", roleId, "clues", foundTitles));
    } else if (!isAiSearcher) {
        // PRIVATE mode + human searcher: send via private channel
        // Find userId for this roleId
        String userId = room.getMembers().stream()
                .filter(m -> !m.isAi() && m.getRoleId() != null
                        && m.getRoleId().toHexString().equals(roleId)
                        && m.getUserId() != null)
                .map(m -> m.getUserId().toHexString())
                .findFirst().orElse(null);
        if (userId != null) {
            messagingTemplate.convertAndSendToUser(userId,
                    "/queue/room." + room.getRoomId() + ".private",
                    Map.of("type", "PRIVATE_CLUE", "clues", foundTitles,
                            "label", "仅你可见"));
        }
    }
    // PRIVATE mode + AI searcher: no broadcast, clues already added to clueInstances
}
```

Also set `isPublic` on the `GameClueInstance` based on mode. In the clue instance creation loop, change `isPublic(false)` to:

```java
.isPublic(!isPrivate)
```

- [ ] **Step 3: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/AuthorizeSearchTool.java
git commit -m "feat: AuthorizeSearchTool supports stage filtering and PUBLIC/PRIVATE mode"
```

---

### Task 13: Update VoteService with elimination and tiebreaker support

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/service/VoteService.java`

- [ ] **Step 1: Add GameFlowService dependency**

Add to constructor (with `@Lazy` to avoid circular dependency):

```java
private final GameFlowService gameFlowService;

public VoteService(LiveGameRoomService liveGameRoomService,
                   StringRedisTemplate redisTemplate,
                   SimpMessagingTemplate messagingTemplate,
                   ObjectMapper objectMapper,
                   @Lazy DmExecutor dmExecutor,
                   @Lazy GameFlowService gameFlowService) {
    this.liveGameRoomService = liveGameRoomService;
    this.redisTemplate = redisTemplate;
    this.messagingTemplate = messagingTemplate;
    this.objectMapper = objectMapper;
    this.dmExecutor = dmExecutor;
    this.gameFlowService = gameFlowService;
}
```

Add import:

```java
import com.example.striptkillgamedemo2.entity.mongo.Member;
```

- [ ] **Step 2: Add private vote confirmation for human players**

In `castVote()`, after the VOTE_UPDATE broadcast (line 87), add private confirmation to human voter:

```java
// Send private vote confirmation to human player
String voterId = voterRoleId.toHexString();
String userId = room.getMembers().stream()
        .filter(m -> !m.isAi() && m.getRoleId() != null
                && m.getRoleId().toHexString().equals(voterId)
                && m.getUserId() != null)
        .map(m -> m.getUserId().toHexString())
        .findFirst().orElse(null);
if (userId != null) {
    messagingTemplate.convertAndSendToUser(userId,
            "/queue/room." + roomId + ".private",
            Map.of("type", "PRIVATE_VOTE", "choice", choice,
                    "label", "仅你可见"));
}
```

- [ ] **Step 3: Set voteSubPhase to RESULT on close**

In `closeVote()`, after `room.setActiveVote(null);` (line 105), add:

```java
room.setVoteSubPhase("RESULT");
```

- [ ] **Step 4: Add elimination and all-humans-check to closeVoteAndNotifyDm**

Update `closeVoteAndNotifyDm()` to include tiebreaker info and elimination:

```java
public void closeVoteAndNotifyDm(String roomId) {
    Map<String, String> results = closeVote(roomId);
    if (results.isEmpty()) return;

    String resultSummary = results.entrySet().stream()
            .map(e -> e.getKey() + " → " + e.getValue())
            .collect(Collectors.joining(", "));

    // Check for tie
    Map<String, Long> voteCounts = results.values().stream()
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
    long maxVotes = voteCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
    List<String> topChoices = voteCounts.entrySet().stream()
            .filter(e -> e.getValue() == maxVotes)
            .map(Map.Entry::getKey)
            .toList();

    String tieInfo = "";
    if (topChoices.size() > 1) {
        tieInfo = " 注意：出现平票（" + String.join("、", topChoices) + " 各 " + maxVotes + " 票）。" +
                "请让平票角色轮流发言，然后再次发起投票。最多重投2次，超过后请强制裁定AI的票。";
    }

    dmExecutor.executeDmAction(roomId, null,
            "投票已结束，结果如下：" + resultSummary + "。" + tieInfo +
            "请宣布投票结果，然后使用 transitionPhase 工具推进流程。");

    log.info("[VoteService] vote closed and DM notified, room={}, results={}", roomId, results);
}
```

- [ ] **Step 5: Add eliminateRole method**

Add a new public method for DM-driven elimination (called after DM announces result):

```java
/**
 * Mark a role as eliminated. If all humans are eliminated, end the game.
 */
public void eliminateRole(String roomId, String roleIdHex) {
    LiveGameRoom room = liveGameRoomService.get(roomId);
    if (room == null) return;

    room.getEliminatedRoleIds().add(roleIdHex);
    liveGameRoomService.save(room);

    log.info("[VoteService] role {} eliminated in room {}", roleIdHex, roomId);

    // Check if all human players are eliminated
    boolean allHumansEliminated = room.getMembers().stream()
            .filter(m -> !m.isAi() && !m.isDm() && m.getRoleId() != null)
            .allMatch(m -> room.getEliminatedRoleIds().contains(m.getRoleId().toHexString()));

    if (allHumansEliminated) {
        log.info("[VoteService] all humans eliminated in room {}, ending game", roomId);
        gameFlowService.endGame(roomId);
    }
}
```

- [ ] **Step 6: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/VoteService.java
git commit -m "feat: VoteService adds elimination, private vote confirmation, tiebreaker hints"
```

---

### Task 14: Update FREE_CHAT overtime silence detection

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/service/PhaseTimerService.java`
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java`

- [ ] **Step 1: Update PhaseTimerService FREE_CHAT timeout to set overtime flag**

In `startPhaseTimer()`, in the timeout handler (lines 94-115), add overtime logic for FREE_CHAT:

Replace the timeout handler:

```java
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
```

- [ ] **Step 2: Update AgentOrchestrator idleTimer to handle overtime mode**

In `resetIdleTimer(String roomId, LiveGameRoom room, Script script)` (lines 233-253), check overtime mode and use 30s timeout:

```java
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
```

- [ ] **Step 3: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/PhaseTimerService.java \
       src/main/java/com/example/striptkillgamedemo2/ai/orchestrator/AgentOrchestrator.java
git commit -m "feat: FREE_CHAT overtime mode with 30s silence detection"
```

---

### Task 15: Update PromptBuilder for sandbox filtering and stage flags

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/prompt/PromptBuilder.java`

- [ ] **Step 1: Add eliminated role labels to buildOtherRoles**

Update `buildOtherRoles()` (line 196) to accept `LiveGameRoom` and mark eliminated:

```java
private String buildOtherRoles(List<Role> roles, ObjectId excludeRoleId, LiveGameRoom room) {
    return roles.stream()
            .filter(r -> !Objects.equals(r.getId(), excludeRoleId))
            .map(r -> {
                String label = "- " + r.getName() + (r.isNpc() ? " (NPC)" : "");
                if (room != null && room.getEliminatedRoleIds() != null
                        && r.getId() != null
                        && room.getEliminatedRoleIds().contains(r.getId().toHexString())) {
                    label += " (已出局)";
                }
                return label;
            })
            .collect(Collectors.joining("\n"));
}
```

Update the call in `buildAgentPrompt()` (line 92):

```java
vars.put("otherRoles", buildOtherRoles(script.getRoles(), targetRole.getId(), room));
```

Update the call in `buildDmPrompt()` — `buildRoleList` should also mark eliminated. Update `buildRoleList`:

```java
private String buildRoleList(List<Role> roles, LiveGameRoom room) {
    return roles.stream()
            .map(r -> {
                String label = "- " + r.getName() + (r.isNpc() ? " (NPC)" : "");
                if (room != null && room.getEliminatedRoleIds() != null
                        && r.getId() != null
                        && room.getEliminatedRoleIds().contains(r.getId().toHexString())) {
                    label += " (已出局)";
                }
                return label;
            })
            .collect(Collectors.joining("\n"));
}
```

Update call in `buildDmPrompt()`:

```java
vars.put("roleList", buildRoleList(script.getRoles(), room));
```

- [ ] **Step 2: Add first/last stage flags to DM prompt**

In `buildDmPrompt()`, add stage position info after existing vars:

```java
int totalStages = script.getStages() != null ? script.getStages().size() : 1;
boolean isFirstStage = room.getCurrentStage() == 0;
boolean isLastStage = room.getCurrentStage() >= totalStages - 1;
vars.put("isFirstStage", String.valueOf(isFirstStage));
vars.put("isLastStage", String.valueOf(isLastStage));
vars.put("totalStages", String.valueOf(totalStages));
```

- [ ] **Step 3: Add first/last stage flags to agent prompt**

In `buildAgentPrompt()`, add:

```java
int totalStages = script.getStages() != null ? script.getStages().size() : 1;
boolean isLastStage = room.getCurrentStage() >= totalStages - 1;
vars.put("isLastStage", String.valueOf(isLastStage));
```

- [ ] **Step 4: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/prompt/PromptBuilder.java
git commit -m "feat: PromptBuilder marks eliminated roles, adds first/last stage flags"
```

---

### Task 16: Update DM system prompt

**Files:**
- Modify: `src/main/resources/prompts/dm-system.md`

- [ ] **Step 1: Rewrite dm-system.md**

Replace the entire file content with the updated prompt:

```markdown
你是"${scriptTitle}"的主持人（DM）。你拥有上帝视角，知晓所有真相。

## 全局规则
- 当前阶段：第${currentStage}幕（共${totalStages}幕）「${stageTitle}」
- 当前环节：${phaseType}（${phaseInstruction}）
- 存活角色：${roleList}
- 是否首幕：${isFirstStage}
- 是否末幕：${isLastStage}

## 搜证规则
角色搜证能力一览：
${searchPowerTable}

## 工具使用规则（严格遵守）
工具只能在对应环节使用，**禁止跨环节调用**：

| 工具 | 允许环节 | 说明 |
|------|----------|------|
| pushStageContent | 仅在新幕开始时 | 通知玩家新幕开启 |
| transitionPhase | 任意环节 | **统一流程推进工具**。NEXT_PHASE=推进到下一环节，NEXT_STAGE=推进到下一幕 |
| summarizeCurrentStage | 每幕结束时 | 归档本幕重点 |
| authorizeSearch | 仅在 INVESTIGATION | 授权搜证，扣除搜证次数 |
| setInvestigationMode | 仅在 INVESTIGATION | 设置搜证模式：PUBLIC（结果全场可见）或 PRIVATE（结果仅搜证者可见） |
| initiateVote | FREE_CHAT 或 VOTE | 发起投票（VOTE 环节内用于平票重投） |
| selectRespondents | 任意环节 | 选择 AI 角色回复（已出局角色会被自动过滤） |
| assignTurn | 仅在 TURN_BASED | 指定发言顺序 |

### 环节类型说明
| 环节 | 说明 |
|------|------|
| SCRIPT_READING | 阅读剧本阶段，玩家静默阅读，不触发讨论。AI 角色不会发言，玩家发言会被提醒保持安静 |
| TURN_BASED | 轮流发言，使用 selectRespondents 或 assignTurn 逐个触发 AI 角色发言，每个 AI 只发言一次 |
| FREE_CHAT | 自由讨论，使用 selectRespondents 选择 1-2 位 AI 角色开启讨论 |
| INVESTIGATION | 搜证阶段，先调用 setInvestigationMode 设置模式，然后轮流询问角色搜证 |
| VOTE | 投票环节，分三步：先让角色轮流陈述→发起投票→宣布结果 |

## 首幕特殊流程
当 isFirstStage 为 true 时，你必须：
1. 发表开场白，介绍故事背景和案件概况
2. 使用 selectRespondents 逐个触发所有 AI 角色进行自我介绍
3. 等待真人玩家自我介绍完毕
4. 然后使用 transitionPhase 进入第一个正式环节

## 末幕特殊流程
当 isLastStage 为 true 时：
1. 先宣布游戏即将进入尾声
2. 正常推进本幕流程
3. 在最后的 TURN_BASED 环节，提醒所有角色"这是最终陈述，请进行简要复盘和辩解"
4. VOTE 结束后宣布游戏结果，进行游戏复盘

## VOTE 环节详细流程
VOTE 环节分为三个子阶段，请严格按顺序执行：

**1. 陈述阶段：** 使用 selectRespondents 让每个角色轮流发言进行申辩（提醒他们这是投票前陈述，应该避免自己被投出）

**2. 投票阶段：** 使用 initiateVote 发起投票。AI 角色会自动投票，请提醒真人玩家进行投票，注明投票信息仅自己可见

**3. 结果阶段：**
- 宣布投票结果
- 如有平票：让平票角色再次轮流发言，然后再次使用 initiateVote 重新投票（最多重投2次）
- 超过2次仍平票：你可以强制裁定 AI 的票来打破平局（优先选择 AI 角色，不能改变真人玩家的票）
- 被投出的角色之后不能再发言（系统会自动阻止）
- 如果所有真人玩家都被投出，游戏将自动结束

## INVESTIGATION 搜证流程
1. 进入 INVESTIGATION 阶段后，首先调用 setInvestigationMode 设置搜证模式
2. PUBLIC 模式：搜证结果对所有人可见
3. PRIVATE 模式：
   - AI 角色搜证：结果不显示在聊天框，直接加入 AI 的记忆。搜证完成后请在聊天框提示"xxx已完成搜证"
   - 真人玩家搜证：结果仅该玩家可见（系统自动通过私密通道推送）

## 阶段播报职责
每当进入新环节（包括游戏刚开始的第一个环节），你必须向所有玩家**明确播报**：
1. 当前所处的**幕次**和**环节名称**（如"第一幕·自由讨论"）
2. 该环节的**时间限制**（如"本环节限时5分钟"）
3. 该环节的**规则要点**（如"请自由发言讨论案情"）

## 纠偏职责
你必须时刻关注讨论走向，当出现以下情况时**立即介入纠正**：
- 玩家或AI角色严重偏题
- 讨论陷入无意义的循环争论
- 有角色试图回避关键问题
- 讨论气氛过于松散

## 已出局角色处理
- 如有玩家 @已出局的角色，请提醒"该角色已被投出，无法回应"
- 已出局的真人玩家仍可发言，但请温和提醒其已出局

## 流程推进原则
1. 每个环节有时间限制，系统会在到期前提醒你
2. VOTE 环节为必须完成环节，不可跳过
3. 推进时务必填写 reason 字段
4. 每幕结束前尽量调用 summarizeCurrentStage 归档重点

当前环节是 **${phaseType}**，只使用该环节允许的工具。

## 重要限制
- 严禁直接向玩家透露剧本原文
- 保持中立，不偏袒任何角色

## 剧本真相
${fullScriptTruth}

## 历史摘要
${memoryFragments}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/prompts/dm-system.md
git commit -m "feat: rewrite DM system prompt with complete phase rules, first/last stage flows, VOTE tiebreaker"
```

---

### Task 17: Update agent system prompt

**Files:**
- Modify: `src/main/resources/prompts/agent-system.md`

- [ ] **Step 1: Add new sections to agent-system.md**

Append before the "正式输出示例" section (before line 47):

```markdown
## 搜证行为（INVESTIGATION 阶段）
当处于 INVESTIGATION 阶段时：
- 通过在聊天框中 @DM 请求搜证（如"@DM 我想搜查书房"）
- 搜证结果会私密地加入你的记忆，不会显示在聊天框中
- 不要在聊天框中讨论你的搜证结果细节，除非你主动选择透露

## 已出局角色
标记了"(已出局)"的角色已被投票淘汰：
- 不要主动 @提及或质问已出局的角色
- 不要回应已出局角色的发言
- 将注意力集中在仍在场的角色上

## 投票陈述
当 DM 宣布进入投票陈述阶段时：
- 这是投票前的最后发言机会
- 重点为自己辩护，避免被投出
- 可以指出其他嫌疑人的可疑之处来转移注意力

## 最终陈述（末幕）
当 isLastStage 为 true 且处于 TURN_BASED 阶段时：
- 这是游戏最后的发言机会
- 进行简要复盘：总结你观察到的关键线索和矛盾点
- 为自己做最后的辩解
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/prompts/agent-system.md
git commit -m "feat: agent prompt adds investigation, elimination, vote statement, final statement sections"
```

---

### Task 18: Update GameFlowService to remove references to deleted PhaseTypes

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/service/GameFlowService.java`

- [ ] **Step 1: Check for PRIVATE_TALK/FINAL_STATEMENT references**

Search for any references to the removed enum values. The file currently doesn't directly reference these types, but verify by compiling.

Run: `./mvnw compile`

If any errors related to `PRIVATE_TALK` or `FINAL_STATEMENT` appear in other files (e.g., `AiEngineProperties`), fix them:

Check `AiEngineProperties` for `privateTalkTimeoutSeconds` and `finalStatementTimeoutSeconds` — these config fields can remain (they just won't be referenced by the timer). Or remove them if you want a clean config.

- [ ] **Step 2: Compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit (if changes needed)**

```bash
git add -A
git commit -m "fix: remove remaining PRIVATE_TALK/FINAL_STATEMENT references"
```

---

### Task 19: Frontend — Private message channel and styling

**Files:**
- Modify: `frontend/src/stores/game.ts`
- Modify: `frontend/src/views/GamePlayView.vue`

- [ ] **Step 1: Add private message support to game store**

In `frontend/src/stores/game.ts`, add a `isPrivate` field to `ChatMessage`:

```typescript
export interface ChatMessage {
  messageId: string
  senderRoleId: string
  senderRoleName: string
  senderAvatar: string
  isAi: boolean
  content: string
  timestamp: string
  isPrivate?: boolean   // true for private messages (clues, votes)
  privateLabel?: string // e.g., "仅你可见"
}
```

Add a method to add private messages:

```typescript
function addPrivateMessage(msg: ChatMessage) {
  msg.isPrivate = true
  msg.privateLabel = msg.privateLabel || '仅你可见'
  messages.value.push(msg)
}
```

Return it from the store:

```typescript
return {
  roomId, scriptId, myRoleId, messages, gameStatus,
  setRoom, setScript, setMyRole, addMessage, addPrivateMessage,
  appendStreamChunk, finalizeStream,
  setGameStatus, clearGame
}
```

- [ ] **Step 2: Subscribe to private channel in GamePlayView.vue**

In `onMounted`, after the existing WebSocket `connect()` call, add subscription to the private channel. The `useWebSocket` composable needs to support user-queue subscriptions. Since WebSocket config already has `/queue` as a broker prefix, subscribe via STOMP:

In the `connect()` call's `onConnect` callback (or wherever subscriptions are set up), add:

```typescript
// Subscribe to private channel for this user
stompClient.subscribe('/user/queue/room.' + roomId + '.private', (message) => {
  const data = JSON.parse(message.body)
  if (data.type === 'PRIVATE_CLUE') {
    gameStore.addPrivateMessage({
      messageId: 'private-' + Date.now(),
      senderRoleId: 'system',
      senderRoleName: '系统',
      senderAvatar: '',
      isAi: false,
      content: '搜证结果：' + (data.clues || []).join('、'),
      timestamp: new Date().toISOString(),
      isPrivate: true,
      privateLabel: data.label || '仅你可见'
    })
  } else if (data.type === 'PRIVATE_VOTE') {
    gameStore.addPrivateMessage({
      messageId: 'private-vote-' + Date.now(),
      senderRoleId: 'system',
      senderRoleName: '系统',
      senderAvatar: '',
      isAi: false,
      content: '你的投票：' + data.choice,
      timestamp: new Date().toISOString(),
      isPrivate: true,
      privateLabel: data.label || '仅你可见'
    })
  }
})
```

Note: The exact integration depends on how `useWebSocket` composable works. If it doesn't expose the raw stompClient, you may need to add a `subscribePrivate(roomId, callback)` method to the composable.

- [ ] **Step 3: Add private message bubble styling**

In the template, update both left and right chat bubbles to handle private messages. Add after the existing `msg-bubble` div:

```html
<div
  :class="['msg-bubble', msg.isPrivate ? 'msg-bubble-private' : '', 'markdown-body']"
  v-html="renderMarkdown(msg.content)"
></div>
<span v-if="msg.isPrivate" class="private-label">{{ msg.privateLabel }}</span>
```

Add CSS in the `<style scoped>` section:

```css
.msg-bubble-private {
  background: linear-gradient(135deg, #2d1b4e 0%, #1a1040 100%) !important;
  border: 1px solid #6c5ce7 !important;
  position: relative;
}

.private-label {
  font-size: 11px;
  color: #6c5ce7;
  margin-top: 2px;
  display: block;
}
```

- [ ] **Step 4: Compile frontend**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/stores/game.ts frontend/src/views/GamePlayView.vue
git commit -m "feat: frontend private message channel subscription and purple bubble styling"
```

---

### Task 20: Full integration compile and test

**Files:**
- All modified files

- [ ] **Step 1: Full backend compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS with no errors

- [ ] **Step 2: Run all tests**

Run: `./mvnw test`
Expected: All tests pass. If tests fail due to PhaseType changes (test fixtures referencing PRIVATE_TALK or FINAL_STATEMENT), update the test fixtures.

- [ ] **Step 3: Frontend build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: game flow standardization — complete integration"
```

---

## File Change Summary

### New Files
| File | Task |
|------|------|
| `service/PhaseRuleEnforcer.java` | Task 5 |
| `ai/tool/impl/SetInvestigationModeTool.java` | Task 11 |

### Modified Files
| File | Tasks |
|------|-------|
| `entity/enums/PhaseType.java` | Task 1 |
| `entity/redis/LiveGameRoom.java` | Task 2 |
| `entity/mongo/Clue.java` | Task 3 |
| `service/PhaseTimerService.java` | Task 1, 14 |
| `ai/orchestrator/AgentOrchestrator.java` | Task 1, 6, 14 |
| `ai/executor/AgentExecutor.java` | Task 7 |
| `ai/tool/impl/TransitionPhaseTool.java` | Task 8 |
| `ai/tool/impl/InitiateVoteTool.java` | Task 9 |
| `ai/tool/impl/SelectRespondentsTool.java` | Task 10 |
| `ai/tool/impl/AuthorizeSearchTool.java` | Task 12 |
| `service/VoteService.java` | Task 13 |
| `ai/prompt/PromptBuilder.java` | Task 15 |
| `resources/prompts/dm-system.md` | Task 16 |
| `resources/prompts/agent-system.md` | Task 17 |
| `service/GameFlowService.java` | Task 18 |
| `frontend/src/stores/game.ts` | Task 19 |
| `frontend/src/views/GamePlayView.vue` | Task 19 |

### Deleted Files
| File | Task |
|------|------|
| `ai/tool/impl/DecidePhaseTransitionTool.java` | Task 4 |
| `ai/tool/impl/EndFreeChatTool.java` | Task 4 |
| `ai/tool/impl/SkipVoteTool.java` | Task 4 |
| `ai/tool/impl/AdvancePhaseTool.java` | Task 4 |
