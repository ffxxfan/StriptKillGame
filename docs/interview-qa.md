# 剧本杀项目 — 面试八股文全集（详细版）

> 基于项目实际技术栈：Spring Boot 3.x、Spring Security + JWT、Spring AI、MongoDB、Redis、WebSocket STOMP、Vue 3 + TypeScript + Pinia，涵盖框架原理、中间件、设计模式、并发、网络协议、前端工程化等方面。每题均附项目实战代码及深度追问。

---

## 目录

- [一、Spring Boot 核心原理](#一spring-boot-核心原理)
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
- [十三、Java 基础与 JVM](#十三java-基础与-jvm)
- [十四、Spring 框架深度](#十四spring-框架深度)

---

## 一、Spring Boot 核心原理

### Q1: Spring Boot 的自动配置原理是什么？请从源码层面讲解。

Spring Boot 自动配置的核心是 `@EnableAutoConfiguration` 注解（包含在 `@SpringBootApplication` 中）。

**完整启动链路**：

1. **入口**：`@SpringBootApplication` = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`

2. **加载阶段**：`@EnableAutoConfiguration` 通过 `@Import(AutoConfigurationImportSelector.class)` 导入选择器。`AutoConfigurationImportSelector` 实现了 `DeferredImportSelector` 接口（注意不是 `ImportSelector`），这意味着它会在所有 `@Configuration` 类处理完之后再执行，确保用户自定义的 Bean 优先注册。

3. **候选类发现**：`AutoConfigurationImportSelector.getAutoConfigurationEntry()` 调用 `ImportCandidates.load()` 方法，扫描所有 JAR 包的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件（Spring Boot 3.x 新格式，2.x 使用 `META-INF/spring.factories`），获取全部候选自动配置类的全限定名。Spring Boot 3.x 通常有 100+ 个候选类。

4. **条件过滤**：通过 `ConditionEvaluator` 对每个候选类上的条件注解求值：
   - `@ConditionalOnClass(MongoClient.class)` — classpath 中存在指定类才生效
   - `@ConditionalOnMissingBean(ChatModel.class)` — 容器中不存在该 Bean 才注册（让用户自定义 Bean 优先）
   - `@ConditionalOnProperty(prefix="game.ai", name="enabled", havingValue="true")` — 配置属性匹配才生效
   - `@ConditionalOnWebApplication` — Web 环境才生效

5. **排序与加载**：通过 `@AutoConfigureOrder`、`@AutoConfigureBefore/After` 控制配置类的加载顺序，最终过滤后的配置类被注册到 Spring 容器中。

**项目实例**：

```java
// 引入 spring-ai-starter-model-anthropic 后，
// AnthropicAutoConfiguration 检测到 classpath 中有 Anthropic 相关类，自动注册：
// 1. AnthropicApi — HTTP 客户端
// 2. AnthropicChatModel — 实现 ChatModel 接口
// 3. RetryTemplate — 重试策略
// 项目代码只需面向 ChatModel 接口编程：
@RequiredArgsConstructor
public class DmExecutor {
    private final ChatModel chatModel; // Spring 自动注入 AnthropicChatModel
}
```

**追问：如果我自定义了一个 `ChatModel` Bean，自动配置的还会生效吗？**

不会。因为自动配置类上标注了 `@ConditionalOnMissingBean(ChatModel.class)`，当检测到容器中已存在 `ChatModel` Bean 时，自动配置的 Bean 不会注册。这就是 `DeferredImportSelector` 延迟执行的意义 —— 确保用户的 `@Bean` 方法先执行，自动配置后执行时能正确检测到用户 Bean 的存在。

**追问：自动配置和 `@ComponentScan` 有什么区别？**

- `@ComponentScan`：扫描指定包路径下的 `@Component`、`@Service`、`@Controller` 等注解类，注册为 Bean。作用范围是当前项目代码。
- 自动配置：扫描的是第三方 JAR 中声明的配置类，通过条件注解按需加载。这两种机制协同工作：`@ComponentScan` 注册项目代码，自动配置注册第三方库。

---

### Q2: @ConfigurationProperties 和 @Value 的区别？适用场景？

| 特性 | @ConfigurationProperties | @Value |
|------|-------------------------|--------|
| 绑定方式 | 批量绑定前缀下所有属性到 POJO | 逐个注入单个值 |
| 类型安全 | 支持复杂类型、嵌套对象、List、Map | 仅基本类型、String 和 SpEL 表达式 |
| 松散绑定 | 支持（`token-threshold-ratio` → `tokenThresholdRatio`） | 不支持，必须精确匹配 |
| 校验 | 支持 JSR303（`@Validated` + `@NotNull`、`@Min`） | 不支持 |
| 元数据 | 配合 `spring-boot-configuration-processor` 生成 IDE 提示 | 无 |
| 动态刷新 | 配合 `@RefreshScope` 可动态刷新 | 需搭配 `@RefreshScope` |
| 适用场景 | 模块化配置（如 AI 引擎、JWT 参数） | 少量独立配置 |

**项目实例 — AiEngineProperties**：

```java
@Data
@Component
@ConfigurationProperties(prefix = "game.ai")
public class AiEngineProperties {
    private double charsPerToken = 3.5;      // 中文字符与 Token 的转换比
    private int maxContextTokens = 100000;    // 上下文窗口大小
    private double tokenThresholdRatio = 0.7; // Token 超限压缩触发阈值
    private int slidingWindowSize = 20;       // 滑动窗口保留最近消息数
}
```

对应配置文件：
```properties
game.ai.chars-per-token=3.5
game.ai.max-context-tokens=100000
game.ai.token-threshold-ratio=0.7
game.ai.sliding-window-size=20
```

**底层原理**：Spring Boot 启动时，`ConfigurationPropertiesBindingPostProcessor`（一个 `BeanPostProcessor`）会在 Bean 初始化后调用 `Binder` 将 `Environment` 中的属性绑定到标注了 `@ConfigurationProperties` 的 Bean 上。`Binder` 支持松散绑定（kebab-case、camelCase、UPPER_CASE 互转）和类型转换（String → int/double/Duration 等）。

**追问：`@ConfigurationProperties` 的 Bean 是如何被发现的？**

有两种方式：
1. 类上标注 `@Component`（本项目采用）—— 被 `@ComponentScan` 扫描注册
2. 在配置类上使用 `@EnableConfigurationProperties(AiEngineProperties.class)` —— 显式声明

---

### Q3: Spring Boot 3.x 相比 2.x 有哪些重要变化？

**1. Java 17 最低版本要求**：
- 支持 Records、Sealed Classes、Pattern Matching for instanceof、Text Blocks
- 本项目使用 Java 17，充分利用了增强的 switch 表达式和 var 局部变量推断

**2. Jakarta EE 迁移**（最大的破坏性变更）：
```java
// Spring Boot 2.x
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotBlank;

// Spring Boot 3.x
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
```
所有 `javax.*` 命名空间迁移到 `jakarta.*`，影响 Servlet、JPA、Validation、Mail 等所有 Java EE API。

**3. 自动配置文件格式变更**：
- 2.x：`META-INF/spring.factories` 中的 `EnableAutoConfiguration` key
- 3.x：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，每行一个配置类全名
- 新格式加载更快（无需解析 properties 格式），且不与其他 Factory 混淆

**4. Spring Security 6.x 重构**：
```java
// 2.x — 继承 WebSecurityConfigurerAdapter（已废弃）
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) { ... }
}

// 3.x — 本项目采用：Bean 注入 SecurityFilterChain
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/register",
                        "/api/auth/refresh").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated())
        .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

**5. 可观测性增强**：Micrometer Observation API + Micrometer Tracing（替代 Spring Cloud Sleuth），自动为 HTTP 请求、数据库调用、消息队列等生成跟踪信息。

**6. GraalVM 原生编译支持**：通过 AOT（Ahead of Time）引擎，将反射调用、动态代理等在编译期预处理，生成原生可执行文件，启动时间 < 100ms，内存占用降低 50%+。

---

### Q4: Spring Boot Starter 的原理是什么？如何自定义 Starter？

Starter 本质是一个**依赖聚合 + 自动配置**的 Maven/Gradle 模块。

**组成结构**：
```
my-spring-boot-starter/
├── pom.xml               ← 传递依赖聚合
my-spring-boot-starter-autoconfigure/
├── src/main/java/
│   └── MyAutoConfiguration.java    ← @AutoConfiguration + 条件注解
├── src/main/resources/
│   └── META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

**工作链路**：
1. 用户引入 Starter → Maven 传递引入所有依赖
2. Spring Boot 扫描 `AutoConfiguration.imports` → 发现自动配置类
3. 条件注解求值 → 按需注册 Bean
4. 用户通过 `application.properties` 覆盖默认配置

**项目实例**：引入 `spring-ai-starter-model-anthropic` 一个依赖，就自动获得：
- `AnthropicApi` — HTTP 客户端（含 API Key 管理、请求签名）
- `AnthropicChatModel` — 实现 `ChatModel` 接口（含消息格式转换、流式支持）
- `RetryTemplate` — 自动重试配置（API 限流时指数退避）
- `ObjectMapper` 定制 — Anthropic 专用的 JSON 序列化器

**自定义 Starter 步骤**：
1. 创建 `autoconfigure` 模块，编写 `@AutoConfiguration` 配置类
2. 在配置类上加条件注解（`@ConditionalOnClass`、`@ConditionalOnProperty`）
3. 使用 `@ConfigurationProperties` 暴露可配置参数
4. 在 `AutoConfiguration.imports` 中注册配置类全名
5. 创建 `starter` 模块，POM 中依赖 `autoconfigure` 模块 + 所有必需依赖

---

### Q5: Spring Boot 的 Profile 机制原理和高级用法？

Profile 允许针对不同环境（dev/test/prod）加载不同的配置和 Bean。

**激活方式**（优先级从高到低）：
1. 命令行参数：`--spring.profiles.active=prod`
2. JVM 系统属性：`-Dspring.profiles.active=prod`
3. 环境变量：`SPRING_PROFILES_ACTIVE=prod`
4. `application.properties`：`spring.profiles.active=dev`

**配置文件加载规则**：
```
application.properties          ← 所有环境加载（基础配置）
application-dev.properties      ← dev 环境加载（覆盖基础配置）
application-prod.properties     ← prod 环境加载
```
Profile 专属文件中的配置会覆盖基础文件中的同名配置。

**项目实例**：
```java
// DataInitializer.java — 仅在 dev 环境自动插入测试剧本数据
@Component
@Profile("dev")
public class DataInitializer implements CommandLineRunner {
    @Override
    public void run(String... args) {
        // 插入测试剧本、测试用户
    }
}
```

**高级用法 — Profile Groups**（Spring Boot 2.4+）：
```properties
# application.properties
spring.profiles.group.production=proddb,prodmq,prodcache
```
激活 `production` 时，自动激活 `proddb`、`prodmq`、`prodcache` 三个子 Profile。

**追问：`@Profile` 和 `@ConditionalOnProperty` 的区别？**

- `@Profile`：基于激活的 Profile 名称判断，语义是"环境选择"
- `@ConditionalOnProperty`：基于配置属性值判断，语义是"功能开关"
- 建议：环境相关用 `@Profile`，功能开关用 `@ConditionalOnProperty`

---

## 二、Spring Security + JWT 认证

### Q6: JWT 的组成、工作原理和安全注意事项？

JWT（JSON Web Token）由三部分组成，以 `.` 分隔：`Header.Payload.Signature`

**Header**（Base64URL 编码）：
```json
{ "alg": "HS256", "typ": "JWT" }
```

**Payload**（Base64URL 编码）：
```json
{
  "sub": "60a1b2c3d4e5f6a7b8c9d0e1",  // 用户 ID（对应 MongoDB ObjectId）
  "username": "player1",
  "type": "access",
  "jti": "550e8400-e29b-41d4-a716-446655440000",  // 唯一标识，用于黑名单
  "iat": 1700000000,
  "exp": 1700007200  // 2 小时后过期
}
```

**Signature**：
```
HMACSHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secretKey
)
```

**项目实现 — JwtService.java 核心代码**：

```java
public String generateAccessToken(String userId, String username) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration() * 1000);
    return Jwts.builder()
            .id(UUID.randomUUID().toString())        // jti: 唯一ID，用于黑名单
            .subject(userId)                          // sub: MongoDB ObjectId 十六进制
            .claim("username", username)              // 自定义声明
            .claim("type", "access")                  // 区分 Access/Refresh Token
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey())                // HMAC-SHA256 签名
            .compact();
}

private SecretKey getSigningKey() {
    // 从配置中读取 Base64 编码的密钥，解码为字节数组
    byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
    return Keys.hmacShaKeyFor(keyBytes);  // 至少 256 位（32 字节）
}
```

**安全注意事项**：
1. **Payload 不是加密的**：任何人都可以 Base64 解码读取内容，**绝对不要存敏感信息**（如密码、手机号）
2. **密钥安全**：HMAC-SHA256 密钥至少 256 位，存储在配置文件中且不能提交到 Git
3. **HTTPS 传输**：JWT 通过 HTTP 头传输，必须使用 HTTPS 防止中间人攻击
4. **过期时间**：Access Token 不宜过长（本项目 2 小时），减少泄露影响
5. **jti 唯一标识**：每个 Token 有唯一 ID，用于实现黑名单精确失效

**追问：JWT 和 Session 的对比？为什么选 JWT？**

| 特性 | JWT（本项目） | Session |
|------|-------------|---------|
| 状态 | 无状态（Token 自包含） | 有状态（服务端存储） |
| 扩展性 | 天然支持分布式（无需 Session 同步） | 需要 Redis Session 共享 |
| 存储 | 客户端存储 | 服务端存储（占内存） |
| 跨域 | 友好（Bearer Token 头） | 依赖 Cookie（跨域复杂） |
| 失效 | 无法主动失效（需黑名单） | 服务端直接删除 |
| 大小 | 较大（含 Payload） | 较小（仅 Session ID） |

本项目选 JWT 原因：游戏平台需要 WebSocket + REST API 并存，JWT 的 Bearer Token 机制对两种协议都友好；且未来可能多实例部署，JWT 无状态天然支持水平扩展。

---

### Q7: 你的项目如何实现 JWT 的主动失效？详细讲解 Redis 黑名单方案。

**问题本质**：JWT 是自包含的，签发后服务端无法"撤销"它。在过期时间之前，只要签名验证通过，Token 就是有效的。但用户登出、改密码时需要立即使 Token 失效。

**Redis 黑名单方案详解**：

```java
// ========== 1. 登出时：将 Access Token 的 jti 加入黑名单 ==========
public void blacklistAccessToken(Claims claims) {
    String jti = claims.getId();
    // 关键：TTL = Token 的剩余有效期，而非固定时间
    // 这样 Token 自然过期后，黑名单记录也自动清理，不浪费内存
    long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
    if (remaining > 0) {
        redisTemplate.opsForValue().set(
                BLACKLIST_PREFIX + jti, "1",  // value 只需占位符
                remaining, TimeUnit.MILLISECONDS);
    }
}

// ========== 2. 验证时：检查是否在黑名单中 ==========
public Claims validateAccessToken(String token) {
    try {
        Claims claims = parseToken(token);
        // 类型检查：必须是 access 类型
        if (!"access".equals(claims.get("type", String.class))) return null;
        // 黑名单检查：jti 存在于 Redis 中则拒绝
        if (isBlacklisted(claims.getId())) return null;
        return claims;
    } catch (JwtException e) {
        log.warn("Access Token 验证失败: {}", e.getMessage());
        return null;
    }
}

public boolean isBlacklisted(String jti) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
}

// ========== 3. 登出同时删除 Refresh Token ==========
public void removeRefreshToken(String userId) {
    redisTemplate.delete(REFRESH_PREFIX + userId);
}
```

**AuthService 中的调用链**：

```java
// 登出
public void logout(Claims accessTokenClaims) {
    String userId = accessTokenClaims.getSubject();
    jwtService.blacklistAccessToken(accessTokenClaims);  // 1. 黑名单当前 Access Token
    jwtService.removeRefreshToken(userId);                // 2. 删除 Refresh Token
    // 效果：当前 Access Token 立即失效，Refresh Token 无法刷新新 Token
}

