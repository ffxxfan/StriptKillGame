# Game Core Business Logic — Design Spec

**Date:** 2026-03-28
**Status:** Approved
**Scope:** Script selection, room lifecycle, real-time chat, game flow, and frontend views

---

## 1. Design Decisions (from brainstorming)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| AI integration | Interface only, no impl | Spring AI not in pom.xml yet; define `AiAgentService` stub |
| Chat storage | Redis during game → flush placeholder to GameRecord on end | `fullChatLog` will hold AI-summarized key info later; for now stores `"Game ended at {time}"` |
| AI summarization | Interface only | `GameSummaryService.summarize()` stub; future AI-powered |
| Room-level auth | Check in `@MessageMapping` handler | Simple, keeps interceptor focused on JWT login auth |
| Room destruction | Soft delete (set FINISHED) | Preserves data for future GameRecord pipeline |
| Clue pool init | Empty stub method | Called at right point in `startGame()`, filled in later |
| Architecture style | Monolithic service layer | Matches existing codebase pattern, simple for solo dev |
| Frontend routing | Separate routes per phase | `/home`, `/game/:roomId/scripts`, `/game/:roomId/roles`, `/game/:roomId/play` |

---

## 2. Backend Package Structure

```
com.example.striptkillgamedemo2/
├── controller/
│   ├── AuthController.java          (existing)
│   ├── ScriptController.java        (new) REST — script listing
│   ├── GameRoomController.java      (new) REST — room CRUD, role assignment, start game
│   └── GameChatController.java      (new) WebSocket @MessageMapping
├── service/
│   ├── AuthService.java             (existing)
│   ├── ScriptService.java           (new) script queries
│   ├── GameRoomService.java         (new) room lifecycle, member management
│   ├── GameFlowService.java         (new) state transitions, stage advancement
│   ├── GameChatService.java         (new) message routing, Redis storage, flush
│   ├── AiAgentService.java          (new) INTERFACE ONLY — future AI integration
│   └── GameSummaryService.java      (new) INTERFACE ONLY — future AI summarization
├── repository/
│   ├── UserRepository.java          (existing)
│   ├── ScriptRepository.java        (new)
│   ├── RoleRepository.java          (new)
│   ├── GameRoomRepository.java      (new)
│   └── GameRecordRepository.java    (new)
├── dto/
│   ├── request/
│   │   ├── CreateRoomRequest.java   (new)
│   │   ├── SelectRoleRequest.java   (new)
│   │   └── ChatMessageRequest.java  (new)
│   └── response/
│       ├── ScriptListResponse.java  (new)
│       ├── RoomDetailResponse.java  (new)
│       ├── RoleListResponse.java    (new)
│       └── ChatMessageResponse.java (new)
```

---

## 3. REST API Design

### ScriptController — `/api/scripts`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/scripts/random` | Returns up to 8 random scripts via MongoDB `$sample`. Frontend pads to 8 with placeholders. |

### GameRoomController — `/api/rooms`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/rooms` | Create room. Returns room with status=WAITING, creator added as member (no role yet). |
| GET | `/api/rooms/{roomId}` | Get room detail: members, script info, current stage. |
| PUT | `/api/rooms/{roomId}/script` | Select script for room. Body: `{ scriptId }`. |
| GET | `/api/rooms/{roomId}/roles` | List all roles for the room's script, with availability status. |
| PUT | `/api/rooms/{roomId}/role` | Player selects a role. Body: `{ roleId }`. Auto-creates AI members for all remaining roles. |
| POST | `/api/rooms/{roomId}/start` | Start game. Validates state, transitions to PLAYING. |
| POST | `/api/rooms/{roomId}/leave` | Player leaves. Sets `isOnline=false`. If no humans online → end game. |

**Auth:** All endpoints require JWT. Room endpoints verify user is a member (except POST create, which adds them).

**Flow:** Create room → Select script → Select role (triggers AI member creation) → Start game.

---

## 4. Refactored GameMessage Model

```java
public class GameMessage {
    private String messageId;
    private ObjectId gameRoomId;
    private ObjectId senderRoleId;      // universal sender — AI or human
    private boolean isAi;
    private String senderRoleName;      // denormalized for display
    private String senderAvatar;        // denormalized for display
    private String content;
    private List<ObjectId> receiverRoleIds;
    private LocalDateTime timestamp;
}
```

- `senderRoleId` is the universal identifier — every member (AI or human) has a role
- No `senderUserId` — unnecessary since role↔member mapping is 1:1
- Frontend alignment: `senderRoleId == myRoleId` → right bubble, else left bubble

---

## 5. WebSocket Chat & Redis Storage

### STOMP Endpoints

- **Send:** `/app/chat.{roomId}` — Player sends `ChatMessageRequest { content }`
- **Receive:** `/topic/room.{roomId}` — All room members subscribe

### Message Flow

1. Player sends message to `/app/chat.{roomId}`
2. `GameChatController` extracts user from STOMP headers (JWT validated by interceptor)
3. Room membership check in handler — verify user is member and room status is PLAYING
4. Build `GameMessage` with role info, `isAi=false`, timestamp, UUID messageId
5. Store to Redis list: `game:messages:{roomId}`
6. Broadcast `ChatMessageResponse` to `/topic/room.{roomId}`
7. *(Future: AiAgentService generates replies for AI members)*

