# Game Flow Standardization Design

## Goal

Standardize the entire game flow state machine: enforce per-phase behavior rules (speaking control, silence detection, elimination), support private messaging, and define first-act / last-act special flows. Replace prompt-only soft constraints with backend-enforced hard rules while keeping DM in control of flow decisions.

## Approach

**Backend state machine + DM prompt guidance (Option A).** Hard rules (speaking blocks, turn limits, elimination) enforced by a new `PhaseRuleEnforcer` service. Soft decisions (when to advance, narration, introductions) remain with DM via prompts and tools.

---

## 1. Data Model Changes

### 1.1 `LiveGameRoom` — New Fields

```java
// Roles that have spoken in current TURN_BASED phase. Cleared on phase transition.
private Set<String> spokenRoleIds = new HashSet<>();

// Roles eliminated by VOTE. Persists across the game.
private Set<String> eliminatedRoleIds = new HashSet<>();

// Whether FREE_CHAT has entered overtime silence-detection mode.
private boolean phaseOvertime = false;

// Current investigation mode, set by DM via SetInvestigationModeTool. Null when not in INVESTIGATION.
private String investigationMode; // "PUBLIC" | "PRIVATE"

// VOTE sub-phase tracking. Set to STATEMENT on VOTE entry, VOTING when initiateVote called, RESULT when vote closes.
private String voteSubPhase; // "STATEMENT" | "VOTING" | "RESULT" | null
```

### 1.2 `Clue` — New Fields

```java
// Stages where this clue can be discovered. Null or empty means any stage.
private List<Integer> stages;

// Visibility when found: "PUBLIC" (all see) or "PRIVATE" (only searcher sees).
private String visibility; // "PUBLIC" | "PRIVATE"
```

### 1.3 `PhaseType` — Reduced to 5

Remove `PRIVATE_TALK` and `FINAL_STATEMENT`. Their functionality is absorbed:
- FINAL_STATEMENT → TURN_BASED sub-phase within VOTE
- PRIVATE_TALK → removed entirely

```java
public enum PhaseType {
    SCRIPT_READING,
    TURN_BASED,
    FREE_CHAT,
    INVESTIGATION,
    VOTE
}
```

All switch statements, prompt templates, timer defaults, and label methods referencing removed types must be updated.

### 1.4 `GameMessage` — Private Message Support

Existing `receiverRoleIds` field is sufficient. When non-null and non-empty, the message is private and delivered only via the private WebSocket channel.

---

## 2. Phase Rule Enforcer

New service: `com.example.striptkillgamedemo2.service.PhaseRuleEnforcer`

### 2.1 Core API

```java
@Service
public class PhaseRuleEnforcer {

    public enum SpeakCheck {
        ALLOWED,            // Normal processing
        BLOCKED_SILENT,     // Drop silently (eliminated AI, already-spoken AI)
        BLOCKED_DM_REMIND   // Drop + notify DM to remind player
    }

    /**
     * Check if a role can speak in the current phase.
     * Called by AgentOrchestrator before message routing.
     */
    public SpeakCheck checkCanSpeak(LiveGameRoom room, PhaseType phase,
                                     String roleIdHex, boolean isAi) { ... }

    /** Mark a role as having spoken (for TURN_BASED). */
    public void markSpoken(LiveGameRoom room, String roleIdHex) { ... }

    /** Clear phase-scoped state (spokenRoleIds, phaseOvertime). Called on phase transition. */
    public void resetPhaseState(LiveGameRoom room) { ... }
}
```

### 2.2 Rule Matrix