// 改密码 — 更严格，所有设备都需要重新登录
public void changePassword(Claims accessTokenClaims, ChangePasswordRequest request) {
    String userId = accessTokenClaims.getSubject();
    // ... 验证旧密码、更新新密码 ...
    jwtService.blacklistAccessToken(accessTokenClaims);  // 黑名单当前 Token
    jwtService.removeRefreshToken(userId);                // 删除 Refresh Token
    // 其他设备的 Access Token 在过期后无法刷新，被迫重新登录
}
```

**Redis Key 设计**：
```
auth:blacklist:{jti}    → "1"    TTL=Token剩余有效期    // 精确到毫秒
auth:refresh:{userId}   → token  TTL=30天               // 一个用户只存一个
```

**追问：为什么黑名单的 TTL 要设置为 Token 的剩余有效期而不是固定值？**

因为 Token 过期后自然失效，黑名单记录就没有存在的必要了。如果用固定 TTL（比如 24 小时），会出现两个问题：
1. **浪费内存**：Token 已过期但黑名单还没过期，白白占用 Redis 空间
2. **覆盖不足**：如果固定 TTL < Token 有效期，Token 还没过期但黑名单已清理，Token 又"复活"了

用 `remaining = expiration - now` 确保精确覆盖 Token 的剩余生命周期。

**追问：这个方案有什么缺点？如何优化？**

缺点：每次请求都要查 Redis（`hasKey` 操作），增加了一次网络 I/O。

优化方案：
1. **本地缓存 + Redis 双层**：用 Caffeine 缓存最近查过的 jti，命中率高时减少 Redis 查询
2. **布隆过滤器前置**：Redis 中维护布隆过滤器，`BF.EXISTS` 判断 jti 是否可能在黑名单中，false 则一定不在（无需查 Key）
3. **短期 Token 免检**：如果 Access Token 有效期很短（如 5 分钟），可以不做黑名单，牺牲几分钟的即时失效换取性能

---

### Q8: Access Token + Refresh Token 双 Token 机制详解

**完整流程图**：

```
┌─────────┐                    ┌─────────┐
│  Client  │                    │  Server  │
└────┬────┘                    └────┬────┘
     │  1. POST /api/auth/login     │
     │  {username, password}        │
     │─────────────────────────────>│
     │                              │  验证密码 (BCrypt)
     │                              │  生成 AccessToken (2h)
     │                              │  生成 RefreshToken (30d) → 存 Redis
     │  {accessToken, refreshToken} │
     │<─────────────────────────────│
     │                              │
     │  2. GET /api/rooms           │
     │  Authorization: Bearer {AT}  │
     │─────────────────────────────>│  JwtAuthFilter 验证 AT
     │  200 OK                      │
     │<─────────────────────────────│
     │                              │
     │  3. GET /api/rooms (AT过期)  │
     │  Authorization: Bearer {AT}  │
     │─────────────────────────────>│  AT 过期 → 401
     │  401 Unauthorized            │
     │<─────────────────────────────│
     │                              │
     │  4. POST /api/auth/refresh   │
     │  {refreshToken}              │
     │─────────────────────────────>│  验证 RT
     │                              │  Token Rotation: 生成新 AT + 新 RT
     │                              │  旧 RT 在 Redis 中被新 RT 覆盖
     │  {newAccessToken, newRT}     │
     │<─────────────────────────────│
     │                              │
     │  5. 重试原始请求 (新 AT)      │
     │─────────────────────────────>│
```

**Refresh Token 存储策略**：

```java
public String generateRefreshToken(String userId) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration() * 1000);
    String token = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId)
            .claim("type", "refresh")    // 明确标记为 refresh 类型
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey())
            .compact();
    // 关键：存入 Redis，一个用户只保留一个 Refresh Token
    redisTemplate.opsForValue().set(
            REFRESH_PREFIX + userId, token,
            jwtProperties.getRefreshTokenExpiration(), TimeUnit.SECONDS);
    return token;
}

public Claims validateRefreshToken(String token) {
    try {
        Claims claims = parseToken(token);
        if (!"refresh".equals(claims.get("type", String.class))) return null;
        String userId = claims.getSubject();
        // 关键：必须与 Redis 中存储的完全一致（防止旧 Token 复用）
        String stored = redisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
        if (!token.equals(stored)) return null;  // Token Rotation 检测
        return claims;
    } catch (JwtException e) {
        return null;
    }
}
```

**Token Rotation（令牌轮换）安全机制**：

```java
// AuthService.refresh()
public TokenResponse refresh(RefreshTokenRequest request) {
    Claims claims = jwtService.validateRefreshToken(request.getRefreshToken());
    if (claims == null) throw new IllegalArgumentException("Refresh Token 无效或已过期");
    String userId = claims.getSubject();
    User user = userService.findById(new ObjectId(userId))
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    String accessToken  = jwtService.generateAccessToken(userId, user.getUsername());
    String refreshToken = jwtService.generateRefreshToken(userId); // 生成新 RT，旧 RT 被 Redis 覆盖
    return TokenResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(jwtService.getAccessTokenExpiration())
            .build();
}
```

**Token Rotation 的安全意义**：每次刷新都生成新的 Refresh Token 并覆盖旧的。如果攻击者窃取了旧的 Refresh Token，在受害者下次刷新后该 Token 就会失效（Redis 中已被新 Token 覆盖）。攻击者再次使用旧 Token 时 `!token.equals(stored)` 校验失败。

**追问：如果攻击者先于受害者使用窃取的 Refresh Token 会怎样？**

攻击者先刷新 → Redis 中存储攻击者的新 RT → 受害者的旧 RT 失效 → 受害者刷新失败 → 被迫重新登录 → 用户发现异常。这种设计无法完全防止窃取，但能确保窃取被检测到（双方中有一方一定会刷新失败）。

---

### Q9: Spring Security 过滤器链的完整工作原理？

**Servlet 容器层面**：

```
Tomcat Filter Chain
  ├── CharacterEncodingFilter
  ├── DelegatingFilterProxy (name="springSecurityFilterChain")
  │     └── FilterChainProxy (Spring Security 的入口)
  │           └── SecurityFilterChain (一组有序的 Security Filter)
  │                 ├── DisableEncodeUrlFilter
  │                 ├── WebAsyncManagerIntegrationFilter
  │                 ├── SecurityContextHolderFilter
  │                 ├── HeaderWriterFilter
  │                 ├── CorsFilter                    ← CORS 预检处理
  │                 ├── LogoutFilter
  │                 ├── JwtAuthenticationFilter        ← 本项目自定义
  │                 ├── UsernamePasswordAuthenticationFilter
  │                 ├── RequestCacheAwareFilter
  │                 ├── SecurityContextHolderAwareRequestFilter
  │                 ├── AnonymousAuthenticationFilter
  │                 ├── SessionManagementFilter
  │                 ├── ExceptionTranslationFilter     ← 异常转 401/403
  │                 └── AuthorizationFilter            ← 权限校验
  ├── DispatcherServlet
  │     └── Controller
```

**DelegatingFilterProxy 的作用**：桥接 Servlet Filter 和 Spring Bean。Servlet 容器管理 Filter 生命周期，但 `FilterChainProxy` 是 Spring Bean（需要依赖注入）。`DelegatingFilterProxy` 从 ApplicationContext 中查找名为 `springSecurityFilterChain` 的 Bean 并委托给它。

**本项目的 JwtAuthenticationFilter 详解**：

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    // 公开路径不走 JWT 校验
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PUBLIC_PATHS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. 从 Authorization 头提取 Token
        String token = extractToken(request);
        if (token != null) {
            // 2. 验证 Token（签名 + 过期 + 黑名单）
            Claims claims = jwtService.validateAccessToken(token);
            if (claims != null) {
                // 3. 构建 Authentication 对象放入 SecurityContext
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                claims,    // Principal 设为 Claims（后续 Controller 可直接获取）
                                null,      // Credentials
                                Collections.singletonList(
                                        new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        // 4. 无论是否认证成功，都继续 Filter Chain
        // 未认证的请求最终由 AuthorizationFilter 拒绝（返回 401/403）
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

**为什么继承 `OncePerRequestFilter` 而不是 `Filter`？**

`OncePerRequestFilter` 保证每个请求只执行一次过滤逻辑。在 Servlet 容器中，请求转发（`forward`）和包含（`include`）可能导致同一个 Filter 被多次调用。`OncePerRequestFilter` 通过 `request.getAttribute(ALREADY_FILTERED_SUFFIX)` 标记避免重复执行。

**为什么 `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`？**

`UsernamePasswordAuthenticationFilter` 是 Spring Security 默认的表单登录认证 Filter。本项目使用 JWT 不需要表单登录，所以在它之前插入 JWT Filter。请求先走 JWT 认证，如果认证成功就设置了 `SecurityContext`，后续 Filter 不会再处理。

**追问：SecurityContextHolder 的工作模式？**

默认使用 `ThreadLocal` 存储 `SecurityContext`（`MODE_THREADLOCAL`）。这意味着同一线程内的任何代码都可以通过 `SecurityContextHolder.getContext().getAuthentication()` 获取当前用户信息。但 `@Async` 异步线程默认无法继承，需要设置 `MODE_INHERITABLETHREADLOCAL` 或在 `ThreadPoolTaskExecutor` 上包装 `DelegatingSecurityContextRunnable`。

---

### Q10: BCrypt 加密的原理？为什么比 MD5/SHA 更安全？

**BCrypt 内部结构**：

生成的哈希值格式：`$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy`

```
$2a$    — 算法标识（BCrypt）
10$     — 成本因子（Cost Factor），2^10 = 1024 轮 Blowfish 加密
N9qo8uLOickgx2ZMRZoMye  — 22字符的盐（128位，随机生成，编码后嵌入结果中）
IjZAgcfl7p92ldGxad68LJZdL17lhWy — 31字符的哈希值
```

**BCrypt 算法步骤**（Blowfish 密钥扩展）：
1. 将密码和盐作为输入
2. 初始化 Blowfish 状态
3. 执行 `2^cost` 轮密钥扩展（每轮涉及大量内存访问和计算）
4. 加密固定明文 `OrpheanBeholderScryDoubt` 64 次
5. 输出最终哈希

**为什么比 MD5/SHA 安全？**

| 维度 | MD5/SHA | BCrypt |
|------|---------|--------|
| 设计目的 | 快速计算消息摘要 | 慢速密码哈希 |
| 速度 | GPU 每秒数十亿次 | 一次约 100ms（cost=10） |
| 盐值 | 需手动生成和存储 | 自动生成，嵌入结果中 |
| 自适应 | 无法调整 | cost+1 → 计算量翻倍 |
| 暴力破解 | 字典攻击每秒尝试百亿次 | 每秒仅能尝试约 10 次 |
| 彩虹表 | 无盐时可用 | 每个密码盐不同，无法用彩虹表 |

**项目使用**：

```java
// SecurityConfig.java — 注册 BCryptPasswordEncoder Bean
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();  // 默认 cost=10
}

// AuthService.java — 注册时加密
user.setPassword(passwordEncoder.encode(request.getPassword()));
// encode() 内部自动生成随机盐 → BCrypt 哈希 → 返回 "$2a$10$..." 格式字符串

// AuthService.java — 登录时验证
authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
// DaoAuthenticationProvider 内部调用 passwordEncoder.matches(rawPassword, encodedPassword)
// matches() 从 encodedPassword 中提取盐，对 rawPassword 用相同盐和 cost 重新计算，比较结果

// AuthService.java — 改密码时验证旧密码
if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword()))
    throw new IllegalArgumentException("旧密码不正确");
```

**追问：cost 因子设置为多少合适？**

取决于服务器性能和安全需求。原则：一次哈希耗时 100-500ms 之间：
- cost=10：约 100ms（适合大多数场景，本项目使用）
- cost=12：约 400ms（高安全要求）
- cost=14：约 1.5s（银行级，用户体验下降）

每增加 1，计算量翻倍。随着 CPU/GPU 性能提升，应定期提高 cost 值。BCrypt 的优势在于可以在不修改代码的情况下通过调整 cost 适应硬件升级。

---

### Q11: Spring Security 中的 AuthenticationManager 工作流程？

**完整认证链路**（以本项目登录为例）：

```java
// 1. AuthService.login() 触发认证
Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(username, password));
```

**内部执行过程**：

```
AuthenticationManager.authenticate(token)
  └── ProviderManager.authenticate(token)
        └── 遍历 AuthenticationProvider 列表
              └── DaoAuthenticationProvider.authenticate(token)
                    │
                    ├── 1. retrieveUser(): 调用 UserDetailsService.loadUserByUsername()
                    │     └── 本项目: UserService implements UserDetailsService
                    │           └── 查询 MongoDB: userRepository.findByUsername()
                    │           └── 返回 UserDetails (包含 password hash)
                    │
                    ├── 2. additionalAuthenticationChecks():
                    │     └── passwordEncoder.matches(presentedPassword, userDetails.getPassword())
                    │     └── BCrypt 验证: 从存储的 hash 中提取盐 → 重新计算 → 比较
                    │
                    └── 3. 验证成功: 返回已认证的 Authentication 对象
                         验证失败: 抛出 BadCredentialsException
```

**项目中的 DaoAuthenticationProvider 配置**：

```java
// SecurityConfig.java
@Bean
public DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userService);  // 告诉 provider 从哪里加载用户
    provider.setPasswordEncoder(passwordEncoder()); // 告诉 provider 用什么算法验证密码
    return provider;
}
```

**追问：`ProviderManager` 为什么要遍历多个 `AuthenticationProvider`？**

设计为责任链模式，支持多种认证方式共存。例如：
- `DaoAuthenticationProvider` 处理用户名密码认证
- `JwtAuthenticationProvider` 处理 JWT Token 认证
- `OAuth2AuthenticationProvider` 处理第三方登录

每个 Provider 通过 `supports(Class<?> authentication)` 判断是否能处理当前认证请求。第一个支持且认证成功的 Provider 返回结果，后续 Provider 不再执行。

---

## 三、MongoDB

### Q12: MongoDB 和 MySQL 的核心区别？什么场景选 MongoDB？

| 特性 | MongoDB | MySQL |
|------|---------|-------|
| 数据模型 | 文档型（BSON，类 JSON） | 关系型（表、行、列） |
| Schema | 灵活，同集合可存不同结构的文档 | 严格 Schema，DDL 变更需迁移 |
| JOIN | 不支持传统 JOIN（用 `$lookup` 聚合或嵌入替代） | 原生支持多表 JOIN |
| 事务 | 4.0+ 支持多文档 ACID 事务 | 原生支持（InnoDB） |
| 扩展方式 | 水平分片（Sharding）+ 副本集 | 垂直扩展为主，读写分离 |
| 索引 | B-Tree、地理空间、全文、TTL 索引 | B+Tree、全文、空间 |
| 存储引擎 | WiredTiger（默认） | InnoDB（默认） |
| 适合 | 快速迭代、文档嵌套、半结构化数据 | 强一致性、复杂查询、强关联数据 |

**本项目选 MongoDB 的具体理由**：

剧本数据是**深度嵌套的树状结构**：

```java
// Script.java — 一级嵌套：角色、线索、阶段
@Document(collection = "scripts")
public class Script {
    @Id private ObjectId id;
    private String title;
    private List<Role> roles;          // 嵌入文档：2-15个角色
    private List<Clue> clues;          // 嵌入文档：线索库
    private List<ScriptStage> stages;  // 嵌入文档：游戏阶段
    private String dmConfig;           // DM AI 全局设定
}

// ScriptStage — 二级嵌套：阶段包含多个 Phase
public class ScriptStage {
    private int stageNumber;
    private String stageTitle;
    private Map<String, String> contentMap;  // roleId → 角色专属剧情
    private List<StagePhase> phases;         // 二级嵌套：阶段内的多个 Phase
}

