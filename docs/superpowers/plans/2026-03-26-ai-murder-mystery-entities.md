# AI Murder Mystery Game Entities Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create all MongoDB entity classes, enums, and Redis DTOs for the AI Murder Mystery Game system based on the approved design spec.

**Architecture:** Hybrid storage with MongoDB for persistent data (User, Script, Role, Clue, GameRoom, GameRecord) and Redis for runtime game state (GameClueInstance, GameMessage, VoteRecord).

**Tech Stack:** Spring Boot 3.5.12, Spring Data MongoDB, Spring Data Redis, Lombok, Jakarta Bean Validation, Jackson

---

## File Structure

```
src/main/java/com/example/striptkillgamedemo2/entity/
├── enums/
│   ├── ClueType.java          # Enum: TEXT, IMAGE
│   ├── GameRoomStatus.java      # Enum: WAITING, PLAYING, FINISHED
│   └── ScriptDifficulty.java    # Enum: EASY, NORMAL, HARD, EXPERT
├── mongo/
│   ├── User.java               # User account entity
│   ├── Script.java             # Game template entity
│   ├── ScriptStage.java        # Embedded: Script stage
│   ├── Role.java               # Character entity
│   ├── Clue.java              # Clue template entity
│   ├── GameRoom.java           # Room metadata entity
│   ├── Member.java             # Embedded: Room member
│   └── GameRecord.java         # Game summary entity
└── redis/
    ├── GameClueInstance.java    # Runtime clue state
    ├── GameMessage.java        # Runtime message
    └── VoteRecord.java         # Runtime vote record
```

---

### Task 1: Create package structure

**Files:**
- Create directories: entity/`enums`, entity/mongo, entity/redis

**Note:** Using 'enums' (plural) because 'enum' is a Java reserved keyword.

- [ ] **Step 1: Create entity package directories**

```bash
mkdir -p src/main/java/com/example/striptkillgamedemo2/entity/enums
mkdir -p src/main/java/com/example/striptkillgamedemo2/entity/mongo
mkdir -p src/main/java/com/example/striptkillgamedemo2/entity/redis
```

- [ ] **Step 2: Verify directory structure**

```bash
ls -la src/main/java/com/example/striptkillgamedemo2/entity/
```

Expected: enum, mongo, redis directories exist

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/
git commit -m "feat: create entity package structure"
```

---

### Task 2: Create GameRoomStatus enum

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/enums/GameRoomStatus.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/enums/GameRoomStatusTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/enum/GameRoomStatusTest.java
package com.example.striptkillgamedemo2.entity.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameRoomStatusTest {

    @Test
    void enumValuesShouldMatchDesign() {
        // These are the three states defined in the spec
        assertEquals(3, GameRoomStatus.values().length);
        assertNotNull(GameRoomStatus.WAITING);
        assertNotNull(GameRoomStatus.PLAYING);
        assertNotNull(GameRoomStatus.FINISHED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=GameRoomStatusTest
```

Expected: FAIL with "cannot find symbol: class GameRoomStatus"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/enum/GameRoomStatus.java
package com.example.striptkillgamedemo2.entity.enums;

/**
 * Game room status enum.
 * WAITING: Room created, waiting for players
 * PLAYING: Game in progress
 * FINISHED: Game completed
 */
public enum GameRoomStatus {
    WAITING,
    PLAYING,
    FINISHED
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=GameRoomStatusTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/enum/GameRoomStatus.java
git add src/test/java/com/example/striptkillgamedemo2/entity/enum/GameRoomStatusTest.java
git commit -m "feat: add GameRoomStatus enum"
```

---

### Task 3: Create ScriptDifficulty enum

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/enum/ScriptDifficulty.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/enum/ScriptDifficultyTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/enum/ScriptDifficultyTest.java
package com.example.striptkillgamedemo2.entity.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptDifficultyTest {

    @Test
    void enumShouldHaveFourDifficultyLevels() {
        assertEquals(4, ScriptDifficulty.values().length);
        assertEquals(1, ScriptDifficulty.EASY.getLevel());
        assertEquals(2, ScriptDifficulty.NORMAL.getLevel());
        assertEquals(3, ScriptDifficulty.HARD.getLevel());
        assertEquals(4, ScriptDifficulty.EXPERT.getLevel());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=ScriptDifficultyTest
```

Expected: FAIL with "cannot find symbol: class ScriptDifficulty"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/enum/ScriptDifficulty.java
package com.example.striptkillgamedemo2.entity.enums;

/**
 * Script difficulty enum.
 * EASY: Level 1
 * NORMAL: Level 2
 * HARD: Level 3
 * EXPERT: Level 4
 */
public enum ScriptDifficulty {
    EASY(1),
    NORMAL(2),
    HARD(3),
    EXPERT(4);

