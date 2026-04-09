# triggerRoundRobinSpeech 工具设计

## 背景

DM 在首幕需要触发所有 AI 角色轮流自我介绍，在末幕需要触发最终陈述。当前的 `selectRespondents`（上限 2 人）和 `assignTurn`（仅修改状态不触发 agent）都无法满足需求。更根本的问题是：`chatModel.call()` 是单次请求-响应，DM 无法在一次调用中驱动多轮 agent 发言并等待完成。

## 方案

新增 DM 工具 `triggerRoundRobinSpeech`，一次调用触发所有存活 AI 角色轮流发言，完成后自动回调 DM 继续流程。

## 新工具：TriggerRoundRobinSpeechTool

### 输入

```java
class Input {
    String instruction; // 发言指令，如"请进行自我介绍"或"这是最终陈述，请复盘和辩解"
}
```

### 执行流程

1. 从 `ctx.getRoom()` 收集所有存活 AI 角色的 roleId（排除 `eliminatedRoleIds` 和 DM）
2. 同步逐个调用 `AgentExecutor.executeAgentReplySync(roomId, roleId, instruction)` — 不走 `@Async`，保证串行
3. 全部完成后，调用 `dmExecutor.executeDmAction(roomId, null, "所有AI角色已完成「{instruction}」，请继续推进流程。如需等待真人玩家发言请提醒他们，否则请使用 transitionPhase 推进。")`
4. 设置 `ctx.setAgentDelegated(true)` 抑制当前 DM 文本输出
5. 返回 `{success: true, triggered: N, instruction: "..."}`

### allowedPhases

返回空集（不限制），因为首幕开场可能在任意 phase 类型下调用。

## AgentExecutor 改动

新增同步公开方法：

```java
public void executeAgentReplySync(String roomId, ObjectId roleId, String extraInstruction)
```

- 与现有 `doExecuteAgentReply` 逻辑相同（构建 prompt → 调用 LLM → 流式推送 → 存储消息 → 发布事件）
- 新增：将 `extraInstruction` 拼接到 agent prompt 末尾作为 `【系统指令】`
- 无 `@Async` 注解，调用者线程内同步执行
- `doExecuteAgentReply` 内部可复用此方法（传 `extraInstruction=null`）

## Prompt 改动

### dm-system.md 工具表

新增一行：

| triggerRoundRobinSpeech | 任意环节 | 触发所有存活AI角色轮流发言（如自我介绍、最终陈述），完成后自动回调DM |

### dm-stage-first.md

第二步"角色自我介绍"改为：调用 `triggerRoundRobinSpeech`，instruction 为自我介绍要求。

### dm-stage-last.md

第三步"最终陈述"改为：调用 `triggerRoundRobinSpeech`，instruction 为最终陈述要求。

## 调用时序

```
GameFlowService.startGame()
  → dmExecutor.executeDmAction("游戏刚刚开始...")     [async, aiExecutor 线程]
    → chatModel.call()                                [阻塞]
      → DM 输出开场白
      → DM 调用 triggerRoundRobinSpeech({instruction: "请进行自我介绍..."})
        → 工具同步执行（在同一线程内）：
          Agent1.executeAgentReplySync() → 发言完成
          Agent2.executeAgentReplySync() → 发言完成
          ...
          AgentN.executeAgentReplySync() → 发言完成
        → dmExecutor.executeDmAction("所有AI角色已完成自我介绍...")  [async, 新线程]
        → ctx.setAgentDelegated(true)
        → 返回工具结果给 LLM
      → LLM 返回最终响应
    → DM 检查 agentDelegated=true，抑制文本，退出
  
  [新的 aiExecutor 线程]
  → DM 被回调："所有AI角色已完成自我介绍，请继续推进流程"
    → DM 提醒真人玩家自我介绍 / 使用 transitionPhase 进入正式环节
```

## 改动清单

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `TriggerRoundRobinSpeechTool.java` | 新建 | DM 工具实现 |
| `AgentExecutor.java` | 修改 | 新增 `executeAgentReplySync()` 公开方法，重构 `doExecuteAgentReply` 复用 |
| `dm-system.md` | 修改 | 工具表新增 triggerRoundRobinSpeech |
| `dm-stage-first.md` | 修改 | 自我介绍改用 triggerRoundRobinSpeech |
| `dm-stage-last.md` | 修改 | 最终陈述改用 triggerRoundRobinSpeech |

## 不改动的部分

- `SelectRespondentsTool` — 不变，仍用于 FREE_CHAT 选 1-2 回复者
- `AssignTurnTool` — 不变，保留 AI agent 触发逻辑供 TURN_BASED 使用
- `AgentOrchestrator` — 不变
- `GameFlowService` — 不变，DM 触发 reason 不需要修改
- `DmToolRegistry` / `DmToolContext` — 不变，新工具自动被 Spring 扫描注册