// Role — 一级嵌套：角色数据
public class Role {
    private ObjectId id;
    private String name;
    private String secret;       // 角色秘密
    private String prompt;       // AI 扮演提示词
    private int searchPower;     // 搜证次数
    private List<ObjectId> searchableClueIds;  // 可搜索的线索列表
}
```

如果用 MySQL，这个结构需要至少 6 张表：`scripts`、`roles`、`clues`、`script_stages`、`stage_phases`、`content_map`，加载一个完整剧本需要 6 次 JOIN 查询。而 MongoDB 只需一次 `findById(scriptId)` 就能获取完整的剧本文档。

**追问：MongoDB 的 16MB 文档大小限制，这个项目的 Script 文档会超限吗？**

不会。一个包含 10 个角色、50 条线索、5 个阶段的剧本，JSON 大约 50-200KB，远低于 16MB。如果未来支持富媒体剧本（嵌入图片），应将图片存储在 GridFS 或 OSS 中，文档里只存 URL。

---

### Q13: MongoDB 的嵌入文档 vs 引用文档？项目中如何选择？

**嵌入文档**（Denormalization）：子文档直接嵌在父文档内

```json
{
  "title": "剧本A",
  "roles": [
    {"_id": ObjectId("..."), "name": "侦探", "secret": "我其实是卧底"},
    {"_id": ObjectId("..."), "name": "医生", "secret": "我目睹了犯罪"}
  ]
}
```

**引用文档**（Normalization）：通过 ObjectId 关联

```json
// scripts 集合
{ "title": "剧本A", "roleIds": [ObjectId("aaa"), ObjectId("bbb")] }
// roles 集合
{ "_id": ObjectId("aaa"), "name": "侦探", "secret": "..." }
```

**选择决策树**：

```
数据是否总是一起访问？
  ├── 是 → 嵌入（如 Script 内的 Role、Clue）
  └── 否 → 是否被多个父文档引用？
              ├── 是 → 引用（避免冗余）
              └── 否 → 子文档是否频繁独立更新？
                        ├── 是 → 引用（避免读-改-写整个父文档）
                        └── 否 → 嵌入
```

**项目中的设计决策及理由**：

| 关系 | 选择 | 理由 |
|------|------|------|
| Script ← Role | 嵌入 | 角色不会独立于剧本存在；加载剧本必须加载所有角色；一个角色只属于一个剧本 |
| Script ← Clue | 嵌入 | 同上，线索是剧本的一部分 |
| Script ← ScriptStage | 嵌入 | 阶段定义是剧本的核心结构，总是一起访问 |
| GameRoom ← Member | 嵌入 | 成员信息总是随房间一起查询，成员不跨房间共享 |
| User ↔ GameRecord | 独立文档 | GameRecord 在游戏结束后独立查询（历史回顾），不与任何活跃数据一起使用 |

**追问：嵌入文档的更新问题如何处理？**

嵌入文档的更新需要使用 MongoDB 的 `$set` + 数组定位操作符：

```javascript
// 更新 Script 中某个角色的 searchPower
db.scripts.updateOne(
  { "_id": scriptId, "roles._id": roleId },
  { "$set": { "roles.$.searchPower": 3 } }   // $ 定位到匹配的数组元素
)
```

但本项目中 Script 是**不可变的静态数据**（游戏模板），运行时状态存储在 Redis 的 `LiveGameRoom` 中，所以不存在频繁更新嵌入文档的问题。

---

### Q14: Spring Data MongoDB 的 @Aggregation 和聚合管道？

**MongoDB 聚合管道**类似于 Unix 管道 `|`，每个阶段接收上一个阶段的输出作为输入：

```
集合文档 → $match → $group → $sort → $project → 最终结果
```

**常用阶段**：
| 阶段 | SQL 等价 | 说明 |
|------|---------|------|
| `$match` | WHERE | 过滤文档 |
| `$group` | GROUP BY | 分组聚合（sum/avg/count） |
| `$project` | SELECT | 字段投影（选择/重命名/计算字段） |
| `$sort` | ORDER BY | 排序 |
| `$limit` / `$skip` | LIMIT / OFFSET | 分页 |
| `$lookup` | LEFT JOIN | 左外连接另一个集合 |
| `$unwind` | — | 将数组字段展开为多条文档 |
| `$sample` | ORDER BY RAND() | 随机采样 |

**项目使用 — 随机抽取剧本**：

```java
// ScriptRepository.java
public interface ScriptRepository extends MongoRepository<Script, ObjectId> {
    @Aggregation(pipeline = { "{ $sample: { size: ?0 } }" })
    List<Script> findRandomScripts(int count);
}
```

`$sample` 阶段使用**伪随机游标**从集合中均匀抽样，时间复杂度 O(N)（N 为抽样数），比 `findAll() + Collections.shuffle()` 效率高得多（后者需要加载全部文档到内存）。

**追问：`$sample` 的内部实现原理？**

当 `size < 5% * 集合文档数` 时，MongoDB 使用**随机游标**算法：在 B-Tree 索引上随机选择起始位置，然后扫描获取指定数量的文档。当 `size >= 5%` 时，回退到全表扫描 + 伪随机选择。

---

### Q15: ObjectId 的组成、特点和分布式唯一性保证？

**ObjectId 结构**（12 字节 = 24 位十六进制字符串）：

```
| 4 字节 |     5 字节     |  3 字节  |
| 时间戳  | 随机值(进程级)  | 递增计数器 |
```

- **时间戳**（4 字节）：Unix 时间戳（秒），可通过 `objectId.getDate()` 提取创建时间
- **随机值**（5 字节）：进程启动时生成的随机数（包含机器标识和进程 ID 的信息）
- **递增计数器**（3 字节）：从随机值开始递增，每次生成 +1，上限约 1677 万（2^24）

**分布式唯一性保证**：
- 同一秒 + 同一进程：靠递增计数器区分（每秒最多 2^24 = ~1677 万个）
- 同一秒 + 不同进程：靠 5 字节随机值区分（碰撞概率 < 2^-40）
- 不同秒：靠时间戳区分

**vs 自增 ID vs UUID**：

| 特性 | ObjectId | 自增 ID | UUID v4 |
|------|----------|---------|---------|
| 长度 | 12 字节 | 4/8 字节 | 16 字节 |
| 有序性 | 大致有序（秒级） | 严格有序 | 完全无序 |
| 分布式 | 无需协调 | 需要中心分配 | 无需协调 |
| 索引效率 | 较好（有序性利于 B-Tree） | 最好 | 较差（无序导致页分裂） |
| 信息泄露 | 可推断创建时间 | 可推断总量 | 无信息 |

**项目使用**：

```java
// 所有 MongoDB 实体使用 ObjectId 主键
@Document(collection = "scripts")
public class Script {
    @Id
    private ObjectId id;
    // ...
}

// Jackson 序列化配置：ObjectId → 24位十六进制字符串
// JacksonConfig 中注册 ObjectIdSerializer，前端看到的是 "60a1b2c3d4e5f6a7b8c9d0e1" 格式
```

---

## 四、Redis

### Q16: Redis 的常用数据结构及适用场景？结合项目详解。

| 结构 | 底层实现 | 时间复杂度 | 适用场景 |
|------|---------|-----------|----------|
| String | SDS（Simple Dynamic String） | GET/SET: O(1) | 缓存、计数器、分布式锁 |
| Hash | ziplist / hashtable | HGET/HSET: O(1) | 对象属性存储 |
| List | quicklist（ziplist + 链表） | LPUSH/RPUSH: O(1), LRANGE: O(S+N) | 消息队列、时间线 |
| Set | intset / hashtable | SADD/SISMEMBER: O(1) | 标签、去重、交并差集 |
| Sorted Set | skiplist + hashtable | ZADD/ZRANGE: O(log N) | 排行榜、延迟队列 |
| Stream | Radix tree | XADD/XREAD: O(1)/O(N) | 消息队列（消费者组） |

**本项目 Redis Key 全景图**：

```
# ===== 认证相关 =====
auth:blacklist:{jti}       → "1"             TTL=Token剩余有效期   [String] JWT黑名单
auth:refresh:{userId}      → refreshToken    TTL=30天             [String] Refresh Token存储

# ===== 游戏房间状态 =====
game:room:{roomId}         → LiveGameRoom JSON  TTL=12h(活跃)/2h(空闲)  [String] 房间完整状态
game:messages:{roomId}     → [msg1, msg2, ...]  TTL=12h                  [List] 聊天消息队列

# ===== AI 记忆系统 =====
game:{roomId}:memory:{stageNumber} → StageSummary JSON  TTL=12h  [String] 阶段压缩摘要

# ===== 剧本缓存 =====
script:{scriptId}          → Script JSON     TTL=24h             [String] 剧本缓存

# ===== 用户-房间绑定 =====
user:{userId}:activeRoom   → roomId          TTL=12h             [String] 用户当前所在房间
```

**为什么游戏房间状态用 String 而不是 Hash？**

```java
// LiveGameRoomService.java — 使用 String 存储整个 JSON
public void save(LiveGameRoom live) {
    live.setLastActiveAt(LocalDateTime.now());
    String json = objectMapper.writeValueAsString(live);
    redisTemplate.opsForValue().set(
            ROOM_KEY_PREFIX + live.getRoomId(), json,
            ACTIVE_TTL_HOURS, TimeUnit.HOURS);
}
```

原因：
1. `LiveGameRoom` 包含嵌套列表（`members`、`clueInstances`），Hash 无法直接存储复杂嵌套结构
2. 游戏房间状态几乎每次都是整体读写（读取完整状态 → 修改 → 写回），Hash 的字段级操作优势不明显
3. JSON 字符串在 redis-cli 中可读性好，方便调试

**聊天消息使用 List 的原因**：

```java
// GameChatService 中存储消息（简化）
redisTemplate.opsForList().rightPush(
        MESSAGES_KEY_PREFIX + roomId,
        objectMapper.writeValueAsString(message));

// 读取所有消息
List<String> msgs = redisTemplate.opsForList().range(
        MESSAGES_KEY_PREFIX + roomId, 0, -1);
```

Redis List 的 `RPUSH` 是 O(1) 操作，天然保证消息按发送时间有序。`LRANGE 0 -1` 获取全部消息虽然是 O(N)，但游戏期间消息量通常在几百到几千条，完全可以接受。

---

### Q17: Redis 的过期策略和内存淘汰策略？

**过期策略**（Key 到期后何时被删除）：

Redis 使用**惰性删除 + 定期删除**双策略：

1. **惰性删除（Lazy Expiration）**：客户端访问某个 Key 时，Redis 先检查是否过期。过期则删除并返回空。
   - 优点：CPU 开销最小（只在访问时检查）
   - 缺点：如果过期 Key 不再被访问，永远不会被删除 → 内存泄漏

2. **定期删除（Periodic Expiration）**：Redis 每秒执行 10 次（`hz` 配置，默认 10）：
   - 从设置了过期时间的 Key 中随机抽取 20 个
   - 删除其中已过期的
   - 如果过期比例 > 25%，重复此过程
   - 每轮有时间限制（25ms），避免阻塞主线程

3. **两者协同**：定期删除兜底清理无人访问的过期 Key，惰性删除确保不返回过期数据。

**内存淘汰策略**（`maxmemory-policy`，内存达到上限时）：

| 策略 | 范围 | 算法 | 适用场景 |
|------|------|------|----------|
| `noeviction` | — | 拒绝写入 | 不允许数据丢失（默认） |
| `allkeys-lru` | 所有 Key | 近似 LRU | 通用缓存（推荐） |
| `volatile-lru` | 有 TTL 的 Key | 近似 LRU | 缓存 + 持久化混用 |
| `allkeys-lfu` | 所有 Key | LFU | 热点数据明显的场景 |
| `volatile-lfu` | 有 TTL 的 Key | LFU | 同上 |
| `allkeys-random` | 所有 Key | 随机 | 均匀访问模式 |
| `volatile-random` | 有 TTL 的 Key | 随机 | — |
| `volatile-ttl` | 有 TTL 的 Key | 最短 TTL 优先 | 优先淘汰即将过期的 |

**Redis 的近似 LRU 实现**：Redis 不维护完整的 LRU 链表（太占内存），而是对每个 Key 记录最后访问时间戳（`lru` 字段，24 位），淘汰时随机采样 N 个 Key（`maxmemory-samples`，默认 5），删除其中最旧的。

**项目的 TTL 设计策略**：

```java
// 1. 活跃房间 → 12小时 TTL
private static final long ACTIVE_TTL_HOURS = 12;

// 2. 空闲房间 → 缩短到 2小时（所有人类玩家离线后）
private static final long IDLE_TTL_HOURS = 2;

public void setMemberOnline(ObjectId roomId, ObjectId userId, boolean online) {
    // ... 更新在线状态 ...
    boolean anyHumanOnline = live.getMembers().stream()
            .anyMatch(m -> !m.isAi() && m.isOnline());
    if (!anyHumanOnline) {
        // 所有人类离线 → 缩短 TTL，加速回收
        redisTemplate.expire(ROOM_KEY_PREFIX + roomId.toHexString(),
                IDLE_TTL_HOURS, TimeUnit.HOURS);
    }
}

// 3. JWT 黑名单 → TTL = Token 剩余有效期（精确匹配）
long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
redisTemplate.opsForValue().set(key, "1", remaining, TimeUnit.MILLISECONDS);

// 4. 剧本缓存 → 24小时 TTL
private static final long TTL_HOURS = 24;
```

---

### Q18: Redis 缓存穿透、击穿、雪崩分别是什么？怎么解决？

**一、缓存穿透** — 查询不存在的数据

```
客户端 → 缓存（MISS）→ 数据库（NULL）→ 不缓存 → 下次还是 MISS → 数据库被打穿
```

解决方案：
1. **缓存空值**：查询结果为 NULL 时缓存一个空标记（短 TTL 如 5 分钟）
2. **布隆过滤器**：在缓存前加一层布隆过滤器，不存在的数据直接返回，不查缓存和数据库
3. **参数校验**：在入口处校验 ID 格式（如 ObjectId 是否合法）

项目设计：`ScriptCacheService.getScript()` 中 scriptId 来源于已选择的合法剧本（用户从列表中选择），不存在非法 ID 的正常场景。如果 ID 不存在，直接抛异常（`orElseThrow`），不缓存空值：

```java
Script script = scriptRepository.findById(scriptId)
        .orElseThrow(() -> new IllegalArgumentException("剧本不存在: " + scriptId));
```

**二、缓存击穿** — 热点 Key 过期瞬间

```
热点 Key 过期 → 大量并发请求同时到达 → 都 MISS → 全部打到数据库 → 数据库崩溃
```

解决方案：
1. **互斥锁（SETNX）**：只允许一个线程查数据库，其他线程等待
   ```java
   if (redis.setnx(lockKey, "1", 10, SECONDS)) {
       try { data = db.query(); redis.set(cacheKey, data); }
       finally { redis.del(lockKey); }
   } else {
       Thread.sleep(50); return getFromCache();  // 重试
   }
   ```
2. **逻辑过期**：缓存不设 TTL，但 value 中包含过期时间。读取时发现逻辑过期则异步更新
3. **永不过期 + 后台刷新**：热点数据不设 TTL，后台定时刷新

项目设计：剧本缓存 24h TTL，游戏最长 12h，游戏期间缓存不会过期。且剧本在 `getScript()` 中是先查缓存、miss 后查 MongoDB 并回填缓存，天然是"缓存旁路"模式。

**三、缓存雪崩** — 大量 Key 同时过期 或 Redis 宕机

```
大量 Key 同时过期 → 数据库瞬间承受全部流量 → 数据库崩溃
```

解决方案：
1. **TTL 加随机偏移**：`TTL = baseTime + random(0, 600)` 秒
2. **多级缓存**：本地缓存（Caffeine）→ Redis → 数据库
3. **Redis 集群 / Sentinel**：高可用部署，故障自动转移
4. **限流降级**：Hystrix / Sentinel 对数据库查询限流

项目设计：不同游戏房间创建时间不同，TTL 起始时间天然分散。剧本缓存也是按需加载（不会同时过期），天然避免雪崩。

---

### Q19: 为什么用 Redis 存储游戏运行时状态而不是 MongoDB？

**数据分层设计**：

```
┌────────────────────────────────────────────┐
│                   Redis                     │
│  ┌─────────────┐  ┌─────────────────────┐  │
│  │ LiveGameRoom │  │ game:messages:{id}  │  │
│  │ (房间状态)    │  │ (聊天消息 List)     │  │
│  ├─────────────┤  ├─────────────────────┤  │
│  │ 当前阶段      │  │ [msg1, msg2, ...]   │  │
│  │ 当前发言者     │  │ RPUSH 追加          │  │
│  │ 在线状态      │  │ LRANGE 批量读取      │  │
│  │ 投票进度      │  └─────────────────────┘  │
│  │ 线索实例      │                           │
│  └─────────────┘                            │
│  TTL=12h (活跃) / 2h (空闲)                   │
└──────────────────┬─────────────────────────┘
                   │ 游戏结束时持久化
