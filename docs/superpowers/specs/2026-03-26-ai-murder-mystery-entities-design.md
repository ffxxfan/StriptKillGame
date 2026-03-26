# AI Murder Mystery Game - MongoDB Entity Design

**Date:** 2026-03-26
**Author:** Claude
**Status:** Design Approved

## Overview

This design defines the MongoDB entity classes for the AI Murder Mystery Game system. The system supports multiple human players and AI agents in a scripted murder mystery game with real-time gameplay and post-game analysis.

## Architecture Approach

### Hybrid Storage Strategy

- **MongoDB:** Static templates (Script, Role, Clue) + persistent records (User, GameRecord, GameRoom metadata)
- **Redis:** Real-time game state (GameClueInstance, GameMessage, VoteRecord, cluePool, stageLog)

This approach provides:
- Performance: Redis for high-frequency real-time operations
- Persistence: MongoDB for static data and game history
- Clean separation: Runtime data doesn't pollute persistent collections

## Entity Structure

### Core Entities (MongoDB)

1. **User** - Player accounts
2. **Script** - Game templates with stages
3. **Role** - Character definitions within scripts
4. **Clue** - Static clue templates
5. **GameRoom** - Room metadata (lightweight only)
6. **GameRecord** - Post-game summaries

### Runtime Entities (Redis-only)

7. **GameClueInstance** - Dynamic clue state during gameplay
8. **GameMessage** - Real-time game messages
9. **VoteRecord** - Voting records during gameplay

## Enums

### GameRoomStatus

```java
public enum GameRoomStatus {
    WAITING,    // Room created, waiting for players
    PLAYING,    // Game in progress
    FINISHED    // Game completed
}
```

### ScriptDifficulty

```java
public enum ScriptDifficulty {
    EASY(1),
    NORMAL(2),
    HARD(3),
    EXPERT(4);

    private final int level;
}
```

### ClueType

```java
public enum ClueType {
    TEXT,   // Text-based clue
    IMAGE   // Image-based clue
}
```

## Core Entities (MongoDB)

### 1. User

Represents user accounts in the system.

```java
@Document(collection = "users")
public class User {
    @Id
    private ObjectId id;

    @Indexed(unique = true)
    @NotBlank
    private String username;

    @NotBlank
    @JsonIgnore
    private String password;

    private String nickname;
    private String avatarUrl;

    @CreatedDate
    private LocalDateTime createdAt;
}
```

**Security Notes:**
- Password field stores BCrypt-hashed passwords (use BCryptPasswordEncoder in service layer)
- Password is excluded from JSON serialization via `@JsonIgnore`
- `@CreatedDate` will auto-populate `createdAt`
- Username is unique indexed for authentication

### 2. Script

Game template containing all static game content.

```java
@Document(collection = "scripts")
public class Script {
    @Id
    private ObjectId id;

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private ScriptDifficulty difficulty;

    @Min(2)
    private int playerCount;

    private String coverImage;

    /**
     * Flexible JSON configuration for DM (Dungeon Master) hosting style.
     * Allows customizable game flow and DM permissions.
     */
    private String dmConfig;

    /**
     * Stages define the game progression.
     * Each stage contains role-specific content that is revealed at that stage.
     */
    private List<ScriptStage> stages;

    /**
     * Script version for tracking updates.
     * Prevents breaking active games when script content changes.
     */
    private int version = 1;

    /**
     * Game-specific configuration overrides.
     * Allows customizing rules, time limits, etc. per game instance.
     */
    private String configuration;
}
```

### 3. ScriptStage (Embedded)

Represents a single stage within a script. Embedded within Script for optimal query performance.

```java
@Embedded
public class ScriptStage {
    private int stageNumber;

    private String stageTitle;

    /**
     * Maps roleId to the content revealed to that role at this stage.
     * RoleId is ObjectId to reference the Role entity.
     */
    private Map<ObjectId, String> contentMap;

    private String audioUrl;
}
```

### 4. Role

Character definition within a script. Each human player or AI agent plays a role.

**Note on Clue-Role Relationship:**
Clue discoverability is controlled by **Clue.searchableRoleIds** (source of truth).
Role.selfClueIds is for convenience/query optimization and should always match
the reverse lookup from Clue collection. The system validates consistency on game start.

