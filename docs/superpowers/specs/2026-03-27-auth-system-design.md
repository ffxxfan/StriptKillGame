# 用户身份验证系统设计文档

**日期**: 2026-03-27
**项目**: StriptKillGameDemo2
**范围**: 登录、登出、注册、修改密码、JWT WebSocket 鉴权、前端认证界面

---

## 1. 背景与目标

基于现有 Spring Boot 3.5.12 + MongoDB + Redis 项目，实现完整的用户身份验证系统。User 实体已存在（MongoDB Document，字段：id/username/password/nickname/avatarUrl/createdAt）。

**目标**：
- 后端：Spring Security 6 + JWT（Access + Refresh Token）鉴权
- WebSocket：STOMP CONNECT 时校验 JWT
- 前端：Vue 3 + Element Plus 登录页及用户信息页

---

## 2. 技术选型

| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 3.5.12 |
| 数据库 | MongoDB（User 实体） |
| 缓存 | Redis（Refresh Token + 黑名单） |
| 安全 | Spring Security 6 + JJWT 0.12.x |
| WebSocket | Spring WebSocket + STOMP |
| 前端 | Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router 4 + Axios |

---

## 3. 后端架构

### 3.1 新增 Maven 依赖

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- WebSocket -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- JJWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

### 3.2 包结构

```
com.example.striptkillgamedemo2
├── config/
│   ├── SecurityConfig.java           # Spring Security 过滤链配置
│   └── WebSocketConfig.java          # STOMP 端点 + ChannelInterceptor 注册
├── security/
│   ├── JwtProperties.java            # @ConfigurationProperties("jwt")
│   ├── JwtService.java               # Token 生成、解析、黑名单检查
│   ├── JwtAuthenticationFilter.java  # OncePerRequestFilter，每次请求验 JWT
│   └── WebSocketAuthInterceptor.java # ChannelInterceptor，CONNECT 时验 JWT
├── controller/
│   └── AuthController.java           # 6 个 REST 端点
├── service/
│   ├── AuthService.java              # 登录/登出/改密/注册/刷新 Token 业务逻辑
│   └── UserService.java              # 用户 CRUD（含 UserDetailsService 实现）
├── repository/
│   └── UserRepository.java           # MongoRepository<User, ObjectId>
└── dto/
    ├── LoginRequest.java
    ├── RegisterRequest.java
    ├── ChangePasswordRequest.java
    ├── TokenResponse.java            # {accessToken, refreshToken, expiresIn}
    └── UserInfoResponse.java         # {id, username, nickname, avatarUrl, createdAt}
```

### 3.3 JWT Token 设计

| 字段 | Access Token | Refresh Token |
|------|-------------|---------------|
| `sub` | userId (hex string) | userId (hex string) |
| `username` | ✓ | — |
| `jti` | UUID | UUID |
| `type` | `access` | `refresh` |
| 有效期 | 2 小时 | 30 天 |

签名算法：HS256，密钥通过 `application.properties` 的 `jwt.secret` 配置（Base64 编码，至少 256 位）。

### 3.4 Redis Key 设计

```
auth:blacklist:{jti}     → "1"，TTL = access token 剩余有效期
auth:refresh:{userId}    → refreshToken 字符串，TTL = 30 天
```

### 3.5 各操作对 Redis 的影响

| 操作 | Redis 写 | Redis 删 |
|------|---------|---------|
| 登录 | `auth:refresh:{userId}` = refreshToken | — |
| 登出 | `auth:blacklist:{jti}` = "1" | `auth:refresh:{userId}` |
| 修改密码 | `auth:blacklist:{jti}` = "1" | `auth:refresh:{userId}` |
| 刷新 Token | `auth:refresh:{userId}` = 新 Refresh Token（轮换） | — |