| Rule | SCRIPT_READING | TURN_BASED | FREE_CHAT | INVESTIGATION | VOTE |
|------|---------------|------------|-----------|---------------|------|
| AI can speak | BLOCKED_SILENT | Only if triggered by DM and not in spokenRoleIds | ALLOWED (round-limited) | ALLOWED (only @DM search requests; no chat response chain) | Only when `voteSubPhase == STATEMENT` and not in spokenRoleIds |
| Player can speak | BLOCKED_DM_REMIND (remind to read silently) | ALLOWED | ALLOWED | ALLOWED | ALLOWED |
| Eliminated AI | BLOCKED_SILENT | BLOCKED_SILENT | BLOCKED_SILENT | BLOCKED_SILENT | BLOCKED_SILENT |
| Eliminated player | BLOCKED_DM_REMIND | BLOCKED_DM_REMIND | BLOCKED_DM_REMIND | BLOCKED_DM_REMIND | BLOCKED_DM_REMIND |
| Player @eliminated AI | DM reminds "role eliminated" | same | same | same | same |
| Phase timer | Yes | No (completion-driven) | Yes | Yes | No (vote-completion-driven) |
| Silence detection | No | No | Yes (30s after overtime) | No | No |

### 2.3 Integration Point

In `AgentOrchestrator.onChatMessage()`, before any routing:

```
message received
  → PhaseRuleEnforcer.checkCanSpeak(room, phase, roleId, isAi)
    → BLOCKED_SILENT: return (no action)
    → BLOCKED_DM_REMIND: dmExecutor.executeDmAction(remind message), return
    → ALLOWED: continue existing routing logic
```

---

## 3. Per-Phase Detailed Behavior

### 3.1 SCRIPT_READING

1. DM announces phase name and time limit.
2. `PhaseTimerService` starts countdown.
3. AI agents: `BLOCKED_SILENT` — all messages dropped.
4. Player messages: `BLOCKED_DM_REMIND` — DM reminds "please read silently."
5. Timer expires → DM receives timeout prompt → calls `transitionPhase(NEXT_PHASE)`.

### 3.2 TURN_BASED

1. DM announces phase.
2. DM triggers AI agents one by one via `assignTurn` / `selectRespondents`.
3. Each AI speaks once → `PhaseRuleEnforcer.markSpoken()` adds to `spokenRoleIds`.
4. Subsequent triggers for already-spoken AI → `BLOCKED_SILENT`.
5. Players speak freely (not restricted by turn order).
6. When all have spoken (DM judges) → DM calls `transitionPhase(NEXT_PHASE)`.
7. After transition, `resetPhaseState()` clears `spokenRoleIds`.

### 3.3 FREE_CHAT

1. DM announces phase and time limit.
2. DM selects 1-2 AI agents to start discussion (`selectRespondents`).
3. AI-to-AI chain limited by `maxAiChatRounds` (e.g., 3). Last-round AI redirects conversation to human players or DM.
4. 60 seconds before timer expires → reminder to DM and players.
5. Timer expires → enter overtime mode:
   - Set `room.phaseOvertime = true`.
   - Switch `idleTimer` to 30-second silence detection.
   - If 30 seconds pass with no messages → DM auto-ends FREE_CHAT.
6. Player requests end / flow advancement → DM ends directly.
7. On end: block AI speaking → DM announces end → `transitionPhase(NEXT_PHASE)`.

### 3.4 INVESTIGATION

1. DM announces phase, time limit, and roles with search power.
2. DM calls `setInvestigationMode("PUBLIC" | "PRIVATE")` to set mode.
3. DM asks AI agents and players to search (in turn or freely).
4. AI agents @DM in chat to request search → DM calls `AuthorizeSearchTool`.
5. **PUBLIC mode:**
   - Results broadcast to all via `CLUE_FOUND` signal.
   - Clue details shown in chat for everyone.
6. **PRIVATE mode:**
   - AI search: results silently added to `clueInstances` (included in next prompt). No broadcast. DM posts "xxx has completed search" in chat.
   - Player search: results pushed via private WebSocket (`/user/queue/room.{roomId}.private`). Special bubble style + "only you can see this" label.
7. Clue filtering: only clues where `clue.stages` contains `room.currentStage` (or `stages` is null/empty) can be found. Already-found clues excluded (existing logic).
8. AI cannot trigger other AI responses in this phase (no chat response chain).
9. Search complete → DM announces end → `transitionPhase(NEXT_PHASE)`.

### 3.5 VOTE

Three sequential sub-phases within a single VOTE phase:

