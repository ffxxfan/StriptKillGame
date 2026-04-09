# AI Multi-Round Conversation Limits & Idle Detection Design

## Overview

Two enhancements to improve game flow in the script-kill game:

1. **AI Multi-Round Conversation**: Allow AI agents to have multi-round conversations when they @mention or reference each other, with configurable + dynamic limits and forced topic redirection on the final round.
2. **Idle Detection**: When no activity occurs for a configurable duration, DM auto-advances the game.

---

## Feature 1: AI Multi-Round Conversation Limits

### Problem

Currently AI-to-AI conversations are either blocked or unlimited. We need controlled multi-round AI dialogue that enriches gameplay without drowning out human players.

### Design

#### Round Counter

`AgentOrchestrator` maintains a per-room counter tracking consecutive AI-to-AI conversation rounds:

```
ConcurrentHashMap<String, AtomicInteger> aiChatRoundCounters
```

- Incremented each time an AI message triggers another AI agent (in `handleFreeChat` when `fromAi=true`)
- Reset to 0 when:
  - A human player sends a message
  - A phase transition occurs

#### Effective Max Calculation

Combines a configurable base value with dynamic adjustment based on human player count:

```
configMax = AiEngineProperties.maxAiChatRounds  (default: 3)
humanCount = number of non-AI, non-DM members in room
effectiveMax = max(2, configMax - (humanCount - 1))
```

Examples (configMax=3):
- 1 human player: effectiveMax = 3 (full AI interaction to maintain atmosphere)
- 2 human players: effectiveMax = 2 (shorter AI exchanges, more room for humans)
- 3+ human players: effectiveMax = 2 (minimum floor)

#### Last-Round Prompt Injection

When `currentRound == effectiveMax`, before calling `AgentExecutor`, append a directive to the agent's context via `ChatMessageEvent`:

The event gets a new field `lastAiRound` (boolean). `AgentExecutor.doExecuteAgentReply` checks this field (passed through a thread-local or method parameter) and appends to the prompt:

> "这是你在本轮 AI 对话中的最后一次发言机会，请将话题自然地引向在场的玩家或主持人，邀请他们参与讨论或表达看法。"

When `currentRound > effectiveMax`, the AI message is silently dropped (no further agents triggered).

#### Flow

```
AI Agent A sends message mentioning Agent B
  → AgentOrchestrator.onChatMessage(fromAi=true)
  → aiChatRoundCounters[roomId].incrementAndGet()
  → if currentRound > effectiveMax: return (stop chain)
  → if currentRound == effectiveMax: set lastAiRound=true
  → handleFreeChat triggers Agent B (Layer 1/2 only)
  → Agent B prompt includes redirect instruction if lastAiRound
  → Agent B replies, publishes ChatMessageEvent(fromAi=true)
  → cycle repeats with limit check
```

#### Config

```properties
# application.properties
game.ai.max-ai-chat-rounds=3
```

#### Files Changed

| File | Change |
|------|--------|
| `AiEngineProperties` | Add `maxAiChatRounds` field |
| `ChatMessageEvent` | Add `lastAiRound` boolean field |
| `AgentOrchestrator` | Add round counter map, effective max calculation, round check in `onChatMessage`, reset on human message |
| `AgentExecutor.doExecuteAgentReply` | Check `lastAiRound` flag, inject redirect prompt |
| `GameFlowService` (phase transitions) | Reset round counter on phase change |

---

## Feature 2: Idle Detection & DM Auto-Advance

### Problem

When human players go silent (e.g. AFK or thinking), the game stalls. The DM should proactively push things forward.

### Design

#### Activity Tracker

`AgentOrchestrator` maintains a per-room last-activity timestamp and a scheduled idle timer:

```
ConcurrentHashMap<String, ScheduledFuture<?>> idleTimers
ScheduledExecutorService idleScheduler
```

#### Scope

Idle detection **only fires during FREE_CHAT phase**. Other phases have their own timer mechanisms (`PhaseTimerService`).

#### Activity Sources (reset idle timer)

The following actions reset the idle timer:
- Human player sends a chat message
- AI agent sends a chat message
- DM agent sends a chat message
- User browses script (查看剧本)
- User scrolls chat history (滚轮查看聊天)

The first three are already captured via `ChatMessageEvent`. The last two require a frontend signal.

#### Frontend: User Activity Signal

Frontend sends a WebSocket message when the user performs passive browsing actions:

```json
{ "type": "USER_ACTIVITY", "roomId": "xxx", "activity": "VIEW_SCRIPT" | "SCROLL_CHAT" }
```

Destination: `/app/room/{roomId}/activity`

Backend receives this and **resets the idle timer** — the user is actively engaged with the game content, so DM should not interrupt.

#### Timer Lifecycle

On each activity event (chat message or user activity signal):
1. Cancel existing idle timer for the room (if any)
2. Schedule a new timer: `idleTimeoutSeconds` (default 60s) from now

When the timer fires:
1. Verify room is still in FREE_CHAT phase
2. Call `dmExecutor.executeDmAction(roomId, null, "自由讨论中所有参与者已沉默超过一分钟。请主动推进游戏进程：可以发起新话题、总结讨论要点、或使用 transitionPhase 工具推进到下一环节。")`
3. The DM action produces a chat message → resets the timer (prevents consecutive firing)

#### Cleanup

- Timer cancelled on phase transition (new phase starts its own timers)
- Timer cancelled on game end
- `@PreDestroy` shuts down the scheduler

#### Config

```properties
# application.properties
game.ai.idle-timeout-seconds=60
```

#### Files Changed

| File | Change |
|------|--------|
| `AiEngineProperties` | Add `idleTimeoutSeconds` field |
| `AgentOrchestrator` | Add idle timer map, scheduler; reset on any chat message or user activity signal; fire DM action on timeout |
| `GameChatController` or new `ActivityController` | Handle `/app/room/{roomId}/activity` WebSocket endpoint, notify `AgentOrchestrator` to reset timer |
| `GameFlowService` | Cancel idle timer on phase transition and game end |
| Frontend (React) | Send `USER_ACTIVITY` WebSocket messages when user views script or scrolls chat |

---

## Interaction Between Features

- AI multi-round conversation resets the idle timer (AI messages count as activity)
- When AI conversation reaches max rounds and stops, if no activity within 60s, DM auto-advances
- Human message resets both the AI round counter AND the idle timer
- DM auto-advance message resets the idle timer (prevents consecutive firing, but if still no human activity after another 60s, DM may fire again)

## Testing Strategy

- Unit test: effective max calculation with varying human player counts
- Unit test: round counter increment, reset, and max enforcement
- Unit test: idle timer scheduling, cancellation, and firing
- Integration: verify AI chain stops at max rounds with redirect prompt
- Integration: verify DM triggers after idle timeout
