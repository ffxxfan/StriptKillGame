# Phase Model Enhancement Design — Batch A

**Date:** 2026-04-03
**Scope:** PhaseType 扩展 + NEXT_STAGE 跳过校验 + summarize 兜底压缩

---

## 1. PhaseType 枚举扩展

### 现状

`PhaseType` 只有 3 个值：`TURN_BASED`, `FREE_CHAT`, `VOTE`。搜证被塞在 `FREE_CHAT` 里通过工具触发，阅读剧本、密谈、最终陈述没有独立阶段。

### 方案

扩展为 7 个值，每个携带 `defaultRequired` 属性：

```java
public enum PhaseType {
    SCRIPT_READING(false),    // 阅读剧本
    TURN_BASED(false),        // 轮流发言
    FREE_CHAT(false),         // 自由讨论
    INVESTIGATION(false),     // 搜证
    PRIVATE_TALK(false),      // 密谈
    FINAL_STATEMENT(false),   // 最终陈述
    VOTE(true);               // 投票（默认必须完成）

    private final boolean defaultRequired;

    PhaseType(boolean defaultRequired) {
        this.defaultRequired = defaultRequired;
    }

    public boolean isDefaultRequired() {
        return defaultRequired;
    }
}
```

### 各阶段行为定义

| PhaseType | 消息路由 | 计时器 | AI Agent 行为 | DM 工具限制 |
|-----------|---------|--------|--------------|-------------|
| SCRIPT_READING | 不路由，静默阅读 | 有（剧本定义时长） | 不发言 | pushStageContent 可用 |
| TURN_BASED | 按 speakOrder 轮流 | 有 | 轮到时发言 | assignTurn 可用 |
| FREE_CHAT | 自由发言 | 有 | 通过 selectRespondents 筛选回复 | selectRespondents, initiateVote 可用 |
| INVESTIGATION | 搜证请求路由到 DM | 有 | 不主动发言，等搜证结果 | authorizeSearch 可用 |
| PRIVATE_TALK | 仅参与密谈的角色间路由 | 有 | 被指定时发言 | 无特殊工具 |
| FINAL_STATEMENT | 按 speakOrder 轮流 | 有 | 轮到时发言 | assignTurn 可用 |
| VOTE | 不路由聊天 | 有（投票超时） | AI 自动投票 | initiateVote 可用 |

### 影响面

**PhaseTimerService：**
- `getDefaultDuration()` 增加新分支，新增配置项：`scriptReadingTimeoutSeconds`, `investigationTimeoutSeconds`, `privateTalkTimeoutSeconds`, `finalStatementTimeoutSeconds`
- `phaseLabel()` 增加中文标签

**AgentOrchestrator.onChatMessage()：**
- `SCRIPT_READING`: 不触发任何 Agent 回复，消息静默
- `INVESTIGATION`: 搜证关键词路由到 DM，其他消息不触发 Agent
- `PRIVATE_TALK`: 仅路由给密谈参与者（预留接口，具体实现放批次 B）
- `FINAL_STATEMENT`: 复用 TURN_BASED 逻辑

**AuthorizeSearchTool.allowedPhases()：**
- 从 `Set.of(PhaseType.FREE_CHAT)` 改为 `Set.of(PhaseType.INVESTIGATION)`

**dm-system.md：**
- 工具表增加新环节的对应关系

---

## 2. NEXT_STAGE 跳过校验

### 现状

`TransitionPhaseTool.executeNextStage()` 可以无条件跳过当前幕的所有剩余环节，没有任何校验。

### 方案

在 `executeNextStage()` 开头加入校验逻辑：

1. 获取当前幕从 `currentPhaseIndex` 到末尾的所有未执行 phase
2. 过滤出 `phase.getType().isDefaultRequired() == true` 的 phase
3. 如果存在必须完成的环节，返回错误并列出需要先完成的环节名称
4. 如果全部非必须，正常推进