**Sub-phase tracking:** `room.voteSubPhase` is set to `"STATEMENT"` when entering VOTE phase (by `TransitionPhaseTool`). `InitiateVoteTool` sets it to `"VOTING"`. `VoteService.closeVote()` sets it to `"RESULT"`. `PhaseRuleEnforcer` uses this to control AI speaking: only allowed when `voteSubPhase == "STATEMENT"`.

**Sub-phase A — Pre-vote Statement (`voteSubPhase = STATEMENT`):**
1. DM announces vote statement period.
2. DM triggers each role to speak. Agent prompt injected: "This is pre-vote statement. Defend yourself to avoid being voted out."
3. Each role speaks once (`spokenRoleIds` enforced).
4. `spokenRoleIds` cleared after statements complete.

**Sub-phase B — Voting (`voteSubPhase = VOTING`):**
1. DM calls `initiateVote(title, options)` — this sets `voteSubPhase = "VOTING"`, blocking further AI speech.
2. AI agents vote silently via `executeAgentVote()`.
3. Player votes: confirmation pushed via private WebSocket (special bubble + "only you can see this").
4. Player hasn't voted for extended time → DM reminds.

**Sub-phase C — Results & Tiebreaker (`voteSubPhase = RESULT`):**
1. Vote closes → `voteSubPhase` set to `"RESULT"`. DM announces vote results.
2. If tie → tied roles speak (TURN_BASED) → re-vote. Max N retries (configurable, default 2).
3. After N retries still tied → DM force-decides **AI votes only** (priority: pick AI agent if AI+human tied; otherwise random among AI). DM then announces updated results.
4. Eliminated role added to `room.eliminatedRoleIds`.
5. Check: if all human players eliminated → `GameFlowService.endGame()`.
6. DM announces VOTE phase end → `transitionPhase(NEXT_PHASE)`.

---

## 4. Stage-Level Flow Control

### 4.1 First Stage (`currentStage == 0`)

Driven by DM prompt, no special backend logic:

```
DM opening speech (introduce story background)
  → DM calls selectRespondents to trigger AI agent self-introductions (one by one)
  → Wait for player self-introductions (DM judges)
  → Enter normal stage flow (SCRIPT_READING → discussion → ...)
```

`PromptBuilder` injects first-stage instructions into DM prompt when `currentStage == 0`.

### 4.2 Normal Stages

Phase sequence defined by `ScriptStage.phases` list. DM advances through phases via `transitionPhase(NEXT_PHASE)`.

Required phases per stage:
- SCRIPT_READING: always present
- At least one of TURN_BASED / FREE_CHAT
- INVESTIGATION: optional
- VOTE: optional

### 4.3 Last Stage (`currentStage == stages.size() - 1`)

```
DM announces approaching finale
  → Normal stage flow (SCRIPT_READING → discussion → ...)
  → TURN_BASED final statement (prompt: "This is the final stage. Provide brief recap and defense.")
  → VOTE (if configured in script phases, DM decides)
  → DM announces game over → game review/recap
  → GameFlowService.endGame()
```

`PromptBuilder` injects last-stage flag. Agent prompts include final-statement instruction when last stage + TURN_BASED phase.

---

## 5. DM Tool Changes

### 5.1 New Tool

**`SetInvestigationModeTool`**

```java
name: "setInvestigationMode"
description: "Set investigation mode for current INVESTIGATION phase"
input: { mode: "PUBLIC" | "PRIVATE" }
allowedPhases: Set.of(PhaseType.INVESTIGATION)
effect: Sets room.investigationMode
```

### 5.2 Modified Tools

**`AuthorizeSearchTool`:**
- Add `clue.stages` filter: only discover clues where `stages` contains `currentStage` (or `stages` is null/empty).
- Branch on `room.investigationMode`:
  - PUBLIC: broadcast CLUE_FOUND (existing behavior).
  - PRIVATE + AI searcher: add to `clueInstances` silently, return result to DM only.
  - PRIVATE + player searcher: add to `clueInstances`, push via private WebSocket.

