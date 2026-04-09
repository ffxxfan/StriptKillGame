# triggerRoundRobinSpeech Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a DM tool that triggers all surviving AI agents to speak one-by-one, then auto-callbacks DM to continue the flow — enabling first-act introductions and last-act final statements.

**Architecture:** New `TriggerRoundRobinSpeechTool` (implements `DmTool`) calls a new synchronous method on `AgentExecutor` for each AI agent in sequence. After all agents finish, it fires an async DM callback and sets `agentDelegated=true`. Prompt templates are updated to reference the new tool.

**Tech Stack:** Spring Boot 3.5, Java 17, Spring AI tool-calling

---

### Task 1: Add synchronous agent execution method to AgentExecutor

**Files:**
- Modify: `src/main/java/com/example/striptkillgamedemo2/ai/executor/AgentExecutor.java`

- [ ] **Step 1: Add `executeAgentReplySync` public method**

Add this method to `AgentExecutor`. It reuses the same logic as `doExecuteAgentReply` but runs synchronously (no `@Async`) and accepts an optional `extraInstruction` to append to the agent prompt.

```java
/**
 * Synchronous agent reply — runs on caller's thread.
 * Used by DM tools that need to wait for agent completion before continuing.
 *
 * @param extraInstruction optional instruction appended to agent prompt as 【系统指令】, may be null
 */
public void executeAgentReplySync(String roomId, ObjectId roleId, String extraInstruction) {
    doExecuteAgentReply(roomId, roleId, false, extraInstruction);
}
```

- [ ] **Step 2: Refactor `doExecuteAgentReply` to accept `extraInstruction` parameter**

Change the existing private method signature from:

```java
private void doExecuteAgentReply(String roomId, ObjectId roleId, boolean lastAiRound) {
```

to:

```java
private void doExecuteAgentReply(String roomId, ObjectId roleId, boolean lastAiRound, String extraInstruction) {
```

Inside the method, after the `lastAiRound` block that appends redirect instructions (the `if (lastAiRound)` block), add:

```java
// Extra instruction from tool (e.g. "请进行自我介绍")
if (extraInstruction != null && !extraInstruction.isBlank()) {
    promptText += "\n\n【系统指令】" + extraInstruction;
}
```

- [ ] **Step 3: Fix all existing callers of `doExecuteAgentReply`**

Update every existing call site to pass `null` as the new parameter:

In `executeAgentReply(String roomId, ObjectId roleId)`:
```java
doExecuteAgentReply(roomId, roleId, false, null);
```

In `executeAgentReply(String roomId, ObjectId roleId, boolean lastAiRound)`:
```java
doExecuteAgentReply(roomId, roleId, lastAiRound, null);
```

In `executeAgentRepliesSequentially(String roomId, List<ObjectId> roleIds)`:
```java
doExecuteAgentReply(roomId, roleId, false, null);
```

In `executeAgentRepliesSequentially(String roomId, List<ObjectId> roleIds, boolean lastAiRound)`:
```java
doExecuteAgentReply(roomId, roleIds.get(i), isLast, null);
```

- [ ] **Step 4: Compile and verify**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS, no errors

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/executor/AgentExecutor.java
git commit -m "refactor: add synchronous executeAgentReplySync with extraInstruction support"
```

---

### Task 2: Create TriggerRoundRobinSpeechTool

**Files:**
- Create: `src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/TriggerRoundRobinSpeechTool.java`

- [ ] **Step 1: Create the tool class**

```java
package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerRoundRobinSpeechTool implements DmTool {

    private final AgentExecutor agentExecutor;
    private final DmExecutor dmExecutor;

    @Data
    public static class Input {
        /** 发言指令，如"请进行自我介绍"或"这是最终陈述，请复盘和辩解" */
        private String instruction;
    }

    @Override
    public String name() {
        return "triggerRoundRobinSpeech";
    }

    @Override
    public String description() {
        return "触发所有存活AI角色轮流发言（如自我介绍、最终陈述）。传入发言指令，所有AI角色将依次发言，完成后自动回调DM继续流程。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        LiveGameRoom room = ctx.getRoom();
        String roomId = room.getRoomId();
        String instruction = input.getInstruction();

        // Collect all surviving AI role IDs (exclude DM and eliminated)
        List<ObjectId> aiRoleIds = room.getMembers().stream()
                .filter(m -> m.isAi() && !m.isDm())
                .map(Member::getRoleId)
                .filter(Objects::nonNull)
                .filter(roleId -> !room.getEliminatedRoleIds().contains(roleId.toHexString()))
                .toList();

        if (aiRoleIds.isEmpty()) {
            log.info("[triggerRoundRobinSpeech] no AI agents to trigger in room={}", roomId);
            return Map.of("success", true, "triggered", 0, "instruction", instruction);
        }

        log.info("[triggerRoundRobinSpeech] triggering {} AI agents in room={}, instruction='{}'",
                aiRoleIds.size(), roomId, instruction);

        // Synchronously execute each agent on the current thread
        for (ObjectId roleId : aiRoleIds) {
            agentExecutor.executeAgentReplySync(roomId, roleId, instruction);
        }

        log.info("[triggerRoundRobinSpeech] all {} agents completed in room={}", aiRoleIds.size(), roomId);

        // Async callback: re-trigger DM to continue the flow
        dmExecutor.executeDmAction(roomId, null,
                "所有AI角色已完成「" + instruction + "」。请继续推进流程。" +
                "如需等待真人玩家发言请提醒他们，否则请使用 transitionPhase 推进到下一环节。");

        // Suppress DM's current text output — agents already spoke
        ctx.setAgentDelegated(true);

        return Map.of("success", true, "triggered", aiRoleIds.size(), "instruction", instruction);
    }
}
```

- [ ] **Step 2: Compile and verify**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS. Spring auto-discovers the `@Component` and `DmToolRegistry` picks it up via `List<DmTool>` injection.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/striptkillgamedemo2/ai/tool/impl/TriggerRoundRobinSpeechTool.java
git commit -m "feat: add triggerRoundRobinSpeech DM tool for round-robin AI speech"
```

