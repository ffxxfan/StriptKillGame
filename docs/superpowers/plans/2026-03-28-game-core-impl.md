# Game Core Business Logic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement script selection, room lifecycle, real-time WebSocket chat, game flow state management, and Vue 3 frontend views for the murder mystery game.

**Architecture:** Monolithic service layer with thin controllers. REST for room CRUD, WebSocket STOMP for real-time chat. Redis for in-game message storage, MongoDB for persistent data. Vue 3 with separate routes per game phase.

**Tech Stack:** Spring Boot 3, Spring Data MongoDB, Spring Data Redis, Spring WebSocket (STOMP), Vue 3 + TypeScript + Element Plus + Tailwind CSS + StompJS

---

## File Structure

### Backend — New Files

| File | Responsibility |
|------|---------------|
| `src/.../repository/ScriptRepository.java` | Script queries including random sampling |
| `src/.../repository/RoleRepository.java` | Role queries by scriptId |
| `src/.../repository/GameRoomRepository.java` | Room CRUD |
| `src/.../repository/GameRecordRepository.java` | Game record persistence |
| `src/.../dto/ScriptSummaryDTO.java` | Script card display data |
| `src/.../dto/RoleDTO.java` | Role card display data |
| `src/.../dto/RoomDetailDTO.java` | Room state for frontend |
| `src/.../dto/ChatMessageRequest.java` | Incoming chat message |
| `src/.../dto/ChatMessageDTO.java` | Outgoing chat message (broadcast) |
| `src/.../service/ScriptService.java` | Script business logic |
| `src/.../service/GameRoomService.java` | Room lifecycle, member management |
| `src/.../service/GameFlowService.java` | State transitions, stage advancement |
| `src/.../service/GameChatService.java` | Message routing, Redis storage, flush |
| `src/.../service/AiAgentService.java` | Interface — future AI integration |
| `src/.../service/GameSummaryService.java` | Interface — future AI summarization |
| `src/.../service/impl/DefaultAiAgentService.java` | No-op stub |
| `src/.../service/impl/DefaultGameSummaryService.java` | Placeholder stub |
| `src/.../controller/ScriptController.java` | REST — script listing |
| `src/.../controller/GameRoomController.java` | REST — room CRUD |
| `src/.../controller/GameChatController.java` | WebSocket @MessageMapping |

### Backend — Modified Files

| File | Change |
|------|--------|
| `src/.../entity/redis/GameMessage.java` | Refactor: add isAi, senderRoleName, senderAvatar; remove senderUserId |
| `src/.../config/SecurityConfig.java` | Permit `/api/scripts/**`, `/api/rooms/**` paths (they still need JWT, but not in the public whitelist — actually these need auth, so no change needed; the default `anyRequest().authenticated()` handles it) |

### Frontend — New Files

| File | Responsibility |
|------|---------------|
| `frontend/src/api/script.ts` | Script API calls |
| `frontend/src/api/room.ts` | Room API calls |
| `frontend/src/composables/useWebSocket.ts` | STOMP connect/subscribe/send |
| `frontend/src/stores/game.ts` | Game state store |
| `frontend/src/views/ScriptWallView.vue` | Script selection grid |
| `frontend/src/views/RoleWallView.vue` | Role selection layout |
| `frontend/src/views/GamePlayView.vue` | Chat room + exit |

### Frontend — Modified Files

| File | Change |
|------|--------|
| `frontend/src/views/HomeView.vue` | Redesign with sidebar menu + create room |
| `frontend/src/router/index.ts` | Add game routes |
| `frontend/package.json` | Add @stomp/stompjs, sockjs-client |

---

## Tasks