**`InitiateVoteTool`:**
- Expand `allowedPhases` to `Set.of(FREE_CHAT, VOTE)` — needed for tiebreaker re-votes within VOTE phase.

**`TransitionPhaseTool`:**
- On phase transition: call `PhaseRuleEnforcer.resetPhaseState(room)` to clear `spokenRoleIds` and `phaseOvertime`.
- Clear `room.investigationMode` when leaving INVESTIGATION.
- Set `room.voteSubPhase = "STATEMENT"` when entering VOTE phase; clear when leaving.

**`SelectRespondentsTool`:**
- Filter out roles in `room.eliminatedRoleIds`.

### 5.3 Deleted Tools

| Tool | Reason |
|------|--------|
| `DecidePhaseTransitionTool` | Superseded by TransitionPhaseTool |
| `EndFreeChatTool` | Superseded by TransitionPhaseTool |
| `SkipVoteTool` | Superseded by TransitionPhaseTool |
| `AdvancePhaseTool` | Redundant with TransitionPhaseTool |

### 5.4 Tool Phase Matrix

| Tool | Allowed Phases |
|------|---------------|
| transitionPhase | All |
| selectRespondents | All (filters eliminated) |
| assignTurn | TURN_BASED |
| pushStageContent | All |
| readFullScript | All |
| summarizeCurrentStage | All |
| initiateVote | FREE_CHAT, VOTE |
| authorizeSearch | INVESTIGATION |
| setInvestigationMode | INVESTIGATION |

---

## 6. Private Messaging

### 6.1 Backend

New WebSocket user-directed channel: `/user/queue/room.{roomId}.private`

Push via `SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/room." + roomId + ".private", payload)`.

Private message types:
- `PRIVATE_CLUE` — private investigation result (clue title, content, "only you can see this")
- `PRIVATE_VOTE` — player's own vote confirmation

Messages stored in Redis (`GameMessage` with `receiverRoleIds` set) for history replay.

### 6.2 Frontend

- Subscribe to `/user/queue/room.{roomId}.private` on room join.
- Render private messages with distinct background color + "仅你可见" label.
- Mix into main chat timeline by timestamp.

### 6.3 Agent Prompt Sandbox

In `PromptBuilder.buildAgentPrompt()`, filter messages before injecting into prompt:

```java
recentMessages.stream()
    .filter(msg -> msg.getReceiverRoleIds() == null
                || msg.getReceiverRoleIds().isEmpty()
                || msg.getReceiverRoleIds().contains(currentRoleId))
    .toList();
```

Each agent sees only: public messages + private messages addressed to them.

---

## 7. Elimination Mechanics

### 7.1 Marking

After vote result confirmed, elected role's `roleIdHex` added to `room.eliminatedRoleIds`.

### 7.2 Enforcement

| Scenario | Behavior |
|----------|----------|
| Eliminated AI agent triggered | `BLOCKED_SILENT` — silently dropped |
| Player @eliminated AI | `AgentOrchestrator` detects target eliminated → DM reminds "this role has been voted out" |
| Eliminated human player speaks | Message sent normally, DM reminds "this player has been voted out" |
| All humans eliminated | `VoteService` detects → `GameFlowService.endGame()` |

### 7.3 Prompt Impact

- Agent prompt `otherRoles` list marks eliminated roles with "(已出局)" label.
- Agents instructed not to @mention or engage eliminated roles.
- `SelectRespondentsTool` and `executeAgentReply` skip eliminated roles.

---

## 8. FREE_CHAT Overtime & Silence Detection

### 8.1 Flow

```
FREE_CHAT timer running
  → 60s remaining: PhaseTimerService sends reminder to DM and players
  → Timer expires:
      - Set room.phaseOvertime = true
      - Broadcast PHASE_TIMER_EXPIRED
      - DM notified: "Time is up. If discussion continues, wait for silence."
      - Switch idleTimer to 30-second mode
  → 30s silence detected (no messages):
      - DM auto-ends FREE_CHAT
      - Call transitionPhase(NEXT_PHASE)
  → Player requests end / advancement:
      - DM ends immediately
```

### 8.2 Implementation