```java
@Document(collection = "roles")
public class Role {
    @Id
    private ObjectId id;

    @Indexed
    private ObjectId scriptId;

    @NotBlank
    private String name;

    private String avatar;

    /**
     * Whether this role is played by an AI agent.
     * If true, userId in Member will be null.
     */
    private boolean isNpc = false;

    /**
     * Core AI prompt instructions for NPCs.
     * Defines behavior, personality, and objectives.
     */
    private String prompt;

    /**
     * Secret information that must not be revealed to other players.
     * Critical for the mystery game logic.
     */
    private String secret;

    /**
     * IDs of clues this role can search for.
     * Limits what clues are discoverable by this role.
     */
    private List<ObjectId> selfClueIds;

    /**
     * Optional tag indicating search location.
     * Used to group clues by physical/virtual location.
     */
    private String locationTag;

    /**
     * Optional search action points.
     * If set, limits how many search actions this role can perform.
     */
    private Integer searchPower;
}
```

### 5. Clue

Static clue template. Multiple instances can be created during gameplay via GameClueInstance.

```java
@Document(collection = "clues")
public class Clue {
    @Id
    private ObjectId id;

    @Indexed
    private ObjectId scriptId;

    @NotBlank
    private String title;

    @NotNull
    private ClueType type;

    /**
     * Text content for TEXT type clues.
     */
    private String content;

    /**
     * Image URL for IMAGE type clues.
     */
    private String imageUrl;

    /**
     * Whether this clue is hidden at game start.
     * Clues become visible through game progression or discovery.
     */
    private boolean isInitialHidden = true;

    /**
     * Stage number when this clue becomes available.
     */
    private int stageNumber;

    /**
     * IDs of roles that can search for this clue.
     * Limits discoverability to specific roles.
     */
    private List<ObjectId> searchableRoleIds;
}
```

### 6. GameRoom

Lightweight room metadata. Heavy runtime state is stored in Redis.

```java
@Document(collection = "game_rooms")
public class GameRoom {
    @Id
    private ObjectId roomId;

    @Indexed
    private ObjectId scriptId;

    private GameRoomStatus status = GameRoomStatus.WAITING;

    private int currentStage = 0;

    /**
     * Members (players and NPCs) in this room.
     */
    private List<Member> members;

    /**
     * When the game started. Used for session tracking and audit.
     */
    private LocalDateTime startTime;

    /**
     * When the game ended. Used for session tracking and audit.
     */
    private LocalDateTime endTime;

    /**
     * Game-specific configuration overrides.
     * Allows customizing rules, time limits, etc. per game instance.
     */
    private String configuration;
}
```

### 7. Member (Embedded)

Represents a player or NPC in a game room. Embedded within GameRoom.

```java
@Embedded
public class Member {
    /**
     * User ID. Null for NPCs.
     */
    private ObjectId userId;

    /**
     * Role ID this member is playing.
     */
    private ObjectId roleId;

    /**
     * Whether this member is controlled by AI.
     */
    private boolean isAi = false;

    /**
     * Whether this member is the Dungeon Master.
     */
    private boolean isDm = false;

    /**
     * Connection status for real-time features.
     */
    private boolean isOnline = true;
}
```

### 8. GameRecord

Persistent summary created after a game ends. Used for history and analysis.

```java
@Document(collection = "game_records")
public class GameRecord {
    @Id
    private ObjectId recordId;

    @Indexed
    private ObjectId roomId;

    private String scriptTitle;

    /**
     * IDs of winning users.
     */
    private List<ObjectId> winnerUserIds;

    /**
     * IDs of winning roles.
     */
    private List<ObjectId> winnerRoleIds;

    /**
     * AI-generated summary of the game.
     * Includes key plot points, revelations, and outcome.
     */
    private String aiSummary;

    /**
     * Key messages from the game log.
     * Only important events are stored to save space.
     */
    private List<String> fullChatLog;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
```

## Runtime Entities (Redis-only)

### 9. GameClueInstance

Dynamic state of a clue during gameplay. Not persisted after game ends.

**Storage:** Redis with key format `game:{roomId}:clue:{clueId}`

```java
public class GameClueInstance {
    private String id;  // Redis key

    /**
     * Reference to the Clue template.
     */
    private ObjectId clueId;

    /**
     * Roles currently holding this clue.
     * A clue can be held by multiple roles simultaneously.
     */
    private List<ObjectId> ownerRoleIds;

    /**
     * Whether this clue is revealed to all players.
     */
    private boolean isPublic;

    /**
     * Whether this clue has been discovered by anyone.
     */
    private boolean isFound = false;

    /**
     * Timestamp when this clue was discovered.
     * Used for audit trail and time-based game mechanics.
     */
    private LocalDateTime discoveredAt;
}
```

