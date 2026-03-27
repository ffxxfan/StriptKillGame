# Script prompt

1. Goal

   > 构建一个支持多人参与的剧本杀游戏系统，玩家可以与多个 AI Agent 一起进行游戏，由 AI DM 控制游戏进程。

2. 架构

   > 单体架构 - Spring Boot 应用整合所有功能模块，通过 WebSocket 与前端 Vue.js 实时通信，使用 Redis 缓存游戏数据，MongoDB 持久化用户和游戏记录。

3. 技术栈

   > Spring Boot 3.x, Spring AI, Spring WebSocket, Spring Data Redis, Spring Data MongoDB, Vue 3, Element Plus

4. 功能实现

   * Stage 1

     > * AuthController 用户注册、登录、登出、修改密码
     > * GameController 游戏创建、用户选择剧本、用户选择角色、分配其他角色 Agent，分配 DM，开始游戏、游戏发言、收集证据、游戏投票、游戏结束、DM复盘、游戏结果生成并存入 MagoDB，房间关闭

## 代码生成

1. Stage1 实体类

   > 我现在需要构建一个支持多人参与的剧本杀游戏系统，玩家可以与多个 AI Agent 一起进行游戏，由 AI DM 控制游戏进程，单体架构 - Spring Boot 应用整合所有功能模块，通过 WebSocket 与前端 Vue.js 实时通信，使用 Redis 缓存游戏数据，MongoDB 持久化用户和游戏记录。需要包含以下功能：用户注册、登录、登出、修改密码、游戏创建、用户选择剧本、用户选择角色、分配其他角色 Agent（包含头像），分配 DM，开始游戏、游戏发言、收集证据、游戏投票、游戏结束、DM复盘、游戏结果生成并存入 MagoDB，房间关闭、语音播报剧本内容 。其中剧本杀的剧本会包含多个角色的剧本和DM剧本和道具（可选），每个角色的剧本包含多幕数。线索可能分为公开线索和隐藏线索，同时线索可能包含图像或文字。你现在为我分析需要哪些实体类来存我的信息

   ```
   # Role: Senior Java Backend Engineer
   
   # Task: 根据业务需求设计并生成 Spring Boot 实体类 (Entity/Document)
   
   ### 1. 技术栈要求：
   
   - 框架：Spring Boot 3.x, Spring Data MongoDB
   - 语言：Java 17+
   - 插件：Lombok (@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor)
   - 校验：Jakarta Bean Validation (Hibernate Validator)
   - 序列化：Jackson (用于控制 JSON 输出)
   
   ### 2. 核心业务背景：
   
   我正在构建一个“AI 剧本杀系统”，支持多人+多 AI Agent 参与。系统需要处理：用户信息、剧本静态模板、游戏房间动态状态、搜证线索、以及游戏复盘记录。
   
   ### 3. 需要生成的实体列表及核心字段：
   
   游戏中发言等信息将由redis存储，游戏结束则对游戏中的信息进行简单总结生成 GameRecord 并存入MongoDB，其他关键信息使用 MongoDB 存储。请为我生成以下 Java 类，并根据“剧本杀”逻辑补充合理的关联关系，如果有不合理的地方请进行修改：
   
   1. **User (用户)**: id, username, password(JsonIgnore), nickname, avatarUrl, createdAt.
   2. **Script (剧本模板)**: id, title, description, difficulty(Enum), playerCount, coverImage, dmConfig(String/Map), stages(List<ScriptStage>).
   3. **ScriptStage (剧本阶段-内部类/嵌套)**: stageNumber, stageTitle, contentMap(Map<roleId, String>), audioUrl.
   4. **Role (剧本角色)**: id, scriptId, name, avatar, isNpc, prompt(AI 核心指令), secret(不可泄露的秘密), selfClueId（可以进行搜证的线索，可以包含多个），locationTag（搜证地点，可以为空），searchPower （(int) 搜证行动力，可选，用于限制搜证次数））。
   5. **Clue (线索模板)**: id, scriptId, title, type(TEXT/IMAGE), content, imageUrl, isInitialHidden(boolean), stageNumber,roleId（可以搜证的角色，可以包含多个）
   6. **GameRoom (游戏房间-运行态)**: roomId, scriptId, status(WAITING/PLAYING/FINISHED), currentStage(int), members(List<Member>).
   7. **Member (房间成员)**: userId, roleId, isAi(boolean), isDm(boolean), isOnline(boolean).
   8. **GameRecord (持久化记录)**: recordId, roomId, scriptTitle, winners, aiSummary, fullChatLog(List), endTime.
   9. GameRoom (房间动态) - `cluePool`: (Map<String, GameClueInstance>) 维护当前房间所有线索的状态。 - `stageLog`: 记录哪一幕解锁了哪些专属线索。-`GameMessagesIds` 用于记录房间中所有人的发言。`VoteRcords` 用于记录房间中所有的投票信息
   10. GameClueInstance (运行时线索实例 - Redis/Mongo) - `clueId`: 关联模板。 - `ownerRoleId`: 当前持有该线索的角色 ID。 - `isPublic`: 是否已公开给全场。 - `isFound`: 是否已被发现。
   11. GameMessages:gameRoomID,RoleID（发言者id）,userID,messages,receiverRoleId（可以看到发言者的玩家id）;
   12. VoteRcord：gameRoomID,userID,stageNumber,vote（role）
   
   ### 4. 代码规范要求：
   
   - 使用 MongoDB 的 `@Document` 和 `@Id` 注解。
   - 复杂的对象（如 Member）在 Room 中建议使用 `@Field` 嵌套或 `@DocumentReference`。
   - 枚举类：请为状态（Status）、难度（Difficulty）、线索类型（ClueType）生成独立的 Enum 类。
   - 包含必要的注释，说明字段在“剧本杀”流程中的作用。
   - 字段命名采用小驼峰 (camelCase)。
   
   请开始生成代码。
   ```

