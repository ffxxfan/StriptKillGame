# 剧本杀项目 — 面试八股文全集

> 基于项目实际技术栈：Spring Boot 3.x、Spring Security + JWT、Spring AI、MongoDB、Redis、WebSocket STOMP、Vue 3 + TypeScript + Pinia，涵盖框架原理、中间件、设计模式、并发、网络协议、前端工程化等方面。

---

## 目录

- [一、Spring Boot](#一spring-boot)
- [二、Spring Security + JWT 认证](#二spring-security--jwt-认证)
- [三、MongoDB](#三mongodb)
- [四、Redis](#四redis)
- [五、WebSocket / STOMP](#五websocket--stomp)
- [六、Spring AI 与大模型集成](#六spring-ai-与大模型集成)
- [七、Spring 事件驱动与异步](#七spring-事件驱动与异步)
- [八、并发编程](#八并发编程)
- [九、设计模式](#九设计模式)
- [十、Vue 3 + TypeScript 前端](#十vue-3--typescript-前端)
- [十一、网络与协议](#十一网络与协议)
- [十二、项目架构与场景设计题](#十二项目架构与场景设计题)

---

## 一、Spring Boot

### Q1: Spring Boot 的自动配置原理是什么？

Spring Boot 自动配置的核心是 `@EnableAutoConfiguration` 注解（包含在 `@SpringBootApplication` 中）。

**原理**：
1. 启动时，`AutoConfigurationImportSelector` 通过 `SpringFactoriesLoader` 加载 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（Spring Boot 3.x）中注册的所有自动配置类
2. 每个自动配置类上标注了条件注解（如 `@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty`），只有条件满足时才会生效
3. 例如引入 `spring-boot-starter-data-mongodb` 后，`MongoAutoConfiguration` 检测到 classpath 中有 MongoDB 驱动类，就会自动配置 `MongoClient`、`MongoTemplate` 等 Bean

**项目举例**：引入 `spring-ai-starter-model-anthropic` 后，Spring AI 自动注册 `ChatModel` Bean（Anthropic 实现），无需手动配置。只需在 `application.properties` 中设置 API Key 和模型参数即可。

### Q2: @ConfigurationProperties 和 @Value 的区别？

| 特性 | @ConfigurationProperties | @Value |
|------|-------------------------|--------|
| 绑定方式 | 批量绑定前缀下所有属性 | 逐个注入 |
| 类型安全 | 支持复杂类型、嵌套对象 | 仅基本类型和 SpEL |
| 松散绑定 | 支持（kebab-case → camelCase） | 不支持 |
| 校验 | 支持 JSR303（@Validated） | 不支持 |
| 元数据 | 可生成 IDE 提示 | 无 |

**项目举例**：`AiEngineProperties` 使用 `@ConfigurationProperties(prefix = "game.ai")` 批量绑定所有 AI 引擎配置（charsPerToken、tokenThresholdRatio 等），修改配置时无需改动 Java 代码。

### Q3: Spring Boot 3.x 相比 2.x 有哪些重要变化？

1. **最低 Java 17**：支持 Records、Sealed Classes、Pattern Matching
2. **Jakarta EE 迁移**：`javax.*` → `jakarta.*`（如 `jakarta.validation.constraints.NotBlank`）
3. **GraalVM 原生编译支持**：通过 AOT 引擎减少启动时间和内存
4. **自动配置文件格式变更**：`spring.factories` → `AutoConfiguration.imports`
5. **可观测性增强**：Micrometer Observation API 替代手动埋点
6. **Spring Security 重构**：`WebSecurityConfigurerAdapter` 被移除，改为 `SecurityFilterChain` Bean 注入

**项目体现**：本项目的 `SecurityConfig` 使用 `SecurityFilterChain` Bean 配置安全规则，而非继承已废弃的 `WebSecurityConfigurerAdapter`。

### Q4: Spring Boot 的 Profile 机制是什么？怎么用？

Profile 允许针对不同环境（dev/test/prod）加载不同的配置。

**激活方式**：
- `application.properties` 中 `spring.profiles.active=dev`
- 命令行 `--spring.profiles.active=prod`
- 环境变量 `SPRING_PROFILES_ACTIVE=prod`

**配置文件约定**：`application-{profile}.properties` 覆盖默认配置。

**代码中使用**：`@Profile("dev")` 注解某个 Bean，只有对应 Profile 激活时才会注册。

**项目举例**：`DataInitializer` 使用 `@Profile("dev")`，仅在开发环境自动插入测试剧本数据。

### Q5: Spring Boot Starter 的原理是什么？

Starter 本质是一个**依赖聚合 + 自动配置**的 Maven/Gradle 模块。

- **依赖聚合**：Starter POM 传递引入该功能所需的所有依赖（如 `spring-boot-starter-data-redis` 引入 Lettuce 驱动、Spring Data Redis 等）
- **自动配置**：配套的 `*-autoconfigure` 模块提供 `@AutoConfiguration` 类，根据条件自动配置 Bean
- **零配置启动**：开发者只需引入 Starter 依赖，写一行配置即可使用

**项目举例**：引入 `spring-ai-starter-model-anthropic` 一个依赖，就自动获得了 `ChatModel` Bean、Anthropic HTTP 客户端、重试策略等完整的 LLM 调用能力。

---

## 二、Spring Security + JWT 认证

### Q6: JWT 的组成和工作原理？

JWT（JSON Web Token）由三部分组成，以 `.` 分隔：

```
Header.Payload.Signature
```

- **Header**：声明类型（JWT）和签名算法（如 HS256）
- **Payload**：存放声明（Claims），如 `sub`（用户ID）、`exp`（过期时间）、`jti`（唯一标识）
- **Signature**：`HMACSHA256(base64(header) + "." + base64(payload), secret)`

**工作流程**：
1. 用户登录 → 服务端生成 JWT → 返回给客户端
2. 客户端每次请求在 `Authorization: Bearer <token>` 中携带
3. 服务端验证签名和过期时间 → 从 Payload 解析用户信息 → 无需查数据库

**优点**：无状态、可跨域、可扩展
**缺点**：无法主动失效（需配合黑名单机制）、Payload 可被 Base64 解码（不要存敏感信息）

### Q7: 你的项目如何实现 JWT 的主动失效？

**问题**：JWT 签发后在过期前无法主动使其失效（如用户登出、改密码）。

**解决方案 — Redis 黑名单**：
1. 登出时，将 Access Token 的 `jti`（JWT ID）存入 Redis，TTL 设为 Token 的剩余有效期
2. 每次请求验证 Token 时，额外检查 Redis 中是否存在该 `jti`
3. 改密码时，删除 Redis 中存储的 Refresh Token，使所有设备的 Token 刷新失败

```java
// JwtService.java
public void blacklistAccessToken(Claims claims) {
    String jti = claims.getId();
    long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
    if (remainingMs > 0) {
        redisTemplate.opsForValue().set("jwt:blacklist:" + jti, "1",
                remainingMs, TimeUnit.MILLISECONDS);
    }
}
```

**为什么不直接删 Token？** 因为 JWT 是客户端持有的，服务端无法直接销毁。黑名单是在验证环节拦截。

### Q8: Access Token + Refresh Token 双 Token 机制的好处？

| | Access Token | Refresh Token |
|--|-------------|---------------|
| 有效期 | 短（本项目 2 小时） | 长（本项目 30 天） |
| 用途 | 访问资源 | 仅用于刷新 Access Token |
| 存储 | 前端内存/localStorage | 前端 localStorage |
| 泄露风险 | 高频使用，窗口短 | 低频使用，仅刷新时发送 |

**好处**：
1. **安全性**：Access Token 短期有效，即使泄露影响有限
2. **用户体验**：Refresh Token 长期有效，用户不用频繁重新登录
3. **可控性**：改密码/登出时只需使 Refresh Token 失效，所有设备的 Access Token 过期后自然失效

**项目实现**：前端 Axios 拦截器在 401 时自动用 Refresh Token 请求新 Access Token，排队等待刷新完成后重试原始请求。

### Q9: Spring Security 过滤器链的工作原理？

Spring Security 基于 **Servlet Filter Chain** 实现：

```
HTTP 请求 → DelegatingFilterProxy → FilterChainProxy
              → SecurityFilterChain (一组有序的 Filter)
                → 认证 Filter（如 JwtAuthenticationFilter）
                → 授权 Filter（AuthorizationFilter）
                → ExceptionTranslationFilter
                → ...
              → DispatcherServlet → Controller
```

**关键点**：
- `SecurityFilterChain` 是一个有序的 Filter 列表
- 通过 `addFilterBefore/After` 控制自定义 Filter 的位置
- 无状态 JWT 认证不需要 `SessionCreationPolicy`

**项目配置**：
```java
http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```
将 `JwtAuthenticationFilter` 插入到默认的表单认证 Filter 之前，所有请求先走 JWT 校验。

### Q10: BCrypt 加密的原理？为什么比 MD5/SHA 更安全？

**BCrypt 特点**：
1. **内置盐值**：每次加密自动生成随机盐，同一密码每次加密结果不同
2. **自适应成本因子**：可通过 `strength` 参数（默认 10）控制计算轮数，2^10 = 1024 轮
3. **刻意慢速**：一次哈希约 100ms，暴力破解代价极高

**对比 MD5/SHA**：
- MD5/SHA 设计目标是快速，GPU 可每秒计算数十亿次
- BCrypt 设计目标是慢，且可随硬件升级调高成本因子
- MD5/SHA 需要手动加盐，BCrypt 自动处理

**项目使用**：`PasswordEncoder passwordEncoder = new BCryptPasswordEncoder()`，注册时 `encode()`，登录时 `matches()` 自动比较。

---

## 三、MongoDB

### Q11: MongoDB 和 MySQL 的核心区别？什么场景选 MongoDB？

| 特性 | MongoDB | MySQL |
|------|---------|-------|
| 数据模型 | 文档型（JSON/BSON） | 关系型（表） |
| Schema | 灵活，无需预定义 | 严格 Schema |
| JOIN | 不支持传统 JOIN | 原生支持 |
| 事务 | 4.0+ 支持多文档事务 | 原生 ACID |
| 扩展方式 | 水平分片（Sharding） | 垂直扩展为主 |
| 查询语言 | MongoDB Query Language | SQL |

**选择 MongoDB 的场景**：
1. **数据结构多变**：如剧本的 `Script` 包含嵌套的 `roles`、`clues`、`stages`，关系型数据库需要多张表 + JOIN
2. **读多写少的文档型数据**：如游戏剧本、用户资料
3. **快速迭代**：Schema 灵活，字段增删不需要迁移
4. **嵌套数据自然映射**：一个 Script 文档直接包含所有角色、线索、幕次，一次查询获取完整数据

**项目选型理由**：剧本数据是深度嵌套的文档结构（Script → Role/Clue/ScriptStage → StagePhase），MongoDB 的文档模型可以一次读取完整剧本，避免多表 JOIN。

### Q12: MongoDB 的嵌入文档 vs 引用文档？如何选择？

**嵌入文档**（Embedded）：子文档直接嵌套在父文档内
```json
{ "title": "剧本A", "roles": [{"name": "侦探", "secret": "..."}] }
```

**引用文档**（Reference）：通过 ObjectId 关联
```json
{ "title": "剧本A", "roleIds": ["65a...", "65b..."] }
```

**选择原则**：

| 场景 | 选择 | 原因 |
|------|------|------|
| 1:1 或 1:少 关系 | 嵌入 | 查询效率高 |
| 数据总是一起访问 | 嵌入 | 避免额外查询 |
| 子文档会独立修改 | 引用 | 避免更新整个父文档 |
| 子文档被多处引用 | 引用 | 避免数据冗余 |
| 文档大小超过 16MB | 引用 | MongoDB 文档大小限制 |

**项目设计**：
- `Script` 嵌入 `Role`、`Clue`、`ScriptStage` → 因为剧本数据总是整体加载，且角色/线索不会独立于剧本存在
- `GameRoom` 嵌入 `Member` → 成员信息总是随房间一起查询
- `GameRecord` 独立文档 → 游戏结束后独立查询，不与 GameRoom 一起使用

### Q13: Spring Data MongoDB 的 @Aggregation 是什么？

MongoDB 聚合管道（Aggregation Pipeline）是一系列数据处理阶段的组合，类似于 Unix 管道。

**常用阶段**：
- `$match` — 过滤（相当于 WHERE）
- `$group` — 分组聚合（相当于 GROUP BY）
- `$project` — 字段投影（相当于 SELECT）
- `$sort` — 排序
- `$sample` — 随机采样
- `$lookup` — 左外连接

**项目使用**：
```java
@Aggregation(pipeline = { "{ $sample: { size: ?0 } }" })
List<Script> findRandomScripts(int count);
```
使用 `$sample` 阶段从 scripts 集合中随机抽取指定数量的剧本，比 `findAll()` + 程序随机效率更高。

### Q14: ObjectId 的组成和特点？

MongoDB 的 `ObjectId` 是 12 字节的唯一标识符：

```
|  4字节   |  5字节   |  3字节  |
| 时间戳   | 随机值   |  递增计数 |
```

**特点**：
1. **全局唯一**：无需中心协调即可生成
2. **大致有序**：前 4 字节是时间戳，ObjectId 按创建时间大致有序
3. **可提取时间**：`objectId.getDate()` 获取创建时间
4. **12 字节 = 24 位十六进制字符串**

**项目使用**：所有实体的主键均使用 `ObjectId` 而非自增 ID，支持分布式环境下无冲突生成。JSON 序列化时通过 `JacksonConfig` 自动转为十六进制字符串。

---

## 四、Redis

### Q15: Redis 的常用数据结构及适用场景？

| 结构 | 命令示例 | 适用场景 |
|------|---------|----------|
| String | GET/SET | 缓存、计数器、JWT 黑名单 |
| Hash | HGET/HSET | 对象存储（用户信息） |
| List | LPUSH/RPUSH/LRANGE | 消息队列、聊天记录 |
| Set | SADD/SMEMBERS | 标签、去重 |
| Sorted Set | ZADD/ZRANGE | 排行榜、延迟队列 |

**项目中的使用**：
- **String**：剧本缓存（`script:{id}` → JSON）、JWT 黑名单（`jwt:blacklist:{jti}` → "1"）、用户房间绑定（`user:room:{userId}` → roomId）
- **String（作为 Hash 替代）**：游戏房间状态（`game:room:{roomId}` → LiveGameRoom JSON）
- **List**：聊天消息（`game:messages:{roomId}`）、投票记录（`game:{roomId}:votes`）、记忆压缩片段（`game:{roomId}:memory:stage:{n}`）

### Q16: Redis 的过期策略和内存淘汰策略？

**过期策略**（Key 到期后如何删除）：
1. **惰性删除**：访问 Key 时才检查是否过期，过期则删除
2. **定期删除**：每 100ms 随机抽查一批 Key，删除已过期的
3. 两者结合使用，平衡 CPU 和内存

**内存淘汰策略**（内存不足时如何处理）：
- `noeviction`：拒绝写入（默认）
- `allkeys-lru`：所有 Key 中淘汰最近最少使用的
- `volatile-lru`：仅淘汰设置了 TTL 的 Key 中 LRU 的
- `allkeys-random`：随机淘汰
- `volatile-ttl`：淘汰 TTL 最短的

**项目设计**：
- 游戏房间设置 12 小时 TTL（活跃），空闲后缩短为 2 小时
- 剧本缓存设置 24 小时 TTL
- JWT 黑名单 TTL = Token 剩余有效期（精确匹配，不浪费内存）

### Q17: Redis 缓存穿透、击穿、雪崩分别是什么？怎么解决？

**缓存穿透** — 查询不存在的数据，缓存永远不命中，每次打到数据库
- 解决：布隆过滤器预判、缓存空值（短 TTL）
- 项目中：`ScriptCacheService.getScript()` 若 MongoDB 也查不到会抛异常，不缓存空值（因为 scriptId 来自已选择的合法剧本）

**缓存击穿** — 热点 Key 过期瞬间，大量并发请求打到数据库
- 解决：互斥锁（setnx）、逻辑过期、热点数据永不过期
- 项目中：剧本缓存 24h TTL，游戏期间不会过期（游戏最长 12h）

**缓存雪崩** — 大量 Key 同时过期 或 Redis 宕机
- 解决：TTL 加随机偏移量、多级缓存、Redis 集群
- 项目中：不同房间的 TTL 起始时间不同（创建时间不同），天然分散

### Q18: 为什么用 Redis 存储游戏运行时状态而不是 MongoDB？

**原因**：
1. **低延迟**：Redis 内存操作 < 1ms，MongoDB 磁盘操作 > 5ms。游戏聊天对延迟敏感
2. **高频读写**：每条消息都触发 Redis List RPUSH + 读取房间状态，MongoDB 频繁写入会产生大量 I/O
3. **临时性数据**：游戏运行状态（当前发言者、投票、在线状态）是临时的，游戏结束即可丢弃
4. **原子操作**：Redis 的 List RPUSH 天然保证消息有序追加

**设计**：Redis 存运行时状态（LiveGameRoom + 消息 + 投票），MongoDB 存持久化数据（User + Script + GameRecord）。游戏结束时从 Redis 提取关键数据写入 MongoDB GameRecord，然后清理 Redis。

### Q19: StringRedisTemplate 和 RedisTemplate 的区别？

| 特性 | StringRedisTemplate | RedisTemplate<Object, Object> |
|------|--------------------|----|
| 序列化器 | Key 和 Value 都是 StringRedisSerializer | Key 是 StringRedisSerializer，Value 是 JdkSerializationRedisSerializer |
| 可读性 | Redis 中存储的是人类可读的字符串 | Value 是二进制，redis-cli 中不可读 |
| 跨语言 | 友好（JSON 字符串） | 仅 Java 可反序列化 |

**项目选择 StringRedisTemplate**：所有值使用 Jackson `ObjectMapper` 手动序列化为 JSON 字符串存储。好处是 Redis 中数据可读，方便调试，且不依赖 Java 序列化机制。

---

## 五、WebSocket / STOMP

### Q20: WebSocket 和 HTTP 的区别？

| 特性 | HTTP | WebSocket |
|------|------|-----------|
| 通信模式 | 请求-响应（半双工） | 全双工 |
| 连接 | 短连接/Keep-Alive | 长连接 |
| 协议头 | 每次请求携带完整头 | 握手后头部开销极小（2-10字节） |
| 服务端推送 | 不支持（需轮询/SSE） | 原生支持 |
| 适用场景 | REST API | 实时通信（聊天、游戏、协作） |

**握手过程**：
1. 客户端发 HTTP 请求：`Upgrade: websocket, Connection: Upgrade`
2. 服务端响应 101 Switching Protocols
3. 之后通信走 WebSocket 协议帧

### Q21: 什么是 STOMP？为什么在 WebSocket 上使用 STOMP？

**STOMP**（Simple Text Oriented Messaging Protocol）是一种消息传输协议，类似于 HTTP 但用于消息系统。

**为什么不直接用原始 WebSocket？**
- 原始 WebSocket 只提供字节流传输，没有消息语义
- STOMP 提供：消息头/消息体分离、发布/订阅模型、消息路由、消息确认

**STOMP 帧格式**：
```
COMMAND
header1:value1
header2:value2

Body^@
```

**常用命令**：CONNECT、SUBSCRIBE、SEND、MESSAGE、DISCONNECT

**项目配置**：
```java
// 消息代理前缀 — 客户端订阅
registry.enableSimpleBroker("/topic", "/queue");
// 应用前缀 — 客户端发送
registry.setApplicationDestinationPrefixes("/app");
```

**消息流向**：
- 客户端 SEND → `/app/chat.{roomId}` → `@MessageMapping` 处理 → `convertAndSend("/topic/room.{roomId}")` → 所有订阅者
- 信号广播：服务端直接 `convertAndSend("/topic/room.{roomId}.signal")` → 订阅者

### Q22: WebSocket 如何做认证？

**问题**：WebSocket 握手是 HTTP 请求，但后续帧不再携带 HTTP 头。

**方案 1：握手时验证（HTTP 层）**
- 在 `HandshakeInterceptor` 中检查 URL 参数或 Cookie 中的 Token
- 缺点：Token 暴露在 URL 中

**方案 2：CONNECT 帧验证（STOMP 层）** — 本项目采用
```java
// WebSocketAuthInterceptor.java
@Override
public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, ...);
    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
        String token = accessor.getFirstNativeHeader("Authorization");
        // 验证 JWT → 设置 Authentication
    }
}
```

**流程**：客户端在 STOMP CONNECT 帧的 headers 中携带 `Authorization: Bearer <token>`，服务端在 `ChannelInterceptor` 中验证。验证通过后后续帧自动关联认证信息。

### Q23: SimpMessagingTemplate 的 convertAndSend 是同步还是异步？

`convertAndSend()` 是**异步**的：
1. 将消息放入 `clientOutboundChannel` 的消息队列
2. 消息代理异步分发给所有订阅者
3. 方法调用立即返回，不等待客户端接收

**注意**：虽然发送是异步的，但消息的**序列化**（Object → JSON）是在调用线程同步完成的。大量消息时要注意序列化开销。

---

## 六、Spring AI 与大模型集成

### Q24: Spring AI 是什么？解决了什么问题？

Spring AI 是 Spring 生态的 AI 框架，提供统一的 API 对接各种 AI 模型。

**核心抽象**：
- `ChatModel` — 统一的聊天模型接口（类似 JDBC 的 DataSource）
- `Prompt` — 提示词封装（SystemMessage + UserMessage）
- `ChatResponse` — 模型响应封装
- `ToolCallback` — Function Calling 工具回调

**解决的问题**：
1. **提供商无关**：切换模型只需更换 Starter（如 `spring-ai-starter-model-anthropic` → `spring-ai-starter-model-openai`）
2. **Spring 生态集成**：Bean 注入、自动配置、@Async 异步
3. **工具调用标准化**：统一的 Function Calling 接口

**项目使用**：代码面向 `ChatModel` 接口编程，通过 `application.properties` 配置选择 Anthropic Claude 模型。切换到 OpenAI 只需换依赖和配置。

### Q25: 什么是 Function Calling（工具调用）？在项目中如何使用？

**Function Calling** 是让 LLM 调用外部函数的能力：
1. 开发者定义可用函数（名称、描述、参数 Schema）
2. 将函数列表随提示词发送给 LLM
3. LLM 判断需要调用哪个函数、传什么参数
4. 框架执行函数并将结果返回给 LLM
5. LLM 基于函数结果继续生成回复

**项目实现**：
```java
// DmToolRegistry 将 DmTool 接口实现转为 Spring AI FunctionToolCallback
FunctionToolCallback.builder(tool.name(), (Object input) -> tool.execute(input, ctx))
    .description(tool.description())
    .inputType(tool.inputType())
    .build()

// DmExecutor 使用 ToolCallingChatOptions 传递工具列表
Prompt prompt = new Prompt(messages,
    ToolCallingChatOptions.builder().toolCallbacks(callbacks).build());
```

**实际效果**：DM（主持人）AI 可以自主决定何时授权搜证、发起投票、推进阶段等，无需硬编码规则。

### Q26: 如何做 AI 上下文窗口管理？Token 超限了怎么办？

**问题**：LLM 有上下文窗口限制（如 Claude 200K tokens），长时间游戏会超限。

**项目方案 — 双触发压缩**：

1. **幕次触发**：每次推进到下一幕时，调用 LLM 将当前幕的所有消息压缩为结构化摘要（`StageSummary`），存入 Redis
2. **阈值触发**：当 `estimateTokens(prompt) > maxContextTokens * 0.7` 时主动触发压缩

**上下文组装策略**：
```
系统提示词（固定）
+ 历史幕次摘要（已压缩，体积小）
+ 最近 N 条消息（滑动窗口，N=20）
```

**Token 估算**：中文约 3.5 字符/Token，`text.length() / 3.5` 快速估算。

### Q27: 如何实现 AI 角色的沙盒隔离？

**问题**：多个 AI 角色共享同一游戏房间，但每个角色只能知道自己的秘密和线索。

**解决方案 — PromptBuilder 构建隔离提示词**：

```
DM 提示词（全局视野）：
  - 所有角色秘密 ✓
  - 所有线索详情 ✓
  - 所有消息（包括私聊）✓

Agent 提示词（沙盒隔离）：
  - 仅自己的秘密 ✓
  - 仅 ownerRoleIds 包含自己的线索 ✓
  - 仅 receiverRoleIds 包含自己或为空（公开）的消息 ✓
  - 其他角色的秘密 ✗
```

**关键代码**：
```java
// 过滤线索：只保留该角色可见的
clueInstances.stream()
    .filter(ci -> ci.getOwnerRoleIds().contains(targetRole.getId()))

// 过滤消息：只保留公开或发给自己的
messages.stream()
    .filter(m -> m.getReceiverRoleIds() == null
            || m.getReceiverRoleIds().isEmpty()
            || m.getReceiverRoleIds().contains(targetRole.getId()))
```

---

## 七、Spring 事件驱动与异步

### Q28: Spring 的事件机制原理？@EventListener 和 ApplicationListener 的区别？

**事件机制**基于观察者模式：
1. 定义事件类（继承 `ApplicationEvent`）
2. 发布者调用 `applicationEventPublisher.publishEvent(event)`
3. 监听者通过 `@EventListener` 或实现 `ApplicationListener` 接收

**区别**：

| 特性 | @EventListener | ApplicationListener<E> |
|------|---------------|----------------------|
| 定义方式 | 方法注解 | 实现接口 |
| 灵活性 | 方法参数推断事件类型，支持 SpEL 条件 | 泛型指定事件类型 |
| 异步 | 配合 @Async 使用 | 需手动处理 |
| 推荐 | Spring 4.2+ 推荐 | 旧版方式 |

**项目使用**：
```java
// 发布
eventPublisher.publishEvent(new ChatMessageEvent(this, roomId, ...));

// 监听
@EventListener
public void onChatMessage(ChatMessageEvent event) { ... }
```

**好处**：`GameChatController` 和 `AgentOrchestrator` 完全解耦。Controller 不知道 AI 引擎的存在，只是发布事件。

### Q29: @Async 的原理？有哪些注意事项？

**原理**：Spring AOP 为 `@Async` 方法生成代理，将方法调用提交到指定的线程池异步执行。

**工作流程**：
1. `@EnableAsync` 启用异步支持
2. 调用 `@Async("aiExecutor")` 方法时，代理拦截调用
3. 将方法调用封装为 `Runnable` 提交到名为 `aiExecutor` 的 `TaskExecutor`
4. 调用方立即返回（void 或 `CompletableFuture`）

**注意事项**：
1. **同类调用失效**：`this.asyncMethod()` 不走代理，不会异步。必须通过注入的 Bean 调用
2. **异常处理**：void 返回类型的 @Async 方法异常默认只打日志，需自定义 `AsyncUncaughtExceptionHandler`
3. **线程池配置**：必须配置合理的线程池，否则默认使用 `SimpleAsyncTaskExecutor`（不复用线程）
4. **返回值**：需要异步结果时返回 `CompletableFuture<T>`

**项目配置**：
```java
@Bean("aiExecutor")
public TaskExecutor aiExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);      // 核心线程：常驻
    executor.setMaxPoolSize(16);      // 最大线程：高峰扩展
    executor.setQueueCapacity(100);   // 队列满后才扩展到最大线程
    executor.setThreadNamePrefix("ai-engine-");
    return executor;
}
```

### Q30: 线程池的核心参数和工作流程？

**核心参数**：
- `corePoolSize`：核心线程数（即使空闲也不销毁）
- `maxPoolSize`：最大线程数
- `queueCapacity`：等待队列容量
- `keepAliveTime`：非核心线程空闲存活时间
- `rejectedExecutionHandler`：拒绝策略

**工作流程**：
```
新任务到达
  ↓
当前线程 < corePoolSize? → 创建新核心线程执行
  ↓ 否
队列未满? → 放入队列等待
  ↓ 已满
当前线程 < maxPoolSize? → 创建非核心线程执行
  ↓ 否
执行拒绝策略（默认 AbortPolicy 抛异常）
```

**项目设计思考**：AI 调用耗时长（3-10秒），core=4 保证至少 4 个并发 AI 请求，max=16 应对突发，queue=100 缓冲排队请求。

---

## 八、并发编程

### Q31: ConcurrentHashMap 的原理？为什么线程安全？

**JDK 8+ 实现**：
- 底层是 `Node<K,V>[]` 数组 + 链表/红黑树
- **不再使用分段锁**，改为 CAS + `synchronized`（锁粒度是单个桶）
- 读操作无锁（volatile 保证可见性）
- 写操作只锁住冲突的那个桶

**关键方法**：
- `putIfAbsent()`：CAS 原子操作
- `compute()`、`merge()`：原子读-改-写
- `size()`：使用 `baseCount` + `CounterCell[]` 分散计数，减少竞争

**项目使用**：`PhaseTimerService` 用 `ConcurrentHashMap<String, ScheduledFuture<?>>` 管理每个房间的计时器，支持多线程安全的增删查改。

### Q32: ScheduledExecutorService 和 Timer 的区别？

| 特性 | ScheduledExecutorService | Timer |
|------|-------------------------|-------|
| 线程模型 | 线程池（多线程） | 单线程 |
| 异常处理 | 一个任务异常不影响其他 | 一个任务异常导致所有任务停止 |
| 时间精度 | 基于 System.nanoTime（单调时钟） | 基于 System.currentTimeMillis（受系统时间调整影响） |
| 任务类型 | 支持 Callable（有返回值） | 只支持 Runnable |

**项目使用**：`PhaseTimerService` 使用 `Executors.newScheduledThreadPool(2)` 管理发言超时、自由讨论超时、投票超时，2 个线程确保一个超时处理阻塞时不影响其他。

### Q33: volatile 关键字的作用和原理？

**作用**：
1. **可见性**：一个线程修改 volatile 变量后，其他线程立即可见
2. **有序性**：禁止指令重排序（通过内存屏障）

**原理**：
- 写操作后插入 StoreStore + StoreLoad 屏障，强制刷新到主内存
- 读操作前插入 LoadLoad + LoadStore 屏障，强制从主内存读取

**不保证原子性**：`volatile int count; count++` 不是原子操作（读-改-写三步）。

**项目相关**：ConcurrentHashMap 内部的 Node.val 和 Node.next 是 volatile 的，保证读操作无需加锁也能看到最新值。

---

## 九、设计模式

### Q34: 项目中用到了哪些设计模式？

**1. 策略模式（Strategy）— DmTool 工具系统**
```java
public interface DmTool {
    String name();
    Object execute(Object input, DmToolContext ctx);
}
// 9 个实现类：AuthorizeSearchTool、InitiateVoteTool、...
// DmToolRegistry 根据名称分发到对应策略
```
不同的工具是不同的策略，DM AI 选择调用哪个策略。

**2. 观察者模式（Observer）— Spring 事件机制**
```java
// 被观察者
eventPublisher.publishEvent(new ChatMessageEvent(...));
// 观察者
@EventListener
public void onChatMessage(ChatMessageEvent event) { ... }
```
发布者和订阅者完全解耦。

**3. 建造者模式（Builder）— Lombok @Builder**
```java
GameMessage.builder()
    .messageId(new ObjectId())
    .content(content)
    .isAi(true)
    .build();
```
项目中几乎所有 POJO 都使用 @Builder，提高可读性。

**4. 模板方法模式（Template Method）— PromptBuilder**
```java
// 固定流程：加载模板 → 替换变量 → 拼接消息 → 估算 Token
// 但每种提示词（DM/Agent/Compression/Review）的变量替换规则不同
```

**5. 代理模式（Proxy）— Spring AOP（@Async、@Transactional）**
```java
@Async("aiExecutor")  // Spring 生成代理，将调用提交到线程池
public void executeAgentReply(...) { ... }
```

**6. 注册表模式（Registry）— DmToolRegistry**
```java
@Service
public class DmToolRegistry {
    private final List<DmTool> tools; // Spring 自动注入所有实现
    // 运行时动态构建回调列表
}
```
通过 Spring DI 自动发现并注册所有工具，新增工具无需修改注册中心。

### Q35: 开闭原则在项目中的体现？

**开闭原则**：对扩展开放，对修改关闭。

**最佳体现 — DmTool 工具系统**：
- 新增一个 DM 工具：只需创建一个类实现 `DmTool` 接口 + 加 `@Component`
- 不需要修改 `DmToolRegistry`、`DmExecutor`、`AgentOrchestrator` 中的任何代码
- Spring DI 自动发现新工具并注册

```java
@Component
public class NewCustomTool implements DmTool {
    @Override public String name() { return "customAction"; }
    @Override public Object execute(Object input, DmToolContext ctx) { ... }
}
// 自动被 DmToolRegistry 的 List<DmTool> 注入
```

---

## 十、Vue 3 + TypeScript 前端

### Q36: Vue 3 Composition API 和 Options API 的区别？

| 特性 | Options API | Composition API |
|------|-------------|-----------------|
| 组织方式 | 按选项分类（data/methods/computed） | 按逻辑功能组织 |
| 复用 | Mixins（命名冲突风险） | Composables 函数（明确引入） |
| TypeScript | 支持较差（this 类型推断困难） | 原生友好（函数式，类型自然流动） |
| 学习曲线 | 低 | 中（需理解响应式原理） |

**项目使用 Composition API**：
```typescript
// composables/useWebSocket.ts
export function useWebSocket() {
    const connected = ref(false);
    function connect(roomId: string, onMessage: Function) { ... }
    function send(roomId: string, content: string) { ... }
    return { connected, connect, send, disconnect };
}
```

### Q37: Vue 3 的响应式原理（ref/reactive）？

**Vue 3 使用 Proxy 实现响应式**（Vue 2 使用 Object.defineProperty）：

**`reactive()`**：
```javascript
const state = reactive({ count: 0 });
// 内部：new Proxy(target, {
//   get(target, key) { track(target, key); return target[key]; },
//   set(target, key, value) { target[key] = value; trigger(target, key); }
// })
```

**`ref()`**：
```javascript
const count = ref(0);
// 内部：{ value: 0 }（包装为对象以支持基本类型的响应式）
// 模板中自动解包，script 中需 .value
```

**Proxy vs Object.defineProperty**：
- Proxy 可以拦截新增/删除属性、数组索引修改
- Object.defineProperty 需要递归遍历所有属性，Proxy 是惰性代理

### Q38: Pinia 和 Vuex 的区别？

| 特性 | Pinia | Vuex |
|------|-------|------|
| API 风格 | Composition API 友好 | Options 风格 |
| Mutations | 无（直接修改 state） | 必须通过 mutation |
| TypeScript | 原生支持，自动类型推断 | 需要大量类型声明 |
| 模块 | 扁平化 Store，无嵌套模块 | 嵌套 modules |
| 体积 | ~1KB | ~10KB |
| DevTools | 支持 | 支持 |

**项目使用 Pinia**：
```typescript
export const useAuthStore = defineStore('auth', () => {
    const accessToken = ref<string | null>(localStorage.getItem('accessToken'));
    const isLoggedIn = computed(() => !!accessToken.value);
    function setTokens(access: string, refresh: string) { ... }
    return { accessToken, isLoggedIn, setTokens };
});
```

### Q39: Axios 拦截器的工作原理？如何实现无感刷新 Token？

**拦截器链**：
```
请求 → [请求拦截器N] → ... → [请求拦截器1] → 发送
响应 → [响应拦截器1] → ... → [响应拦截器N] → 返回
```

**无感刷新 Token 实现**：
```typescript
// 响应拦截器
axios.interceptors.response.use(null, async (error) => {
    if (error.response?.status === 401 && !error.config._retry) {
        if (isRefreshing) {
            // 已有刷新请求，排队等待
            return new Promise((resolve) => {
                failedQueue.push({ resolve });
            }).then(() => axios(error.config));
        }
        isRefreshing = true;
        const newToken = await refreshToken();
        // 重试队列中的所有请求
        failedQueue.forEach(({ resolve }) => resolve());
        return axios(error.config);
    }
});
```

**关键点**：使用队列和标志位确保并发 401 时只刷新一次，其他请求排队等待。

### Q40: Vue Router 的导航守卫有哪些？

**全局守卫**：
- `beforeEach` — 每次导航前（常用于权限校验）
- `afterEach` — 导航完成后（如修改页面标题）
- `beforeResolve` — 组件内守卫和异步路由组件解析后

**路由守卫**：`beforeEnter` — 路由配置中定义

**组件守卫**：`onBeforeRouteLeave`、`onBeforeRouteUpdate`

**项目使用**：
```typescript
router.beforeEach((to) => {
    const auth = useAuthStore();
    if (to.path !== '/login' && !auth.isLoggedIn) {
        return '/login';
    }
});
```

---

## 十一、网络与协议

### Q41: HTTP 1.1 vs HTTP 2.0 vs HTTP 3.0？

| 特性 | HTTP 1.1 | HTTP 2.0 | HTTP 3.0 |
|------|----------|----------|----------|
| 传输层 | TCP | TCP | QUIC (UDP) |
| 多路复用 | 不支持（队头阻塞） | 支持（一个连接多个流） | 支持（无队头阻塞） |
| 头部压缩 | 无 | HPACK | QPACK |
| 服务端推送 | 无 | 支持 | 支持 |
| 连接建立 | TCP 三次握手 + TLS | 同 1.1 | 0-RTT / 1-RTT |

### Q42: TCP 三次握手和四次挥手？

**三次握手**（建立连接）：
```
Client → SYN(seq=x)           → Server    （客户端请求连接）
Client ← SYN+ACK(seq=y,ack=x+1) ← Server  （服务端同意并请求连接）
Client → ACK(ack=y+1)         → Server    （客户端确认，连接建立）
```

**四次挥手**（断开连接）：
```
Client → FIN(seq=u)           → Server    （客户端请求关闭）
Client ← ACK(ack=u+1)        ← Server    （服务端确认，半关闭）
Client ← FIN(seq=v)          ← Server    （服务端也请求关闭）
Client → ACK(ack=v+1)        → Server    （客户端确认，等待 2MSL 后关闭）
```

**为什么是三次握手？** 防止历史重复连接的 SYN 报文被服务端误接受（如果两次握手，服务端无法确认客户端是否收到 SYN+ACK）。

**为什么是四次挥手？** TCP 是全双工，关闭需要双方各发一次 FIN。服务端收到 FIN 后可能还有数据要发送，所以 ACK 和 FIN 分开发。

### Q43: CORS 跨域的原理和解决方案？

**同源策略**：浏览器限制 JS 只能访问同协议、同域名、同端口的资源。

**CORS（Cross-Origin Resource Sharing）**：
1. **简单请求**：GET/POST/HEAD 且 Content-Type 为 text/plain 等 → 浏览器直接发送，响应头有 `Access-Control-Allow-Origin` 则允许
2. **预检请求**：PUT/DELETE 或自定义头 → 先发 OPTIONS 请求，服务端返回允许的方法和头，通过后才发实际请求

**项目配置**：
```java
CorsConfiguration config = new CorsConfiguration();
config.addAllowedOriginPattern("http://localhost:*");
config.addAllowedMethod("*");
config.addAllowedHeader("*");
config.setAllowCredentials(true);
```

**WebSocket 的 CORS**：WebSocket 握手是 HTTP 请求，也受 CORS 限制。`registerStompEndpoints` 中需要 `setAllowedOriginPatterns("*")`。

---

## 十二、项目架构与场景设计题

### Q44: 为什么采用事件驱动架构而不是直接调用？

**直接调用**：
```java
// GameChatController 直接调用 AI 引擎
gameChatService.sendMessage(...);
agentOrchestrator.handleMessage(...); // 强耦合
```

**事件驱动**：
```java
// GameChatController 只发布事件
gameChatService.sendMessage(...);
eventPublisher.publishEvent(new ChatMessageEvent(...));
// AgentOrchestrator 独立监听
```

**好处**：
1. **解耦**：Controller 不知道 AI 引擎的存在，删除整个 AI 模块不影响基础聊天功能
2. **可扩展**：新增监听者（如消息统计、日志审计）无需修改发布者
3. **可测试**：可以独立测试 Controller（不触发 AI）和 Orchestrator（手动构造事件）

### Q45: 如何保证 AI 回复不会形成死循环？

**风险场景**：Agent A 回复 → 触发 Agent B 回复 → 触发 Agent A 回复 → ...

**项目防护措施**：
1. **源头过滤**：`AgentOrchestrator.onChatMessage()` 第一行：`if (event.isFromAi()) return;` — AI 消息不触发 AI 回复
2. **ChatMessageEvent 标记**：AI 消息的 `fromAi=true`，人类消息的 `fromAi=false`
3. **DM 控制**：自由讨论阶段由 DM AI 选择回复者，而非所有 AI 都回复

### Q46: 如果游戏中途 Redis 宕机怎么办？

**影响**：
- 所有进行中的游戏状态丢失（LiveGameRoom、消息、投票）
- JWT 黑名单丢失（已登出的 Token 可能重新生效）
- 剧本缓存丢失（可从 MongoDB 重新加载）

**现有缓解**：
- MongoDB 中的 GameRoom 在里程碑时更新（状态至少恢复到最近的保存点）
- GameRecord 在游戏结束时持久化到 MongoDB

**可优化方案**：
1. **Redis 持久化**：开启 AOF（appendonly yes）确保数据不完全丢失
2. **Redis Sentinel / Cluster**：高可用集群，自动故障转移
3. **关键操作双写**：幕次推进时同时写 Redis 和 MongoDB
4. **客户端降级**：WebSocket 断连后前端显示重连提示

### Q47: 如何优化 AI 响应延迟？用户等待 5-10 秒的体验问题？

**现有优化**：
1. **TYPING 信号**：AI 开始处理时广播 TYPING 指示器，用户知道 AI 正在思考
2. **异步执行**：`@Async("aiExecutor")` 不阻塞消息处理链
3. **上下文压缩**：减少发送给 LLM 的 Token 数量，缩短处理时间

**可进一步优化**：
1. **流式响应（Streaming）**：Spring AI 支持 `stream()` 方法，逐 Token 返回，用户立即看到内容
2. **预热缓存**：在阶段开始时预构建提示词模板
3. **降级模型**：简单回复使用更快的小模型（Haiku），复杂决策使用大模型（Sonnet）
4. **并行调用**：多个 Agent 需要回复时可以并行调用 LLM

### Q48: 如何设计搜证系统保证线索分发的正确性？

**挑战**：多个玩家可能同时在同一地点搜证，需要保证线索不重复分发。

**项目设计**：
1. **搜证次数限制**：每个角色有 `searchPower`（搜证次数），每次搜证扣 1
2. **线索过滤规则**：`clue.locationTag.contains(location) && clue.searchableRoleIds.contains(roleId)`
3. **去重机制**：检查 `alreadyFoundClueIds`，已发现的线索不再分发
4. **原子操作**：`AuthorizeSearchTool` 在单次执行中完成校验+扣除+分发+保存，存入 Redis

**潜在问题**：如果两个请求并发修改同一个 LiveGameRoom，Redis 的读-改-写不是原子的。可优化为 Redis Lua 脚本或乐观锁（版本号）。

### Q49: 这个项目的技术难点是什么？你是怎么解决的？

**难点 1：AI 角色的沙盒隔离**
- 问题：多个 AI 角色共享游戏数据，但各自只能看到自己的信息
- 解决：`PromptBuilder` 根据角色 ID 过滤线索和消息，在提示词层面实现隔离

**难点 2：上下文窗口管理**
- 问题：长时间游戏消息累积，超出 LLM 上下文限制
- 解决：双触发压缩（幕次切换 + Token 阈值）+ 滑动窗口保留最近消息 + 历史摘要

**难点 3：DM 工具调用的可扩展性**
- 问题：DM 需要执行搜证、投票、阶段推进等多种操作，且未来可能新增
- 解决：DmTool 接口 + Spring DI 自动发现 + Spring AI Function Calling，新增工具零修改

**难点 4：事件驱动的消息路由**
- 问题：不同阶段（轮流发言/自由讨论/投票）下 AI 的行为完全不同
- 解决：AgentOrchestrator 根据当前 PhaseType 分发到不同处理逻辑，DM AI 通过工具自主决定阶段转换

### Q50: 如果让你重新设计，你会改进哪些地方？

1. **消息存储**：当前所有消息存在一个 Redis List 中，消息量大时 `LRANGE` 全量读取性能差。改进：按幕次分 Key（`game:{roomId}:messages:stage:{n}`），或使用 Redis Streams
2. **LiveGameRoom 并发安全**：当前读-改-写不是原子操作。改进：使用 Redis 事务（MULTI/EXEC）或 Lua 脚本保证原子性
3. **流式 AI 响应**：当前是等 AI 完整回复后一次性发送。改进：使用 Spring AI 的 `stream()` + WebSocket 逐 Token 推送
4. **分层缓存**：热点剧本可增加本地缓存（Caffeine）→ Redis → MongoDB 三级缓存
5. **监控指标**：增加 AI 调用延迟、Token 消耗、成功率等 Prometheus 指标
6. **消息队列**：AI 处理高峰期可引入 RabbitMQ/Kafka 做流量削峰，替代内存中的 Spring Event