```java
private Object executeNextStage(DmToolContext ctx, String reason) {
    LiveGameRoom room = ctx.getRoom();
    List<ScriptStage> stages = ctx.getScript().getStages();
    int currentStage = room.getCurrentStage();

    // 校验必须完成的环节
    if (stages != null && currentStage < stages.size()) {
        ScriptStage stage = stages.get(currentStage);
        if (stage.getPhases() != null) {
            List<String> requiredSkipped = stage.getPhases().stream()
                .skip(room.getCurrentPhaseIndex())
                .filter(p -> p.getType().isDefaultRequired())
                .map(p -> p.getType().name())
                .toList();
            if (!requiredSkipped.isEmpty()) {
                return Map.of("success", false,
                    "error", "以下必须环节未完成，不能跳过: " + requiredSkipped,
                    "hint", "请先使用 NEXT_PHASE 推进完成这些环节");
            }
        }
    }

    // ... 原有逻辑
}
```

### 未来扩展预留

当后续在 `StagePhase` 上加 `required` 字段时，校验逻辑改为：

```java
boolean isRequired = phase.getRequired() != null
    ? phase.getRequired()
    : phase.getType().isDefaultRequired();
```

当前阶段不加 `StagePhase.required` 字段，仅靠枚举默认值。

---

## 3. summarize 兜底压缩

### 现状

`SummarizeCurrentStageTool` 完全依赖 DM 主动调用。如果 DM（LLM）忘记调用，该幕摘要缺失。

### 方案

**MemoryManager 新增方法：**

```java
public boolean hasSummary(String roomId, int stageNumber) {
    String key = MEMORY_KEY_PREFIX + roomId + MEMORY_KEY_INFIX + stageNumber;
    return Boolean.TRUE.equals(redisTemplate.hasKey(key));
}
```

**TransitionPhaseTool.executeNextStage() 增加兜底：**

在通过校验、即将推进到下一幕之前：
1. 调用 `memoryManager.hasSummary(roomId, currentStage)`
2. 如果没有摘要，异步调用 `memoryManager.compressStage(roomId, currentStage, stageMessages)`
3. 日志标记 `[fallback compression]`
4. 不阻塞 NEXT_STAGE 推进

**新增依赖：**
- `TransitionPhaseTool` 注入 `MemoryManager`（`LiveGameRoomService` 已有）
- `TransitionPhaseTool` 注入 `ObjectMapper` 用于反序列化消息（复用已有的 `deserializeMessages` 模式）

```java
// 在 executeNextStage 中，推进前
if (!memoryManager.hasSummary(room.getRoomId(), currentStage)) {
    log.info("[transitionPhase] fallback compression for room={}, stage={}",
            room.getRoomId(), currentStage);
    List<GameMessage> msgs = deserializeMessages(
            liveGameRoomService.getMessages(new ObjectId(room.getRoomId())));
    memoryManager.compressStage(room.getRoomId(), currentStage, msgs);
}
```

---

## 文件变更清单

| 文件 | 变更 |
|------|------|
| `PhaseType.java` | 重写：7 个枚举值 + `defaultRequired` 属性 |
| `TransitionPhaseTool.java` | 修改：注入 MemoryManager，加跳过校验 + 兜底压缩 |
| `MemoryManager.java` | 新增 `hasSummary()` 方法 |
| `PhaseTimerService.java` | 扩展 `getDefaultDuration()` 和 `phaseLabel()` 的 switch 分支 |
| `AgentOrchestrator.java` | 扩展 `onChatMessage()` 对新阶段的路由逻辑 |
| `AuthorizeSearchTool.java` | `allowedPhases()` 改为 `INVESTIGATION` |
| `AiEngineProperties.java` | 新增 4 个超时配置项 |
| `dm-system.md` | 更新工具/环节对应表 |

## 不在本批次范围内

- `StagePhase.required` 字段（批次 A 仅用枚举默认值）
- `PRIVATE_TALK` 的密谈路由实现（留接口，具体实现放后续批次）
- 计时器 key 改造（批次 B）
- Agent 响应筛选策略（批次 B）
- 投票闭环（批次 B）
- 暂停/恢复机制（批次 C）