┌──────────────────▼─────────────────────────┐
│                  MongoDB                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │  User     │  │  Script   │  │GameRecord│  │
│  │  (用户)   │  │  (剧本)   │  │ (对局记录)│  │
│  └──────────┘  └──────────┘  └──────────┘  │
│  永久存储，ACID 保证                          │
└────────────────────────────────────────────┘
```

**选择 Redis 的核心原因**：

| 维度 | Redis | MongoDB |
|------|-------|---------|
| 读写延迟 | < 1ms（内存操作） | 5-50ms（磁盘 I/O + 网络） |
| 并发吞吐 | 10万+ QPS（单机） | 1万 QPS（单机） |
| 消息追加 | RPUSH O(1)，天然有序 | insert O(1) + 索引维护 |
| TTL 自动清理 | 原生支持 | 需要 TTL 索引（后台线程每分钟检查） |
| 原子操作 | INCR、SETNX 等原生原子 | 需要 `$inc`、findAndModify |

**游戏运行时的高频操作**：
- 每条消息：Redis List RPUSH（~0.1ms）+ 读取房间状态（~0.1ms）
- 每次发言者切换：读取 + 修改 + 写入 LiveGameRoom（~0.3ms）
- 每次在线状态变化：更新 LiveGameRoom 中的 member 字段

如果用 MongoDB，每条消息都触发 disk I/O，延迟上升 10-50 倍，在实时游戏中不可接受。

**游戏结束时的持久化**：

```java
// GameFlowService.endGame() — 将关键数据从 Redis 写入 MongoDB
private void persistGameRecord(String roomId, LiveGameRoom room) {
    List<String> summary = gameSummaryService.summarize(rid);
    GameRecord record = GameRecord.builder()
            .roomId(rid)
            .scriptTitle(scriptTitle)
            .fullChatLog(summary)       // 摘要版聊天记录
            .startTime(room.getStartTime())
            .endTime(room.getEndTime())
            .build();
    gameRecordRepository.save(record);  // 持久化到 MongoDB
}

// 然后清理 Redis
liveGameRoomService.evict(new ObjectId(roomId));  // 删除房间状态
scriptCacheService.evictScript(new ObjectId(room.getScriptId()));  // 删除剧本缓存
```

---

### Q20: StringRedisTemplate 和 RedisTemplate 的区别？为什么选 StringRedisTemplate？

**序列化器对比**：

```
StringRedisTemplate:
  Key 序列化器: StringRedisSerializer (UTF-8 字符串)
  Value 序列化器: StringRedisSerializer (UTF-8 字符串)
  Hash Key/Value: StringRedisSerializer

  Redis 中存储: game:room:abc123 → {"roomId":"abc123","status":"PLAYING",...}
  可读性: ✓ redis-cli 可直接查看

RedisTemplate<String, Object>:
  Key 序列化器: StringRedisSerializer
  Value 序列化器: JdkSerializationRedisSerializer (Java 序列化)

  Redis 中存储: game:room:abc123 → \xac\xed\x00\x05sr\x00&com.example...
  可读性: ✗ 二进制不可读
```

**本项目的选择 — StringRedisTemplate + Jackson 手动序列化**：

```java
@RequiredArgsConstructor
public class LiveGameRoomService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(LiveGameRoom live) {
        // 手动序列化为 JSON 字符串
        String json = objectMapper.writeValueAsString(live);
        redisTemplate.opsForValue().set(key, json, TTL, TimeUnit.HOURS);
    }

    public LiveGameRoom get(String roomId) {
        String json = redisTemplate.opsForValue().get(key);
        // 手动反序列化
        return objectMapper.readValue(json, LiveGameRoom.class);
    }
}
```

**优势**：
1. **可调试**：redis-cli 中 `GET game:room:abc123` 直接看到可读 JSON
2. **跨语言**：JSON 是通用格式，未来如果有 Python/Node.js 微服务也能直接读取
3. **不依赖 Java 序列化**：JDK 序列化有安全漏洞（反序列化攻击），且版本兼容性差
4. **体积更小**：JSON 通常比 JDK 序列化结果小 2-5 倍

---

## 五、WebSocket / STOMP

### Q21: WebSocket 和 HTTP 的区别？握手过程详解？

**协议对比**：

| 特性 | HTTP/1.1 | WebSocket |
|------|----------|-----------|
| 通信模式 | 请求-响应（半双工） | 全双工（双向同时） |
| 连接 | 短连接 / Keep-Alive | 长连接 |
| 头部开销 | 每次请求 200-800 字节 | 握手后帧头仅 2-10 字节 |
| 服务端推送 | 不支持（需轮询/SSE） | 原生支持 |
| 数据格式 | 文本 | 文本 + 二进制帧 |
| URL 协议 | `http://` / `https://` | `ws://` / `wss://` |

**WebSocket 握手过程**（基于 HTTP Upgrade）：

```
# 1. 客户端发送 HTTP Upgrade 请求
GET /ws HTTP/1.1
Host: localhost:8080
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==    ← 客户端随机生成的 Base64 值
Sec-WebSocket-Version: 13

# 2. 服务端响应 101 Switching Protocols
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=    ← SHA1(Key + Magic) 的 Base64

# 3. 此后所有通信走 WebSocket 协议帧，不再是 HTTP
```

`Sec-WebSocket-Accept` 的计算：`Base64(SHA1(Sec-WebSocket-Key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))`，用于证明服务端理解 WebSocket 协议（防止误将 HTTP 请求当作 WebSocket 处理）。

**项目中的 WebSocket 用途**：
1. **聊天消息实时推送**：玩家发言后，服务端广播到 `/topic/room.{roomId}`，所有订阅者实时收到
2. **AI 状态信号**：TYPING / TYPING_END 信号通过 `/topic/room.{roomId}.signal` 推送
3. **系统通知**：阶段推进、游戏结束等系统消息通过 `/topic/room.{roomId}` 推送

---

### Q22: STOMP 协议详解？为什么在 WebSocket 上使用 STOMP？

**原始 WebSocket 的问题**：只提供字节流传输通道，没有消息语义。开发者需要自己定义：消息格式、路由规则、订阅/取消订阅机制、消息确认。

**STOMP 解决的问题**：

| 特性 | 原始 WebSocket | STOMP over WebSocket |
|------|---------------|---------------------|
| 消息格式 | 自定义 | 标准帧格式（COMMAND + headers + body） |
| 路由 | 手动解析 | 目的地（destination）路由 |
| 发布订阅 | 手动实现 | SUBSCRIBE/UNSUBSCRIBE 命令 |
| 消息确认 | 无 | ACK/NACK 命令 |
| 消息代理 | 无 | 内置简单代理 / 外部代理（RabbitMQ） |

**STOMP 帧格式**：
```
COMMAND\n
header1:value1\n
header2:value2\n
\n
Body^@    (^@ 是 NULL 字符，标记帧结束)
```

**项目 WebSocket 配置详解**：

```java
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用内置简单消息代理，处理 /topic（广播）和 /queue（点对点）前缀的目的地
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端发送消息的前缀，匹配 @MessageMapping 方法
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 原生 WebSocket 端点
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
        // SockJS 回退端点（兼容不支持 WebSocket 的浏览器）
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 在入站消息通道上注册拦截器，用于 STOMP CONNECT 时的 JWT 认证
        registration.interceptors(webSocketAuthInterceptor);
    }
}
```

**消息流转路径**：

```
客户端 SEND /app/chat.room123
  ↓
clientInboundChannel（入站通道）
  ↓ WebSocketAuthInterceptor 验证 JWT
  ↓
@MessageMapping("/chat.{roomId}")  ← GameChatController 处理
  ↓
gameChatService.sendMessage()  → 消息存入 Redis
eventPublisher.publishEvent()  → 触发 AI 引擎
messagingTemplate.convertAndSend("/topic/room.room123", msg)
  ↓
SimpleBrokerMessageHandler（内置消息代理）
  ↓
clientOutboundChannel（出站通道）
  ↓
所有订阅了 /topic/room.room123 的客户端收到消息
```

---

### Q23: WebSocket 如何做认证？STOMP 层认证 vs HTTP 层认证的区别？

**HTTP 层认证（HandshakeInterceptor）**：
- 在 WebSocket 握手（HTTP Upgrade 请求）时验证
- Token 通过 URL 参数传递：`ws://host/ws?token=xxx`
- 缺点：Token 暴露在 URL 中（出现在日志、浏览器历史、代理服务器中）

**STOMP 层认证（ChannelInterceptor）** — 本项目采用：

```java
// WebSocketAuthInterceptor.java
@Override
public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
            message, StompHeaderAccessor.class);
    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
        // 从 STOMP CONNECT 帧的 headers 中获取 JWT
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Claims claims = jwtService.validateAccessToken(token);
            if (claims != null) {
                // 设置认证信息，后续帧自动关联
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                claims, null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));
                accessor.setUser(auth);
            }
        }
    }
    return message;
}
```

**优势**：
1. Token 在 WebSocket 帧中传输，不暴露在 URL
2. STOMP CONNECT 后的所有帧自动关联认证信息（`accessor.getUser()`）
3. 与 REST API 的认证方式统一（都是 Bearer Token）

**前端连接代码**：
```typescript
const client = new Client({
    brokerURL: 'ws://localhost:8080/ws',
    connectHeaders: {
        'Authorization': `Bearer ${accessToken}`  // STOMP CONNECT 帧中携带
    },
    onConnect: () => {
        client.subscribe('/topic/room.' + roomId, (message) => {
            // 处理消息
        });
    }
});
```

---

### Q24: SimpMessagingTemplate 的 convertAndSend 是同步还是异步？

**答案**：消息的**序列化是同步的**，但**发送是异步的**。

```java
// 调用链分析：
messagingTemplate.convertAndSend("/topic/room." + roomId,
        Map.of("type", "SYSTEM", "content", "游戏开始！"));

// 内部执行：
// 1. [同步] MessageConverter 将 Map 转为 JSON（Jackson 序列化）
// 2. [同步] 创建 Message<byte[]> 对象
// 3. [异步] 将 Message 放入 clientOutboundChannel 的消息队列
// 4. 方法立即返回
// 5. [后台] 消息代理从队列取出消息，分发给所有订阅者
```

**项目中的使用场景**：

```java
// 1. 系统通知（阶段推进、游戏开始/结束）
messagingTemplate.convertAndSend("/topic/room." + roomId,
        Map.of("type", "SYSTEM", "content", "进入新阶段: " + stageTitle));

// 2. AI 状态信号（打字指示器）
messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
        Map.of("type", "TYPING",
               "roleId", roleId.toHexString(),
               "roleName", "主持人"));

// 3. 聊天消息广播（在 GameChatService 中）
messagingTemplate.convertAndSend("/topic/room." + roomId, chatMessageDTO);
```

**追问：如果大量消息同时发送，会不会有性能问题？**

`clientOutboundChannel` 默认使用线程池（核心线程数 = CPU 核数），消息的网络发送是并行的。瓶颈通常在序列化和网络带宽上。如果担心性能，可以：
1. 增大 `clientOutboundChannel` 的线程池大小
2. 使用外部消息代理（RabbitMQ / ActiveMQ）替代内置简单代理
3. 对消息做批量合并（减少帧数）

---

## 六、Spring AI 与大模型集成

### Q25: Spring AI 的核心架构和抽象？

**Spring AI 的核心抽象层次**：

```
应用代码
  ↓
ChatModel 接口（统一 API）          ← 本项目面向此接口编程
  ↓
AnthropicChatModel（提供商实现）     ← 自动配置注入
  ↓
AnthropicApi（HTTP 客户端）         ← 处理请求签名、重试、流式
  ↓
Anthropic Claude API               ← 外部 LLM 服务
```

**核心接口详解**：

```java
// 1. ChatModel — 统一的聊天模型接口
public interface ChatModel {
    ChatResponse call(Prompt prompt);          // 同步调用
    Flux<ChatResponse> stream(Prompt prompt);  // 流式调用
}

// 2. Prompt — 提示词封装
Prompt prompt = new Prompt(
    List.of(
        new SystemMessage("你是游戏主持人..."),    // 系统提示词
        new UserMessage("请评估当前局势...")        // 用户消息
    ),
    ToolCallingChatOptions.builder()              // 可选：工具调用配置
        .toolCallbacks(callbacks)
        .build()
);

// 3. ChatResponse — 模型响应
ChatResponse response = chatModel.call(prompt);
String reply = response.getResult().getOutput().getText();

// 4. ToolCallback — Function Calling 工具回调
FunctionToolCallback.builder("authorizeSearch", (input) -> tool.execute(input, ctx))
    .description("授权角色进行搜证")
    .inputType(AuthorizeSearchInput.class)
    .build();
```

**项目中的两种 LLM 调用模式**：

**模式 1 — 纯文本调用（AgentExecutor，角色 Agent）**：
```java
// 没有工具调用，只是生成回复
String reply = chatModel.call(new Prompt(agentPrompt))
        .getResult().getOutput().getText();
```

**模式 2 — 带工具调用（DmExecutor，DM 主持人）**：
```java
// DM 可以调用工具（搜证、投票、阶段推进等）
List<ToolCallback> callbacks = dmToolRegistry.buildCallbacks(toolCtx);
Prompt prompt = new Prompt(
    List.of(new SystemMessage(systemPrompt), new UserMessage(userContent)),
    ToolCallingChatOptions.builder().toolCallbacks(callbacks).build()
);
ChatResponse response = chatModel.call(prompt);
// Spring AI 自动处理工具调用循环：
// LLM 返回 tool_call → 框架执行工具 → 结果返回给 LLM → LLM 继续生成
```

**追问：为什么用 `ToolCallingChatOptions` 而不是 `ChatOptions`？**

这是 Spring AI 1.1.x 的 API 设计。`ChatOptions` 接口不包含 `toolCallbacks` 方法（它的 builder 没有 `.toolCallbacks()`），`ToolCallingChatOptions` 是专门为工具调用扩展的子接口。这个问题在开发过程中通过 `javap` 反编译 Spring AI JAR 发现并解决。

---

### Q26: Function Calling（工具调用）的完整流程？

**Function Calling 是让 LLM "长出手脚" 的能力**：LLM 不再只是生成文本，还可以调用外部函数执行真实操作。

**完整交互流程**：

```
1. 开发者定义工具
   ┌────────────────────────────────────┐
   │ name: "authorizeSearch"            │
   │ description: "授权角色进行搜证"     │
   │ parameters: {roleId, location}     │
   └────────────────────────────────────┘

2. 随提示词发送给 LLM
   ┌────────────────────────────────────┐
   │ System: 你是游戏主持人...           │
   │ User: 玩家说"我要搜证厨房"          │
   │ Tools: [authorizeSearch, ...]      │
   └────────────────────────────────────┘

3. LLM 判断需要调用工具
   ┌────────────────────────────────────┐
   │ tool_call: authorizeSearch         │
   │ arguments: {                       │
   │   "roleId": "60a1b2...",          │
   │   "location": "厨房"              │
   │ }                                  │
   └────────────────────────────────────┘

4. Spring AI 框架自动执行工具
   ┌────────────────────────────────────┐
   │ tool.execute(input, ctx)           │
   │ → 检查搜证次数 → 过滤匹配线索      │
   │ → 返回: "发现线索：血迹证据"        │
   └────────────────────────────────────┘

5. 工具结果返回给 LLM
   ┌────────────────────────────────────┐
   │ tool_result: "发现线索：血迹证据"    │
   └────────────────────────────────────┘

6. LLM 基于结果生成最终回复
   ┌────────────────────────────────────┐
   │ "侦探在厨房发现了一块可疑的血迹..."  │
   └────────────────────────────────────┘
```

**项目的 DmTool 工具系统**：