### Task 1: Refactor GameMessage Entity

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/entity/redis/GameMessage.java`

- [ ] **Step 1: Update GameMessage fields**

Replace the entire file content with:

```java
package com.example.striptkillgamedemo2.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {
    private String messageId;
    private ObjectId gameRoomId;
    private ObjectId senderRoleId;
    private boolean isAi;
    private String senderRoleName;
    private String senderAvatar;
    private String content;
    private List<ObjectId> receiverRoleIds;
    private LocalDateTime timestamp;
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/redis/GameMessage.java
git commit -m "refactor: update GameMessage to support both AI and human senders"
```

---

### Task 2: MongoDB Repositories

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/repository/ScriptRepository.java`
- Create: `src/main/java/com/example/striptkillgamedemo2/repository/RoleRepository.java`
- Create: `src/main/java/com/example/striptkillgamedemo2/repository/GameRoomRepository.java`
- Create: `src/main/java/com/example/striptkillgamedemo2/repository/GameRecordRepository.java`

- [ ] **Step 1: Create ScriptRepository**

```java
package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.Script;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ScriptRepository extends MongoRepository<Script, ObjectId> {

    @Aggregation(pipeline = { "{ $sample: { size: ?0 } }" })
    List<Script> findRandomScripts(int count);
}
```

- [ ] **Step 2: Create RoleRepository**

```java
package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.Role;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RoleRepository extends MongoRepository<Role, ObjectId> {

    List<Role> findByScriptId(ObjectId scriptId);
}
```

- [ ] **Step 3: Create GameRoomRepository**

```java
package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface GameRoomRepository extends MongoRepository<GameRoom, ObjectId> {

    List<GameRoom> findByStatus(GameRoomStatus status);
}
```

- [ ] **Step 4: Create GameRecordRepository**

```java
package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.GameRecord;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface GameRecordRepository extends MongoRepository<GameRecord, ObjectId> {

    Optional<GameRecord> findByRoomId(ObjectId roomId);
}
```

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/repository/ScriptRepository.java \
        src/main/java/com/example/striptkillgamedemo2/repository/RoleRepository.java \
        src/main/java/com/example/striptkillgamedemo2/repository/GameRoomRepository.java \
        src/main/java/com/example/striptkillgamedemo2/repository/GameRecordRepository.java
git commit -m "feat: add game repositories for Script, Role, GameRoom, GameRecord"
```

---

### Task 3: DTOs

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/dto/ScriptSummaryDTO.java`
- Create: `src/main/java/com/example/striptkillgamedemo2/dto/RoleDTO.java`
- Create: `src/main/java/com/example/striptkillgamedemo2/dto/RoomDetailDTO.java`
- Create: `src/main/java/com/example/striptkillgamedemo2/dto/ChatMessageRequest.java`
- Create: `src/main/java/com/example/striptkillgamedemo2/dto/ChatMessageDTO.java`

- [ ] **Step 1: Create ScriptSummaryDTO**

```java
package com.example.striptkillgamedemo2.dto;

import com.example.striptkillgamedemo2.entity.enums.ScriptDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptSummaryDTO {
    private String id;
    private String title;
    private String description;
    private ScriptDifficulty difficulty;
    private int playerCount;
    private String coverImage;
}
```

- [ ] **Step 2: Create RoleDTO**

```java
package com.example.striptkillgamedemo2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {
    private String id;
    private String name;
    private String avatar;
    private boolean isNpc;
    private boolean isAvailable;
}
```

- [ ] **Step 3: Create RoomDetailDTO**

```java
package com.example.striptkillgamedemo2.dto;

import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDetailDTO {
    private String roomId;
    private String scriptId;
    private String scriptTitle;
    private GameRoomStatus status;
    private int currentStage;
    private List<MemberDTO> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberDTO {
        private String userId;
        private String roleId;
        private String roleName;
        private String roleAvatar;
        private boolean isAi;
        private boolean isDm;
        private boolean isOnline;
    }
}
```

- [ ] **Step 4: Create ChatMessageRequest**

```java
package com.example.striptkillgamedemo2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatMessageRequest {
    @NotBlank(message = "消息内容不能为空")
    private String content;
}
```

- [ ] **Step 5: Create ChatMessageDTO**

```java
package com.example.striptkillgamedemo2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private String messageId;
    private String senderRoleId;
    private String senderRoleName;
    private String senderAvatar;
    private boolean isAi;
    private String content;
    private LocalDateTime timestamp;
}
```

- [ ] **Step 6: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/dto/ScriptSummaryDTO.java \
        src/main/java/com/example/striptkillgamedemo2/dto/RoleDTO.java \
        src/main/java/com/example/striptkillgamedemo2/dto/RoomDetailDTO.java \
        src/main/java/com/example/striptkillgamedemo2/dto/ChatMessageRequest.java \
        src/main/java/com/example/striptkillgamedemo2/dto/ChatMessageDTO.java
git commit -m "feat: add DTOs for script, role, room, and chat message"
```

---

### Task 4: Service Interfaces (AI Stubs)

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/service/AiAgentService.java`
- Create: `src/main/java/com/example/striptkillgamedemo2/service/GameSummaryService.java`
- Create: `src/main/java/com/example/striptkillgamedemo2/service/impl/DefaultAiAgentService.java`
- Create: `src/main/java/com/example/striptkillgamedemo2/service/impl/DefaultGameSummaryService.java`

- [ ] **Step 1: Create AiAgentService interface**

```java
package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;

import java.util.List;

/**
 * AI Agent service interface.
 * Generates AI character responses during gameplay.
 * Current implementation is a no-op stub; replace with Spring AI integration later.
 */
public interface AiAgentService {

    /**
     * Generate an AI response for the given role based on conversation context and current stage.
     *
     * @param role     the AI role that should respond
     * @param context  recent chat messages for context
     * @param stage    the current script stage
     * @return the generated reply content, or null to skip
     */
    String generateReply(Role role, List<GameMessage> context, ScriptStage stage);
}
```

- [ ] **Step 2: Create GameSummaryService interface**

```java
package com.example.striptkillgamedemo2.service;

import org.bson.types.ObjectId;

import java.util.List;

/**
 * Game summary service interface.
 * Summarizes game chat into key information for GameRecord.fullChatLog.
 * Current implementation returns a placeholder; replace with AI summarization later.
 */
public interface GameSummaryService {

    /**
     * Summarize the game session.
     *
     * @param roomId the game room ID
     * @return list of key information strings for fullChatLog
     */
    List<String> summarize(ObjectId roomId);
}
```

- [ ] **Step 3: Create DefaultAiAgentService**

```java
package com.example.striptkillgamedemo2.service.impl;

import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.service.AiAgentService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultAiAgentService implements AiAgentService {

    @Override
    public String generateReply(Role role, List<GameMessage> context, ScriptStage stage) {
        // No-op stub — return null to skip AI response
        return null;
    }
}
```

- [ ] **Step 4: Create DefaultGameSummaryService**

```java
package com.example.striptkillgamedemo2.service.impl;

import com.example.striptkillgamedemo2.service.GameSummaryService;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DefaultGameSummaryService implements GameSummaryService {

    @Override
    public List<String> summarize(ObjectId roomId) {
        return List.of("Game ended at " + LocalDateTime.now());
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/AiAgentService.java \
        src/main/java/com/example/striptkillgamedemo2/service/GameSummaryService.java \
        src/main/java/com/example/striptkillgamedemo2/service/impl/DefaultAiAgentService.java \
        src/main/java/com/example/striptkillgamedemo2/service/impl/DefaultGameSummaryService.java
git commit -m "feat: add AI agent and game summary service interfaces with stub impls"
```

---

### Task 5: ScriptService + ScriptController

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/service/ScriptService.java`
- Create: `src/main/java/com/example/striptkillgamedemo2/controller/ScriptController.java`

- [ ] **Step 1: Create ScriptService**

```java
package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.dto.ScriptSummaryDTO;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptRepository scriptRepository;

    public List<ScriptSummaryDTO> getRandomScripts(int count) {
        List<Script> scripts = scriptRepository.findRandomScripts(count);
        return scripts.stream().map(this::toSummaryDTO).toList();
    }

    private ScriptSummaryDTO toSummaryDTO(Script script) {
        return ScriptSummaryDTO.builder()
                .id(script.getId().toHexString())
                .title(script.getTitle())
                .description(script.getDescription())
                .difficulty(script.getDifficulty())
                .playerCount(script.getPlayerCount())
                .coverImage(script.getCoverImage())
                .build();
    }
}
```

- [ ] **Step 2: Create ScriptController**

```java
package com.example.striptkillgamedemo2.controller;

