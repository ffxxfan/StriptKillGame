# Runtime Enhancement Design — Batch B

**Date:** 2026-04-03
**Scope:** 计时器 key 粒度改造 + Agent 响应筛选 + 投票闭环

---

## 1. 计时器 key 粒度改造

### 现状

`PhaseTimerService` 用 `roomId` 作为 `timers` 和 `reminders` 的 ConcurrentHashMap key。同一房间只能有一个计时器，投票计时和环节计时会互相覆盖。

### 方案

key 改为 `roomId + ":" + phaseType.name()`，例如 `room123:VOTE`、`room123:FREE_CHAT`。

**API 变更：**

```java
// 之前
void startPhaseTimer(String roomId, PhaseType phaseType, int durationSeconds)
void cancelTimer(String roomId)

// 之后
void startPhaseTimer(String roomId, PhaseType phaseType, int durationSeconds)  // 不变
void cancelTimer(String roomId, PhaseType phaseType)                           // 新签名
void cancelAllTimers(String roomId)                                            // 新增
```

**行为变更：**
- `startPhaseTimer` 内部先 cancel 同 key 旧计时器，不影响其他 phaseType
- `startVoteTimer(roomId)` 删除，改为 `startPhaseTimer(roomId, VOTE, voteTimeoutSeconds)`
- `cancelTimer(roomId, phaseType)` 只取消特定类型
- `cancelAllTimers(roomId)` 遍历取消该房间所有计时器，用于游戏结束

**内部存储：**
```java
// key 格式: "roomId:PHASE_TYPE"
private String timerKey(String roomId, PhaseType phaseType) {
    return roomId + ":" + phaseType.name();
}
```

**调用方更新：**
- `TransitionPhaseTool` — `cancelTimer(roomId)` 改为 `cancelTimer(roomId, currentPhaseType)`
- `InitiateVoteTool` — 投票计时改用 `startPhaseTimer(roomId, VOTE, timeout)`
- `GameFlowService.endGame()` — 如果调用了 cancelTimer，改为 `cancelAllTimers`

---

## 2. Agent 响应筛选（混合策略）

### 现状

`AgentOrchestrator.handleFreeChat()` 无 @mention 时触发所有 AI Agent 顺序发言，多角色场景下造成冗长对话。

### 方案

三层匹配策略，在 `handleFreeChat` 中按优先级执行：

**第一层：@mention 匹配（已有）**
- 检测 `@角色名`，直接触发对应 AI 角色

**第二层：角色名提及匹配（新增）**
- 扫描消息内容，检查是否包含任何 AI 角色名（不含 @ 前缀）
- 匹配到的角色触发回复，最多 2 个
- 例如："我觉得苏婉很可疑" → 触发苏婉回复

**第三层：DM 筛选兜底（新增）**
- 前两层都没匹配到时，调用 `dmExecutor.executeDmAction()` 让 DM 使用 `selectRespondents` 工具选择 1-2 个相关角色
- DM 提示词：告知消息内容和候选 AI 角色列表，要求选择最相关的 1-2 个

**代码逻辑：**
```java
private void handleFreeChat(String roomId, LiveGameRoom room, Script script,
                             String content, ObjectId senderRoleId) {
    // Layer 1: @mention (already exists)
    List<ObjectId> mentionedIds = findMentionedAiRoles(content, script, room);
    if (!mentionedIds.isEmpty()) {
        mentionedIds.forEach(id -> agentExecutor.executeAgentReply(roomId, id));
        return;
    }

    // Layer 2: role name in text
    List<ObjectId> namedIds = findNamedAiRoles(content, script, room);
    if (!namedIds.isEmpty()) {
        namedIds.stream().limit(2).forEach(id -> agentExecutor.executeAgentReply(roomId, id));
        return;
    }

    // Layer 3: DM decides
    List<String> candidateNames = getAiRoleNames(script, room);
    dmExecutor.executeDmAction(roomId, senderRoleId,
        "玩家说：「" + content + "」。请使用 selectRespondents 工具从以下角色中选择 1-2 个最相关的回复：" + candidateNames);
}
```