```java
// 1. 工具接口定义
public interface DmTool {
    String name();          // 工具名称，LLM 通过此名称选择工具
    String description();   // 工具描述，帮助 LLM 理解何时使用
    Class<?> inputType();   // 参数类型，Spring AI 从中提取 JSON Schema
    Object execute(Object input, DmToolContext ctx);  // 执行逻辑
}

// 2. 工具注册表 — 利用 Spring DI 自动发现所有实现
@Service
@RequiredArgsConstructor
public class DmToolRegistry {
    private final List<DmTool> tools;  // Spring 自动注入所有 @Component DmTool 实现

    public List<ToolCallback> buildCallbacks(DmToolContext ctx) {
        return tools.stream()
                .map(tool -> (ToolCallback) FunctionToolCallback
                        .builder(tool.name(), (Object input) -> tool.execute(input, ctx))
                        .description(tool.description())
                        .inputType((Class) tool.inputType())
                        .build())
                .toList();
    }
}
```

**DmToolContext — 上下文注入设计**：

```java
// 每次 DM 调用时创建新的上下文，包含当前游戏状态
DmToolContext toolCtx = DmToolContext.builder()
        .room(room)                    // 当前房间状态
        .script(script)                // 剧本数据
        .currentPhaseId(currentPhaseId) // 当前阶段 ID
        .triggerRoleId(triggerRoleId)   // 触发者角色 ID
        .build();

// 工具实现中通过 ctx 访问游戏状态
@Component
public class AuthorizeSearchTool implements DmTool {
    @Override
    public Object execute(Object input, DmToolContext ctx) {
        LiveGameRoom room = ctx.getRoom();
        Script script = ctx.getScript();
        // 检查搜证次数、过滤线索、更新状态...
    }
}
```

**追问：为什么 DmTool 的 execute 方法签名用 Object 而不是泛型？**

因为 Spring AI 的 `FunctionToolCallback.builder()` 要求 `Function<Object, Object>` 签名。LLM 返回的参数是 JSON，Spring AI 会根据 `inputType()` 声明的类型自动反序列化为对应的 Java 对象传入。返回值也需要序列化为字符串传回 LLM，所以用 Object 保持灵活性。

---

### Q27: AI 上下文窗口管理 — 双触发压缩机制详解

**问题**：LLM 有上下文窗口限制（Claude 200K tokens），长时间游戏（5+ 幕，数百条消息）会超限。

**解决方案 — MemoryManager 双触发压缩**：

```java
@Service
@RequiredArgsConstructor
public class MemoryManager {

    // ========== 触发器 1：幕次切换时压缩 ==========
    @Async("aiExecutor")
    public void compressStage(String roomId, int stageNumber,
                              List<GameMessage> stageMessages) {
        if (stageMessages.isEmpty()) return;
        // 调用 LLM 将当前幕的所有消息压缩为结构化摘要
        String compressionPrompt = promptBuilder.buildCompressionPrompt(
                stageNumber, stageMessages);
        String response = chatModel.call(new Prompt(compressionPrompt))
                .getResult().getOutput().getText();
        String json = extractJson(response);
        // 验证格式后存入 Redis
        objectMapper.readValue(json, StageSummary.class);
        String key = "game:" + roomId + ":memory:" + stageNumber;
        redisTemplate.opsForValue().set(key, json, 12, TimeUnit.HOURS);
    }

    // ========== 触发器 2：Token 阈值检测 ==========
    public boolean shouldCompress(String currentPrompt) {
        int tokens = promptBuilder.estimateTokens(currentPrompt);
        int threshold = (int) (properties.getMaxContextTokens()
                * properties.getTokenThresholdRatio());  // 100000 * 0.7 = 70000
        return tokens > threshold;
    }

    // ========== 上下文组装：摘要 + 滑动窗口 ==========
    public List<String> getMemoryFragments(String roomId, int upToStage) {
        List<String> fragments = new ArrayList<>();
        for (int i = 0; i < upToStage; i++) {
            String key = "game:" + roomId + ":memory:" + i;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) fragments.add("### 第" + (i + 1) + "幕摘要\n" + json);
        }
        return fragments;
    }

    public List<GameMessage> getRecentMessages(List<GameMessage> allMessages) {
        int windowSize = properties.getSlidingWindowSize();  // 20
        if (allMessages.size() <= windowSize) return allMessages;
        return allMessages.subList(allMessages.size() - windowSize, allMessages.size());
    }
}
```

**上下文组装策略**：

```
┌──────────────────────────────────────────┐
│ 系统提示词（固定 ~2000 tokens）           │ ← 角色设定、游戏规则
├──────────────────────────────────────────┤
│ 第 1 幕摘要（压缩后 ~500 tokens）         │ ← MemoryManager.compressStage() 生成
│ 第 2 幕摘要（压缩后 ~500 tokens）         │
│ ...                                      │
├──────────────────────────────────────────┤
│ 最近 20 条消息（原文 ~2000 tokens）        │ ← 滑动窗口，保持近期对话的完整性
├──────────────────────────────────────────┤
│ 用户最新消息                              │
└──────────────────────────────────────────┘
总计: ~5000-10000 tokens，远低于 200K 限制
```

**Token 估算方法**：

```java
public int estimateTokens(String text) {
    if (text == null || text.isEmpty()) return 0;
    // 中文约 3.5 字符 = 1 Token（经验值）
    // 英文约 4-5 字符 = 1 Token
    return (int) (text.length() / properties.getCharsPerToken());
}
```

**GameFlowService 中的压缩触发点**：

```java
// advanceStage() — 幕次切换时触发压缩
public LiveGameRoom advanceStage(String roomId) {
    // ... 省略验证 ...
    // 压缩当前幕的消息（异步执行，不阻塞阶段推进）
    List<GameMessage> stageMessages = deserializeMessages(
            liveGameRoomService.getMessages(new ObjectId(roomId)));
    memoryManager.compressStage(roomId, room.getCurrentStage(), stageMessages);
    // 推进到下一幕
    liveGameRoomService.advanceStage(new ObjectId(roomId), nextStage);
    // ...
}
```

**追问：为什么用 LLM 压缩而不是简单截断或关键词提取？**

因为游戏对话需要保留**推理链和角色关系变化**：
- 截断：直接丢失早期信息
- 关键词提取：丢失语境和因果关系
- LLM 压缩：能提取"侦探在第 2 幕怀疑医生说谎，因为他的不在场证明与线索矛盾"这样的高层语义摘要

---

### Q28: AI 角色的沙盒隔离如何实现？

**问题**：游戏中有多个 AI 角色（如侦探、医生、嫌疑人），每个角色只能知道自己的秘密和获得的线索。但它们共享同一个游戏房间的数据。如果不做隔离，AI 角色 A 就能"偷看"角色 B 的秘密。

**解决方案 — PromptBuilder 构建隔离上下文**：

```java
// PromptBuilder.buildAgentPrompt() — 为每个角色构建独立的提示词

// 1. 线索隔离：只保留该角色可见的线索
String roleIdHex = targetRole.getId().toHexString();
List<GameClueInstance> visibleClues = allClueInstances.stream()
        .filter(c -> c.isFound() &&               // 已被发现的线索
                (c.isPublic() ||                   // 公开线索所有人可见
                 c.getOwnerRoleIds().stream()      // 或者该角色是拥有者
                         .anyMatch(id -> id.toHexString().equals(roleIdHex))))
        .toList();

// 2. 消息隔离：只保留公开消息或发给自己的消息
ObjectId roleId = targetRole.getId();
List<GameMessage> visibleMessages = recentMessages.stream()
        .filter(m -> m.getReceiverRoleIds() == null ||       // receiverRoleIds 为 null → 公开消息
                m.getReceiverRoleIds().isEmpty() ||           // 空列表 → 公开消息
                m.getReceiverRoleIds().contains(roleId))      // 包含自己 → 可见
        .toList();
```

**DM vs Agent 的权限对比**：

```
DM 提示词（buildDmPrompt）：
  ┌── 所有角色的秘密 ✓（buildFullScriptTruth 遍历所有 Role.secret）
  ├── 所有线索详情 ✓
  ├── 所有消息（含私聊）✓（不做 filter）
  ├── 搜证次数表 ✓（buildSearchPowerTable）
  └── 工具调用能力 ✓（DmToolRegistry.buildCallbacks）

Agent 提示词（buildAgentPrompt）：
  ┌── 仅自己的秘密 ✓（targetRole.getSecret()）
  ├── 仅可见的线索 ✓（ownerRoleIds 过滤）
  ├── 仅可见的消息 ✓（receiverRoleIds 过滤）
  ├── 其他角色的名称 ✓（但不知道秘密）
  └── 无工具调用能力 ✗（纯文本 Prompt）
```

**追问：这种隔离方式有没有可能被 LLM 绕过？**

在当前架构下不可能绕过，因为隔离发生在**提示词构建层面**：
- Agent 的 Prompt 中物理上不存在其他角色的秘密信息
- LLM 只能基于 Prompt 中的信息生成回复
- 即使 LLM 试图"推理"其他角色的秘密，也只能基于公开信息猜测（这恰好是游戏的玩法）

但要注意：DM 的回复是广播的，如果 DM 不小心在回复中泄露了某角色的秘密，所有人都能看到。这需要在 DM 的系统提示词中强调保密规则。

---

## 七、Spring 事件驱动与异步

### Q29: Spring 的事件机制原理？同步 vs 异步事件？

**Spring 事件机制基于观察者模式（Observer Pattern）**：

**核心组件**：
- `ApplicationEvent` — 事件基类
- `ApplicationEventPublisher` — 事件发布者
- `@EventListener` — 事件监听者

**项目中的事件系统**：

```java
// 1. 定义事件
@Getter
public class ChatMessageEvent extends ApplicationEvent {
    private final String roomId;
    private final ObjectId senderRoleId;
    private final String content;
    private final boolean fromAi;

    public ChatMessageEvent(Object source, String roomId, ObjectId senderRoleId,
                            String content, boolean fromAi) {
        super(source);
        this.roomId = roomId;
        this.senderRoleId = senderRoleId;
        this.content = content;
        this.fromAi = fromAi;
    }
}

// 2. 发布事件 — GameChatController.java
@MessageMapping("/chat.{roomId}")
public void handleChatMessage(...) {
    gameChatService.sendMessage(roomId, senderRoleId, request.getContent(), room);
    // 发布事件，完全不知道谁在监听
    eventPublisher.publishEvent(new ChatMessageEvent(
            this, roomId, senderRoleId, request.getContent(), false));
}

// 3. 监听事件 — AgentOrchestrator.java
@EventListener
public void onChatMessage(ChatMessageEvent event) {
    if (event.isFromAi()) return;  // 防止 AI 消息触发 AI 回复（死循环防护）
    // 路由到 DmExecutor 或 AgentExecutor
}
```

**同步 vs 异步事件**：

默认情况下，`@EventListener` 是**同步执行**的 —— 发布者在 `publishEvent()` 调用中会等待所有监听者执行完毕。

```java
// 同步：publishEvent() 会阻塞直到 onChatMessage() 返回
eventPublisher.publishEvent(event);

// 异步方案1：监听方法标注 @Async
@Async
@EventListener
public void onChatMessage(ChatMessageEvent event) { ... }

// 异步方案2：使用 @TransactionalEventListener + @Async
// 事务提交后才发布事件，避免监听者看到未提交的数据
```

**本项目的设计**：`AgentOrchestrator.onChatMessage()` 是同步监听，但内部调用的 `dmExecutor.executeDmAction()` 和 `agentExecutor.executeAgentReply()` 是 `@Async` 方法。这意味着：
1. 事件路由逻辑（判断调用谁）是同步的、快速的
2. 实际的 LLM 调用是异步的，不阻塞消息处理链

**追问：`@EventListener` 的方法参数是如何匹配事件类型的？**

Spring 通过方法参数类型进行匹配。`onChatMessage(ChatMessageEvent event)` 的参数类型是 `ChatMessageEvent`，当发布的事件类型是 `ChatMessageEvent` 或其子类时，该方法被调用。底层使用 `ResolvableType` 做类型推断。

---

### Q30: @Async 的原理、注意事项和线程池设计？

**@Async 的底层原理**：

```
调用方 → AOP 代理（AsyncAnnotationBeanPostProcessor 生成）
          ↓
       AsyncExecutionInterceptor.invoke()
          ↓
       从 BeanFactory 获取名为 "aiExecutor" 的 TaskExecutor
          ↓
       将方法调用封装为 Callable/Runnable
          ↓
       executor.submit(callable)  ← 提交到线程池
          ↓
       调用方立即返回（void 返回 null，CompletableFuture 返回代理 Future）
```

**项目的线程池配置**：

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);       // 核心线程：4 个常驻
        executor.setMaxPoolSize(16);       // 最大线程：高峰期最多 16 个
        executor.setQueueCapacity(100);    // 等待队列：核心线程都忙时排队
        executor.setThreadNamePrefix("ai-engine-");  // 线程名前缀，方便日志定位
        executor.initialize();
        return executor;
    }
}
```

**参数设计理由**：
- **core=4**：AI 调用耗时长（3-10秒），4 个核心线程可以并行处理 4 个 AI 请求（约等于 4 个同时活跃的游戏房间）
- **max=16**：突发高峰时扩展到 16 个线程，对应 16 个并行 AI 请求
- **queue=100**：队列满之前不会创建新线程，100 个等待位置可以缓冲短时突发流量
- **线程名前缀**：日志中显示 `ai-engine-1`、`ai-engine-2`，方便区分 AI 调用的线程和其他线程

**关键注意事项**：

1. **同类调用失效（最常见的坑）**：
```java
@Service
public class MyService {
    public void normalMethod() {
        asyncMethod();  // ❌ 不会异步！直接调用 this.asyncMethod()，绕过了代理
    }

    @Async
    public void asyncMethod() { ... }
}
// 解决：注入自身 @Lazy @Autowired MyService self; self.asyncMethod();
// 或拆分到两个类中
```

2. **异常处理**：void 返回类型的 @Async 方法，异常默认只打日志不抛出。本项目中 DmExecutor 和 AgentExecutor 在 @Async 方法内部 try-catch 所有异常：
```java
@Async("aiExecutor")
public void executeDmAction(String roomId, ObjectId triggerRoleId, String reason) {
    try {
        // ... LLM 调用 ...
    } catch (Exception e) {
        log.error("DM execution failed for room {}", roomId, e);
        // 确保 TYPING_END 信号发出，避免前端一直显示"正在输入"
    }
}
```

3. **SecurityContext 传递**：`@Async` 方法在新线程中执行，默认无法访问 `SecurityContextHolder` 中的认证信息。本项目的 AI 执行器不需要认证信息（使用 roomId 和 roleId 标识），所以不受影响。

---

### Q31: 线程池的核心参数和工作流程？拒绝策略？

**ThreadPoolExecutor 的工作流程**：

```
新任务到达
  │
  ├── 当前线程数 < corePoolSize? ──YES──> 创建核心线程执行任务
  │                                       （即使有空闲核心线程也创建新的）
  │──NO
  │
  ├── 队列未满? ──YES──> 放入 workQueue 等待
  │
  │──NO（队列满）
  │
  ├── 当前线程数 < maxPoolSize? ──YES──> 创建非核心线程执行任务
  │
  │──NO（线程数已达上限）
  │
  └── 执行拒绝策略 ──> RejectedExecutionHandler.rejectedExecution()
```

**四种内置拒绝策略**：

| 策略 | 行为 | 适用场景 |
|------|------|----------|
| `AbortPolicy`（默认） | 抛出 `RejectedExecutionException` | 严格不丢任务 |
| `CallerRunsPolicy` | 由提交任务的线程执行 | 不丢任务，但会阻塞提交线程 |
| `DiscardPolicy` | 静默丢弃 | 可以丢失的非关键任务 |
| `DiscardOldestPolicy` | 丢弃队列头部最旧的任务 | 关注最新任务 |

**项目的线程池工作示例**：

```
场景：4 个核心线程，100 个队列，16 个最大线程

初始状态：0 个线程
  → 请求1：创建核心线程1 (当前: 1/4 核心)
  → 请求2：创建核心线程2 (当前: 2/4 核心)
  → 请求3：创建核心线程3 (当前: 3/4 核心)
  → 请求4：创建核心线程4 (当前: 4/4 核心)
  → 请求5：核心线程全忙 → 放入队列 (队列: 1/100)
  → ...
  → 请求104：队列已满 (100/100) → 创建非核心线程5 (线程: 5/16)
  → ...
  → 请求116：线程全忙 (16/16)，队列全满 (100/100) → 拒绝策略

流量下降后：
  → 非核心线程空闲超过 keepAliveTime → 销毁
  → 最终回到 4 个核心线程
