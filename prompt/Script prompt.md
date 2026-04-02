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
   你是一位精通 Spring Boot 3.x 和 Spring Security 的资深后端架构师，同时熟悉 Vue 前端开发。
   
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
   
   ## 2. 前端实现 (Vue)
   - 实现简单漂亮的登录页面。
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
   
     ```
     # Role
     你是一位全栈架构师，精通 Spring Boot 3、Spring AI、MongoDB、WebSocket (STOMP) 以及 Vue 3。
     
     # Context
     我正在开发一款“剧本杀”游戏，玩家可以与多个 AI Agent 共同游戏。目前数据库模型（User, Script, Role, GameRoom, Member, GameMessages 等，详细信息位于src/main/java/com/example/striptkillgamedemo2/entity）已准备就绪。技术栈：后端 Spring Boot + Spring AI + MongoDB + Redis；前端 Vue 3 + Tailwind CSS + StompJS。
     
     # Task
     请根据提供的 Model 结构，实现以下核心业务逻辑：
     
     1. 剧本选择与房间创建 (REST API)
     剧本加载逻辑：从 MongoDB 随机获取 8 个 Script。如果数据库数量不足 8 个，前端需用占位图补齐。
     
     创建房间：初始化 GameRoom，状态设为 WAITING。
     
     角色分配：根据所选 Script 加载所有 Role。玩家选择一个角色后，查询该剧本下所有未被真人占用的 Role (根据 scriptId)，遍历这些角色：如果是 isNpc=true 的角色（如主持人 DM），生成一个 Member，isAi=true。如果是普通侦探/嫌疑人角色，生成一个 Member，isAi=true。
     
     2. 游戏流程控制 (WebSocket + State Machine)
     开始游戏：房间状态转为 PLAYING。根据 Script 中的 stages 初始化第一幕。
     
     搜证系统基础：初始化 GameRoom 中的 cluePool。根据 Role 的 searchPower 限制玩家的搜证次数。
     
     阶段流转：实现从当前 ScriptStage 跳转至下一阶段的逻辑。
     
     3. 实时游戏对话 (WebSocket STOMP)
     消息路由：实现 /app/chat.{roomId} 接收消息，并通过 /topic/room.{roomId} 广播。
     
     权限校验：使用之前提到的 JWT 拦截器，确保只有房间内的 Member 才能发言。
     
     对话存储：所有发言实时持久化到 GameMessages 集合，使用 redis 进行存储，游戏结束后释放。
     
     AI 响应触发：当玩家发言后，根据当前阶段上下文和 Role 中的 prompt 指令，调用 Spring AI 接口生成对应 Agent 的回复，并标记为 isAi=true。
     
     4. 前端交互界面 (Vue 3)
     frontend/src/views/HomeView.vue 处用于展示用户进入游戏前的页面，现已用占位图进行表示，你需要修改这里的代码。
     在 HomeView 界面，左边提供多个选择，包括`剧本杀游戏`、`剧本杀创作`、`剧本杀上传`，其中本次需要完成`剧本杀游戏`板块的前端页面，其余使用图片或文字进行展位。
     在`剧本杀游戏`页面，用于可以点击右下角`创建房间`进入后端的创建房间，进入房间后右下角显示开始游戏，在`剧本墙`和`角色墙`选择结束之前不能选择开始游戏。
     创建房间后包含`剧本墙`，用户在选择`剧本墙`中的剧本后，进入`角色墙`，`角色墙`选择结束之后，右下角`开始游戏`按钮变亮，用户可以点击开始游戏。
     剧本墙：两排，一排四个，支持随机展示与占位。
     角色墙：根据角色数量进行排列，角色<=5则一行展示，居中显示，角色 > 5 && 角色 <= 10 分两行展示，角色>10则分三行展示
     开始游戏包含`聊天室`和`退出机制`
     聊天室：
     
     玩家发言：头像和气泡靠右显示。
     
     Agent/其他玩家发言：头像和气泡靠左显示。
     
     退出机制：左上角退出按钮，点击后逻辑删除或更新 Member 状态，若房间无真人玩家则销毁房间。
     
     # Requirements
     
     代码质量：后端需包含 Service 层逻辑处理（特别是复杂的 Room 状态更新）。
     
     安全性：所有 WebSocket 动作必须校验 JWT 和用户在房间内的合法性。
     
     扩展性：Role 中的 secret 和 selfClueId 需在后续搜证功能中易于调用。
     
     输出内容：请先给出后端核心 Controller 和 Service 及其相关的实现代码，再给出前端核心组件的代码示例。
     ```
   
     ```
     # Task
     请基于提供的模型，分模块实现以下核心功能，确保代码符合 Clean Architecture 原则：
     
     1. 房间生命周期管理 (Domain Logic & REST)
     剧本筛选: 实现 ScriptService.getRandomScripts(int limit)。使用 MongoDB 的 $sample 聚合操作随机获取剧本。
     
     房间初始化: 实现 createRoom 逻辑。初始化 GameRoom 为 WAITING 状态。
     
     自动化选角 (The Auto-Fill Logic):
     
     玩家选定 roleId 后，系统需自动扫描该剧本剩余角色。
     
     为所有空余角色创建 Member 实体，设置 isAi=true。
     
     区分 isNpc（如 DM）和普通玩家角色。
     
     状态流转: 当房间内所有角色（真人+AI）分配完毕，房间状态变更为 PLAYING。
     
     2. 实时通信与消息路由 (WebSocket & Redis)
     STOMP 安全:
     
     配置 WebSocketMessageBrokerConfigurer。
     
     在 ChannelInterceptor 中校验 JWT，并验证 userId 是否属于 roomId。
     
     消息存储策略:
     
     玩家发言后，消息先通过 Redis List 或 ZSet 存储（Key 结构：game:chat:{roomId}），保证实时读写性能。
     
     同步逻辑: 实现一个监听器或定时任务，在游戏结束（FINISHED）时将 Redis 中的 fullChatLog 批量持久化到 MongoDB。
     
     前端适配: 区分发言者。玩家本人消息 align-right，Agent 和其他玩家 align-left。
     
     3. Spring AI 代理集成 (Agent Logic)
     触发机制: 玩家发言存入数据库后，异步触发 AgentService。
     
     智能回复:
     
     从 GameRoom 和 ScriptStage 获取当前上下文。
     
     提取对应 Role 的 prompt 和 secret。
     
     使用 Spring AI 的 ChatClient 调用 LLM，生成符合角色人设的回复。
     
     回复消息通过 WebSocket 再次广播，roleId 标记为 Agent。
     
     4. 前端页面流转 (Vue 3 HomeView)
     多功能看板: 修改 HomeView.vue，实现左侧侧边栏（游戏、创作、上传）。
     
     剧本/角色墙布局:
     
     剧本墙: 2x4 响应式网格，空位显示 placeholder 图片。
     
     角色墙算法: 实现动态行计算（<=5 一行，<=10 两行，>10 三行）。
     
     状态驱动 UI: 使用单一状态变量（如 currentStep: 'LOBBY' | 'SELECTING_SCRIPT' | 'SELECTING_ROLE' | 'GAMING'）切换界面组件。
     
     游戏逻辑: 实现“开始游戏”按钮的置灰逻辑（必须选完剧本和角色）。
     
     # Requirements
     
     Service 层解耦: 不要将所有逻辑写在 Controller，创建 GameFlowService 处理状态机。
     
     错误处理: 后端需有 GlobalExceptionHandler 处理房间已满、权限不足等业务异常。
     
     性能: 使用 Redis 缓解 MongoDB 的频繁写入压力。
     
     输出内容:
     
     第一步：给出后端核心配置（WebSocket, Security）及 Service 类。
     
     第二步：给出 Vue 3 核心状态管理及组件模板。
     ```
   
   * 修改 Stricp 和 clude、stage，role 的 entity
   
     ```
     @Document(collection = "scripts")
     @Data
     public class Script {
         @Id
         private String id; // 建议用 String，方便前端和缓存处理
     
         @NotBlank
         private String title;
         private String description;
         private ScriptDifficulty difficulty;
         private int playerCount;
         private String coverImage;
     
         // --- 核心内嵌数据 ---
     
         // 1. 角色库：AI Agent 的灵魂都在这里
         private List<Role> roles; 
     
         // 2. 线索库：存储所有静态线索定义
         private List<Clue> clues; 
     
         // 3. 阶段流转：定义每一幕解锁什么，内容是什么
         private List<ScriptStage> stages;
     
         // --- 其他配置 ---
         private Map<String, Object> dmConfig; // 存储 DM AI 的全局设定
         private int version = 1;
     }
     ```
   
     ```
     @Data
     public class Role {
         private String id; // 在剧本内部唯一的 ID，如 "ROLE_001"
         private String name;
         private String avatar;
         private boolean isNpc;
         
         // --- AI 相关 ---
         private String prompt;    // AI 核心人设指令
         private String secret;    // 不可泄露的秘密
         
         // --- 游戏机制相关 ---
         private List<String> selfClueIds; // 角色自带的线索 ID
         private String locationTag;       // 该角色初始所在的地点（用于搜证）
         private int searchPower;          // 初始行动力
     }
     ```
   
     ```
     @Data
     public class Clue {
         private String id; // 剧本内唯一 ID，如 "CLUE_001"
         private String title;
         private ClueType type; // TEXT, IMAGE, AUDIO
         private String content;
         private String imageUrl;
         
         // 控制逻辑
         private boolean isInitialHidden = true; // 是否初始隐藏
         private List<String> searchableRoleIds; // 哪些角色可以搜到这个线索
         private String locationTag;             // 所在地点
     }
     ```
   
     ```
     @Data
     public class ScriptStage {
         private int stageNumber;
         private String stageTitle;
         
         /**
          * Key: roleId (角色的 ID)
          * Value: 该阶段该角色看到的剧本内容（包含 AI 需要知道的本幕任务）
          */
         private Map<String, String> contentMap;
         
         private String audioUrl; // 本幕 BGM 或开场白
     
         /**
          * 重点：本阶段“解锁”的线索 ID 列表
          * 当游戏进入这一幕时，逻辑上将这些 ID 加入 GameRoom 的可搜索池
          */
         private List<String> unlockClueIds; 
     }
     ```
     ```
      不再需要 GameRoom entity，你现在需要确保代码的流程正确：用户创建房间->随机选择八个剧本（无需加载全部信息）->用户选择剧本->用户开始游戏->将已选择的剧本所有内容加载到redis->用户选择角色->用户开始游戏  
      ->用户当前幕剧本->用户发言->...（用户可以持续发言）->下一幕开始（需要判断条件或用户决定开始下一幕）-> ...(用户可持续发言)-> ...直到所有的幕结束或用户选择退出或用户长时间离开 -> 游戏结束 ->           
      总结所有内容（这里目前只需要接口）-> 将该用户在 redis 中的游戏信息如 已经加载的 Script 信息和 LiveGameRoom 等信息进行回收。注意，用户在同一时间只能加入一个游戏。
     ```
   
   * Stage2 AI 驱动的剧本杀引擎
   
     ```
     # Role
     你是一位顶级全栈架构师，精通 Spring AI (Function Calling)、Multi-Agent 系统设计、WebSocket (STOMP) 以及 MongoDB/Redis。你擅长构建高并发、强状态逻辑的游戏后端，并能优雅地解决 LLM 的长文本上下文压缩问题。
     
     # Context
     项目是一款“剧本杀”游戏，核心是 “Harness 模式”，其中 AI DM 和 AI Agents 的初始 prompt 和上下文压缩时的 prompt 应该可以在配置文件中进行配置：
     
     AI DM (主持人)：掌握全局，拥有上帝视角，负责推进流程、审批玩家动作（搜证/投票）。
     
     AI Agents (角色)：扮演剧本中的 NPC 或剩余玩家位，拥有各自的秘密（Secret）和人设（Prompt）。
     
     真人玩家：通过前端 UI 与 AI 们实时对话和互动。
     
     # Task: 实现核心驱动引擎
     
     1. 自动化 Agent 分配与初始化 (AgentOrchestrator)
     逻辑：实现一个 Service，当真人玩家选定角色后：
     
     查询 Script 中所有 Role。
     
     为每个未占用的 Role 创建 Member 实体（isAi=true）。
     
     强制生成一个 isDm=true, isAi=true 的成员。
     
     隔离性：确保每个 Member 只能读取其对应 Role 的 prompt 和当前/历史 ScriptStage 内容。
     
     2. AI DM 工具箱 (Function Calling)
     请使用 Spring AI 的 FunctionCallback 机制为 DM Agent 实现以下工具，你也可以加入你认为合适的工具：
     
     authorizeSearch(roomId, roleId, location)：
     
     校验玩家 searchPower 和地点开放情况或时间节点，由 AI DM 选择是否可以进行搜证，如果包含多个证据，由 DM 根据剧本来决定 Agent 或玩家可以选择一条或 n 条证据。
     
     成功则从 cluePool 分发线索，并通过 WebSocket 推送。
     
     initiateVote(roomId, title, options)：
     
     开启投票状态机，前端弹出投票框。
     
     readFullScript(roomId, queryType)：
     
     允许 DM 查阅 Redis 中缓存的剧本完整真相（SECRET），但禁止直接复述原文。
     
     3. 上下文压缩与长程记忆 (MemoryManager)
     压缩触发器：每一幕 (ScriptStage) 结束时，调用 Spring AI 将本幕 GameMessages 摘要为“关键事件碎片”（如：A 怀疑 B 有匕首）。
     
     动态 Prompt 构造：
     
     Final Prompt = [全局人设] + [历史各幕摘要] + [当前幕任务] + [最近 N 条对话]。
     
     确保 AI 不会因为对话过长而“失忆”或突破 Token 限制。
     
     
     
     4. 实时通讯流 (Communication Loop)
     后端路由：/app/chat.{roomId} 接收消息 -> 存入 Redis -> 异步触发 AI 响应。
     
     AI 响应逻辑：
     
     如果是玩家对 DM 说话，触发 DM。
     
     如果是公屏发言，根据算法（或随机，或根据关键词）触发一个或多个 AI Agent 回复。
     
     UI 渲染策略：通过 isAi, isDm, roleId 标签让前端识别气泡位置和颜色。
     
     5. 游戏结算与复盘 (FinalReviewService)
     玩家点击“结束游戏”后，DM 调用 DefaultGameSummaryService。
     
     生成复盘报告：包含全场表现、秘密真相揭露、AI 生成的剧情评价，存入 GameRecord。
     
     # Requirements for Output
     
     Java 实现：给出 DmToolService 类（处理搜证/投票逻辑）和 Spring AI 配置类。
     
     AI 指令设计：
     
     设计 DM Agent 的 System Prompt（强调其“裁判”与“叙事者”的双重身份）。
     
     设计 Player Agent 的 System Prompt（强调“保护秘密”与“角色扮演”）。
     
     上下文压缩逻辑：给出如何将多轮对话压缩并存入 Redis 的代码示例。
     ```
   
     ```
     # Role
     你是一位顶级全栈架构师，精通 Spring AI (Function Calling)、Multi-Agent 系统设计、WebSocket (STOMP) 以及 MongoDB/Redis。你擅长构建高并发、强状态逻辑的游戏后端，并能优雅地解决 LLM 的长文本上下文压缩问题，现在为已有项目添加新的功能。
     
     # Context
     项目是一款“剧本杀”游戏，核心是 “Harness 模式”。
     - **AI DM (主持人)**：上帝视角，负责推进流程、审批动作（搜证/投票）。
     - **AI Agents (角色)**：扮演剧本角色，拥有秘密(Secret)和独立人设。
     - **配置化**：所有系统指令（System Prompt）和压缩模板需支持动态加载。
     
     # Task: 实现核心驱动引擎
     
     ### 1. 自动化 Agent 分配 (AgentOrchestrator)
     - 实现 Service 逻辑：玩家选角后，自动为剩余 Role 创建 `isAi=true` 的 `Member`。
     - **强制初始化**：生成一个 `isDm=true, isAi=true` 的成员作为全局裁判。
     - **沙箱隔离**：设计一个上下文包装器，确保 Agent 在构造 Prompt 时，无法越权访问其他角色的 `Secret`。
     
     ### 2. 增强型 DM 工具箱 (Function Calling)
     利用 Spring AI 的 `FunctionCallback` 实现以下工具，需保证操作的**原子性**：
     - **`authorizeSearch(roomId, roleId, location)`**：
         - **逻辑**：校验 `searchPower` 和当前阶段开放性。
         - **多线索处理**：若地点有多个线索，AI 先调用此工具获取“线索摘要清单”，根据剧本逻辑决定分发哪几条。
         - **副作用**：扣除 `searchPower`，更新 `cluePool` 状态，并通过 WebSocket `convertAndSendToUser` 推送线索详情。
     - **`initiateVote(roomId, title, options)`**：
         - 开启投票状态机，变更 `GameRoom.status`，广播投票 UI 信令。
     - **`readFullScript(roomId, queryType)`**：
         - 允许 DM 查阅剧本真相，但指令中需强制 AI 只能以“引导者”口吻回复，严禁剧透原文。
     
     ### 3. 上下文压缩与分层记忆 (MemoryManager)
     - **压缩触发器**：每一幕结束或 Token 接近上限时，触发摘要任务。
     - **存储策略**：将“关键事件碎片”存入 Redis（如：角色关系变更、已揭露谎言）。
     - **动态构造**：`Final Prompt = [全局设定] + [历史碎片(JSON摘要)] + [当前幕任务] + [最近 N 条对话滑动窗口]`。
     
     ### 4. 异步通讯循环 (Communication Loop)
     - **非阻塞架构**：`/app/chat.{roomId}` 接收消息后立即存入 Redis 队列并返回。
     - **智能分发器**：
         - 若玩家 @DM 或提出申请，仅触发 DM。
         - 公屏发言：由 `AgentOrchestrator` 根据发言内容的相关性，随机或按权重选择 1-2 个 Agent 异步回复。
     - **UI 状态同步**：所有 AI 动作（正在输入、工具调用中、搜证成功）需有明确的信令通知前端。
     
     ### 5. 复盘总结 (FinalReviewService)
     - 游戏结束时，汇总 `GameMessages` 和 `cluePool` 状态。
     - AI 生成：角色表现评分、未解之谜揭秘、剧情总结。
     
     # Requirements for Output
     1. **Java 实现**：
        - 提供 `DmToolService`（包含搜证分发逻辑）。
        - 提供 Spring AI 结合工具调用的配置类（`@Bean` 方式）。
     2. **AI 指令设计**：
        - 提供 DM 的 System Prompt（包含如何判断搜证权力的逻辑）。
        - 提供 Player Agent 的 System Prompt（强调保护秘密）。
     3. **压缩代码示例**：演示如何使用 Spring AI 将 List<Message> 压缩为结构化事实并更新 Redis。
     ```
   
     ```
     # Role
     你是一位精通逻辑推理的剧本杀专业记录员。你的任务是将当前阶段（Stage）的原始对话记录提炼为“核心事实快照”。
     
     # Input
     - 历史摘要：{{previousSummary}}
     - 本幕对话流：{{currentChatLogs}}
     - 已公开线索：{{revealedClues}}
     
     # Task
     请分析对话，提取并更新以下信息，确保逻辑严密且不丢失关键反转：
     
     1. **信息暴露清单 (Information Revealed)**:
        - 哪些玩家的秘密被揭穿了？
        - 哪些玩家主动交代了关键时间点或动机？
     2. **怀疑链条 (Suspicion Chain)**:
        - 当前大家公认的怀疑对象是谁？理由是什么？
        - 是否存在明显的逻辑矛盾或谎言？
     3. **关键线索状态 (Clue Status)**:
        - 本幕中哪个线索起到了决定性作用？
        - 谁持有关键证物？
     4. **情感与氛围 (Vibe & Relation)**:
        - 玩家之间的关系发生了什么变化（如结盟、反目）？
     
     # Constraint
     - 请使用极简的陈述句。
     - 仅保留对后续推理有实质影响的信息。
     - **输出格式**：JSON 格式，便于系统解析。
     
     # Output Format (JSON)
     {
       "summary": "一句话概括本幕进展",
       "revealedSecrets": ["角色A的秘密B被发现", "..."],
       "logicalConflicts": ["角色C关于20:00的描述与线索D冲突"],
       "keyEvidence": "...",
       "currentSuspicionMap": {"roleId": "怀疑权重0-1"}
     }
     ```
   
   * Stage3 bug 修复
   
     ```
     # Role
     你是一位精通 Spring Boot 3、Spring AI 和 WebSocket 的高级调试工程师。你擅长处理复杂的分布式状态机逻辑和多智能体（Multi-Agent）交互优化。
     
     # Context
     这是一个基于 "Harness 模式" 的 AI 剧本杀系统。
     
     核心组件：AI DM (主持人)、AI Agents (角色)、真人玩家。
     
     技术栈：Spring AI (ChatClient), WebSocket (STOMP), Redis (暂存消息), MongoDB (存储剧本/房间)。
     
     # Task: 修复以下 4 个核心 Bug
     
     Bug 1: 玩家剧本可见性异常
     现象：开始游戏后，玩家界面无法显示剧本内容。不要在一次性查询中返回所有 Stage。查阅 DM Agent 的工具箱，如果没有剧本推送机制则添加新解锁的剧本内容推送机制，当 DM Agent 决定开启下一幕时为游戏中的 Agent 和 玩家推送剧本内容。DM Agent 工具箱在`com/example/striptkillgamedemo2/ai/tool`
     Bug 2: 游戏开场白缺失
     现象：点击“开始游戏”后，系统无反应，DM 未进行引导。
     Bug 3: 非 DM Agent 沉默（对话流调度失效）
     现象：自由发言阶段只有 DM 发言，其他 AI 角色不说话。
     Bug 4: AI 响应延迟过高（体验优化）
     现象：AI 生成长文本时，玩家需等待完整段落生成后才能看到，响应时间超 5-10 秒。WebSocket 分片推送：在流式响应的 Flux 订阅中，将每一个 content 片段（Chunk）通过 WebSocket 即时推送给前端。Spring AI 的 Flux 和 WebSocket 的 simpMessagingTemplate 配合时，要防止消息乱序。
     ```
   
     