### 10. GameMessage

Real-time game messages during gameplay. Stored in Redis for performance.

**Storage:** Redis List with key `game:{roomId}:messages`

```java
public class GameMessage {
    private String messageId;

    private ObjectId gameRoomId;

    /**
     * Role ID of the sender.
     */
    private ObjectId senderRoleId;

    /**
     * User ID of the sender. Null for NPCs.
     */
    private ObjectId senderUserId;

    /**
     * Message content.
     */
    private String content;

    /**
     * Roles authorized to view this message.
     * Supports private messaging and role-based visibility.
     */
    private List<ObjectId> receiverRoleIds;

    private LocalDateTime timestamp;
}
```

### 11. VoteRecord

Voting records during gameplay. Used for elimination or other game mechanics.

**Storage:** Redis List with key `game:{roomId}:votes`

```java
public class VoteRecord {
    private String id;

    private ObjectId gameRoomId;

    /**
     * User ID of the voter.
     */
    private ObjectId voterUserId;

    private int stageNumber;

    /**
     * Role ID being voted for.
     */
    private ObjectId votedRoleId;

    /**
     * Optional category/type of vote.
     * Supports different voting mechanics (elimination, revelation, etc.).
     */
    private String voteCategory;

    /**
     * Optional reason for the vote.
     * Provides context for game analysis and AI summarization.
     */
    private String voteReason;

    /**
     * Optional vote weight.
     * Supports weighted voting mechanics.
     */
    private Integer voteWeight;

    private LocalDateTime timestamp;
}
```

## Redis Data Structure Summary

| Key Pattern | Data Structure | Purpose |
|------------|----------------|---------|
| `game:{roomId}:clue:{clueId}` | Hash | GameClueInstance data |
| `game:{roomId}:messages` | List | GameMessage IDs |
| `game:{roomId}:message:{messageId}` | Hash | GameMessage details |
| `game:{roomId}:votes` | List | VoteRecord IDs |
| `game:{roomId}:vote:{voteId}` | Hash | VoteRecord details |
| `game:{roomId}:stageLog` | List | Stage unlock history |
| `game:{roomId}:cluePool` | Hash | Clue pool metadata |
| `game:{roomId}:state` | Hash | Game state snapshot (for recovery) |
| `session:{userId}` | Hash | WebSocket session metadata |
| `game:{roomId}:participants` | Set | Online participant user IDs |

**Redis Key Expiration:**
- All game keys (`game:{roomId}:*`) expire after 24 hours if not explicitly cleaned
- Session keys (`session:{userId}`) expire after 1 hour of inactivity
- Keys are explicitly deleted when a game transitions to FINISHED state

## Dependencies

Required Maven dependencies (already in project):

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

## Package Structure

```
com.example.striptkillgamedemo2.entity
├── enum
│   ├── ClueType.java
│   ├── GameRoomStatus.java
│   └── ScriptDifficulty.java
├── mongo
│   ├── Clue.java
│   ├── GameRecord.java
│   ├── GameRoom.java
│   ├── Member.java
│   ├── Role.java
│   ├── Script.java
│   ├── ScriptStage.java
│   └── User.java
└── redis
    ├── GameClueInstance.java
    ├── GameMessage.java
    └── VoteRecord.java
```

## Technical Specifications