2. Stage2 登录登出功能

   > 我现在需要制作websocket 的登录登出，使用 Spring ai 架构，model 实体已经创建，现在需要写具体的用户登录、登出和修改密码这三部分，需要使用 jwt 加密验证，为后续功能实现提供验证（即用户登录才能使用其他功能）,需要同时包含前端和后端，现在你告诉我应该使用什么技术比较好？并写一版 prompt

   ```
   # Role
   你是一位精通 Spring Boot 3.x 和 Spring Security 的资深后端架构师，同时熟悉 React/Vue 前端开发。
   
   # Task
   请基于 Spring AI 项目架构，实现一套完整的用户身份验证系统。包含：登录、登出、修改密码，以及基于 JWT 的 WebSocket (STOMP) 鉴权机制。User Entity 已经创建，**User (用户)**: id, username, password(JsonIgnore), nickname, avatarUrl, createdAt.
   
   # Requirements
   ## 1. 后端实现 (Java/Spring Boot)
   - **Security**: 使用 Spring Security 6 配合 JWT。
   - **JWT 逻辑**: 实现 Token 生成、解析和拦截器。
   - **WebSocket 配置**: 
       - 使用 STOMP 协议。
       - 实现一个 `ChannelInterceptor`，在 `CONNECT` 命令时拦截并校验 Header 中的 JWT。
       - 如果校验失败，拒绝连接。
   - **Controller**: 
       - `/api/auth/login`: 验证用户名密码，返回 JWT。
       - `/api/auth/logout`: 逻辑登出。
       - `/api/auth/password`: 修改密码（需验证旧密码）。
       - `/api/auth/register`: 注册账户
       - `/api/auth/info`: 查看账户信息
   
   ## 2. 前端实现 (JavaScript/React 或 Vue)
   - 使用 Axios 处理登录和密码修改。
   - 使用 `stompjs` 和 `SockJS` 建立连接。
   - 演示如何在 `stompClient.connect` 的 headers 中传递 JWT。
   - 实现简单的登录页面。
   - 在用户登录后直接显示默认图像（后续会提供其他功能，请留好接口），右上角显示用户头像，点击后切换到用户信息展示`/api/auth/info`，用户可以在该界面进行 逻辑登出 和 修改密码。
   
   ## 3. 约束
   - 代码需符合生产规范，包含必要的异常处理。
   - 使用 Spring Boot 3 标准的配置方式。
   - 逻辑清晰，注释丰富。
   ```

3. Stage3 GameConteoller

   > 包含创建房间，用户选择剧本、用户随机刷新剧本，用户搜索剧本，用户选择角色，游戏开始，用户发言，用户投票，用户搜证，用户阅读剧本，用户查看历史信息，游戏结束。

   * Step1 简化的游戏过程

     > 创建房间，开始游戏，选择角色，游戏发言，游戏结束