**辅助方法：**
- `findMentionedAiRoles(content, script, room)` — 提取 @mention 中的 AI 角色 ID
- `findNamedAiRoles(content, script, room)` — 扫描文本中出现的 AI 角色名
- `getAiRoleNames(script, room)` — 获取所有 AI 角色名列表

现有的 @DM/@主持人 检测和 @mention 循环逻辑将被重构进这些方法中。

---

## 3. 投票闭环

### 现状

- `InitiateVoteTool` 发起投票，广播 `VOTE_OPEN`
- `VoteService.castVote()` 记录投票，检测全员投完返回 boolean
- `VoteService.closeVote()` 清除 activeVote，广播 `VOTE_CLOSED`
- `PhaseTimerService.startVoteTimer()` 超时广播 `VOTE_CLOSED`
- **缺失：** 全员投完/超时后无自动关闭、无结果通知 DM、AI 不投票

### 方案

#### 3a. 自动关闭 + 通知 DM

**`VoteService.castVote()` 改造：**
- 全员投完（返回 true）时，自动调用 `closeVote(roomId)`
- 关闭后调用 `dmExecutor.executeDmAction()` 发送投票结果给 DM
- DM 提示词："投票已结束，结果如下：{results}。请宣布投票结果，然后使用 transitionPhase 工具推进流程。"

**投票超时改造：**
- `PhaseTimerService` 投票超时回调中：调用 `VoteService.closeVote()` + `dmExecutor.executeDmAction()`
- 删除 `startVoteTimer` 方法（合并到 `startPhaseTimer`），超时回调统一走 DM 通知路径

**`VoteService` 新增依赖：** `DmExecutor`（用于通知 DM）

#### 3b. AI 自动投票（LLM 驱动）

**投票发起后，AI 角色自动参与投票：**

- `InitiateVoteTool.execute()` 发起投票后，异步触发所有 AI 角色投票
- 新增 `AgentExecutor.executeAgentVote(roomId, roleId, voteTitle, options)` 方法
- 该方法给 AI Agent 发一条消息："投票主题：{title}，选项：{options}。根据你的角色身份和已知信息，选择一个选项并说明理由。请只回复选项内容。"
- AI 的回复解析为投票选项，调用 `VoteService.castVote()`
- 如果解析失败（AI 回复不在选项中），随机选一个兜底

**执行顺序：**
1. `InitiateVoteTool` 发起投票 → 广播 VOTE_OPEN
2. 异步触发每个 AI 角色投票（顺序执行，避免并发问题）
3. 真人玩家通过前端 UI 投票
4. 全员投完 → 自动关闭 → 通知 DM
5. DM 宣布结果 → transitionPhase 推进

---

## 文件变更清单

| 文件 | 变更 |
|------|------|
| `PhaseTimerService.java` | key 改为 `roomId:phaseType`，删除 `startVoteTimer`，`cancelTimer` 新签名，新增 `cancelAllTimers`，投票超时调用 VoteService+DM |
| `AgentOrchestrator.java` | 重构 `handleFreeChat` 为三层匹配，提取辅助方法 |
| `VoteService.java` | `castVote` 全员投完自动关闭+通知 DM，注入 DmExecutor |
| `AgentExecutor.java` | 新增 `executeAgentVote` 方法 |
| `TransitionPhaseTool.java` | `cancelTimer` 调用改为新签名 |
| `InitiateVoteTool.java` | 发起投票后异步触发 AI 投票 |

## 不在本批次范围内

- PRIVATE_TALK 密谈路由实现
- 暂停/恢复机制（批次 C）
- Redis 持久化计时器
- 投票结果对游戏结局的自动判定（DM 手动解读）