---

### Task 3: Update DM prompt templates

**Files:**
- Modify: `src/main/resources/prompts/dm-system.md`
- Modify: `src/main/resources/prompts/dm-stage-first.md`
- Modify: `src/main/resources/prompts/dm-stage-last.md`

- [ ] **Step 1: Add tool to dm-system.md tool table**

In `dm-system.md`, find the tool table row:

```
| assignTurn | 仅在 TURN_BASED | 指定发言顺序 |
```

Add a new row after it:

```
| triggerRoundRobinSpeech | 任意环节 | 触发所有存活AI角色轮流发言（如自我介绍、最终陈述），完成后自动回调DM |
```

- [ ] **Step 2: Update dm-stage-first.md**

Replace the entire "第二步" section (lines 12-17):

```markdown
### 第二步：角色自我介绍（轮流进行）
1. 宣布"现在请各位角色依次进行自我介绍"
2. 使用 **assignTurn** 逐个指定 AI 角色进行自我介绍（每个角色介绍自己的公开身份、职业、与其他角色的关系等）
3. 每个 AI 角色介绍完毕后，再指定下一个
4. 所有 AI 角色介绍完毕后，提醒真人玩家进行自我介绍
5. 等待真人玩家自我介绍完毕（如果玩家长时间未介绍，可以温和催促）
```

with:

```markdown
### 第二步：角色自我介绍（轮流进行）
1. 宣布"现在请各位角色依次进行自我介绍"
2. 调用 **triggerRoundRobinSpeech** 工具，instruction 设为"请进行自我介绍，介绍你的公开身份、职业和与其他角色的关系"
3. 工具会自动触发所有 AI 角色逐个发言，完成后系统会自动回调你
4. 回调后，提醒真人玩家进行自我介绍
5. 等待真人玩家自我介绍完毕（如果玩家长时间未介绍，可以温和催促）
```

- [ ] **Step 3: Update dm-stage-last.md**

Replace the "第三步" section (lines 16-24):

```markdown
### 第三步：最终陈述（TURN_BASED）
正常流程结束后，进入最终陈述环节：
1. 宣布"现在进入**最终陈述**环节，请每位角色进行最后的发言"
2. 明确提醒所有角色：**这是游戏最后阶段的最终陈述**，要求：
   - 简要复盘自己在整个游戏中的行为和发现
   - 为自己辩解（如果被怀疑）
   - 指出自己认为的真凶及理由
3. 使用 **assignTurn** 逐个指定角色进行最终陈述
4. 所有角色陈述完毕后，提醒真人玩家进行最终陈述
```

with:

```markdown
### 第三步：最终陈述（TURN_BASED）
正常流程结束后，进入最终陈述环节：
1. 宣布"现在进入**最终陈述**环节，请每位角色进行最后的发言"
2. 调用 **triggerRoundRobinSpeech** 工具，instruction 设为"这是游戏最后阶段的最终陈述。请简要复盘你在整个游戏中的行为和发现，为自己辩解（如果被怀疑），并指出你认为的真凶及理由"
3. 工具会自动触发所有 AI 角色逐个发言，完成后系统会自动回调你
4. 回调后，提醒真人玩家进行最终陈述
```

- [ ] **Step 4: Compile and verify**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS (prompt files are classpath resources, no compile impact — just verify nothing else broke)

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/prompts/dm-system.md src/main/resources/prompts/dm-stage-first.md src/main/resources/prompts/dm-stage-last.md
git commit -m "docs: update DM prompts to use triggerRoundRobinSpeech tool"
```