```

**追问：为什么核心线程满了先放队列，而不是直接创建新线程？**

这是设计取舍。核心线程是"长期工作者"，队列是"缓冲区"，非核心线程是"临时工"。先用队列缓冲可以：
1. 避免线程频繁创建销毁的开销
2. 平滑短时突发流量（突发请求在队列中等待，核心线程处理完即可消化）
3. 只有持续高负载才创建额外线程

---

## 八、并发编程

### Q32: ConcurrentHashMap 的原理？JDK 7 vs JDK 8 的区别？

**JDK 7：分段锁（Segment）**：
```
ConcurrentHashMap
  ├── Segment[0] (ReentrantLock) → HashEntry[]
  ├── Segment[1] (ReentrantLock) → HashEntry[]
  ├── ...
  └── Segment[15] (ReentrantLock) → HashEntry[]
```
- 16 个 Segment，每个 Segment 是一把独立的锁
- 并发度最高 16（16 个线程同时写不同 Segment）
- 缺点：锁粒度粗，Segment 内的所有桶共享一把锁

**JDK 8+：CAS + synchronized（本项目 Java 17 使用）**：
```
ConcurrentHashMap
  └── Node<K,V>[] table
        ├── table[0] → null
        ├── table[1] → Node → Node → ...（链表）
        ├── table[2] → TreeBin → TreeNode → ...（红黑树）
        ├── ...
        └── table[n-1]
```

**写操作**：
1. 计算 hash，找到 table 下标
2. 如果桶为空：CAS 插入新 Node（无锁）
3. 如果桶不为空：`synchronized(首节点)` 锁住该桶，然后遍历链表/红黑树
4. 链表长度 ≥ 8 且 table 长度 ≥ 64：链表转红黑树

**读操作**：
- 无锁。Node 的 `val` 和 `next` 字段都是 `volatile`，保证可见性
- 遍历链表/红黑树不需要加锁

**size() 计算**：
- 不是一把全局锁统计，使用 `baseCount` + `CounterCell[]` 分散计数
- 类似 LongAdder 的思想：每个线程的计数分散到不同 Cell，避免竞争
- `size()` = `baseCount + sum(CounterCell[])`

**项目使用**：

```java
// PhaseTimerService.java — 管理每个房间的计时器
public class PhaseTimerService {
    private final ConcurrentHashMap<String, ScheduledFuture<?>> activeTimers
            = new ConcurrentHashMap<>();

    public void startTurnTimer(String roomId, long timeoutSeconds, Runnable onTimeout) {
        cancelTimer(roomId);  // 先取消已有计时器
        ScheduledFuture<?> future = scheduler.schedule(onTimeout,
                timeoutSeconds, TimeUnit.SECONDS);
        activeTimers.put(roomId, future);
    }

    public void cancelTimer(String roomId) {
        ScheduledFuture<?> future = activeTimers.remove(roomId);
        if (future != null) future.cancel(false);
    }
}
```

**追问：`putIfAbsent` 和 `computeIfAbsent` 的区别？**

- `putIfAbsent(key, value)`：如果 key 不存在则插入 value。**value 已经计算好了**（即使不插入也白计算了）
- `computeIfAbsent(key, k -> createValue())`：如果 key 不存在才执行 lambda 计算 value 并插入。**惰性计算，更高效**。且在 ConcurrentHashMap 中，lambda 在锁内执行，保证原子性。

---

### Q33: ScheduledExecutorService vs Timer？

| 特性 | ScheduledExecutorService | Timer |
|------|-------------------------|-------|
| 线程模型 | 线程池（可配置多个线程） | 单线程 |
| 异常处理 | 一个任务异常不影响其他任务 | 一个任务抛异常 → 整个 Timer 停止 |
| 时间基准 | `System.nanoTime()`（单调时钟） | `System.currentTimeMillis()`（系统时钟） |
| 并发任务 | 多个任务可并行执行 | 所有任务串行执行 |
| 任务延迟 | 线程池消化突发延迟 | 一个长任务会延迟后续任务 |

**项目使用**：

```java
// PhaseTimerService.java
private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2);  // 2个线程

// 为什么是 2 个线程？
// - 发言超时和投票超时可能同时存在
// - 2 个线程确保一个超时处理阻塞时不影响另一个
```

**追问：ScheduledExecutorService 中任务抛异常后会怎样？**

如果 `schedule()` 的任务抛出异常，该任务的 `ScheduledFuture` 会被标记为完成（异常状态），但**不会影响线程池中的其他任务**。不过对于 `scheduleAtFixedRate()` / `scheduleWithFixedDelay()`，一次异常会导致后续执行**被取消**。所以建议在任务内部 try-catch 所有异常。

---

### Q34: volatile 关键字的作用、原理和局限性？

**作用**：
1. **可见性**：一个线程修改 volatile 变量后，其他线程**立即**可见（不从 CPU 缓存读取，强制从主内存加载）
2. **有序性**：禁止 volatile 读写前后的指令重排序（通过内存屏障实现）

**JMM（Java Memory Model）层面的原理**：

```
线程A                    主内存                  线程B
┌──────┐               ┌──────┐               ┌──────┐
│ 工作内存 │               │      │               │ 工作内存 │
│ x = 0  │──── read ────│ x = 0│               │ x = 0  │
│        │               │      │               │        │
│ x = 1  │──── write ───│ x = 1│               │        │
│(volatile)│  (StoreLoad) │      │               │        │
│        │               │      │──── read ────│ x = 1  │
│        │               │      │  (LoadLoad)  │ 看到最新值│
└──────┘               └──────┘               └──────┘
```

**内存屏障**：
- volatile 写后插入 `StoreStore` + `StoreLoad` 屏障 → 确保写操作对其他线程可见
- volatile 读前插入 `LoadLoad` + `LoadStore` 屏障 → 确保读到最新值

**不保证原子性**：
```java
volatile int count = 0;
count++;  // ❌ 不是原子操作！
// 实际执行：1. 读 count (0)  2. 计算 +1  3. 写 count (1)
// 两个线程可能同时读到 0，都写回 1，丢失一次递增
// 解决：使用 AtomicInteger 或 synchronized
```

**适用场景**：
- 状态标志（`volatile boolean running`）
- 双重检查锁定（DCL 单例模式中的 instance 变量）
- 一写多读的场景

**项目相关**：ConcurrentHashMap 内部的 `Node.val` 和 `Node.next` 是 volatile 的，保证读操作无需加锁也能看到最新值。

---

## 九、设计模式

### Q35: 项目中用到了哪些设计模式？详细说明。

**1. 策略模式（Strategy）— DmTool 工具系统**

```java
// 策略接口
public interface DmTool {
    String name();
    String description();
    Class<?> inputType();
    Object execute(Object input, DmToolContext ctx);
}

// 策略实现（9 个工具，每个是一种策略）
@Component public class AuthorizeSearchTool implements DmTool { ... }
@Component public class InitiateVoteTool implements DmTool { ... }
@Component public class DecidePhaseTransitionTool implements DmTool { ... }
@Component public class SelectRespondentsTool implements DmTool { ... }
// ...

// 策略选择者 — 不是代码选择，而是 LLM 根据语义自主选择！
// DmToolRegistry 将所有策略注册给 LLM，由 LLM 决定使用哪个
```

这是策略模式的一个独特变体：传统策略模式由代码中的 if/else 或枚举选择策略，本项目由 AI 根据游戏语境自主选择。

**2. 观察者模式（Observer）— Spring 事件机制**

```java
// 被观察的事件
eventPublisher.publishEvent(new ChatMessageEvent(this, roomId, ...));

// 观察者1 — AI 引擎
@EventListener
public void onChatMessage(ChatMessageEvent event) {
    // 路由到 DmExecutor 或 AgentExecutor
}

// 观察者2 — 未来可扩展的消息统计、审计日志等
// 新增观察者只需加一个 @EventListener 方法，发布者无需修改
```

**3. 建造者模式（Builder）— Lombok @Builder**

```java
// LiveGameRoom 的构建
LiveGameRoom room = LiveGameRoom.builder()
        .roomId(roomId)
        .scriptId(scriptId)
        .status(GameRoomStatus.WAITING)
        .currentStage(0)
        .members(new ArrayList<>())
        .build();

// GameMessage 的构建
GameMessage message = GameMessage.builder()
        .messageId(new ObjectId())
        .roomId(new ObjectId(roomId))
        .senderRoleId(senderRoleId)
        .content(content)
        .isAi(true)  // AI 消息标记
        .timestamp(LocalDateTime.now())
        .build();
```

Builder 模式的优势：当对象有大量可选参数时，避免望远镜式构造器（telescoping constructor），提高可读性。

**4. 模板方法模式（Template Method）— PromptBuilder**

```java
// 固定的模板流程：加载模板 → 替换变量 → 拼接上下文
// 不同类型的提示词只是变量替换规则不同

public String buildDmPrompt(...) {
    String template = loadTemplate("prompts/dm-system.md", script.getDmConfig());
    Map<String, String> vars = new HashMap<>();
    vars.put("scriptTitle", script.getTitle());
    vars.put("roleList", buildRoleList(script.getRoles()));
    vars.put("fullScriptTruth", buildFullScriptTruth(script)); // DM独有：全部真相
    // ...
    return replaceVars(template, vars);
}

public String buildAgentPrompt(...) {
    String template = loadClasspathTemplate("prompts/agent-system.md");
    Map<String, String> vars = new HashMap<>();
    vars.put("roleName", targetRole.getName());
    vars.put("roleSecret", targetRole.getSecret());  // Agent独有：仅自己的秘密
    // ...
    return replaceVars(template, vars);
}
```

**5. 代理模式（Proxy）— Spring AOP**

```java
@Async("aiExecutor")
public void executeAgentReply(String roomId, ObjectId roleId) { ... }
// Spring 为 AgentExecutor 生成 CGLIB 代理
// 外部调用 agentExecutor.executeAgentReply() 实际调用的是代理方法
// 代理方法将真实调用封装为 Runnable 提交到 aiExecutor 线程池
```

**6. 注册表模式（Registry）— DmToolRegistry + Spring DI 自动发现**

```java
@Service
@RequiredArgsConstructor
public class DmToolRegistry {
    // Spring 自动注入所有实现了 DmTool 接口的 @Component
    private final List<DmTool> tools;

    public List<ToolCallback> buildCallbacks(DmToolContext ctx) {
        // 运行时动态构建回调列表
        return tools.stream()
                .map(tool -> FunctionToolCallback.builder(...)..build())
                .toList();
    }
}
// 新增工具：只需创建类 implements DmTool + @Component
// DmToolRegistry、DmExecutor、AgentOrchestrator 零修改
```

**7. 缓存旁路模式（Cache-Aside）— ScriptCacheService**

```java
public Script getScript(ObjectId scriptId) {
    // 1. 先查缓存
    String json = redisTemplate.opsForValue().get(KEY_PREFIX + scriptId.toHexString());
    if (json != null) {
        return objectMapper.readValue(json, Script.class);  // 缓存命中
    }
    // 2. 缓存未命中 → 查数据库
    Script script = scriptRepository.findById(scriptId)
            .orElseThrow(() -> new IllegalArgumentException("剧本不存在"));
    // 3. 回填缓存
    cacheScript(script);
    return script;
}
```

---

### Q36: 开闭原则、SOLID 在项目中的体现？

**开闭原则（Open-Closed Principle）— DmTool 系统是最佳体现**：

```
新增"发放线索"工具：
1. 创建 DistributeClueToolimplements DmTool + @Component  ← 扩展（Open）
2. DmToolRegistry 的 List<DmTool> 自动注入新工具           ← 无需修改（Closed）
3. DmExecutor 构建 Prompt 时自动包含新工具                 ← 无需修改（Closed）
4. LLM 在 Function Calling 列表中看到新工具               ← 自动生效
```

**单一职责原则（SRP）**：
- `JwtService`：只负责 Token 的生成、验证、黑名单（不涉及用户查询）
- `JwtAuthenticationFilter`：只负责从请求中提取和验证 Token（不涉及业务逻辑）
- `AuthService`：只负责认证业务流程编排（不涉及 Token 细节）

**依赖倒置原则（DIP）**：
```java
// DmExecutor 依赖 ChatModel 接口，不依赖 AnthropicChatModel 实现
private final ChatModel chatModel;
// 切换到 OpenAI 只需换 Starter 依赖，代码不变

// 同理，所有 Service 依赖 StringRedisTemplate（接口），不依赖具体 Redis 客户端
```

**接口隔离原则（ISP）**：
```java
// DmTool 接口精简到 4 个方法，每个工具只需实现自己的逻辑
public interface DmTool {
    String name();
    String description();
    Class<?> inputType();
    Object execute(Object input, DmToolContext ctx);
}
```

---

## 十、Vue 3 + TypeScript 前端

### Q37: Vue 3 Composition API vs Options API 的深度对比？

**Options API 的问题**：

```javascript
// 一个复杂组件中，同一个功能的代码分散在不同选项中
export default {
    data() { return { messages: [], connected: false, roomInfo: null } },
    computed: { ... },
    methods: {
        sendMessage() { ... },      // 聊天功能
        connect() { ... },          // WebSocket 功能
        loadRoom() { ... }          // 房间功能
    },
    mounted() { this.connect(); this.loadRoom(); },
    unmounted() { this.disconnect(); }
}
// 问题："聊天"功能的代码分散在 data、methods、mounted、unmounted 中
```

**Composition API 的解决方案 — 按逻辑功能组织**：

```typescript
// composables/useWebSocket.ts — 封装 WebSocket 逻辑
export function useWebSocket() {
    const connected = ref(false);

    function connect(roomId: string, onMessage: Function) {
        const client = new Client({
            brokerURL: `ws://localhost:8080/ws`,
            connectHeaders: { 'Authorization': `Bearer ${token}` },
            onConnect: () => {
                connected.value = true;
                client.subscribe(`/topic/room.${roomId}`, onMessage);
            }
        });
        client.activate();
    }

    function disconnect() { ... }
    function send(roomId: string, content: string) { ... }

    return { connected, connect, disconnect, send };
}

// 组件中使用 — 功能高度内聚
const { connected, connect, send } = useWebSocket();
const { messages, loadMessages } = useMessages();

onMounted(() => {
    connect(roomId.value, handleMessage);
    loadMessages(roomId.value);
});

onUnmounted(() => disconnect());
```

**TypeScript 支持的差异**：

```typescript
// Options API — this 类型推断困难
export default {
    data() { return { count: 0 } },
    methods: {
        increment() {
            this.count++;  // TypeScript 难以推断 this.count 的类型
        }
    }
}

// Composition API — 函数式，类型自然流动
const count = ref(0);          // Ref<number>
const double = computed(() => count.value * 2);  // ComputedRef<number>
function increment() {
    count.value++;  // TypeScript 完全理解类型
}
```

---

### Q38: Vue 3 的响应式原理（Proxy vs Object.defineProperty）？

**Vue 2：Object.defineProperty（劫持已有属性）**

```javascript
// 每个属性都需要递归定义 getter/setter
Object.defineProperty(obj, 'name', {
    get() { /* 依赖收集 */ return value; },
    set(newVal) { value = newVal; /* 触发更新 */ }
});

// 局限性：
// ❌ 无法检测新增属性: obj.newProp = 1  → 不响应（需要 Vue.set()）
// ❌ 无法检测删除属性: delete obj.prop  → 不响应（需要 Vue.delete()）
// ❌ 无法检测数组索引: arr[0] = 1      → 不响应（需要 Vue.set()）
// ❌ 初始化时递归遍历所有属性，大对象性能差
```

**Vue 3：Proxy（劫持整个对象的访问）**

```javascript
const proxy = new Proxy(target, {
    get(target, key, receiver) {
        track(target, key);          // 依赖收集
        const result = Reflect.get(target, key, receiver);
        if (isObject(result)) {
            return reactive(result); // 惰性代理：只在访问时才递归
        }
        return result;
    },
    set(target, key, value, receiver) {
        const oldValue = target[key];
        const result = Reflect.set(target, key, value, receiver);
        if (oldValue !== value) {
            trigger(target, key);    // 触发更新
        }
        return result;
    },
    deleteProperty(target, key) {
        const result = Reflect.deleteProperty(target, key);
        trigger(target, key);        // 删除也能触发更新 ✓
        return result;
    }
});
```

**`ref()` vs `reactive()` 的区别**：

```typescript
// ref() — 包装基本类型为响应式
const count = ref(0);       // { value: 0 } → Proxy
count.value++;              // 需要 .value 访问
// 模板中自动解包: {{ count }} 而不是 {{ count.value }}