Reuse existing `AgentOrchestrator.idleTimer` mechanism:
- Normal FREE_CHAT: idleTimer = `aiProperties.idleTimeoutSeconds` (default 60s)
- After phase overtime: idleTimer = 30s, and on fire → DM transitions instead of just prompting

---

## 9. Prompt Changes

### 9.1 `dm-system.md` Additions

- **First stage instructions:** When `currentStage == 0`, DM must deliver opening speech, trigger AI self-introductions via `selectRespondents` one by one, wait for player introductions before advancing.
- **Last stage instructions:** When last stage, DM announces finale, guides final TURN_BASED statement with recap/defense prompt, handles final VOTE if configured, announces game end and conducts review.
- **INVESTIGATION mode instructions:** DM must call `setInvestigationMode` at start of INVESTIGATION phase. Describe PUBLIC vs PRIVATE behavior.
- **VOTE tiebreaker instructions:** Max N retries, then force-decide AI votes.
- **Phase announcement:** Every phase transition → DM must announce: stage name, phase name, time limit, and rules.

### 9.2 `agent-system.md` Additions

- **INVESTIGATION behavior:** AI agents @DM to request search. Search results are private (injected into next prompt, not shown in chat).
- **Eliminated role handling:** Do not @mention or engage eliminated roles.
- **Vote statement prompt:** When in VOTE phase TURN_BASED, defend yourself to avoid elimination.
- **Last stage final statement:** Provide brief recap and defense.

---

## 10. File Change Summary

### New Files

| File | Purpose |
|------|---------|
| `service/PhaseRuleEnforcer.java` | Phase behavior rule checking |
| `ai/tool/impl/SetInvestigationModeTool.java` | DM sets investigation mode |

### Modified Files

| File | Changes |
|------|---------|
| `entity/redis/LiveGameRoom.java` | Add `spokenRoleIds`, `eliminatedRoleIds`, `phaseOvertime`, `investigationMode`, `voteSubPhase` |
| `entity/mongo/Clue.java` | Add `stages: List<Integer>`, `visibility: String` |
| `entity/enums/PhaseType.java` | Remove `PRIVATE_TALK`, `FINAL_STATEMENT` |
| `ai/orchestrator/AgentOrchestrator.java` | Integrate PhaseRuleEnforcer at message entry; INVESTIGATION no chat chain |
| `ai/executor/AgentExecutor.java` | Check elimination before executing |
| `ai/tool/impl/AuthorizeSearchTool.java` | stages filter, PUBLIC/PRIVATE mode split, private WebSocket |
| `ai/tool/impl/InitiateVoteTool.java` | allowedPhases → FREE_CHAT + VOTE |
| `ai/tool/impl/TransitionPhaseTool.java` | Clear spokenRoleIds, phaseOvertime, investigationMode on transition |
| `ai/tool/impl/SelectRespondentsTool.java` | Filter eliminated roles |
| `service/VoteService.java` | Tiebreaker loop, DM force-decide, all-humans-eliminated check |
| `service/PhaseTimerService.java` | FREE_CHAT overtime → 30s silence mode |
| `ai/prompt/PromptBuilder.java` | Private message filter; first/last stage flags; eliminated role labels |
| `resources/prompts/dm-system.md` | First stage intro, last stage finale, INVESTIGATION mode, VOTE tiebreaker, phase announcements |
| `resources/prompts/agent-system.md` | INVESTIGATION search format, elimination handling, vote statement, final statement |
| `service/GameFlowService.java` | Remove PRIVATE_TALK/FINAL_STATEMENT references |
| `frontend/src/views/GamePlayView.vue` | Subscribe private channel, private bubble style |
| `frontend/src/stores/game.ts` | Handle private messages |

### Deleted Files

| File | Reason |
|------|--------|
| `ai/tool/impl/DecidePhaseTransitionTool.java` | Superseded |
| `ai/tool/impl/EndFreeChatTool.java` | Superseded |
| `ai/tool/impl/SkipVoteTool.java` | Superseded |
| `ai/tool/impl/AdvancePhaseTool.java` | Redundant |