### 3.6 REST API 端点

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/api/auth/register` | 无 | 注册新用户 |
| POST | `/api/auth/login` | 无 | 验证用户名密码，返回 Token 对 |
| POST | `/api/auth/refresh` | 无（Bearer Refresh Token） | 换取新 Access Token |
| POST | `/api/auth/logout` | Bearer Access Token | 登出，加黑名单，删 Refresh Token |
| PUT | `/api/auth/password` | Bearer Access Token | 验旧密码，改密，踢所有 Token |
| GET | `/api/auth/info` | Bearer Access Token | 返回当前用户信息 |

### 3.7 Spring Security 过滤链

- **放行**（无需鉴权）：`POST /api/auth/login`、`POST /api/auth/register`、`POST /api/auth/refresh`、`/ws/**`
- **保护**：其他所有路径，需 Bearer Access Token
- Session 策略：`STATELESS`
- CSRF：禁用（纯 API + JWT）

### 3.8 WebSocket 鉴权

`WebSocketAuthInterceptor` 实现 `ChannelInterceptor`，在 `CONNECT` 命令时：
1. 从 `nativeHeaders.Authorization` 取 `Bearer {token}`
2. 调用 `JwtService.validateAccessToken(token)`
3. 验证失败 → 抛出 `MessageDeliveryException`，拒绝连接
4. 验证成功 → 将 `UsernamePasswordAuthenticationToken` 设入 `MessageHeaders`

---

## 4. 前端架构

### 4.1 技术栈

Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router 4 + Axios

### 4.2 目录结构

```
frontend/src/
├── views/
│   ├── LoginView.vue        # 登录页：居中卡片，用户名/密码，Element Plus
│   ├── HomeView.vue         # 主页：默认占位图，右上角头像，主内容区预留插槽
│   └── ProfileView.vue      # 用户信息页：信息展示、改密表单、登出按钮
├── stores/
│   └── auth.ts              # Pinia store：accessToken, refreshToken, userInfo
├── api/
│   ├── axios.ts             # Axios 实例 + 请求/响应拦截器
│   └── auth.ts              # 封装所有 /api/auth/* 调用
├── router/
│   └── index.ts             # 路由守卫：未登录跳 /login
└── App.vue
```

### 4.3 路由设计

| 路径 | 组件 | 鉴权 |
|------|------|------|
| `/login` | LoginView | 否（已登录跳 `/`） |
| `/` | HomeView | 是 |
| `/profile` | ProfileView | 是 |

### 4.4 关键交互流程

**登录**：
1. 用户提交表单 → 调 `POST /api/auth/login`
2. 成功 → Token 存入 Pinia store + localStorage
3. 跳转 `/`

**自动刷新 Token（Axios 响应拦截器）**：
1. 收到 401 → 取 localStorage 中 refreshToken
2. 调 `POST /api/auth/refresh`，成功 → 更新 store（新 Access Token + 新 Refresh Token）→ 重试原请求
3. Refresh Token 也失效 → 清除 store → 跳 `/login`

**用户头像**：
- 右上角显示头像（有 `avatarUrl` 则用图片，否则 Element Plus `<el-avatar>` 默认图标）
- 点击 → 跳转 `/profile`

**ProfileView**：
- 页面加载时调 `GET /api/auth/info` 展示最新信息
- 修改密码表单：旧密码 + 新密码 + 确认新密码，调 `PUT /api/auth/password`
- 登出按钮：调 `POST /api/auth/logout` → 清除 store/localStorage → 跳 `/login`

**HomeView 扩展接口**：
- 主内容区提供具名插槽 `<slot name="main-content" />`，供后续功能注入

---

## 5. 错误处理

**后端**：
- 统一使用 `ResponseEntity` 返回，错误时带 `{code, message}` JSON
- `UsernameNotFoundException` → 401
- 密码不匹配 → 400
- Token 无效/过期 → 401
- 用户名已存在 → 409

**前端**：
- Axios 拦截器统一捕获错误，弹出 Element Plus `ElMessage` 提示
- 401 → 自动刷新或跳登录页

---

## 6. 配置（application.properties 新增）

```properties
# JWT 配置
jwt.secret=your-base64-encoded-secret-key-at-least-256-bits
jwt.access-token-expiration=7200    # 秒，2小时
jwt.refresh-token-expiration=2592000 # 秒，30天
```
