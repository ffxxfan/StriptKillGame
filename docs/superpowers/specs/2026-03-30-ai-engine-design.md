# AI Core Engine Design Spec

**Date**: 2026-03-30
**Status**: Approved
**Scope**: AgentOrchestrator, DM Tool System, MemoryManager, Communication Loop, FinalReview

---

## 1. Design Decisions

| Question | Decision |
|---|---|
| LLM Provider | Provider-agnostic (`ChatModel` interface), Anthropic as default, model configurable via `application.properties` |
| Prompt Templates | Hybrid: `Script.dmConfig` / `Role.prompt` override classpath defaults (`resources/prompts/*.md`) |
| Agent Reply Strategy | Hybrid: rule-based for `@mention` / turn enforcement, DM-judged for ambiguous public chat |
| Compression | Dual-trigger: stage transitions + 70% token threshold |
| Vote System | Simple majority with timer, extensible for weighted/multi-round later |
| Phase Flow | TURN_BASED (mandatory) → FREE_CHAT (optional, DM-gated) → VOTE (optional, DM-gated) |
| Architecture | Event-driven pipeline with `@Async` listeners |
| DmToolService | Registry pattern — `DmTool` interface, auto-discovered via Spring DI |

---

## 2. Data Model Extensions

### 2.1 New: `StagePhase` (embedded in `ScriptStage`)

```java
@Data @Builder
public class StagePhase {
    String phaseId;
    PhaseType type;            // FREE_CHAT, TURN_BASED, VOTE
    List<String> speakOrder;   // roleId sequence (TURN_BASED only)
    int timeLimitSeconds;      // 0 = no limit
    String dmInstruction;      // phase-specific DM guidance
}
```

### 2.2 New: `PhaseType` enum

```java
public enum PhaseType { FREE_CHAT, TURN_BASED, VOTE }
```

### 2.3 New: `VoteSession` (embedded in `LiveGameRoom`)

```java
@Data @Builder
public class VoteSession {
    String voteId;
    String title;
    List<String> options;
    Map<String, String> results;   // roleId → chosen option
    LocalDateTime deadline;
}
```

### 2.4 `ScriptStage` — add field

```java
List<StagePhase> phases;  // ordered phases within this stage
```

### 2.5 `LiveGameRoom` — add fields

```java
int currentPhaseIndex;             // which phase within current stage
String currentSpeakerRoleId;       // active speaker in TURN_BASED (null in FREE_CHAT)
VoteSession activeVote;            // non-null when vote is open
```

### 2.6 New: classpath prompt templates

```
resources/prompts/
  dm-system.md          — DM system prompt with ${placeholders}
  agent-system.md       — Agent system prompt with ${placeholders}
  compression.md        — Compression instruction template
  review-system.md      — FinalReview prompt template
```

### 2.7 New: Redis memory fragments

```
Key:    game:{roomId}:memory:{stageNumber}
Value:  JSON structured summary (see Section 6)
TTL:    same as room TTL
```

---

## 3. Service Architecture

### 3.1 Pipeline Overview

```
Message In → GameChatController (store + broadcast)
          → ChatMessageEvent
              ├─ AgentOrchestrator (decides who replies, enforces turn order)
              │   ├─ AgentReplyEvent → AgentExecutor (sandboxed LLM call)
              │   └─ DmRequestEvent → DmExecutor (LLM call with tools)
              └─ MemoryManager (token threshold check)
```

### 3.2 Service Responsibilities

| Service | Responsibility | Async? |
|---|---|---|
| `AgentOrchestrator` | Decides which agents reply, enforces turn order, routes to DM | `@EventListener` |
| `AgentExecutor` | Builds sandboxed prompt, calls ChatModel for agent roles | `@Async` |
| `DmExecutor` | Builds DM prompt, calls ChatModel with FunctionCallbacks | `@Async` |
| `DmToolRegistry` | Collects all `DmTool` beans, builds context-bound callbacks | Sync |
| `PromptBuilder` | Loads templates, injects variables, enforces sandbox isolation | Sync |
| `MemoryManager` | Token estimation, LLM-based compression, Redis fragment storage | `@Async` on trigger |
| `PhaseTimerService` | Dynamic turn/phase timeouts via ScheduledExecutorService | Scheduled |
| `VoteService` | Opens/closes votes, collects ballots, broadcasts results | Sync |
| `FinalReviewService` | End-game LLM summarization: scores, reveals, narrative | `@Async` |

---

## 4. Extensible DmTool System

### 4.1 Interface

```java
public interface DmTool {
    String name();              // function name for LLM
    String description();       // LLM-facing description
    Class<?> inputType();       // request DTO for JSON schema
    Object execute(Object input, DmToolContext ctx);
}

@Data @Builder
public class DmToolContext {
    LiveGameRoom room;
    Script script;
    String currentPhaseId;
    ObjectId triggerRoleId;
}
```