// reactive() — 直接代理对象
const state = reactive({ count: 0, name: 'test' });
state.count++;              // 直接访问，不需要 .value
// ⚠ 解构会失去响应式: const { count } = state; → count 不再响应
// 解决: const { count } = toRefs(state);
```

**依赖收集与触发更新的原理**：

```
1. 组件渲染时创建一个 ReactiveEffect（副作用）
2. 渲染函数执行，访问响应式数据 → 触发 Proxy.get → track()
3. track() 将当前 Effect 记录到该属性的依赖集合中（dep）
4. 数据修改 → 触发 Proxy.set → trigger()
5. trigger() 遍历 dep 中的所有 Effect，重新执行（重新渲染）

数据结构:
targetMap: WeakMap<target, Map<key, Set<Effect>>>
  └── target (原始对象)
        └── key (属性名)
              └── Set<Effect> (依赖该属性的所有副作用)
```

---

### Q39: Pinia 状态管理详解？

**项目中的 Pinia Store**：

```typescript
// stores/auth.ts — 认证状态管理
export const useAuthStore = defineStore('auth', () => {
    // State — 使用 ref()
    const accessToken = ref<string | null>(localStorage.getItem('accessToken'));
    const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'));
    const userInfo = ref<UserInfo | null>(null);

    // Getters — 使用 computed()
    const isLoggedIn = computed(() => !!accessToken.value);

    // Actions — 普通函数
    function setTokens(access: string, refresh: string) {
        accessToken.value = access;
        refreshToken.value = refresh;
        localStorage.setItem('accessToken', access);
        localStorage.setItem('refreshToken', refresh);
    }

    function logout() {
        accessToken.value = null;
        refreshToken.value = null;
        userInfo.value = null;
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
    }

    return { accessToken, refreshToken, userInfo, isLoggedIn, setTokens, logout };
});
```

**Pinia vs Vuex 的核心差异**：

```typescript
// Vuex — 必须通过 mutation 修改 state（严格模式下）
store.commit('SET_TOKEN', token);  // mutation
store.dispatch('login', credentials);  // action → commit mutation

// Pinia — 直接修改，无 mutation 概念
store.setTokens(access, refresh);  // action 直接修改 state
store.accessToken = 'xxx';         // 甚至可以直接赋值（不推荐但可行）
```

**Pinia 的 Setup Store vs Options Store**：

```typescript
// Options Store（类似 Vuex 风格）
defineStore('counter', {
    state: () => ({ count: 0 }),
    getters: { double: (state) => state.count * 2 },
    actions: { increment() { this.count++ } }
});

// Setup Store（本项目使用，更灵活）
defineStore('counter', () => {
    const count = ref(0);
    const double = computed(() => count.value * 2);
    function increment() { count.value++ }
    return { count, double, increment };
});
// Setup Store 的优势：可以使用 Composable、watch、computed 等所有 Composition API
```

---

### Q40: Axios 拦截器的工作原理？如何实现无感 Token 刷新？

**拦截器执行顺序**：

```
请求拦截器（LIFO 后进先出）:
  拦截器2.request → 拦截器1.request → 发送请求

响应拦截器（FIFO 先进先出）:
  响应 → 拦截器1.response → 拦截器2.response → 返回给调用者
```

**项目的请求拦截器**：

```typescript
// 每个请求自动附加 Token
axios.interceptors.request.use((config) => {
    const auth = useAuthStore();
    if (auth.accessToken) {
        config.headers.Authorization = `Bearer ${auth.accessToken}`;
    }
    return config;
});
```

**无感 Token 刷新的完整实现**：

```typescript
let isRefreshing = false;
let failedQueue: Array<{ resolve: Function; reject: Function }> = [];

axios.interceptors.response.use(
    (response) => response,  // 成功直接返回
    async (error) => {
        const originalRequest = error.config;

        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;  // 标记已重试，防止死循环

            if (isRefreshing) {
                // 已有刷新请求在执行 → 当前请求排队等待
                return new Promise((resolve, reject) => {
                    failedQueue.push({ resolve, reject });
                }).then(() => {
                    // 刷新完成后，用新 Token 重试
                    originalRequest.headers.Authorization =
                        `Bearer ${useAuthStore().accessToken}`;
                    return axios(originalRequest);
                });
            }

            isRefreshing = true;
            try {
                const auth = useAuthStore();
                const { data } = await axios.post('/api/auth/refresh', {
                    refreshToken: auth.refreshToken
                });
                auth.setTokens(data.accessToken, data.refreshToken);

                // 通知队列中的所有等待请求
                failedQueue.forEach(({ resolve }) => resolve());
                failedQueue = [];

                // 重试原始请求
                originalRequest.headers.Authorization =
                    `Bearer ${data.accessToken}`;
                return axios(originalRequest);
            } catch (refreshError) {
                // Refresh Token 也过期 → 强制登出
                failedQueue.forEach(({ reject }) => reject(refreshError));
                failedQueue = [];
                useAuthStore().logout();
                router.push('/login');
                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }
        return Promise.reject(error);
    }
);
```

**关键设计点**：
1. **防重复刷新**：`isRefreshing` 标志确保同一时间只有一个刷新请求
2. **请求排队**：其他并发的 401 请求放入 `failedQueue`，刷新成功后统一重试
3. **防死循环**：`_retry` 标记确保同一请求只重试一次
4. **失败降级**：刷新失败时清空队列、登出用户

---

### Q41: Vue Router 导航守卫的执行顺序？

**完整的导航解析流程**：

```
1. 导航被触发
2. 在失活的组件里调用 onBeforeRouteLeave 守卫
3. 调用全局 beforeEach 守卫
4. 在重用的组件里调用 onBeforeRouteUpdate 守卫
5. 在路由配置里调用 beforeEnter
6. 解析异步路由组件
7. 在被激活的组件里调用 onBeforeRouteEnter
8. 调用全局 beforeResolve 守卫
9. 导航被确认
10. 调用全局 afterEach 钩子
11. 触发 DOM 更新
12. 调用 onBeforeRouteEnter 中传给 next 的回调函数
```

**项目中的路由守卫**：

```typescript
// router/index.ts
router.beforeEach((to, from) => {
    const auth = useAuthStore();
    // 未登录 → 重定向到登录页
    if (to.path !== '/login' && !auth.isLoggedIn) {
        return '/login';
    }
    // 已登录 → 不允许再访问登录页
    if (to.path === '/login' && auth.isLoggedIn) {
        return '/';
    }
});
```

---

## 十一、网络与协议

### Q42: HTTP 1.1 vs HTTP 2.0 vs HTTP 3.0？

| 特性 | HTTP/1.1 | HTTP/2 | HTTP/3 |
|------|----------|--------|--------|
| 年份 | 1997 | 2015 | 2022 |
| 传输层 | TCP | TCP | QUIC (基于 UDP) |
| 多路复用 | ❌ 队头阻塞 | ✅ 一个连接多个流 | ✅ 无队头阻塞 |
| 头部压缩 | ❌ | HPACK | QPACK |
| 服务端推送 | ❌ | ✅ | ✅ |
| 连接建立 | TCP 三次握手 + TLS 四次握手 | 同 1.1 | 0-RTT / 1-RTT |

**HTTP/1.1 的队头阻塞**：
- 同一连接上的请求必须按顺序响应（FIFO）
- 请求 A 耗时长 → 请求 B、C 被阻塞
- 解决方法：浏览器对同一域名开 6 个 TCP 连接

**HTTP/2 的多路复用**：
- 将 HTTP 报文分割为二进制帧（Frame），帧头标记属于哪个流（Stream）
- 一个 TCP 连接上可以并行传输多个流
- 但 TCP 层面的丢包会导致所有流被阻塞（TCP 队头阻塞）

**HTTP/3 的 QUIC 协议**：
- 基于 UDP，在应用层实现了可靠传输
- 每个流独立可靠传输，一个流丢包不影响其他流
- 连接 ID 标识连接（非 IP+Port），WiFi 切移动网络时连接不断

---

### Q43: TCP 三次握手和四次挥手？为什么不是两次/五次？

**三次握手**：

```
Client          Network          Server
  |                                |
  |--- SYN (seq=x) --------------->|  1. 客户端发送 SYN，进入 SYN_SENT
  |                                |     Server 进入 SYN_RCVD
  |<-- SYN+ACK (seq=y, ack=x+1) --|  2. 服务端发送 SYN+ACK
  |                                |
  |--- ACK (ack=y+1) ------------->|  3. 客户端发送 ACK，进入 ESTABLISHED
  |                                |     Server 进入 ESTABLISHED
```

**为什么不是两次？**

假设只有两次握手：客户端发 SYN → 服务端收到后直接建立连接。问题：如果客户端之前发的一个**延迟的旧 SYN**（网络拥堵导致）现在到达了服务端，服务端会认为是新连接请求并建立连接，但客户端并不知道。三次握手的第三步（客户端 ACK）让服务端确认客户端也认可这个连接。

**为什么不是四次？**

服务端的 SYN 和 ACK 可以合并在一个报文中发送（SYN+ACK），所以只需要三次而不是四次。

**四次挥手**：

```
Client          Network          Server
  |                                |
  |--- FIN (seq=u) --------------->|  1. 客户端请求关闭，进入 FIN_WAIT_1
  |                                |     Server 进入 CLOSE_WAIT
  |<-- ACK (ack=u+1) -------------|  2. 服务端确认（半关闭状态）
  |                                |     Client 进入 FIN_WAIT_2
  |  （服务端继续发送剩余数据...）      |
  |<-- FIN (seq=v) ----------------|  3. 服务端也请求关闭，进入 LAST_ACK
  |                                |
  |--- ACK (ack=v+1) ------------>|  4. 客户端确认，进入 TIME_WAIT
  |                                |     Server 进入 CLOSED
  |  （等待 2MSL 后）                |
  |  Client 进入 CLOSED            |
```

**为什么四次而不是三次？**

TCP 是全双工的，每个方向的关闭是独立的。收到 FIN 表示对方不再发数据了，但自己可能还有数据要发。所以 ACK 和 FIN 必须分开发：先 ACK 确认收到对方的关闭请求，等自己的数据发完后再发 FIN。

**TIME_WAIT 存在的原因**：
1. 确保最后一个 ACK 能到达（如果丢失，对方会重发 FIN）
2. 让网络中延迟的旧报文在连接关闭前消亡（2MSL ≈ 60秒）

---

### Q44: CORS 跨域的原理、流程和项目配置？

**同源策略**：浏览器限制 JS 脚本只能访问同协议、同域名、同端口的资源。

```
http://localhost:5173 (Vue 前端)  →  http://localhost:8080 (Spring Boot 后端)
       ↑ 端口不同 → 跨域！
```

**CORS 工作流程**：

**简单请求**（GET/POST/HEAD，且 Content-Type 为 text/plain、multipart/form-data、application/x-www-form-urlencoded）：
```
Browser → 直接发送请求（附加 Origin 头）
Server → 响应中包含 Access-Control-Allow-Origin
Browser → 检查响应头，匹配则允许 JS 读取响应
```

**预检请求**（PUT/DELETE，或 Content-Type 为 application/json，或有自定义头如 Authorization）：
```
Browser → OPTIONS 预检请求
           Origin: http://localhost:5173
           Access-Control-Request-Method: POST
           Access-Control-Request-Headers: Authorization, Content-Type

Server → 预检响应
           Access-Control-Allow-Origin: http://localhost:5173
           Access-Control-Allow-Methods: GET, POST, PUT, DELETE
           Access-Control-Allow-Headers: Authorization, Content-Type
           Access-Control-Max-Age: 3600  ← 预检结果缓存时间

Browser → 预检通过 → 发送实际请求
```

**项目配置**：

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    // 允许的源（支持通配符模式）
    config.setAllowedOriginPatterns(List.of("http://localhost:*"));
    // 允许的 HTTP 方法
    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    // 允许的请求头（Authorization 头是必须的，用于 JWT）
    config.setAllowedHeaders(List.of("*"));
    // 允许携带凭证（Cookie / Authorization 头）
    config.setAllowCredentials(true);
    // ⚠ setAllowCredentials(true) 时不能用 setAllowedOrigins("*")
    // 必须用 setAllowedOriginPatterns

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

**WebSocket 的 CORS**：WebSocket 握手是 HTTP 请求，也受 CORS 限制：
```java
registry.addEndpoint("/ws")
        .setAllowedOriginPatterns("*");  // WebSocket 端点也需要配置 CORS
```

---

## 十二、项目架构与场景设计题

### Q45: 为什么采用事件驱动架构？

**直接调用方式的问题**：

```java
// 假设直接调用
public void handleChatMessage(...) {
    gameChatService.sendMessage(...);
    agentOrchestrator.handleMessage(...);  // 强耦合
    messageStatService.record(...);         // 再加一个功能就要再改一行
    auditLogService.log(...);              // 越来越多...
}
```

**事件驱动方式**：

```java
// Controller 只做一件事：发消息 + 发事件
gameChatService.sendMessage(...);
eventPublisher.publishEvent(new ChatMessageEvent(...));
// 谁关心消息谁自己注册监听，Controller 不需要知道

// AgentOrchestrator — AI 引擎
@EventListener
public void onChatMessage(ChatMessageEvent event) { ... }

// 未来可扩展（零修改 Controller）：
// MessageStatService — 消息统计
// AuditLogService — 审计日志
// ContentFilterService — 敏感词过滤
```

**核心优势**：
1. **解耦**：删除整个 AI 引擎模块，基础聊天功能完全不受影响
2. **可扩展**：新增功能只需新增 `@EventListener`，不修改任何已有代码
3. **可测试**：Controller 和 AI 引擎可以完全独立测试

---

### Q46: 如何保证 AI 回复不会形成死循环？

**风险场景**：
```
Agent A 回复消息 → 触发 ChatMessageEvent → AgentOrchestrator 监听到
→ 判断需要 Agent B 回复 → Agent B 回复 → 触发 ChatMessageEvent
→ AgentOrchestrator 监听到 → 判断需要 Agent A 回复 → ...（死循环）
```

**项目的三层防护**：

```java
// 第一层：源头过滤（AgentOrchestrator）
@EventListener
public void onChatMessage(ChatMessageEvent event) {
    if (event.isFromAi()) return;  // AI 消息不触发 AI 回复
    // ...
}

// 第二层：事件标记
// Controller 发布事件时标记 fromAi=false
eventPublisher.publishEvent(new ChatMessageEvent(
        this, roomId, senderRoleId, content, false));  // false = 人类消息

// GameChatService.sendAiMessage() 不发布事件（只有人类消息才发事件）
// 或发布时标记 fromAi=true → 被第一层过滤

// 第三层：DM 控制
// 自由讨论阶段不是所有 AI 都回复
// DM 通过 selectRespondents 工具选择 1-2 个最相关的角色回复
// 避免"所有人一起说话"的混乱场景
```

---

### Q47: 如果游戏中途 Redis 宕机怎么办？

**影响分析**：

| 数据 | 影响 | 恢复可能性 |
|------|------|-----------|
| LiveGameRoom | 所有进行中的游戏状态丢失 | MongoDB 有 GameRoom 快照（但不是最新的） |
| game:messages | 聊天消息全部丢失 | 无法恢复 |
| auth:blacklist | JWT 黑名单丢失 → 已登出的 Token 可能重新生效 | Token 过期后自然失效（最多 2 小时风险窗口） |
| script:{id} | 剧本缓存丢失 | 可从 MongoDB 重新加载 |
| auth:refresh | Refresh Token 丢失 → 所有用户需要重新登录 | 需要重新登录 |

**现有的缓解措施**：
- MongoDB GameRoom 在关键节点（创建、开始、阶段推进）更新
- GameRecord 在游戏正常结束时持久化到 MongoDB

**可优化方案**：
1. **Redis AOF 持久化**：`appendonly yes`，每次写命令追加到日志文件，宕机后可恢复
2. **Redis Sentinel / Cluster**：自动故障转移，主节点宕机后从节点接管
3. **关键操作双写**：阶段推进时同时写 Redis 和 MongoDB
4. **优雅降级**：WebSocket 检测到 Redis 不可用时，前端显示"服务暂时不可用"

---

### Q48: 如何优化 AI 响应延迟？

**当前延迟分解**：

```
用户发消息 → 事件发布 (~0ms) → AgentOrchestrator 路由 (~1ms)
→ 构建 Prompt (~5ms) → LLM API 调用 (~3-10秒) → 解析响应 (~1ms)
→ 存储消息 (~1ms) → WebSocket 广播 (~1ms)