### Redis Key Design

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `game:messages:{roomId}` | List | Explicit delete on flush | Chat history during game |
| `game:room:{roomId}:stage` | String | Explicit delete | Current stage number cache |
| `game:room:{roomId}:clues` | Hash | Explicit delete | Clue pool state (future) |

### Chat Flush on Game End

1. Read all messages from `game:messages:{roomId}`
2. Call `GameSummaryService.summarize(roomId)` → returns `["Game ended at {endTime}"]`
3. Create `GameRecord` with summary as `fullChatLog`
4. Delete all `game:room:{roomId}:*` and `game:messages:{roomId}` from Redis

---

## 6. Game Flow & State Management

### GameFlowService

**`startGame(roomId)`**
1. Validate: room status WAITING, script selected, player has role
2. Set status PLAYING, set `startTime`, set `currentStage = 0`
3. Call `initCluePool(roomId, scriptId, 0)` — **no-op stub for now**
4. Cache current stage in Redis
5. Broadcast game start system message

**`advanceStage(roomId)`** *(interface defined, basic impl)*
1. Increment `currentStage`
2. If `currentStage >= stages.size()` → call `endGame(roomId)`
3. Otherwise update Redis stage cache
4. Broadcast stage transition message

**`endGame(roomId)`**
1. Set room status FINISHED, set `endTime`
2. Call `GameChatService.flushMessages(roomId)` → creates GameRecord with placeholder summary
3. Clean up all Redis keys for this room
4. Broadcast game-over message

**`leaveRoom(roomId, userId)`**
1. Find member by userId, set `isOnline = false`
2. If no human member has `isOnline=true` → call `endGame(roomId)`
3. Broadcast member-left message

### Extension Points

| Interface | Method Signature | Current Impl | Future |
|-----------|-----------------|-------------|--------|
| `AiAgentService` | `generateReply(Role, List<GameMessage>, ScriptStage)` → `String` | Returns null (skip) | Spring AI call |
| `GameSummaryService` | `summarize(ObjectId roomId)` → `List<String>` | Returns `["Game ended at {time}"]` | AI summary |
| `GameFlowService` | `initCluePool(ObjectId roomId, ObjectId scriptId, int stage)` | No-op | Load clues into Redis |
| `GameFlowService` | `advanceStage(ObjectId roomId)` | Basic stage increment | Voting triggers, evidence |

---

## 7. Frontend Architecture

### Route Structure

```
/home                        → HomeView.vue
/game/:roomId/scripts        → ScriptWallView.vue
/game/:roomId/roles          → RoleWallView.vue
/game/:roomId/play           → GamePlayView.vue
```

### HomeView.vue — Hub

- Left sidebar: `剧本杀游戏` (active), `剧本杀创作` (placeholder), `剧本杀上传` (placeholder)
- Right content area: shows content based on selected menu
- Bottom-right: `创建房间` button → `POST /api/rooms` → navigate to `/game/{roomId}/scripts`
- Header: logo + user avatar (reuse existing)

### ScriptWallView.vue — Script Selection

- Fetch `GET /api/scripts/random` on mount
- 2 rows × 4 columns grid; pad with placeholder cards if < 8
- Card shows: `coverImage`, `title`, `difficulty` badge, `playerCount`
- Click → `PUT /api/rooms/{roomId}/script` → navigate to `/game/{roomId}/roles`

### RoleWallView.vue — Role Selection

- Fetch `GET /api/rooms/{roomId}/roles` on mount
- Layout: `≤5` → 1 row centered, `6-10` → 2 rows, `>10` → 3 rows
- Click role card → `PUT /api/rooms/{roomId}/role`
- After selection: `开始游戏` button enables
- Click → `POST /api/rooms/{roomId}/start` → navigate to `/game/{roomId}/play`

### GamePlayView.vue — Chat Room

- Top bar: script title, current stage info
- Top-left: exit button → `POST /api/rooms/{roomId}/leave` → navigate to `/home`
- Chat area: scrollable messages
  - `senderRoleId == myRoleId` → right bubble (avatar + name + content)
  - Others → left bubble
- Bottom: text input + send button
- WebSocket: connect `/ws`, subscribe `/topic/room.{roomId}`, send `/app/chat.{roomId}`

### Frontend File Structure

```
frontend/src/
├── api/
│   ├── auth.ts          (existing)
│   ├── script.ts        (new) — getRandomScripts()
│   ├── room.ts          (new) — createRoom(), getRoom(), selectScript(), getRoles(),
│   │                             selectRole(), startGame(), leaveRoom()
│   └── axios.ts         (existing)
├── composables/
│   └── useWebSocket.ts  (new) — STOMP connect/disconnect/subscribe/send with JWT
├── stores/
│   ├── auth.ts          (existing)
│   └── game.ts          (new) — roomId, scriptId, myRoleId, messages, gameStatus
├── views/
│   ├── HomeView.vue     (modify)
│   ├── ScriptWallView.vue  (new)
│   ├── RoleWallView.vue    (new)
│   └── GamePlayView.vue    (new)
```