### 4.2 Registry

```java
@Service
public class DmToolRegistry {
    private final List<DmTool> tools;  // Spring auto-injects all implementations

    public List<FunctionCallback> buildCallbacks(DmToolContext ctx) {
        // converts each DmTool to Spring AI FunctionCallback bound to ctx
    }
}
```

### 4.3 Built-in Tools (v1)

| Tool | Side Effects |
|---|---|
| `AuthorizeSearchTool` | Deduct searchPower, update cluePool, push clue via WebSocket `/queue/clue` |
| `InitiateVoteTool` | Open VoteSession on room, broadcast VOTE_OPEN signal |
| `ReadFullScriptTool` | Read-only, returns script truth to DM context |
| `AssignTurnTool` | Update currentSpeakerRoleId, broadcast TURN_CHANGE signal |
| `AdvancePhaseTool` | Increment phase/stage, broadcast PHASE_CHANGE signal |
| `SelectRespondentsTool` | Returns roleId list (no side effects) |
| `DecidePhaseTransitionTool` | Returns action: ENTER_FREE_CHAT / SKIP_TO_VOTE_CHECK / ADVANCE_STAGE |
| `EndFreeChatTool` | Close FREE_CHAT, proceed to vote check |
| `SkipVoteTool` | Skip vote, advance to next stage |

Extension: new tool = new `@Component implements DmTool`. Zero changes to existing code.

---

## 5. Prompt Sandbox & Templates

### 5.1 DM Prompt Structure

```
[1] Classpath: dm-system.md (overridden by Script.dmConfig if non-blank)
[2] Full script truth (all secrets, all clues)
[3] Memory fragments (compressed stage summaries from Redis)
[4] Current phase instruction (StagePhase.dmInstruction)
[5] Sliding window: last N messages (ALL messages — DM sees everything)
[6] Available tools (auto-injected by Spring AI)
```

### 5.2 Agent Prompt Structure (per role)

```
[1] Classpath: agent-system.md (overridden by Role.prompt if non-blank)
[2] OWN secret only (Role.secret — sandboxed)
[3] OWN clue discoveries only (filtered GameClueInstances)
[4] Public role info: other roles as {name, avatar, description} — NO secrets
[5] Memory fragments (filtered: public events + own events only)
[6] Current stage briefing (phase content for this role)
[7] Sliding window: last N messages (only public + messages addressed to this role)
```

### 5.3 Sandbox Enforcement

`PromptBuilder.buildAgentPrompt(roomId, roleId)`:
1. Load Script → extract ONLY target role's `secret` and `selfClueIds`
2. Filter clueInstances → only where `ownerRoleIds` contains this roleId OR `isPublic=true`
3. Filter messages → only where `receiverRoleIds` is null (public) OR contains this roleId
4. Other roles rendered as `{name, avatar, description}` — NEVER their `secret` or `prompt`

### 5.4 Template Loading Priority

`Role.prompt` / `Script.dmConfig` (if non-blank) → classpath default → throw error

### 5.5 Token Budget (configurable, example for 100k context)

| Section | Max Tokens | Priority |
|---|---|---|
| System prompt | 3,000 | Fixed |
| Memory fragments | 8,000 | Grows with game |
| Stage briefing | 2,000 | Fixed per stage |
| Sliding window | fills remainder | Adaptive |

If memory fragments exceed budget → oldest summaries further compressed (summary-of-summaries).

---

## 6. MemoryManager — Compression

### 6.1 Triggers

- **Stage transition**: compress ALL messages from completed stage
- **Token threshold**: before each LLM call, if estimated tokens > 70% of max → compress oldest uncompressed messages

### 6.2 Token Estimation

```java
int estimateTokens(String text) {
    return (int) (text.length() / charsPerToken);  // configurable, default 3.5
}
```

### 6.3 Compressed Output Schema

```json
{
  "stageNumber": 2,
  "keyEvents": [
    {"type": "ACCUSATION", "from": "林默", "to": "苏婉", "summary": "公开质疑不在场证明"},
    {"type": "CLUE_FOUND", "role": "陈探长", "clue": "血迹报告", "reaction": "引起震动"},
    {"type": "LIE_DETECTED", "role": "苏婉", "detail": "声称在书房但被证实在花园"}
  ],
  "relationshipChanges": [
    {"from": "林默", "to": "苏婉", "change": "SUSPICIOUS"}
  ],
  "unresolved": ["花园脚印归属未定", "密室钥匙下落不明"]
}
```

### 6.4 Storage