- **Java Version:** 17+
- **Spring Boot Version:** 3.5.12
- **Spring Data MongoDB:** Document entities with `@Document` and `@Id`
- **Lombok:** `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- **Validation:** Jakarta Bean Validation (`@NotBlank`, `@NotNull`, `@Min`)
- **Serialization:** Jackson (`@JsonIgnore` for sensitive fields)
- **Date Handling:** `LocalDateTime`
- **ID Type:** `ObjectId` from MongoDB driver

## Key Design Decisions

1. **Hybrid Storage:** MongoDB for persistent data, Redis for real-time game state
2. **Embedded Documents:** ScriptStage and Member are embedded for query efficiency
3. **Runtime Clues:** GameClueInstance stored only in Redis, recreated each game
4. **Role-based Visibility:** Messages support selective role visibility via receiverRoleIds
5. **Multi-owner Clues:** A clue can be held by multiple roles simultaneously
6. **Optional Search Limits:** searchPower is nullable to support unlimited searches
7. **Game Log Summarization:** GameRecord stores only key messages, not full chat history
8. **Flexible DM Config:** dmConfig as JSON string allows customizable DM behaviors
9. **Password Security:** Password field stores BCrypt-hashed passwords (not plain text)
10. **Audit Trail:** GameClueInstance includes discoveredAt timestamp for tracking
11. **Clue-Role Relationship:** Clue.searchableRoleIds is source of truth, Role.selfClueIds for optimization
12. **Script Versioning:** Script.version and Script.configuration support updates and customization
13. **GameRoom Audit:** GameRoom includes startTime, endTime, and configuration for tracking
14. **Flexible Voting:** VoteRecord supports categories, reasons, and weighted votes for diverse mechanics

## Architecture Guidelines

### Concurrency Control

- **Redis Lua Scripts:** Use Redis scripting for atomic operations on clue discovery and voting
- **Optimistic Locking:** MongoDB updates use version field for optimistic concurrency
- **Distributed Locks:** Use Redis RedLock for game state transitions and critical operations

### Session Management

- **WebSocket Tracking:** Active user sessions tracked in `session:{userId}` keys
- **Reconnection Support:** User can reconnect to active game via session restoration
- **Online Status:** Participant set tracks who is currently connected
- **Heartbeat:** Periodic heartbeat updates session TTL

### Game Flow Control

- **Stage Transitions:** Validated by GameFlowService with state machine pattern
- **Transition Rules:** Each stage has specific completion conditions
- **Event Publishing:** Spring events for stage transitions, game start/end
- **State Snapshots:** Redis hash `game:{roomId}:state` stores current state for recovery

### Disaster Recovery

- **Redis Persistence:** Enable Redis AOF (Append Only File) for crash recovery
- **State Snapshots:** Periodic snapshots of game state to MongoDB
- **Graceful Degradation:** If Redis is unavailable, game pauses and notifies players
- **Data Rebuild:** On Redis recovery, rebuild from MongoDB GameRoom + GameRecord if available

### Cross-Database Consistency

- **Transaction Boundaries:** MongoDB operations wrapped in transactions (MongoDB 4.0+)
- **Saga Pattern:** For multi-step operations (e.g., game end → cleanup → record creation)
- **Compensation Actions:** Define rollback procedures for failed operations
- **Idempotency:** All write operations designed to be idempotent

### Data Cleanup

- **Game End Cleanup:** Explicit deletion of all `game:{roomId}:*` keys on game completion
- **Scheduled Cleanup:** Background job removes expired keys (24-hour TTL safety net)
- **Message Retention:** Messages archived to GameRecord before Redis cleanup
- **Vote Retention:** Vote summaries included in GameRecord before Redis cleanup

### Query Patterns

- **Redis Queries:** Use key patterns and Redis Search module for complex queries
- **MongoDB Indexes:** Compound indexes on (scriptId, stageNumber) for clue lookup
- **Pagination:** Use cursor-based pagination for message/vote history
- **Caching:** Static script/role/clue data cached in application cache (Caffeine)

### Error Handling

- **Redis Unavailable:** Circuit breaker pattern, fallback to game pause
- **MongoDB Unavailable:** Write operations fail gracefully with user notification
- **Network Issues:** Retry with exponential backoff for transient failures
- **Data Corruption:** Validation on read, reject malformed entities

## Data Migration and Schema Evolution

### Migration Strategy

- **Version Tracking:** Each entity includes version or timestamp for change detection
- **Backward Compatibility:** New fields default to null/empty for existing documents
- **Migration Scripts:** Use MongoDB changelog for structured migrations
- **Zero-Downtime Migrations:** Run migrations with feature flags

### Current Entity Versions

- **User:** v1.0
- **Script:** v1.1 (added version and configuration fields)
- **Role:** v1.1 (added selfClueIds for optimization)
- **Clue:** v1.1 (renamed searcheableRoleIds to searchableRoleIds)
- **GameRoom:** v1.1 (added startTime, endTime, configuration)
- **Member:** v1.0
- **GameRecord:** v1.0
- **GameClueInstance:** v1.1 (added discoveredAt)
- **GameMessage:** v1.0
- **VoteRecord:** v1.1 (added voteCategory, voteReason, voteWeight)

### Migration Checklist

When evolving schema:
1. Add migration script to changelog collection
2. Test migration on staging environment
3. Plan rollback procedure
4. Schedule maintenance during low-traffic period
5. Monitor migration progress and errors
6. Update entity version number
7. Update application code to handle both old and new formats