import com.example.striptkillgamedemo2.dto.ScriptSummaryDTO;
import com.example.striptkillgamedemo2.service.ScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;

    @GetMapping("/random")
    public ResponseEntity<List<ScriptSummaryDTO>> getRandomScripts() {
        List<ScriptSummaryDTO> scripts = scriptService.getRandomScripts(8);
        return ResponseEntity.ok(scripts);
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/ScriptService.java \
        src/main/java/com/example/striptkillgamedemo2/controller/ScriptController.java
git commit -m "feat: add script service and controller for random script listing"
```

---

### Task 6: GameRoomService

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/service/GameRoomService.java`

- [ ] **Step 1: Create GameRoomService**

```java
package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.dto.RoleDTO;
import com.example.striptkillgamedemo2.dto.RoomDetailDTO;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.repository.GameRoomRepository;
import com.example.striptkillgamedemo2.repository.RoleRepository;
import com.example.striptkillgamedemo2.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameRoomService {

    private final GameRoomRepository gameRoomRepository;
    private final ScriptRepository scriptRepository;
    private final RoleRepository roleRepository;

    public GameRoom createRoom(ObjectId userId) {
        Member creator = Member.builder()
                .userId(userId)
                .isAi(false)
                .isDm(false)
                .isOnline(true)
                .build();

        GameRoom room = GameRoom.builder()
                .status(GameRoomStatus.WAITING)
                .currentStage(0)
                .members(new ArrayList<>(List.of(creator)))
                .build();

        return gameRoomRepository.save(room);
    }

    public GameRoom getRoom(ObjectId roomId) {
        return gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("房间不存在: " + roomId));
    }

    public GameRoom selectScript(ObjectId roomId, ObjectId scriptId) {
        GameRoom room = getRoom(roomId);
        validateRoomStatus(room, GameRoomStatus.WAITING);

        scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("剧本不存在: " + scriptId));

        room.setScriptId(scriptId);
        return gameRoomRepository.save(room);
    }

    public List<RoleDTO> getRoles(ObjectId roomId) {
        GameRoom room = getRoom(roomId);
        if (room.getScriptId() == null) {
            throw new IllegalStateException("请先选择剧本");
        }

        List<Role> roles = roleRepository.findByScriptId(room.getScriptId());
        List<ObjectId> takenRoleIds = room.getMembers().stream()
                .filter(m -> m.getRoleId() != null && !m.isAi())
                .map(Member::getRoleId)
                .toList();

        return roles.stream().map(role -> RoleDTO.builder()
                .id(role.getId().toHexString())
                .name(role.getName())
                .avatar(role.getAvatar())
                .isNpc(role.isNpc())
                .isAvailable(!takenRoleIds.contains(role.getId()))
                .build()
        ).toList();
    }

    public GameRoom selectRole(ObjectId roomId, ObjectId roleId, ObjectId userId) {
        GameRoom room = getRoom(roomId);
        validateRoomStatus(room, GameRoomStatus.WAITING);

        if (room.getScriptId() == null) {
            throw new IllegalStateException("请先选择剧本");
        }

        Role selectedRole = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleId));

        if (!selectedRole.getScriptId().equals(room.getScriptId())) {
            throw new IllegalArgumentException("该角色不属于当前剧本");
        }

        // Assign role to the human player
        Member playerMember = room.getMembers().stream()
                .filter(m -> m.getUserId() != null && m.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("你不在该房间中"));

        playerMember.setRoleId(roleId);

        // Auto-create AI members for all other unoccupied roles
        List<Role> allRoles = roleRepository.findByScriptId(room.getScriptId());
        List<ObjectId> humanRoleIds = room.getMembers().stream()
                .filter(m -> !m.isAi() && m.getRoleId() != null)
                .map(Member::getRoleId)
                .toList();

        // Remove existing AI members (in case of re-selection)
        room.getMembers().removeIf(Member::isAi);

        for (Role role : allRoles) {
            if (!humanRoleIds.contains(role.getId())) {
                Member aiMember = Member.builder()
                        .roleId(role.getId())
                        .isAi(true)
                        .isDm(role.isNpc())
                        .isOnline(true)
                        .build();
                room.getMembers().add(aiMember);
            }
        }

        return gameRoomRepository.save(room);
    }

    public GameRoom leaveRoom(ObjectId roomId, ObjectId userId) {
        GameRoom room = getRoom(roomId);

        Member member = room.getMembers().stream()
                .filter(m -> m.getUserId() != null && m.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("你不在该房间中"));

        member.setOnline(false);

        boolean anyHumanOnline = room.getMembers().stream()
                .anyMatch(m -> !m.isAi() && m.isOnline());

        if (!anyHumanOnline) {
            room.setStatus(GameRoomStatus.FINISHED);
            log.info("Room {} has no human players online, setting to FINISHED", roomId);
        }

        return gameRoomRepository.save(room);
    }

    public void validateMembership(GameRoom room, ObjectId userId) {
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId() != null && m.getUserId().equals(userId));
        if (!isMember) {
            throw new IllegalStateException("你不在该房间中");
        }
    }

    private void validateRoomStatus(GameRoom room, GameRoomStatus expected) {
        if (room.getStatus() != expected) {
            throw new IllegalStateException("房间状态不正确，当前: " + room.getStatus() + "，期望: " + expected);
        }
    }

    public RoomDetailDTO toDetailDTO(GameRoom room) {
        String scriptTitle = null;
        if (room.getScriptId() != null) {
            scriptTitle = scriptRepository.findById(room.getScriptId())
                    .map(Script::getTitle)
                    .orElse(null);
        }

        List<Role> roles = room.getScriptId() != null
                ? roleRepository.findByScriptId(room.getScriptId())
                : List.of();

        List<RoomDetailDTO.MemberDTO> memberDTOs = room.getMembers().stream().map(m -> {
            Role role = roles.stream()
                    .filter(r -> r.getId().equals(m.getRoleId()))
                    .findFirst()
                    .orElse(null);

            return RoomDetailDTO.MemberDTO.builder()
                    .userId(m.getUserId() != null ? m.getUserId().toHexString() : null)
                    .roleId(m.getRoleId() != null ? m.getRoleId().toHexString() : null)
                    .roleName(role != null ? role.getName() : null)
                    .roleAvatar(role != null ? role.getAvatar() : null)
                    .isAi(m.isAi())
                    .isDm(m.isDm())
                    .isOnline(m.isOnline())
                    .build();
        }).toList();

        return RoomDetailDTO.builder()
                .roomId(room.getRoomId().toHexString())
                .scriptId(room.getScriptId() != null ? room.getScriptId().toHexString() : null)
                .scriptTitle(scriptTitle)
                .status(room.getStatus())
                .currentStage(room.getCurrentStage())
                .members(memberDTOs)
                .build();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/GameRoomService.java
git commit -m "feat: add GameRoomService for room lifecycle and member management"
```

---

### Task 7: GameFlowService

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/service/GameFlowService.java`

- [ ] **Step 1: Create GameFlowService**

```java
package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.repository.GameRoomRepository;
import com.example.striptkillgamedemo2.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameFlowService {

    private final GameRoomRepository gameRoomRepository;
    private final ScriptRepository scriptRepository;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameChatService gameChatService;

    public GameRoom startGame(ObjectId roomId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("房间不存在: " + roomId));

        if (room.getStatus() != GameRoomStatus.WAITING) {
            throw new IllegalStateException("房间状态不正确，无法开始游戏");
        }
        if (room.getScriptId() == null) {
            throw new IllegalStateException("请先选择剧本");
        }

        boolean hasPlayerWithRole = room.getMembers().stream()
                .anyMatch(m -> !m.isAi() && m.getRoleId() != null);
        if (!hasPlayerWithRole) {
            throw new IllegalStateException("请先选择角色");
        }

        Script script = scriptRepository.findById(room.getScriptId())
                .orElseThrow(() -> new IllegalStateException("剧本数据异常"));

        room.setStatus(GameRoomStatus.PLAYING);
        room.setStartTime(LocalDateTime.now());
        room.setCurrentStage(0);

        // Cache current stage in Redis
        redisTemplate.opsForValue().set(
                "game:room:" + roomId.toHexString() + ":stage", "0");

        // Stub: initialize clue pool (no-op for now)
        initCluePool(roomId, room.getScriptId(), 0);

        GameRoom saved = gameRoomRepository.save(room);

        // Broadcast game start
        String stageTitle = (script.getStages() != null && !script.getStages().isEmpty())
                ? script.getStages().get(0).getStageTitle()
                : "第一幕";

        messagingTemplate.convertAndSend("/topic/room." + roomId.toHexString(),
                Map.of("type", "SYSTEM", "content", "游戏开始！当前阶段: " + stageTitle));

        log.info("Game started in room {}", roomId);
        return saved;
    }

    public GameRoom advanceStage(ObjectId roomId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("房间不存在: " + roomId));

        if (room.getStatus() != GameRoomStatus.PLAYING) {
            throw new IllegalStateException("游戏未在进行中");
        }

        Script script = scriptRepository.findById(room.getScriptId())
                .orElseThrow(() -> new IllegalStateException("剧本数据异常"));

        int nextStage = room.getCurrentStage() + 1;

        if (script.getStages() == null || nextStage >= script.getStages().size()) {
            return endGame(roomId);
        }

        room.setCurrentStage(nextStage);
        redisTemplate.opsForValue().set(
                "game:room:" + roomId.toHexString() + ":stage", String.valueOf(nextStage));

        GameRoom saved = gameRoomRepository.save(room);

        ScriptStage stage = script.getStages().get(nextStage);
        messagingTemplate.convertAndSend("/topic/room." + roomId.toHexString(),
                Map.of("type", "SYSTEM", "content", "进入新阶段: " + stage.getStageTitle()));

        log.info("Room {} advanced to stage {}", roomId, nextStage);
        return saved;
    }

    public GameRoom endGame(ObjectId roomId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("房间不存在: " + roomId));

        room.setStatus(GameRoomStatus.FINISHED);
        room.setEndTime(LocalDateTime.now());
        GameRoom saved = gameRoomRepository.save(room);

        // Flush chat messages to GameRecord
        gameChatService.flushMessages(roomId, room);

        // Clean up Redis keys
        String roomKey = roomId.toHexString();
        redisTemplate.delete("game:room:" + roomKey + ":stage");
        redisTemplate.delete("game:room:" + roomKey + ":clues");

        messagingTemplate.convertAndSend("/topic/room." + roomKey,
                Map.of("type", "SYSTEM", "content", "游戏结束！"));

        log.info("Game ended in room {}", roomId);
        return saved;
    }

    /**
     * Initialize clue pool for a game stage.
     * Stub — no-op for now. Future: load Clue documents into Redis.
     */
    protected void initCluePool(ObjectId roomId, ObjectId scriptId, int stageNumber) {
        // No-op stub — will load clues into Redis game:room:{roomId}:clues in future
        log.debug("initCluePool called for room={}, script={}, stage={} (no-op)", roomId, scriptId, stageNumber);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: FAIL (GameChatService not yet created — expected, will compile after Task 8)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/GameFlowService.java
git commit -m "feat: add GameFlowService for game state transitions and stage management"
```

---

### Task 8: GameChatService

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/service/GameChatService.java`

- [ ] **Step 1: Create GameChatService**

```java
package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.dto.ChatMessageDTO;
import com.example.striptkillgamedemo2.entity.mongo.GameRecord;
import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.repository.GameRecordRepository;
import com.example.striptkillgamedemo2.repository.RoleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameChatService {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final RoleRepository roleRepository;
    private final GameRecordRepository gameRecordRepository;
    private final GameSummaryService gameSummaryService;
    private final ObjectMapper objectMapper;

    private static final String MESSAGES_KEY_PREFIX = "game:messages:";

    public ChatMessageDTO sendMessage(ObjectId roomId, ObjectId senderRoleId, String content) {
        Role role = roleRepository.findById(senderRoleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        GameMessage message = GameMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .gameRoomId(roomId)
                .senderRoleId(senderRoleId)
                .isAi(false)
                .senderRoleName(role.getName())
                .senderAvatar(role.getAvatar())
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        storeMessage(roomId, message);

        ChatMessageDTO dto = toChatDTO(message);
        messagingTemplate.convertAndSend("/topic/room." + roomId.toHexString(), dto);

        return dto;
    }

    public void flushMessages(ObjectId roomId, GameRoom room) {
        String key = MESSAGES_KEY_PREFIX + roomId.toHexString();

        try {
            List<String> summarized = gameSummaryService.summarize(roomId);

            String scriptTitle = null;
            // Script title will be set by caller if needed; use room config for now

            GameRecord record = GameRecord.builder()
                    .roomId(roomId)
                    .scriptTitle(scriptTitle)
                    .fullChatLog(summarized)
                    .startTime(room.getStartTime())
                    .endTime(room.getEndTime())
                    .build();

            gameRecordRepository.save(record);
            log.info("Game record saved for room {}", roomId);
        } finally {
            redisTemplate.delete(key);
            log.info("Redis messages flushed for room {}", roomId);
        }
    }

    public ObjectId findRoleIdForUser(GameRoom room, ObjectId userId) {
        return room.getMembers().stream()
                .filter(m -> m.getUserId() != null && m.getUserId().equals(userId))
                .map(Member::getRoleId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("你不在该房间中或未选择角色"));
    }

    private void storeMessage(ObjectId roomId, GameMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(MESSAGES_KEY_PREFIX + roomId.toHexString(), json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message", e);
            throw new RuntimeException("消息序列化失败", e);
        }
    }

    private ChatMessageDTO toChatDTO(GameMessage message) {
        return ChatMessageDTO.builder()
                .messageId(message.getMessageId())
                .senderRoleId(message.getSenderRoleId().toHexString())
                .senderRoleName(message.getSenderRoleName())
                .senderAvatar(message.getSenderAvatar())
                .isAi(message.isAi())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .build();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS (now GameFlowService can also resolve GameChatService)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/service/GameChatService.java
git commit -m "feat: add GameChatService for message routing, Redis storage, and flush"
```

---

### Task 9: GameRoomController

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/controller/GameRoomController.java`

- [ ] **Step 1: Create GameRoomController**

```java
package com.example.striptkillgamedemo2.controller;

import com.example.striptkillgamedemo2.dto.RoleDTO;
import com.example.striptkillgamedemo2.dto.RoomDetailDTO;
import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
import com.example.striptkillgamedemo2.service.GameFlowService;
import com.example.striptkillgamedemo2.service.GameRoomService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class GameRoomController {

    private final GameRoomService gameRoomService;
    private final GameFlowService gameFlowService;

    @PostMapping
    public ResponseEntity<RoomDetailDTO> createRoom(Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        GameRoom room = gameRoomService.createRoom(userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(room));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDetailDTO> getRoom(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);
        GameRoom room = gameRoomService.getRoom(rid);
        gameRoomService.validateMembership(room, userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(room));
    }

    @PutMapping("/{roomId}/script")
    public ResponseEntity<RoomDetailDTO> selectScript(
            @PathVariable String roomId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);
        GameRoom room = gameRoomService.getRoom(rid);
        gameRoomService.validateMembership(room, userId);

        ObjectId scriptId = new ObjectId(body.get("scriptId"));
        GameRoom updated = gameRoomService.selectScript(rid, scriptId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(updated));
    }

    @GetMapping("/{roomId}/roles")
    public ResponseEntity<List<RoleDTO>> getRoles(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);
        GameRoom room = gameRoomService.getRoom(rid);
        gameRoomService.validateMembership(room, userId);
        return ResponseEntity.ok(gameRoomService.getRoles(rid));
    }

    @PutMapping("/{roomId}/role")
    public ResponseEntity<RoomDetailDTO> selectRole(
            @PathVariable String roomId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);

        ObjectId roleId = new ObjectId(body.get("roleId"));
        GameRoom updated = gameRoomService.selectRole(rid, roleId, userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(updated));
    }

    @PostMapping("/{roomId}/start")
    public ResponseEntity<RoomDetailDTO> startGame(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);
        GameRoom room = gameRoomService.getRoom(rid);
        gameRoomService.validateMembership(room, userId);

        GameRoom updated = gameFlowService.startGame(rid);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(updated));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);

        GameRoom room = gameRoomService.leaveRoom(rid, userId);

        if (room.getStatus() == com.example.striptkillgamedemo2.entity.enums.GameRoomStatus.FINISHED) {
            gameFlowService.endGame(rid);
        }

        return ResponseEntity.ok(Map.of("message", "已离开房间"));
    }

    private ObjectId extractUserId(Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        return new ObjectId(claims.getSubject());
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/controller/GameRoomController.java
git commit -m "feat: add GameRoomController for room CRUD and game lifecycle REST APIs"
```

---

### Task 10: GameChatController (WebSocket)

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/controller/GameChatController.java`

- [ ] **Step 1: Create GameChatController**

```java
package com.example.striptkillgamedemo2.controller;

import com.example.striptkillgamedemo2.dto.ChatMessageRequest;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
import com.example.striptkillgamedemo2.service.GameChatService;
import com.example.striptkillgamedemo2.service.GameRoomService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameChatController {

    private final GameChatService gameChatService;
    private final GameRoomService gameRoomService;

    @MessageMapping("/chat.{roomId}")
    public void handleChatMessage(
            @DestinationVariable String roomId,
            @Payload ChatMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) headerAccessor.getUser();
        if (auth == null) {
            log.warn("Unauthenticated message attempt for room {}", roomId);
            return;
        }

        Claims claims = (Claims) auth.getPrincipal();
        ObjectId userId = new ObjectId(claims.getSubject());
        ObjectId rid = new ObjectId(roomId);

        // Room membership and status check
        GameRoom room = gameRoomService.getRoom(rid);
        gameRoomService.validateMembership(room, userId);

        if (room.getStatus() != GameRoomStatus.PLAYING) {
            log.warn("Message attempt in non-playing room {} by user {}", roomId, userId);
            return;
        }

        ObjectId senderRoleId = gameChatService.findRoleIdForUser(room, userId);
        gameChatService.sendMessage(rid, senderRoleId, request.getContent());
    }
}
```

- [ ] **Step 2: Verify full backend compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/controller/GameChatController.java
git commit -m "feat: add GameChatController for WebSocket STOMP chat with room auth"
```

---

### Task 11: Frontend Dependencies & API Layer

**Files:**
- Modify: `frontend/package.json` (install deps)
- Create: `frontend/src/api/script.ts`
- Create: `frontend/src/api/room.ts`

- [ ] **Step 1: Install WebSocket dependencies**

Run from `frontend/` directory:
```bash
cd frontend && npm install @stomp/stompjs sockjs-client && npm install -D @types/sockjs-client
```

- [ ] **Step 2: Create script API**

```typescript
// frontend/src/api/script.ts
import http from './axios'

export function getRandomScripts() {
  return http.get('/scripts/random')
}
```

- [ ] **Step 3: Create room API**

```typescript
// frontend/src/api/room.ts
import http from './axios'

export function createRoom() {
  return http.post('/rooms')
}

export function getRoom(roomId: string) {
  return http.get(`/rooms/${roomId}`)
}

export function selectScript(roomId: string, scriptId: string) {
  return http.put(`/rooms/${roomId}/script`, { scriptId })
}

export function getRoles(roomId: string) {
  return http.get(`/rooms/${roomId}/roles`)
}

export function selectRole(roomId: string, roleId: string) {
  return http.put(`/rooms/${roomId}/role`, { roleId })
}

export function startGame(roomId: string) {
  return http.post(`/rooms/${roomId}/start`)
}

export function leaveRoom(roomId: string) {
  return http.post(`/rooms/${roomId}/leave`)
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/package.json frontend/package-lock.json \
        frontend/src/api/script.ts frontend/src/api/room.ts
git commit -m "feat: add frontend API layer for scripts and rooms, install stompjs"
```

---

### Task 12: Game Store & WebSocket Composable

**Files:**
- Create: `frontend/src/stores/game.ts`
- Create: `frontend/src/composables/useWebSocket.ts`

- [ ] **Step 1: Create game store**

```typescript
// frontend/src/stores/game.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ChatMessage {
  messageId: string
  senderRoleId: string
  senderRoleName: string
  senderAvatar: string
  isAi: boolean
  content: string
  timestamp: string
}

export const useGameStore = defineStore('game', () => {
  const roomId = ref<string | null>(null)
  const scriptId = ref<string | null>(null)
  const myRoleId = ref<string | null>(null)
  const messages = ref<ChatMessage[]>([])
  const gameStatus = ref<'WAITING' | 'PLAYING' | 'FINISHED'>('WAITING')

  function setRoom(id: string) {
    roomId.value = id
  }

  function setScript(id: string) {
    scriptId.value = id
  }

  function setMyRole(id: string) {
    myRoleId.value = id
  }

  function addMessage(msg: ChatMessage) {
    messages.value.push(msg)
  }

  function setGameStatus(status: 'WAITING' | 'PLAYING' | 'FINISHED') {
    gameStatus.value = status
  }

  function clearGame() {
    roomId.value = null
    scriptId.value = null
    myRoleId.value = null
    messages.value = []
    gameStatus.value = 'WAITING'
  }

  return {
    roomId, scriptId, myRoleId, messages, gameStatus,
    setRoom, setScript, setMyRole, addMessage, setGameStatus, clearGame
  }
})
```

- [ ] **Step 2: Create WebSocket composable**

```typescript
// frontend/src/composables/useWebSocket.ts
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from '../stores/auth'
import { ref } from 'vue'

let stompClient: Client | null = null
const connected = ref(false)

export function useWebSocket() {
  const authStore = useAuthStore()

  function connect(roomId: string, onMessage: (msg: any) => void) {
    if (stompClient?.active) {
      stompClient.deactivate()
    }

    stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: {
        Authorization: `Bearer ${authStore.accessToken}`
      },
      onConnect: () => {
        connected.value = true
        stompClient!.subscribe(`/topic/room.${roomId}`, (frame) => {
          const body = JSON.parse(frame.body)
          onMessage(body)
        })
      },
      onDisconnect: () => {
        connected.value = false
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'])
        connected.value = false
      }
    })

    stompClient.activate()
  }

  function send(roomId: string, content: string) {
    if (!stompClient?.active) return
    stompClient.publish({
      destination: `/app/chat.${roomId}`,
      body: JSON.stringify({ content })
    })
  }

  function disconnect() {
    if (stompClient?.active) {
      stompClient.deactivate()
    }
    connected.value = false
  }

  return { connect, send, disconnect, connected }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/stores/game.ts frontend/src/composables/useWebSocket.ts