    private final int level;

    ScriptDifficulty(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=ScriptDifficultyTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/enum/ScriptDifficulty.java
git add src/test/java/com/example/striptkillgamedemo2/entity/enum/ScriptDifficultyTest.java
git commit -m "feat: add ScriptDifficulty enum with level property"
```

---

### Task 4: Create ClueType enum

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/enum/ClueType.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/enum/ClueTypeTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/enum/ClueTypeTest.java
package com.example.striptkillgamedemo2.entity.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClueTypeTest {

    @Test
    void enumShouldHaveTextAndImageTypes() {
        assertEquals(2, ClueType.values().length);
        assertNotNull(ClueType.TEXT);
        assertNotNull(ClueType.IMAGE);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=ClueTypeTest
```

Expected: FAIL with "cannot find symbol: class ClueType"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/enum/ClueType.java
package com.example.striptkillgamedemo2.entity.enums;

/**
 * Clue type enum.
 * TEXT: Text-based clue
 * IMAGE: Image-based clue
 */
public enum ClueType {
    TEXT,
    IMAGE
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=ClueTypeTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/enum/ClueType.java
git add src/test/java/com/example/striptkillgamedemo2/entity/enum/ClueTypeTest.java
git commit -m "feat: add ClueType enum"
```

---

### Task 5: Create User entity

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/mongo/User.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/mongo/UserTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/mongo/UserTest.java
package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void userEntityShouldHaveRequiredFields() {
        User user = new User();
        user.setId(new ObjectId());
        user.setUsername("testuser");
        user.setPassword("hashedpassword");
        user.setNickname("Test User");
        user.setAvatarUrl("http://example.com/avatar.png");
        user.setCreatedAt(LocalDateTime.now());

        assertNotNull(user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("hashedpassword", user.getPassword());
        assertEquals("Test User", user.getNickname());
        assertEquals("http://example.com/avatar.png", user.getAvatarUrl());
        assertNotNull(user.getCreatedAt());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=UserTest
```

Expected: FAIL with "cannot find symbol: class User"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/mongo/User.java
package com.example.striptkillgamedemo2.entity.mongo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * User entity representing player accounts in the system.
 *
 * Security Notes:
 * - Password field stores BCrypt-hashed passwords (use BCryptPasswordEncoder in service layer)
 * - Password is excluded from JSON serialization via @JsonIgnore
 * - Username is unique indexed for authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @org.springframework.data.annotation.CreatedDate
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=UserTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/mongo/User.java
git add src/test/java/com/example/striptkillgamedemo2/entity/mongo/UserTest.java
git commit -m "feat: add User entity with BCrypt password hashing support"
```

---

### Task 6: Create ScriptStage embedded entity

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/mongo/ScriptStage.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/mongo/ScriptStageTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/mongo/ScriptStageTest.java
package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ScriptStageTest {

    @Test
    void scriptStageShouldHaveRequiredFields() {
        ScriptStage stage = ScriptStage.builder()
            .stageNumber(1)
            .stageTitle("Introduction")
            .contentMap(Map.of(new ObjectId(), "Welcome to the mystery"))
            .audioUrl("http://example.com/intro.mp3")
            .build();

        assertEquals(1, stage.getStageNumber());
        assertEquals("Introduction", stage.getStageTitle());
        assertNotNull(stage.getContentMap());
        assertEquals("http://example.com/intro.mp3", stage.getAudioUrl());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=ScriptStageTest
```

Expected: FAIL with "cannot find symbol: class ScriptStage"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/mongo/ScriptStage.java
package com.example.striptkillgamedemo2.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Embedded;

import java.util.Map;

/**
 * ScriptStage represents a single stage within a script.
 * Embedded within Script for optimal query performance.
 *
 * ContentMap maps roleId (ObjectId) to content revealed to that role at this stage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embedded
public class ScriptStage {
    private int stageNumber;
    private String stageTitle;
    private Map<ObjectId, String> contentMap;
    private String audioUrl;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=ScriptStageTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/mongo/ScriptStage.java
git add src/test/java/com/example/striptkillgamedemo2/entity/mongo/ScriptStageTest.java
git commit -m "feat: add ScriptStage embedded entity"
```

---

### Task 7: Create Script entity

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/mongo/Script.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/mongo/ScriptTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/mongo/ScriptTest.java
package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.example.striptkillgamedemo2.entity.enums.ScriptDifficulty;

import java.util.List;

class ScriptTest {

    @Test
    void scriptEntityShouldHaveRequiredFields() {
        Script script = Script.builder()
            .title("Mystery at the Mansion")
            .description("A classic whodunit")
            .difficulty(ScriptDifficulty.NORMAL)
            .playerCount(6)
            .coverImage("http://example.com/cover.png")
            .dmConfig("{\"mode\":\"standard\"}")
            .stages(List.of())
            .version(1)
            .configuration("{}")
            .build();

        assertEquals("Mystery at the Mansion", script.getTitle());
        assertEquals("A classic whodunit", script.getDescription());
        assertEquals(ScriptDifficulty.NORMAL, script.getDifficulty());
        assertEquals(6, script.getPlayerCount());
        assertEquals(1, script.getVersion());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=ScriptTest
```

Expected: FAIL with "cannot find symbol: class Script"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/mongo/Script.java
package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enum.ScriptDifficulty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Script entity representing game templates containing all static game content.
 *
 * Fields:
 * - dmConfig: Flexible JSON configuration for DM hosting style
 * - stages: Define game progression with role-specific content
 * - version: Script version for tracking updates and preventing breaking changes
 * - configuration: Game-specific configuration overrides
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    private String dmConfig;

    private List<ScriptStage> stages;

    private int version = 1;

    private String configuration;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=ScriptTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/mongo/Script.java
git add src/test/java/com/example/striptkillgamedemo2/entity/mongo/ScriptTest.java
git commit -m "feat: add Script entity with version and configuration fields"
```

---

### Task 8: Create Role entity

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/mongo/Role.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/mongo/RoleTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/mongo/RoleTest.java
package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    @Test
    void roleEntityShouldHaveRequiredFields() {
        Role role = Role.builder()
            .scriptId(new ObjectId())
            .name("Detective Smith")
            .avatar("http://example.com/detective.png")
            .isNpc(false)
            .prompt("You are a clever detective")
            .secret("You know the real killer")
            .selfClueIds(List.of(new ObjectId()))
            .locationTag("Living Room")
            .searchPower(5)
            .build();

        assertEquals("Detective Smith", role.getName());
        assertFalse(role.isNpc());
        assertEquals("Living Room", role.getLocationTag());
        assertEquals(5, role.getSearchPower());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=RoleTest
```

Expected: FAIL with "cannot find symbol: class Role"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/mongo/Role.java
package com.example.striptkillgamedemo2.entity.mongo;

import jakarta.validation.constraints.Indexed;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Role entity representing character definition within a script.
 *
 * Note on Clue-Role Relationship:
 * Clue discoverability is controlled by Clue.searchableRoleIds (source of truth).
 * Role.selfClueIds is for convenience/query optimization and should always match
 * the reverse lookup from Clue collection. The system validates consistency on game start.
 *
 * Fields:
 * - isNpc: Whether this role is played by an AI agent
 * - prompt: Core AI prompt instructions for NPCs
 * - secret: Secret information that must not be revealed to other players
 * - selfClueIds: IDs of clues this role can search for
 * - locationTag: Optional tag indicating search location
 * - searchPower: Optional search action points for limiting searches
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "roles")
public class Role {
    @Id
    private ObjectId id;

    @Indexed
    private ObjectId scriptId;

    @NotBlank
    private String name;

    private String avatar;

    private boolean isNpc = false;

    private String prompt;

    private String secret;

    private List<ObjectId> selfClueIds;

    private String locationTag;

    private Integer searchPower;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=RoleTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/mongo/Role.java
git add src/test/java/com/example/striptkillgamedemo2/entity/mongo/RoleTest.java
git commit -m "feat: add Role entity with clue search optimization"
```

---

### Task 9: Create Clue entity

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/mongo/Clue.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/mongo/ClueTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/mongo/ClueTest.java
package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import com.example.striptkillgamedemo2.entity.enum.ClueType;

class ClueTest {

    @Test
    void clueEntityShouldHaveRequiredFields() {
        Clue clue = Clue.builder()
            .scriptId(new ObjectId())
            .title("Bloody Knife")
            .type(ClueType.TEXT)
            .content("Found a knife with blood stains")
            .isInitialHidden(true)
            .stageNumber(2)
            .searchableRoleIds(List.of(new ObjectId()))
            .build();

        assertEquals("Bloody Knife", clue.getTitle());
        assertEquals(ClueType.TEXT, clue.getType());
        assertEquals("Found a knife with blood stains", clue.getContent());
        assertTrue(clue.isInitialHidden());
        assertEquals(2, clue.getStageNumber());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=ClueTest
```

Expected: FAIL with "cannot find symbol: class Clue"

- [ ] **Step 3: Write minimal implementation**

```java

// src/main/java/com/example/striptkillgamedemo2/entity/mongo/Clue.java
package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.ClueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Clue entity representing static clue templates.
 * Multiple instances can be created during gameplay via GameClueInstance.
 *
 * Fields:
 * - type: TEXT or IMAGE clue type
 * - content: Text content for TEXT type clues
 * - imageUrl: Image URL for IMAGE type clues
 * - isInitialHidden: Whether this clue is hidden at game start
 * - stageNumber: Stage number when this clue becomes available
 * - searchableRoleIds: IDs of roles that can search for this clue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    private String content;

    private String imageUrl;

    private boolean isInitialHidden = true;

    private int stageNumber;

    private List<ObjectId> searchableRoleIds;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=ClueTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/mongo/Clue.java
git add src/test/java/com/example/striptkillgamedemo2/entity/mongo/ClueTest.java
git commit -m "feat: add Clue entity with searchableRoleIds"
```

---

### Task 10: Create Member embedded entity

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/mongo/Member.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/mongo/MemberTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/mongo/MemberTest.java
package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import static org.junit.jupiter.api.Assertions.*;

class MemberTest {

    @Test
    void memberEntityShouldHaveRequiredFields() {
        Member member = Member.builder()
            .userId(new ObjectId())
            .roleId(new ObjectId())
            .isAi(false)
            .isDm(false)
            .isOnline(true)
            .build();

        assertNotNull(member.getUserId());
        assertNotNull(member.getRoleId());
        assertFalse(member.isAi());
        assertFalse(member.isDm());
        assertTrue(member.isOnline());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=MemberTest
```

Expected: FAIL with "cannot find symbol: class Member"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/mongo/Member.java
package com.example.striptkillgamedemo2.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Embedded;

/**
 * Member embedded entity representing a player or NPC in a game room.
 * Embedded within GameRoom.
 *
 * Fields:
 * - userId: User ID. Null for NPCs.
 * - roleId: Role ID this member is playing.
 * - isAi: Whether this member is controlled by AI.
 * - isDm: Whether this member is Dungeon Master.
 * - isOnline: Connection status for real-time features.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embedded
public class Member {
    private ObjectId userId;
    private ObjectId roleId;
    private boolean isAi = false;
    private boolean isDm = false;
    private boolean isOnline = true;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=MemberTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/mongo/Member.java
git add src/test/java/com/example/striptkillgamedemo2/entity/mongo/MemberTest.java
git commit -m "feat: add Member embedded entity for game rooms"
```

---

### Task 11: Create GameRoom entity

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/mongo/GameRoom.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/mongo/GameRoomTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/mongo/GameRoomTest.java
package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import com.example.striptkillgamedemo2.entity.enum.GameRoomStatus;

class GameRoomTest {

    @Test
    void gameRoomEntityShouldHaveRequiredFields() {
        GameRoom room = GameRoom.builder()
            .roomId(new ObjectId())
            .scriptId(new ObjectId())
            .status(GameRoomStatus.WAITING)
            .currentStage(0)
            .members(List.of())
            .startTime(LocalDateTime.now())
            .configuration("{}")
            .build();

        assertNotNull(room.getRoomId());
        assertNotNull(room.getScriptId());
        assertEquals(GameRoomStatus.WAITING, room.getStatus());
        assertEquals(0, room.getCurrentStage());
        assertNotNull(room.getStartTime());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=GameRoomTest
```

Expected: FAIL with "cannot find symbol: class GameRoom"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/mongo/GameRoom.java
package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GameRoom entity representing lightweight room metadata.
 * Heavy runtime state is stored in Redis.
 *
 * Fields:
 * - scriptId: Reference to the script being played
 * - status: WAITING, PLAYING, or FINISHED
 * - currentStage: Current game stage number
 * - members: List of players and NPCs in this room
 * - startTime: When game started (for audit)
 * - endTime: When game ended (for audit)
 * - configuration: Game-specific configuration overrides
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "game_rooms")
public class GameRoom {
    @Id
    private ObjectId roomId;

    @Indexed
    private ObjectId scriptId;

    private GameRoomStatus status = GameRoomStatus.WAITING;

    private int currentStage = 0;

    private List<Member> members;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String configuration;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=GameRoomTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/mongo/GameRoom.java
git add src/test/java/com/example/striptkillgamedemo2/entity/mongo/GameRoomTest.java
git commit -m "feat: add GameRoom entity with audit timestamps"
```

---

### Task 12: Create GameRecord entity

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/mongo/GameRecord.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/mongo/GameRecordTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/mongo/GameRecordTest.java
package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GameRecordTest {

    @Test
    void gameRecordEntityShouldHaveRequiredFields() {
        GameRecord record = GameRecord.builder()
            .roomId(new ObjectId())
            .scriptTitle("Mystery at the Mansion")
            .winnerUserIds(List.of(new ObjectId()))
            .winnerRoleIds(List.of(new ObjectId()))
            .aiSummary("The killer was revealed")
            .fullChatLog(List.of("Player 1: I found a clue"))
            .startTime(LocalDateTime.now())
            .endTime(LocalDateTime.now())
            .build();

        assertEquals("Mystery at the Mansion", record.getScriptTitle());
        assertEquals("The killer was revealed", record.getAiSummary());
        assertNotNull(record.getWinnerUserIds());
        assertNotNull(record.getWinnerRoleIds());
        assertNotNull(record.getStartTime());
        assertNotNull(record.getEndTime());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=GameRecordTest
```

Expected: FAIL with "cannot find symbol: class GameRecord"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/mongo/GameRecord.java
package com.example.striptkillgamedemo2.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GameRecord entity representing persistent summary created after a game ends.
 * Used for history and analysis.
 *
 * Fields:
 * - roomId: Reference to the game room
 * - scriptTitle: Title of the script played
 * - winnerUserIds: IDs of winning users
 * - winnerRoleIds: IDs of winning roles
 * - aiSummary: AI-generated summary of the game
 * - fullChatLog: Key messages from game log
 * - startTime: When game started
 * - endTime: When game ended
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "game_records")
public class GameRecord {
    @Id
    private ObjectId recordId;

    @Indexed
    private ObjectId roomId;

    private String scriptTitle;

    private List<ObjectId> winnerUserIds;

    private List<ObjectId> winnerRoleIds;

    private String aiSummary;

    private List<String> fullChatLog;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=GameRecordTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/mongo/GameRecord.java
git add src/test/java/com/example/striptkillgamedemo2/entity/mongo/GameRecordTest.java
git commit -m "feat: add GameRecord entity for game summaries"
```

---

### Task 13: Create GameClueInstance Redis DTO

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/redis/GameClueInstance.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/redis/GameClueInstanceTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/redis/GameClueInstanceTest.java
package com.example.striptkillgamedemo2.entity.redis;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GameClueInstanceTest {

    @Test
    void gameClueInstanceShouldHaveRequiredFields() {
        GameClueInstance instance = GameClueInstance.builder()
            .id("game:123:clue:456")
            .clueId(new ObjectId())
            .ownerRoleIds(List.of(new ObjectId()))
            .isPublic(false)
            .isFound(true)
            .discoveredAt(LocalDateTime.now())
            .build();

        assertEquals("game:123:clue:456", instance.getId());
        assertNotNull(instance.getClueId());
        assertFalse(instance.isPublic());
        assertTrue(instance.isFound());
        assertNotNull(instance.getDiscoveredAt());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=GameClueInstanceTest
```

Expected: FAIL with "cannot find symbol: class GameClueInstance"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/redis/GameClueInstance.java
package com.example.striptkillgamedemo2.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GameClueInstance representing dynamic state of a clue during gameplay.
 * Stored in Redis with key format: game:{roomId}:clue:{clueId}
 * Not persisted after game ends.
 *
 * Fields:
 * - id: Redis key
 * - clueId: Reference to Clue template
 * - ownerRoleIds: Roles currently holding this clue (multiple allowed)
 * - isPublic: Whether this clue is revealed to all players
 * - isFound: Whether this clue has been discovered by anyone
 * - discoveredAt: Timestamp when this clue was discovered (for audit)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameClueInstance {
    private String id;
    private ObjectId clueId;
    private List<ObjectId> ownerRoleIds;
    private boolean isPublic;
    private boolean isFound = false;
    private LocalDateTime discoveredAt;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=GameClueInstanceTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/redis/GameClueInstance.java
git add src/test/java/com/example/striptkillgamedemo2/entity/redis/GameClueInstanceTest.java
git commit -m "feat: add GameClueInstance Redis DTO with discoveredAt"
```

---

### Task 14: Create GameMessage Redis DTO

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/redis/GameMessage.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/redis/GameMessageTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/redis/GameMessageTest.java
package com.example.striptkillgamedemo2.entity.redis;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GameMessageTest {

    @Test
    void gameMessageShouldHaveRequiredFields() {
        GameMessage message = GameMessage.builder()
            .messageId("msg:123")
            .gameRoomId(new ObjectId())
            .senderRoleId(new ObjectId())
            .senderUserId(new ObjectId())
            .content("I found a clue!")
            .receiverRoleIds(List.of(new ObjectId()))
            .timestamp(LocalDateTime.now())
            .build();

        assertEquals("msg:123", message.getMessageId());
        assertNotNull(message.getGameRoomId());
        assertNotNull(message.getSenderRoleId());
        assertEquals("I found a clue!", message.getContent());
        assertNotNull(message.getReceiverRoleIds());
        assertNotNull(message.getTimestamp());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=GameMessageTest
```

Expected: FAIL with "cannot find symbol: class GameMessage"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/redis/GameMessage.java
package com.example.striptkillgamedemo2.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GameMessage representing real-time game messages during gameplay.
 * Stored in Redis List with key: game:{roomId}:messages
 *
 * Fields:
 * - messageId: Unique message identifier
 * - gameRoomId: Reference to the game room
 * - senderRoleId: Role ID of the sender
 * - senderUserId: User ID of the sender (null for NPCs)
 * - content: Message content
 * - receiverRoleIds: Roles authorized to view this message
 * - timestamp: When the message was sent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {
    private String messageId;
    private ObjectId gameRoomId;
    private ObjectId senderRoleId;
    private ObjectId senderUserId;
    private String content;
    private List<ObjectId> receiverRoleIds;
    private LocalDateTime timestamp;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=GameMessageTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/redis/GameMessage.java
git add src/test/java/com/example/striptkillgamedemo2/entity/redis/GameMessageTest.java
git commit -m "feat: add GameMessage Redis DTO for real-time chat"
```

---

### Task 15: Create VoteRecord Redis DTO

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/entity/redis/VoteRecord.java`
- Test: `src/test/java/com/example/striptkillgamedemo2/entity/redis/VoteRecordTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/example/striptkillgamedemo2/entity/redis/VoteRecordTest.java
package com.example.striptkillgamedemo2.entity.redis;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class VoteRecordTest {

    @Test
    void voteRecordShouldHaveRequiredFields() {
        VoteRecord vote = VoteRecord.builder()
            .id("vote:123")
            .gameRoomId(new ObjectId())
            .voterUserId(new ObjectId())
            .stageNumber(2)
            .votedRoleId(new ObjectId())
            .voteCategory("elimination")
            .voteReason("Suspicious behavior")
            .voteWeight(1)
            .timestamp(LocalDateTime.now())
            .build();

        assertEquals("vote:123", vote.getId());
        assertNotNull(vote.getGameRoomId());
        assertNotNull(vote.getVoterUserId());
        assertEquals(2, vote.getStageNumber());
        assertNotNull(vote.getVotedRoleId());
        assertEquals("elimination", vote.getVoteCategory());
        assertEquals("Suspicious behavior", vote.getVoteReason());
        assertEquals(1, vote.getVoteWeight());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=VoteRecordTest
```

Expected: FAIL with "cannot find symbol: class VoteRecord"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/striptkillgamedemo2/entity/redis/VoteRecord.java
package com.example.striptkillgamedemo2.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;

/**
 * VoteRecord representing voting records during gameplay.
 * Stored in Redis List with key: game:{roomId}:votes
 *
 * Fields:
 * - id: Vote record identifier
 * - gameRoomId: Reference to the game room
 * - voterUserId: User ID of the voter
 * - stageNumber: Current stage number
 * - votedRoleId: Role ID being voted for
 * - voteCategory: Optional category/type of vote
 * - voteReason: Optional reason for the vote
 * - voteWeight: Optional vote weight for weighted voting
 * - timestamp: When the vote was cast
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteRecord {
    private String id;
    private ObjectId gameRoomId;
    private ObjectId voterUserId;
    private int stageNumber;
    private ObjectId votedRoleId;
    private String voteCategory;
    private String voteReason;
    private Integer voteWeight;
    private LocalDateTime timestamp;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=VoteRecordTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/entity/redis/VoteRecord.java
git add src/test/java/com/example/striptkillgamedemo2/entity/redis/VoteRecordTest.java
git commit -m "feat: add VoteRecord Redis DTO with flexible voting"
```

---

### Task 16: Run all tests

**Files:**
- Test: All entity tests

- [ ] **Step 1: Run all entity tests**

```bash
mvn test
```

Expected: All tests pass

- [ ] **Step 2: Verify coverage**

```bash
mvn test jacoco:report
```

Expected: All entity classes have test coverage

- [ ] **Step 3: Final commit**

```bash
git add .
git commit -m "test: ensure all entity tests pass"
```

---

## Summary

This plan creates all entity classes for the AI Murder Mystery Game system:

### Enums (3)
1. GameRoomStatus - WAITING, PLAYING, FINISHED
2. ScriptDifficulty - EASY, NORMAL, HARD, EXPERT
3. ClueType - TEXT, IMAGE

### MongoDB Entities (8)
1. User - Player accounts with BCrypt password hashing
2. Script - Game templates with version and configuration
3. ScriptStage - Embedded script stages
4. Role - Character definitions
5. Clue - Static clue templates
6. GameRoom - Room metadata with audit timestamps
7. Member - Embedded room members
8. GameRecord - Game summaries

### Redis DTOs (3)
1. GameClueInstance - Runtime clue state
2. GameMessage - Real-time messages
3. VoteRecord - Voting records

### Total: 16 bite-sized tasks with TDD approach

Each task follows the test-driven development cycle:
1. Write failing test
2. Verify test fails
3. Implement minimal code
4. Verify test passes
5. Commit

All tasks are self-contained and can be executed independently.
