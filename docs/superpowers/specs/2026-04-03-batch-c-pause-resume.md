# Batch C: 暂停/恢复机制（待实现）

**Date:** 2026-04-03
**Status:** 未实现，待后续排期

---

## 问题

当前没有玩家请求暂停游戏的能力。如果真人玩家需要离开，计时器和流程推进无法暂停，DM 和 AI Agent 会继续推进游戏。

## 设计思路

### 暂停触发方式
- 玩家发送 `@DM 暂停` 或点击前端暂停按钮
- DM 通过新工具 `pauseGame` / `resumeGame` 控制

### LiveGameRoom 状态扩展
- `GameRoomStatus` 新增 `PAUSED` 状态
- `LiveGameRoom` 新增 `pausedAt: LocalDateTime` 和 `pausedBy: ObjectId` 字段

### 计时器处理
- 暂停时：`PhaseTimerService.cancelAllTimers(roomId)` 取消所有计时器，记录剩余时间到 `LiveGameRoom`
- 恢复时：用剩余时间重新启动计时器
- 需要 `LiveGameRoom` 新增 `remainingSeconds: int` 字段

### 流程推进阻断
- `DmToolRegistry.executeWithGuard()` 检查房间状态，PAUSED 时拒绝所有工具调用
- `AgentOrchestrator.onChatMessage()` 检查状态，PAUSED 时不路由消息

### WebSocket 信号
- `GAME_PAUSED` — 通知前端显示暂停 UI
- `GAME_RESUMED` — 通知前端恢复，附带剩余计时

### DM 工具
- `pauseGame` — 暂停游戏，广播信号，取消计时器
- `resumeGame` — 恢复游戏，广播信号，重启计时器

## 涉及文件（预估）

| 文件 | 变更 |
|------|------|
| `GameRoomStatus.java` | 新增 `PAUSED` |
| `LiveGameRoom.java` | 新增 `pausedAt`, `pausedBy`, `remainingSeconds` |
| `PhaseTimerService.java` | 新增暂停/恢复逻辑 |
| `DmToolRegistry.java` | PAUSED 状态拒绝工具调用 |
| `AgentOrchestrator.java` | PAUSED 状态不路由消息 |
| 新增 `PauseGameTool.java` | DM 暂停工具 |
| 新增 `ResumeGameTool.java` | DM 恢复工具 |
| `dm-system.md` | 新增暂停/恢复工具说明 |
| 前端 | 暂停按钮 + 暂停 UI overlay |