git commit -m "feat: add game store and WebSocket composable for STOMP messaging"
```

---

### Task 13: Vue Router — Add Game Routes

**Files:**
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: Add game routes**

Add the following route objects to the `routes` array, after the existing routes (before the closing `]`):

```typescript
{
  path: '/game/:roomId/scripts',
  name: 'ScriptWall',
  component: () => import('../views/ScriptWallView.vue'),
  meta: { requiresAuth: true }
},
{
  path: '/game/:roomId/roles',
  name: 'RoleWall',
  component: () => import('../views/RoleWallView.vue'),
  meta: { requiresAuth: true }
},
{
  path: '/game/:roomId/play',
  name: 'GamePlay',
  component: () => import('../views/GamePlayView.vue'),
  meta: { requiresAuth: true }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/router/index.ts
git commit -m "feat: add game routes for script wall, role wall, and gameplay"
```

---

### Task 14: HomeView.vue Redesign

**Files:**
- Modify: `frontend/src/views/HomeView.vue`

- [ ] **Step 1: Rewrite HomeView with sidebar menu**

Replace the entire file with:

```vue
<template>
  <div class="home-container">
    <!-- Header -->
    <header class="home-header">
      <div class="header-left">
        <span class="logo-text">剧本杀</span>
      </div>
      <div class="header-right" @click="router.push('/profile')" style="cursor: pointer;">
        <el-avatar :size="36" :src="authStore.userInfo?.avatarUrl">
          <el-icon><UserFilled /></el-icon>
        </el-avatar>
        <span class="nickname">{{ authStore.userInfo?.nickname || '玩家' }}</span>
      </div>
    </header>

    <!-- Body -->
    <div class="home-body">
      <!-- Sidebar -->
      <aside class="home-sidebar">
        <div
          v-for="item in menuItems"
          :key="item.key"
          class="sidebar-item"
          :class="{ active: activeMenu === item.key }"
          @click="activeMenu = item.key"
        >
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </div>
      </aside>

      <!-- Content -->
      <main class="home-content">
        <!-- 剧本杀游戏 -->
        <div v-if="activeMenu === 'game'" class="content-panel">
          <div class="panel-header">
            <h2>剧本杀游戏</h2>
            <p>选择剧本，邀请好友，开始一场烧脑之旅</p>
          </div>
          <div class="panel-body">
            <el-empty description="点击右下角创建房间，开始游戏" />
          </div>
          <div class="panel-footer">
            <el-button type="primary" size="large" @click="handleCreateRoom" :loading="creating">
              创建房间
            </el-button>
          </div>
        </div>

        <!-- 剧本杀创作 -->
        <div v-else-if="activeMenu === 'create'" class="content-panel">
          <div class="panel-body placeholder">
            <el-empty description="剧本创作功能即将上线，敬请期待" />
          </div>
        </div>

        <!-- 剧本杀上传 -->
        <div v-else-if="activeMenu === 'upload'" class="content-panel">
          <div class="panel-body placeholder">
            <el-empty description="剧本上传功能即将上线，敬请期待" />
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { UserFilled, Opportunity, EditPen, UploadFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useGameStore } from '../stores/game'
import { getUserInfo } from '../api/auth'
import { createRoom } from '../api/room'

const router = useRouter()
const authStore = useAuthStore()
const gameStore = useGameStore()

const activeMenu = ref('game')
const creating = ref(false)

const menuItems = [
  { key: 'game', label: '剧本杀游戏', icon: markRaw(Opportunity) },
  { key: 'create', label: '剧本杀创作', icon: markRaw(EditPen) },
  { key: 'upload', label: '剧本杀上传', icon: markRaw(UploadFilled) },
]

onMounted(async () => {
  try {
    const { data } = await getUserInfo()
    authStore.setUserInfo(data)
  } catch (e) {
    // handled by interceptor
  }
})

async function handleCreateRoom() {
  creating.value = true
  try {
    const { data } = await createRoom()
    gameStore.setRoom(data.roomId)
    router.push(`/game/${data.roomId}/scripts`)
  } catch (e) {
    console.error('创建房间失败', e)
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.home-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #1a1a2e;
  color: #e0e0e0;
}

.home-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: #16213e;
  border-bottom: 1px solid #0f3460;
}

.header-left .logo-text {
  font-size: 22px;
  font-weight: bold;
  color: #e94560;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nickname {
  font-size: 14px;
  color: #a0a0b0;
}

.home-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.home-sidebar {
  width: 200px;
  background: #16213e;
  padding: 16px 0;
  border-right: 1px solid #0f3460;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 24px;
  cursor: pointer;
  transition: all 0.2s;
  color: #a0a0b0;
}

.sidebar-item:hover {
  background: #1a1a40;
  color: #fff;
}

.sidebar-item.active {
  background: #0f3460;
  color: #e94560;
  border-right: 3px solid #e94560;
}

.home-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.content-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header {
  margin-bottom: 24px;
}

.panel-header h2 {
  font-size: 24px;
  color: #fff;
  margin: 0 0 8px;
}

.panel-header p {
  color: #a0a0b0;
  margin: 0;
}

.panel-body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel-footer {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0;
}
</style>
```

- [ ] **Step 2: Verify frontend build**

Run: `cd frontend && npm run build`
Expected: Build succeeds (ScriptWallView, RoleWallView, GamePlayView not yet created — lazy imports only fail at runtime, not build time)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/HomeView.vue
git commit -m "feat: redesign HomeView with sidebar menu and create room button"
```

---

### Task 15: ScriptWallView.vue

**Files:**
- Create: `frontend/src/views/ScriptWallView.vue`

- [ ] **Step 1: Create ScriptWallView**

```vue
<template>
  <div class="script-wall-container">
    <header class="sw-header">
      <h2>剧本墙</h2>
      <p>选择一个剧本开始你的推理之旅</p>
    </header>

    <div class="script-grid">
      <div
        v-for="(item, index) in displayScripts"
        :key="index"
        class="script-card"
        :class="{ placeholder: !item, selected: item && selectedId === item.id }"
        @click="item && handleSelect(item)"
      >
        <template v-if="item">
          <div class="card-cover">
            <img v-if="item.coverImage" :src="item.coverImage" :alt="item.title" />
            <div v-else class="cover-fallback">
              <el-icon :size="40"><Opportunity /></el-icon>
            </div>
          </div>
          <div class="card-info">
            <h3>{{ item.title }}</h3>
            <div class="card-meta">
              <el-tag size="small" :type="difficultyType(item.difficulty)">
                {{ item.difficulty }}
              </el-tag>
              <span class="player-count">{{ item.playerCount }}人</span>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="card-cover cover-fallback">
            <el-icon :size="40" color="#555"><Lock /></el-icon>
          </div>
          <div class="card-info">
            <h3 class="placeholder-text">敬请期待</h3>
          </div>
        </template>
      </div>
    </div>

    <div class="sw-footer">
      <el-button @click="router.push('/home')">返回</el-button>
      <el-button
        type="primary"
        :disabled="!selectedId"
        :loading="submitting"
        @click="handleConfirm"
      >
        确认选择
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Opportunity, Lock } from '@element-plus/icons-vue'
import { getRandomScripts } from '../api/script'
import { selectScript } from '../api/room'
import { useGameStore } from '../stores/game'

interface ScriptItem {
  id: string
  title: string
  description: string
  difficulty: string
  playerCount: number
  coverImage: string
}

const route = useRoute()
const router = useRouter()
const gameStore = useGameStore()
const roomId = route.params.roomId as string

const scripts = ref<ScriptItem[]>([])
const selectedId = ref<string | null>(null)
const submitting = ref(false)

const displayScripts = computed(() => {
  const result: (ScriptItem | null)[] = [...scripts.value]
  while (result.length < 8) {
    result.push(null)
  }
  return result
})

onMounted(async () => {
  try {
    const { data } = await getRandomScripts()
    scripts.value = data
  } catch (e) {
    console.error('加载剧本失败', e)
  }
})

function handleSelect(script: ScriptItem) {
  selectedId.value = script.id
}

function difficultyType(difficulty: string) {
  const map: Record<string, string> = {
    EASY: 'success',
    NORMAL: 'info',
    HARD: 'warning',
    EXPERT: 'danger'
  }
  return (map[difficulty] || 'info') as any
}

async function handleConfirm() {
  if (!selectedId.value) return
  submitting.value = true
  try {
    await selectScript(roomId, selectedId.value)
    gameStore.setScript(selectedId.value)
    router.push(`/game/${roomId}/roles`)
  } catch (e) {
    console.error('选择剧本失败', e)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.script-wall-container {
  min-height: 100vh;
  background: #1a1a2e;
  color: #e0e0e0;
  padding: 32px;
  display: flex;
  flex-direction: column;
}

.sw-header {
  text-align: center;
  margin-bottom: 32px;
}

.sw-header h2 {
  font-size: 28px;
  color: #fff;
  margin: 0 0 8px;
}

.sw-header p {
  color: #a0a0b0;
  margin: 0;
}

.script-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  flex: 1;
}

.script-card {
  background: #16213e;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s;
  border: 2px solid transparent;
}

.script-card:hover:not(.placeholder) {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(233, 69, 96, 0.2);
}

.script-card.selected {
  border-color: #e94560;
  box-shadow: 0 0 16px rgba(233, 69, 96, 0.4);
}

.script-card.placeholder {
  cursor: default;
  opacity: 0.5;
}

.card-cover {
  height: 160px;
  overflow: hidden;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-fallback {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0f3460;
}

.card-info {
  padding: 12px;
}

.card-info h3 {
  font-size: 16px;
  color: #fff;
  margin: 0 0 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.player-count {
  font-size: 12px;
  color: #a0a0b0;
}

.placeholder-text {
  color: #555 !important;
}

.sw-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 24px 0 0;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/ScriptWallView.vue
git commit -m "feat: add ScriptWallView with 4x2 grid, selection, and placeholders"
```

---

### Task 16: RoleWallView.vue

**Files:**
- Create: `frontend/src/views/RoleWallView.vue`

- [ ] **Step 1: Create RoleWallView**

```vue
<template>
  <div class="role-wall-container">
    <header class="rw-header">
      <h2>角色墙</h2>
      <p>选择你要扮演的角色</p>
    </header>

    <div class="role-grid" :class="gridClass">
      <div
        v-for="role in roles"
        :key="role.id"
        class="role-card"
        :class="{
          selected: selectedId === role.id,
          unavailable: !role.isAvailable,
          npc: role.isNpc
        }"
        @click="role.isAvailable && !role.isNpc && handleSelect(role)"
      >
        <div class="role-avatar">
          <el-avatar :size="64" :src="role.avatar">
            {{ role.name.charAt(0) }}
          </el-avatar>
        </div>
        <div class="role-name">{{ role.name }}</div>
        <div v-if="role.isNpc" class="role-tag">NPC</div>
        <div v-else-if="!role.isAvailable" class="role-tag taken">已选</div>
      </div>
    </div>

    <div class="rw-footer">
      <el-button @click="router.back()">返回</el-button>
      <el-button
        type="primary"
        size="large"
        :disabled="!selectedId"
        :loading="submitting"
        @click="handleConfirm"
      >
        开始游戏
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getRoles, selectRole, startGame } from '../api/room'
import { useGameStore } from '../stores/game'

interface RoleItem {
  id: string
  name: string
  avatar: string
  isNpc: boolean
  isAvailable: boolean
}

const route = useRoute()
const router = useRouter()
const gameStore = useGameStore()
const roomId = route.params.roomId as string

const roles = ref<RoleItem[]>([])
const selectedId = ref<string | null>(null)
const submitting = ref(false)

const gridClass = computed(() => {
  const count = roles.value.length
  if (count <= 5) return 'grid-1-row'
  if (count <= 10) return 'grid-2-rows'
  return 'grid-3-rows'
})

onMounted(async () => {
  try {
    const { data } = await getRoles(roomId)
    roles.value = data
  } catch (e) {
    console.error('加载角色失败', e)
  }
})

function handleSelect(role: RoleItem) {
  selectedId.value = role.id
}

async function handleConfirm() {
  if (!selectedId.value) return
  submitting.value = true
  try {
    await selectRole(roomId, selectedId.value)
    gameStore.setMyRole(selectedId.value)
    await startGame(roomId)
    gameStore.setGameStatus('PLAYING')
    router.push(`/game/${roomId}/play`)
  } catch (e) {
    console.error('开始游戏失败', e)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.role-wall-container {
  min-height: 100vh;
  background: #1a1a2e;
  color: #e0e0e0;
  padding: 32px;
  display: flex;
  flex-direction: column;
}

.rw-header {
  text-align: center;
  margin-bottom: 32px;
}

.rw-header h2 {
  font-size: 28px;
  color: #fff;
  margin: 0 0 8px;
}

.rw-header p {
  color: #a0a0b0;
  margin: 0;
}

.role-grid {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 20px;
}

.grid-1-row {
  align-content: center;
}

.grid-2-rows,
.grid-3-rows {
  align-content: center;
}

.role-card {
  width: 140px;
  padding: 20px 16px;
  background: #16213e;
  border-radius: 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  border: 2px solid transparent;
  position: relative;
}

.role-card:hover:not(.unavailable):not(.npc) {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(233, 69, 96, 0.2);
}

.role-card.selected {
  border-color: #e94560;
  box-shadow: 0 0 16px rgba(233, 69, 96, 0.4);
}

.role-card.unavailable,
.role-card.npc {
  cursor: default;
  opacity: 0.5;
}

.role-avatar {
  margin-bottom: 12px;
}

.role-name {
  font-size: 14px;
  color: #fff;
}

.role-tag {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #0f3460;
  color: #a0a0b0;
}

.role-tag.taken {
  background: #e94560;
  color: #fff;
}

.rw-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 24px 0 0;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/RoleWallView.vue
git commit -m "feat: add RoleWallView with adaptive grid layout and role selection"
```

---

### Task 17: GamePlayView.vue

**Files:**
- Create: `frontend/src/views/GamePlayView.vue`

- [ ] **Step 1: Create GamePlayView**

```vue
<template>
  <div class="game-play-container">
    <!-- Top Bar -->
    <header class="gp-header">
      <el-button class="exit-btn" text @click="handleLeave">
        <el-icon :size="20"><ArrowLeft /></el-icon>
        退出
      </el-button>
      <div class="room-info">
        <span v-if="roomDetail">{{ roomDetail.scriptTitle || '剧本杀' }}</span>
        <el-tag size="small" type="info" v-if="roomDetail">
          第 {{ (roomDetail.currentStage || 0) + 1 }} 幕
        </el-tag>
      </div>
      <div style="width: 80px"></div>
    </header>

    <!-- Chat Area -->
    <div class="chat-area" ref="chatAreaRef">
      <div
        v-for="msg in gameStore.messages"
        :key="msg.messageId"
        class="chat-message"
        :class="{ 'is-self': msg.senderRoleId === gameStore.myRoleId }"
      >
        <!-- Left: others -->
        <template v-if="msg.senderRoleId !== gameStore.myRoleId">
          <el-avatar :size="36" :src="msg.senderAvatar" class="msg-avatar">
            {{ msg.senderRoleName?.charAt(0) }}
          </el-avatar>
          <div class="msg-body">
            <div class="msg-name">{{ msg.senderRoleName }}</div>
            <div class="msg-bubble left">{{ msg.content }}</div>
          </div>
        </template>
        <!-- Right: self -->
        <template v-else>
          <div class="msg-body">
            <div class="msg-name self">{{ msg.senderRoleName }}</div>
            <div class="msg-bubble right">{{ msg.content }}</div>
          </div>
          <el-avatar :size="36" :src="msg.senderAvatar" class="msg-avatar">
            {{ msg.senderRoleName?.charAt(0) }}
          </el-avatar>
        </template>
      </div>

      <!-- System messages -->
      <div
        v-for="(sysMsg, idx) in systemMessages"
        :key="'sys-' + idx"
        class="system-message"
      >
        {{ sysMsg }}
      </div>
    </div>

    <!-- Input Area -->
    <div class="input-area">
      <el-input
        v-model="inputText"
        placeholder="输入消息..."
        @keyup.enter="handleSend"
        :disabled="gameStore.gameStatus !== 'PLAYING'"
      />
      <el-button
        type="primary"
        @click="handleSend"
        :disabled="!inputText.trim() || gameStore.gameStatus !== 'PLAYING'"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getRoom, leaveRoom } from '../api/room'
import { useGameStore } from '../stores/game'
import { useWebSocket } from '../composables/useWebSocket'

const route = useRoute()
const router = useRouter()
const gameStore = useGameStore()
const { connect, send, disconnect } = useWebSocket()

const roomId = route.params.roomId as string
const roomDetail = ref<any>(null)
const inputText = ref('')
const chatAreaRef = ref<HTMLElement | null>(null)
const systemMessages = ref<string[]>([])

onMounted(async () => {
  try {
    const { data } = await getRoom(roomId)
    roomDetail.value = data
    gameStore.setGameStatus(data.status)
  } catch (e) {
    console.error('加载房间信息失败', e)
    router.push('/home')
    return
  }

  connect(roomId, (msg: any) => {
    if (msg.type === 'SYSTEM') {
      systemMessages.value.push(msg.content)
      if (msg.content === '游戏结束！') {
        gameStore.setGameStatus('FINISHED')
      }
    } else {
      gameStore.addMessage(msg)
    }
    nextTick(() => scrollToBottom())
  })
})

onUnmounted(() => {
  disconnect()
})

watch(() => gameStore.messages.length, () => {
  nextTick(() => scrollToBottom())
})

function scrollToBottom() {
  if (chatAreaRef.value) {
    chatAreaRef.value.scrollTop = chatAreaRef.value.scrollHeight
  }
}

function handleSend() {
  const text = inputText.value.trim()
  if (!text) return
  send(roomId, text)
  inputText.value = ''
}

async function handleLeave() {
  try {
    await leaveRoom(roomId)
  } catch (e) {
    console.error('离开房间失败', e)
  }
  gameStore.clearGame()
  disconnect()
  router.push('/home')
}
</script>

<style scoped>
.game-play-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #1a1a2e;
  color: #e0e0e0;
}

.gp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #16213e;
  border-bottom: 1px solid #0f3460;
}

.exit-btn {
  color: #e94560 !important;
}

.room-info {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  color: #fff;
}

.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.chat-message.is-self {
  flex-direction: row;
  justify-content: flex-end;
}

.msg-avatar {
  flex-shrink: 0;
}

.msg-body {
  max-width: 60%;
}

.msg-name {
  font-size: 12px;
  color: #a0a0b0;
  margin-bottom: 4px;
}

.msg-name.self {
  text-align: right;
}

.msg-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.msg-bubble.left {
  background: #16213e;
  color: #e0e0e0;
  border-top-left-radius: 2px;
}

.msg-bubble.right {
  background: #e94560;
  color: #fff;
  border-top-right-radius: 2px;
}

.system-message {
  text-align: center;
  font-size: 12px;
  color: #666;
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  align-self: center;
}

.input-area {
  display: flex;
  gap: 10px;
  padding: 16px 20px;
  background: #16213e;
  border-top: 1px solid #0f3460;
}

.input-area .el-input {
  flex: 1;
}
</style>
```

- [ ] **Step 2: Verify frontend build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/GamePlayView.vue
git commit -m "feat: add GamePlayView with chat room, message bubbles, and exit"
```

---

### Task 18: Final Compilation & Integration Verification

- [ ] **Step 1: Full backend build**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Full frontend build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 3: Final commit (if any remaining changes)**

```bash
git add -A
git status
# Only commit if there are changes
git commit -m "chore: integration verification — full build passes"
```