总延迟: ~3-10秒（瓶颈在 LLM API 调用）
```

**现有优化**：
1. **TYPING 信号**：AI 开始处理时广播 TYPING，前端显示"正在输入..."
2. **@Async 异步**：不阻塞消息处理链
3. **上下文压缩**：减少 Prompt 中的 Token 数量，LLM 处理更快

**进一步优化方案**：

```java
// 方案1：流式响应（Streaming）
Flux<ChatResponse> stream = chatModel.stream(prompt);
stream.subscribe(response -> {
    String chunk = response.getResult().getOutput().getText();
    // 每收到一个 Token 就通过 WebSocket 推送
    messagingTemplate.convertAndSend("/topic/room." + roomId + ".stream",
            Map.of("type", "STREAM_CHUNK", "roleId", roleId, "chunk", chunk));
});
// 用户在 LLM 生成第一个 Token 时就能看到内容

// 方案2：模型分级
// 简单回复（日常闲聊）→ Claude Haiku（快，便宜）
// 复杂决策（搜证判断、投票分析）→ Claude Sonnet（强，贵）

// 方案3：并行调用
// 多个 Agent 需要回复时，用 CompletableFuture.allOf() 并行调用
CompletableFuture.allOf(
    CompletableFuture.runAsync(() -> agentExecutor.executeAgentReply(roomId, roleA)),
    CompletableFuture.runAsync(() -> agentExecutor.executeAgentReply(roomId, roleB))
).join();
```

---

### Q49: 项目的技术难点和解决方案？

**难点 1：AI 角色沙盒隔离**
- 挑战：多个 AI 角色共享游戏数据，但各自只能看到自己的信息
- 方案：`PromptBuilder` 在提示词构建层面实现过滤（按 roleId 过滤线索和消息）
- 效果：隔离发生在数据进入 LLM 之前，物理上不可能泄露

**难点 2：上下文窗口管理**
- 挑战：长时间游戏消息累积，超出 LLM 200K token 限制
- 方案：双触发压缩（幕次切换 + Token 阈值）+ 滑动窗口 + 历史摘要
- 效果：5 幕 300 条消息的游戏，实际 Prompt 控制在 ~10K tokens

**难点 3：DM 工具调用的可扩展性**
- 挑战：DM 需要执行搜证、投票、阶段推进等操作，且未来可能新增
- 方案：DmTool 接口 + Spring DI 自动发现 + Spring AI Function Calling
- 效果：新增工具只需一个 `@Component` 类，零修改注册和调用代码

**难点 4：事件驱动的消息路由**
- 挑战：不同阶段（轮流发言/自由讨论/投票）下 AI 行为完全不同
- 方案：`AgentOrchestrator` 根据 `PhaseType` 分发 + DM AI 通过工具自主决定阶段转换
- 效果：业务逻辑集中在 Orchestrator 中，DM AI 拥有真正的"决策权"

**难点 5：Spring AI API 兼容性**
- 挑战：`ChatOptions.builder()` 不支持 `toolCallbacks()`，编译通过但运行时失败
- 方案：通过 `javap` 反编译 Spring AI JAR，发现需要使用 `ToolCallingChatOptions`
- 教训：新框架的 API 文档可能不完善，需要直接查看字节码确认

---

### Q50: 如果让你重新设计，你会改进哪些地方？

**1. 消息存储优化**：
```
当前：所有消息在一个 Redis List → LRANGE 全量读取
改进：按幕次分 Key（game:{roomId}:messages:stage:{n}）
更进一步：使用 Redis Streams（支持消费者组、消息确认、范围查询）
```

**2. LiveGameRoom 并发安全**：
```
当前：读-改-写不是原子的（get → 修改 Java 对象 → save 回 Redis）
风险：两个请求并发修改同一个房间（如同时搜证）会互相覆盖
改进：
- 方案A：Redis Lua 脚本（保证原子的读-改-写）
- 方案B：乐观锁（save 时检查版本号，冲突则重试）
- 方案C：WATCH + MULTI/EXEC（Redis 事务）
```

**3. 流式 AI 响应**：
```
当前：等 AI 完整回复后一次性发送
改进：使用 Spring AI 的 stream() + WebSocket 逐 Token 推送
效果：用户从等待 5-10 秒变为 0.5 秒后开始看到内容
```

**4. 分层缓存**：
```
当前：Redis 单层缓存
改进：Caffeine（本地缓存）→ Redis → MongoDB 三级缓存
热点剧本缓存在 JVM 内存中，0ms 读取
```

**5. 可观测性**：
```
当前：仅 log
改进：
- Prometheus 指标：AI 调用延迟、Token 消耗、成功率
- Grafana 仪表板：实时监控游戏房间数、在线人数
- Micrometer Tracing：请求链路追踪（从 WebSocket 到 AI 调用）
```

**6. 消息队列解耦**：
```
当前：Spring Event（进程内）
改进：RabbitMQ / Kafka（跨进程）
优势：
- AI 处理高峰期可以流量削峰
- AI 服务可以独立部署、独立扩缩容
- 消息持久化，进程崩溃不丢消息
```

---

## 十三、Java 基础与 JVM

### Q51: HashMap 的原理？为什么线程不安全？

**JDK 8 HashMap 结构**：数组 + 链表 + 红黑树

```
table[0] → null
table[1] → Node(K1,V1) → Node(K2,V2) → ...  （链表）
table[2] → TreeNode(K3,V3) → ...              （红黑树，链表长度≥8时转换）
...
table[n-1]
```

**put 操作流程**：
1. 计算 `key.hashCode()`，经过扰动函数 `(h = key.hashCode()) ^ (h >>> 16)` 减少碰撞
2. `hash & (table.length - 1)` 计算桶下标（要求 table 长度为 2 的幂）
3. 桶为空：直接插入新 Node
4. 桶不为空：遍历链表/红黑树，找到相同 key 则覆盖 value，找不到则尾插
5. 链表长度 ≥ 8 且 table 长度 ≥ 64：链表转红黑树（O(n) → O(log n)）
6. `size > capacity * loadFactor(0.75)` 时扩容（容量翻倍，rehash）

**线程不安全的原因**：
1. **JDK 7 死循环**：扩容时链表采用头插法，并发 rehash 可能形成环形链表 → CPU 100%
2. **JDK 8 数据丢失**：扩容时虽然改为尾插法，但并发 put 可能互相覆盖节点
3. **size 计数不准确**：`size++` 不是原子操作

**解决**：使用 `ConcurrentHashMap`（本项目在 `PhaseTimerService` 中使用）

---

### Q52: Java 中的四种引用类型？

| 引用类型 | 回收时机 | 用途 |
|---------|---------|------|
| 强引用（Strong） | 永不回收（除非 null） | 99% 的场景 |
| 软引用（Soft） | 内存不足时回收 | 缓存（如图片缓存） |
| 弱引用（Weak） | 下次 GC 时回收 | ThreadLocalMap、WeakHashMap |
| 虚引用（Phantom） | 随时回收，仅用于跟踪回收 | 资源释放跟踪（如 DirectByteBuffer） |

**与项目的关联**：Vue 3 的 `WeakMap` 用于存储响应式对象的依赖关系（`targetMap: WeakMap<target, depsMap>`），当原始对象被 GC 回收时，对应的依赖关系自动清理，避免内存泄漏。Java 的 `ConcurrentHashMap` 中 `targetMap` 也有类似用法。

---

### Q53: JVM 垃圾回收算法？G1 vs ZGC？

**基础算法**：

| 算法 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| 标记-清除 | 标记存活对象，清除未标记 | 简单 | 内存碎片 |
| 标记-整理 | 标记后将存活对象移到一端 | 无碎片 | 移动对象开销大 |
| 复制算法 | 存活对象复制到另一半空间 | 无碎片，高效 | 浪费一半空间 |
| 分代算法 | 新生代（复制）+ 老年代（标记-整理） | 综合最优 | 复杂 |

**G1 vs ZGC**：

| 特性 | G1（JDK 9+ 默认） | ZGC（JDK 15+） |
|------|-------------------|----------------|
| 设计目标 | 可预测的停顿时间 | 超低延迟（< 10ms） |
| 堆大小 | TB 级 | TB 级 |
| 停顿时间 | 几十到几百毫秒 | < 10ms（不随堆大小变化） |
| 并发标记 | 是 | 是 |
| 并发压缩 | 否（需要 STW） | 是（通过染色指针） |
| 适用场景 | 通用服务端应用 | 延迟敏感（实时系统） |

**项目场景分析**：本项目是实时游戏服务，对延迟敏感（AI 回复 + WebSocket 推送）。生产环境建议使用 G1（默认够用），如果观察到 GC 停顿影响 WebSocket 延迟，可以切换到 ZGC（`-XX:+UseZGC`）。

---

## 十四、Spring 框架深度

### Q54: Spring IoC 容器的启动过程？

**核心流程**（`AbstractApplicationContext.refresh()`）：

```
1. prepareRefresh()           — 设置启动时间、活跃标志、初始化属性源
2. obtainFreshBeanFactory()   — 创建 BeanFactory，加载 BeanDefinition
3. prepareBeanFactory()       — 配置 BeanFactory（ClassLoader、后处理器）
4. postProcessBeanFactory()   — 子类扩展点
5. invokeBeanFactoryPostProcessors() — 执行 BeanFactory 后处理器
   ↓ ConfigurationClassPostProcessor 解析 @Configuration、@Bean、@Import
   ↓ AutoConfigurationImportSelector 加载自动配置类
6. registerBeanPostProcessors()  — 注册 Bean 后处理器
   ↓ AsyncAnnotationBeanPostProcessor（处理 @Async）
   ↓ AutowiredAnnotationBeanPostProcessor（处理 @Autowired）
7. initMessageSource()        — 国际化
8. initApplicationEventMulticaster()  — 事件广播器
9. onRefresh()                — 子类扩展（如 ServletWebServer 启动 Tomcat）
10. registerListeners()       — 注册事件监听器
11. finishBeanFactoryInitialization() — 实例化所有非懒加载的单例 Bean
    ↓ 创建 Bean → 属性注入 → 初始化回调 → AOP 代理
12. finishRefresh()           — 发布 ContextRefreshedEvent
```

**Bean 的生命周期**：

```
实例化（构造函数）
  → 属性注入（@Autowired）
  → Aware 接口回调（BeanNameAware、ApplicationContextAware）
  → @PostConstruct
  → InitializingBean.afterPropertiesSet()
  → 自定义 init-method
  → BeanPostProcessor.postProcessAfterInitialization()（AOP 代理在此生成）
  → 就绪（可被使用）
  → @PreDestroy
  → DisposableBean.destroy()
  → 自定义 destroy-method
```

---

### Q55: Spring AOP 的实现原理？JDK 动态代理 vs CGLIB？

**AOP 代理的创建时机**：在 `BeanPostProcessor.postProcessAfterInitialization()` 阶段，`AnnotationAwareAspectJAutoProxyCreator` 检查 Bean 是否需要代理。

**JDK 动态代理**：
- 条件：目标类实现了接口
- 原理：`java.lang.reflect.Proxy.newProxyInstance()` 生成接口的代理类
- 代理类实现相同接口，方法调用通过 `InvocationHandler` 转发

**CGLIB 代理**：
- 条件：目标类没有实现接口，或配置了 `proxyTargetClass=true`
- 原理：通过字节码生成目标类的子类，重写方法实现拦截
- Spring Boot 2.0+ 默认使用 CGLIB（即使有接口）

**项目中的 AOP 应用**：

```java
// @Async — AsyncAnnotationBeanPostProcessor 为标注 @Async 的 Bean 生成代理
// 代理方法将调用提交到线程池
@Async("aiExecutor")
public void executeAgentReply(String roomId, ObjectId roleId) { ... }

// 调用方看到的是 CGLIB 代理对象
// agentExecutor.executeAgentReply() → 代理 → 线程池.submit(真实方法)
```

**追问：为什么 this 调用 @Async 方法不会异步？**

因为 `this` 指向的是真实对象，不是代理对象。AOP 的拦截逻辑在代理对象上，直接调用 `this.asyncMethod()` 绕过了代理，自然不会触发异步行为。

---

### Q56: @Transactional 的传播行为？

| 传播行为 | 说明 |
|---------|------|
| REQUIRED（默认） | 有事务则加入，没有则创建新事务 |
| REQUIRES_NEW | 挂起当前事务，创建新事务 |
| SUPPORTS | 有事务则加入，没有则非事务执行 |
| NOT_SUPPORTED | 挂起当前事务，非事务执行 |
| MANDATORY | 必须在事务中调用，否则抛异常 |
| NEVER | 必须在非事务中调用，否则抛异常 |
| NESTED | 有事务则创建嵌套事务（保存点） |

**本项目为什么不使用 @Transactional？**

因为 MongoDB 4.0+ 虽然支持多文档事务，但：
1. 本项目的写操作多是单文档操作（MongoDB 单文档操作天然原子）
2. 运行时数据在 Redis 中（Redis 不参与 Spring 事务管理）
3. 游戏结束时的 `persistGameRecord()` 是单次 `save()`，不涉及多文档事务

如果未来需要跨文档事务（如同时更新 User 积分和 GameRecord），再引入 `@Transactional`。

---

### Q57: Spring 中的循环依赖问题和三级缓存？

**什么是循环依赖**：A 依赖 B，B 依赖 A。

**Spring 的三级缓存解决**：

```
singletonObjects       — 一级缓存：完全初始化的 Bean
earlySingletonObjects  — 二级缓存：早期曝光的 Bean（属性未注入）
singletonFactories     — 三级缓存：ObjectFactory（创建早期 Bean 的工厂）
```

**解决过程**：
```
1. 创建 A → 实例化（构造函数）→ 放入三级缓存（ObjectFactory）
2. A 属性注入 → 发现需要 B → 创建 B
3. 创建 B → 实例化 → 放入三级缓存
4. B 属性注入 → 发现需要 A → 从三级缓存获取 A 的 ObjectFactory
   → 调用 ObjectFactory.getObject() 获取 A 的早期引用（可能是代理）
   → 放入二级缓存，删除三级缓存
5. B 属性注入完成 → B 初始化完成 → 放入一级缓存
6. A 获取到 B → A 属性注入完成 → A 初始化完成 → 放入一级缓存
```

**为什么需要三级缓存而不是两级？**

三级缓存的 `ObjectFactory` 可以决定返回原始对象还是 AOP 代理对象。如果 A 需要被代理（如 `@Async`），`ObjectFactory.getObject()` 会提前创建代理。二级缓存存储的是这个（可能是代理的）早期引用，确保后续所有对 A 的引用都是同一个对象（要么都是原始对象，要么都是代理）。

**注意**：构造函数注入无法解决循环依赖（因为构造函数调用时对象还没创建，无法放入缓存）。本项目使用 `@RequiredArgsConstructor`（Lombok 生成的构造函数注入），需要确保不存在循环依赖。

---

### Q58: Spring Boot 中如何自定义异常处理？

**@ControllerAdvice + @ExceptionHandler**：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(IllegalStateException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        return Map.of("error", "服务器内部错误");
    }
}
```

**项目中的异常使用**：
- `IllegalArgumentException`：参数校验失败（如"剧本不存在"、"Refresh Token 无效"）
- `IllegalStateException`：状态校验失败（如"房间状态不正确，无法开始游戏"、"请先选择剧本"）
- 这些异常在 Service 层抛出，由全局异常处理器统一转为 HTTP 响应

---

> **文档说明**：本文基于剧本杀游戏项目实际代码撰写，所有代码片段均来自项目源文件。适合用于 Java 后端 / Spring Boot / 全栈开发方向的面试准备。每题均包含原理讲解、项目实战代码和深度追问，建议结合项目源码一起复习。
