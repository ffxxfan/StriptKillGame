# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

剧本杀 (Script Kill / Murder Mystery) web game with AI-powered NPC agents and an AI Dungeon Master (DM). Players join rooms, read scripts, investigate clues, discuss, vote, and solve mysteries — AI agents fill empty roles and a DM agent orchestrates game flow.

## Build & Run

```bash
# Backend (Spring Boot 3.5, Java 17, Maven)
./mvnw compile                          # compile
./mvnw spring-boot:run                  # run (needs MongoDB + Redis locally)
./mvnw test                             # all tests
./mvnw test -Dtest=AgentOrchestratorTest # single test class
./mvnw test -Dtest=AgentOrchestratorTest#testMethodName # single test method

# Frontend (Vue 3 + Vite + TypeScript)
cd frontend && npm install && npm run dev   # dev server
cd frontend && npm run build                # production build
```

Tests use embedded MongoDB (`de.flapdoodle.embed.mongo.spring3x`).

## Architecture

### Backend Layers

- **`controller/`** — REST + WebSocket STOMP endpoints (`GameChatController` handles `/app/room.{roomId}.chat` messages)
- **`service/`** — Game logic. Key services:
  - `GameFlowService` — start/advance/end game lifecycle
  - `LiveGameRoomService` — Redis CRUD for `LiveGameRoom` (runtime game state)
  - `PhaseTimerService` — per-phase countdown timers that auto-advance via DM
  - `VoteService` — vote sessions with auto-close on completion
  - `GameChatService` — message storage and WebSocket broadcast
- **`entity/`** — Data models split by storage:
  - `entity/mongo/` — Persistent: `Script`, `Role`, `ScriptStage`, `StagePhase`, `GameRoom`, `User`
  - `entity/redis/` — Runtime: `LiveGameRoom`, `GameMessage`, `VoteSession`, `GameClueInstance`
  - `entity/enums/` — `PhaseType` (SCRIPT_READING, TURN_BASED, FREE_CHAT, INVESTIGATION, PRIVATE_TALK, FINAL_STATEMENT, VOTE)

### AI Subsystem (`ai/`)

This is the most complex part of the codebase.

- **`orchestrator/AgentOrchestrator`** — Central event listener on `ChatMessageEvent`. Routes messages through a 3-layer response strategy:
  1. Layer 1: `@mention` — exact `@RoleName` match triggers that agent
  2. Layer 2: Name-in-text — role name substring match triggers up to 2 agents
  3. Layer 3: DM fallback — DM uses `selectRespondents` tool to pick agents (human messages only)
- **`executor/AgentExecutor`** — Calls LLM for AI player agents. Streams reply via WebSocket chunks. Supports `[MSG]` splitting for multi-message replies. Publishes `ChatMessageEvent(fromAi=true)` to enable AI-to-AI chaining.
- **`executor/DmExecutor`** — Calls LLM for DM with tool-calling support. Uses `DmToolRegistry` to register available tools. Suppresses DM text output when `agentDelegated=true` (agents speak instead).
- **`tool/DmTool`** — Interface for DM-callable tools. Each tool has `name()`, `description()`, `inputType()`, `execute()`, and optional `allowedPhases()`.
- **`tool/DmToolRegistry`** — Converts `DmTool` implementations into Spring AI `ToolCallback` instances, filtered by current phase.
- **`tool/DmToolContext`** — Mutable context shared during a single DM execution (room, script, phase, `agentDelegated` flag).
- **`prompt/PromptBuilder`** — Builds system prompts from Markdown templates in `src/main/resources/prompts/`.
- **`memory/MemoryManager`** — Stage-level conversation compression and sliding window for context management.

### LLM Integration

Uses Spring AI Anthropic adapter pointed at a compatible endpoint (Volcengine/minimax-m2.5). For reasoning models (DeepSeek), `extractReply()` prefers `getResults().get(1)` (actual reply) over `get(0)` (thinking), with fallback.

### Known Patterns

- **Circular dependencies**: Broken with `@Lazy` on constructor params in `InitiateVoteTool`, `PhaseTimerService`, `VoteService`
- **Async execution**: AI agent/DM calls run on `@Async("aiExecutor")` thread pool (configured in `AsyncConfig`)
- **WebSocket topics**: `/topic/room.{roomId}` (system), `/topic/room.{roomId}.stream` (LLM streaming chunks), `/topic/room.{roomId}.signal` (typing indicators)
- **Config prefix**: `game.ai.*` maps to `AiEngineProperties`

### Frontend

Vue 3 + Element Plus + Pinia + Vue Router + STOMP.js for WebSocket. Source in `frontend/src/`.