Redis key: `game:{roomId}:memory:{stageNumber}`, TTL = room TTL.

---

## 7. Communication Loop & Phase Management

### 7.1 Phase State Machine

```
TURN_BASED (mandatory, defined in ScriptStage.phases)
    │ all speakers done
    ▼
DM evaluates via decidePhaseTransition tool
    ├─ Player/Agent requests free chat → DM allows or denies
    ├─ DM autonomously decides
    │
    ├─ ENTER_FREE_CHAT ──▶ FREE_CHAT
    │                        │ DM calls endFreeChat or timer expires
    │                        ▼
    │                    DM evaluates: vote needed?
    │                        ├─ yes → VOTE → next stage
    │                        └─ no  → next stage
    │
    └─ SKIP ──▶ DM evaluates: vote needed?
                    ├─ yes → VOTE → next stage
                    └─ no  → next stage
```

DM is the **sole authority** on phase transitions. Any participant can request, DM decides.

### 7.2 Turn-Based Enforcement

1. Phase enters TURN_BASED → `currentSpeakerRoleId` = first in `speakOrder`
2. Human message → AgentOrchestrator checks: is sender current speaker? If not, reject.
3. Speaker finishes or timer expires → advance to next speaker
4. AI speaker → AgentExecutor triggered immediately
5. All done → DM auto-evaluates next phase

### 7.3 WebSocket Signals (`/topic/room.{roomId}.signal`)

| Signal | Payload | Purpose |
|---|---|---|
| `TYPING` | `{roleId, roleName}` | Typing indicator |
| `TYPING_END` | `{roleId}` | Remove typing indicator |
| `PHASE_CHANGE` | `{phaseId, type, speakOrder?}` | UI mode switch |
| `TURN_CHANGE` | `{currentSpeakerRoleId, roleName}` | Highlight speaker |
| `VOTE_OPEN` | `{voteId, title, options, deadline}` | Show vote UI |
| `VOTE_UPDATE` | `{voteId, votedCount, total}` | Vote progress |
| `VOTE_CLOSED` | `{voteId, results}` | Show results |
| `CLUE_FOUND` | `{roleId, clueTitle}` | Public notification |
| `CLUE_DETAIL` | `{clue}` | Private via `/queue/clue` |
| `STAGE_ADVANCE` | `{stageNumber, stageTitle}` | Stage transition |
| `GAME_END` | `{summary}` | Final screen |

### 7.4 Agent Routing Rules (AgentOrchestrator)

| Condition | Action |
|---|---|
| `@DM` or search/vote request | Route to DmExecutor |
| TURN_BASED, current speaker is AI | Trigger that agent via AgentExecutor |
| FREE_CHAT, `@角色名` mentioned | Trigger named agent |
| FREE_CHAT, general message | DM selects 1-2 respondents via `selectRespondents` tool |

---

## 8. FinalReviewService

### 8.1 Trigger

Last stage completes or DM calls `endGame`.

### 8.2 Input

- All memory fragments (compressed stage summaries)
- cluePool final state (found/missed/by whom)
- VoteRecords (all rounds)
- Final sliding window messages

### 8.3 Output Schema

```json
{
  "narrative": "剧情总结...",
  "truthReveal": "真相揭秘...",
  "roleScores": [
    {
      "roleId": "xxx",
      "roleName": "林默",
      "score": 85,
      "highlights": ["成功发现关键线索", "精准质疑"],
      "missedClues": ["未发现花园脚印"]
    }
  ],
  "unresolvedMysteries": ["密室钥匙的真正用途"],
  "mvp": {"roleId": "xxx", "reason": "最接近真相的推理链"}
}
```

### 8.4 Side Effects

1. Broadcast `GAME_END` signal with summary
2. Persist to `GameRecord` in MongoDB (`aiSummary` = narrative + truthReveal)
3. Evict Redis room state (existing `endGame` flow)

---

## 9. Configuration

### 9.1 application.properties additions

```properties
# Spring AI - Anthropic (default provider, switchable)
spring.ai.anthropic.api-key=${AI_API_KEY}
spring.ai.anthropic.chat.options.model=claude-sonnet-4-20250514
spring.ai.anthropic.chat.options.max-tokens=4096

# Game AI engine
game.ai.chars-per-token=3.5
game.ai.token-threshold-ratio=0.7
game.ai.max-context-tokens=100000
game.ai.sliding-window-size=20
game.ai.turn-timeout-seconds=60
game.ai.vote-timeout-seconds=120
game.ai.free-chat-timeout-seconds=300
```

### 9.2 Model switching

Change `spring.ai.anthropic.*` to `spring.ai.openai.*` (or any provider) — all service code uses `ChatModel` interface, no provider-specific imports.
