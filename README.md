# 剧本杀 AI — Script Kill Web Game

一款支持 AI NPC 和 AI 主持人（DM）的在线剧本杀 Web 游戏。玩家可以创建/加入房间，阅读剧本，搜证调查，讨论推理，投票指认凶手。AI Agent 自动填充空缺角色，AI DM 全程主持游戏流程。

## 技术栈

| 层 | 技术 |
|---|------|
| 后端 | Spring Boot 3.5 / Java 17 / Maven |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus |
| 数据库 | MongoDB（持久化） + Redis（运行时状态） |
| 实时通信 | WebSocket STOMP |
| AI | Spring AI + Anthropic 适配层（可对接火山引擎等兼容端点） |

## 已实现功能

### 用户系统
- 注册 / 登录（JWT 认证）
- 个人资料页

### 剧本与角色
- 剧本墙：浏览可用剧本列表
- 角色墙：查看剧本内角色信息
- 多幕剧本结构（Script → Stage → Phase）

### 房间与游戏
- 创建 / 加入游戏房间
- 房间内实时聊天（WebSocket）
- 完整的游戏阶段流转：
  - **读本阶段**（SCRIPT_READING）— 阅读角色剧本
  - **轮流发言**（TURN_BASED）— 按顺序轮转发言
  - **自由讨论**（FREE_CHAT）— 开放式讨论
  - **搜证调查**（INVESTIGATION）— 搜集线索
  - **投票指认**（VOTE）— 投票选出嫌疑人
- 阶段倒计时自动推进
- 投票系统（全员投票完成自动结算）

### AI 系统
- **AI DM（主持人）**：自动引导游戏流程、推进阶段、发起投票、分配线索
- **AI NPC 玩家**：自动填充空缺角色，根据角色人设进行对话和推理
- **智能响应路由**：三层触发策略
  1. `@角色名` 精确触发
  2. 消息中提及角色名触发（最多 2 个）
  3. DM 通过工具调用选择响应者
- **DM 工具集**：阶段转换、投票发起、线索分发、轮次分配、搜证授权等
- **记忆管理**：阶段级对话压缩 + 滑动窗口，控制上下文长度
- **流式输出**：LLM 回复通过 WebSocket 实时流式推送
- **游戏复盘**：AI 生成游戏回顾总结

## 快速开始

### 环境要求
- Java 17+
- Node.js 18+
- MongoDB
- Redis

### 启动后端
> 注意：需要配置 application.properties 文件
```bash
./mvnw spring-boot:run
```

### 启动前端
```bash
cd frontend
npm install
npm run dev
```

### 运行测试
```bash
./mvnw test
```

## 项目结构

```
├── src/main/java/.../
│   ├── ai/                  # AI 子系统
│   │   ├── orchestrator/    # 消息路由与 Agent 编排
│   │   ├── executor/        # Agent / DM 执行器
│   │   ├── tool/impl/       # DM 可调用工具
│   │   ├── prompt/          # 提示词构建
│   │   ├── memory/          # 上下文记忆管理
│   │   └── review/          # 游戏复盘
│   ├── controller/          # REST + WebSocket 端点
│   ├── service/             # 业务逻辑
│   ├── entity/              # 数据模型 (mongo/redis/enums)
│   ├── security/            # JWT 认证
│   └── config/              # 配置类
├── frontend/src/
│   ├── views/               # 页面组件
│   ├── stores/              # Pinia 状态管理
│   └── router/              # 路由配置
└── src/main/resources/
    └── prompts/             # AI 提示词模板
```
