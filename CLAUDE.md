# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build (skips tests)
mvn clean package -DskipTests

# Build the release assembly (fat JAR + scripts via maven-assembly-plugin)
mvn clean package

# Run (dev profile is active by default)
mvn spring-boot:run

# Run a single test class
mvn test -Dtest=JwtTokenTest

# Docker build & run
docker build -t online-mall-app .
docker run -d -p 8080:8080 --env-file .env online-mall-app

# Or use the all-in-one script (MySQL + Redis containers included)
bash run.sh
```

The app starts on **port 8080**. The `dev` profile is active by default (`application.yaml` → `spring.profiles.active: dev`).
Java 17, Spring Boot 3.5.5. Main class: `com.scutmmq.OnlineMallApplication`.

## Required Infrastructure

- **MySQL** — default connects to `119.23.76.234:3306/online_mall` (overridable via `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`)
- **Redis** — default `localhost:6379` db 1 (overridable via `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_DATABASE`)
- **Redis Stream consumer group** — must exist before startup; create manually:
  ```
  XGROUP CREATE order:timeout:stream order:timeout:group $ MKSTREAM
  XGROUP CREATECONSUMER order:timeout:stream order:timeout:group c1
  ```
- **AI provider** — set `AI_API_KEY` env var. All provider config (`url`, `model`, `auth-header`, `auth-scheme`) is in `application.yaml` under `ai.api.*` and overridable by env vars, so swapping from DeepSeek to any OpenAI-compatible provider requires only env var changes.
- **Aliyun OSS** — credentials via `aliyun.oss.*` config or env vars (used only for image upload).

### Database initialization

No Flyway/Liquibase. Run the full schema + seed data manually:
```bash
mysql -u root -p < src/main/resources/online_mall.sql
```

## Architecture Overview

Spring Boot 3.5 / Java 17 monolith — classic layered architecture plus an embedded AI assistant module.

### Request flow

```
HTTP → RefreshInterceptor (JWT parse + token refresh) → LoginCertificationInterceptor (UserHolder check) → Controller → Service → Mapper (MyBatis-Plus + XML)
```

`RefreshInterceptor` (order 0, all paths) parses JWT and stores the user in `UserHolder` (ThreadLocal). `LoginCertificationInterceptor` (order 1) rejects requests with no user, except `/user/login`, `/user/register`, `/image/upload`.

### Response conventions

Every controller and service returns `Result` (from `com.scutmmq.entity.Result`):
- `Result.success(data)` → `{code: 1, msg: "success", data: ...}`
- `Result.error(msg)` → `{code: 0, msg: "...", data: null}`

Paginated lists use `PageResult<T>` (`{total, rows}`) wrapped inside `Result.success(pageResult)`.

### Exception handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) catches:
- `BusinessException` → `Result.error(msg)`
- `AuthorizeException` → `Result.error("登录异常:" + msg)`
- `DuplicateKeyException` → parses the duplicate field from MySQL error message
- Generic `Exception` → `Result.error` with stack trace

Druid monitor URLs (`/druid/`) are excluded from the handler.

### Entity & enum conventions

- **No base entity class** — every entity is a standalone `@Data` class with its own `id`, `createdTime`/`updatedTime` fields
- **Timestamps**: `@TableField(fill = FieldFill.INSERT)` for `createdTime`, `FieldFill.INSERT_UPDATE` for `updatedTime` (though some entities don't use fill)
- **Enums**: `@EnumValue` on the DB-mapped code field, `@JsonValue` on the Chinese description field. `MerchantType` additionally has `@JsonCreator` for AI tool-call compatibility (accepts codes, enum names, Chinese text)
- **Field naming**: camelCase in Java, snake_case in DB (`@TableField("column_name")`)

### Key patterns

- **Dependency injection**: constructor injection via `@RequiredArgsConstructor` on `final` fields
- **Bean copying**: Hutool `BeanUtil.copyProperties()` — not MapStruct
- **AOP**: `@LogAnnotation(module, type, description)` on methods/classes → `LogAdvice` persists operation logs to `operation_log` table. `ProductAdvice` times all `ProductServiceImpl` methods.
- **No `@Valid`/`@Validated`** on controller parameters — validation is done manually in services

### Package layout (`com.scutmmq`)

| Package | Responsibility |
|---|---|
| `controller` | REST endpoints: User, Product, Category, Cart, Merchant, Pay, Image, Audit |
| `service/Impl` | Business logic; `PayServiceImpl` owns the order→payment→inventory flow |
| `mapper` | MyBatis-Plus mappers; complex queries use XML in `resources/com/scutmmq/mapper/` |
| `entity` | JPA/MP entities; also contains `Result` and `PageResult` (response wrappers) |
| `dto` / `vo` | Input DTOs and response VOs; never expose entities directly |
| `utils` | `RedisIdWorker` (snowflake-style IDs), `RedisUtils` (Lua script runner), `JwtUtils`, `UserHolder`, `OrderTimeOutTask` |
| `interceptor` | `RefreshInterceptor`, `LoginCertificationInterceptor` |
| `config` | `WebConfig` (interceptor registry), `WebSocketConfig`, `RedissonConfig` |
| `exception` | `BusinessException`, `AuthorizeException`, `GlobalExceptionHandler` |
| `enums` | `OrderStatus`, `PaymentStatus`, `ChangeType`, `LoginType`, `MerchantStatus`, `MerchantType`, … |
| `aop` | `LogAdvice` (persists operation logs), `ProductAdvice` (performance timing) |
| `anno` | `@LogAnnotation` |

### Redis usage

- **Token store**: `login:token:<token>` (1 h TTL, refreshed when < 20 min left)
- **Product stock**: `product:stock:available:<productId>` — Lua scripts in `resources/lua/` atomically manage pre-reservation, cancellation, rollback, and sync. Reserved quantities tracked in hash `product:stock:reserve:<productId>`
- **Order IDs**: `RedisIdWorker` — timestamp (32-bit shift) OR incremented counter per day key `icr:shopping:yyyy:MM:dd`
- **Timeout orders**: sorted set `order:timeout:trigger` scanned every 10 s by `OrderTimeOutTask`; expired orders pushed to Stream `order:timeout:stream` consumed by a single-thread consumer
- **Notifications**: Redis Streams `notify:user:<id>` and `notify:merchant:<id>` bridged to WebSocket via `NotifyWebSocketHandler`
- **Redisson**: distributed lock `lock:stock:<productId>` guards stock writes

### WebSocket notifications

Endpoint: `/ws/notify?token=<jwt>`. On connect, `NotifyWebSocketHandler` validates the JWT, registers the session in an in-memory `ConcurrentHashMap<Long, Set<WebSocketSession>>` (separate maps for users and merchants), and flushes any offline messages from Redis Streams. Real-time pushes go through `pushToUser(userId, json)` / `pushToMerchant(merchantId, json)`.

### Key design decisions

- **Inventory pre-reservation**: placing an order calls `RedisUtils.ReserveStock` (Lua) to atomically pre-reserve. Payment calls `CancelReserveStock` then updates the DB. Timeout/cancel calls `rollBackReserveStock`.
- **Token refresh**: the `RefreshInterceptor` slides expiry when < 20 min remains; the new token is returned in the `Authorization` response header.
- **Logging**: SLF4J + Logback. Console at INFO, rolling file at `log/online-shopping-yyyy-MM-dd-N.log` (30-day retention, 10 MB max per file).

## AI Assistant Module (`com.scutmmq.ai`)

Self-contained package layered identically to the main app (entity, mapper, service, controller, dto, config). Entry point: `AiAssistantController`.

### Async submission flow

```
POST /ai/chat {sessionId, message}
  → AiAssistantService.chat()
     1. Resolve/create AiSession, persist user AiMessage
     2. Create placeholder assistant AiMessage (status=STREAMING)
     3. Create AiRun (status=QUEUED), backfill runId onto assistant message
     4. Submit AiRunRunnable to AiSessionTaskScheduler
     5. Return AiChatSubmitResponse immediately (sessionId, runId, status)

Background worker (AiRunRunnable):
  → MallUserContextExecutor.runAs(user, ...) restores UserHolder
  → aiRunService.start(runId) → RUNNING
  → AgentOrchestrator.runStreaming(user, history, userMessage, listener)
  → persistToolExecutions + persistDraftIfPresent
  → aiRunService.complete/fail(runId)
```

### AiRun state machine

```
QUEUED → RUNNING → COMPLETED / FAILED / CANCELLED
```

`AiRunService` enforces valid transitions (e.g., `start()` only from `QUEUED`).

### Per-session serialization (AiSessionTaskScheduler)

Each AI session has its own `ConcurrentLinkedDeque<Runnable>` with an `AtomicBoolean` flag. Only one worker drains a session's queue at a time (FIFO within a session). Cross-session tasks run in parallel, bounded by the `aiTaskExecutor` thread pool (core=4, max=8, queue=100, CallerRunsPolicy).

### Agent orchestrator (tool-call loop)

`AgentOrchestrator.runStreaming()`:
1. Sends system prompt (from `MallSystemPromptProvider`) + conversation history + user message + tool definitions to the AI provider
2. If the model returns `tool_calls`, executes them via `MallSkillRegistry` and feeds results back
3. Repeats until the model responds with plain text or the iteration cap (`ai.assistant.max-tool-iterations`, default 8) is reached
4. When the cap is hit, makes one final call with an empty tool list to force a natural-language response

### Tool modes (`ToolMode`)

- `READ_ONLY` — executes immediately and returns data to the model (e.g., search, get orders)
- `DRAFT_ONLY` — generates an `AiActionDraft` persisted to DB; the actual action runs only after the user confirms via `POST /ai/drafts/{id}/confirm`

### Built-in tools (10)

| Tool | Mode | What it does |
|---|---|---|
| `search_products` | READ_ONLY | Search by keyword/category/price; Chinese synonym fuzzy fallback |
| `get_product_detail` | READ_ONLY | Single product detail with merchant + category |
| `get_my_orders` | READ_ONLY | Current user's orders (optional status filter) |
| `get_my_addresses` | READ_ONLY | Current user's shipping addresses |
| `get_my_merchant` | READ_ONLY | Current user's shop info |
| `draft_create_order` | DRAFT_ONLY | Order draft with full pre-validation (stock, active, self-purchase block, address ownership) |
| `draft_add_cart_item` | DRAFT_ONLY | Cart add draft |
| `draft_register_merchant` | DRAFT_ONLY | Shop registration draft |
| `draft_update_merchant` | DRAFT_ONLY | Shop profile update draft |
| `draft_update_user_profile` | DRAFT_ONLY | User profile update draft |

Add a new tool by implementing `MallAgentTool` and annotating with `@Component`; `MallSkillRegistry` auto-discovers it. `MallUserContextExecutor.runAs(user, ...)` sets `UserHolder` before tool execution so tools can call the same services as normal requests.

### SSE streaming & replay protocol

`GET /ai/sessions/{sessionId}/events?afterId=N`:

```
1. Register SseEmitter in AiStreamHub FIRST (so live broadcasts reach it)
2. Snapshot latest DB event ID N
3. Replay: query DB for events where id > afterId AND id < N → send to emitter
4. Live: AiStreamHub.broadcast() sends all events with id >= N
```

This "register-first, snapshot, replay < N, broadcast >= N" ordering guarantees no duplicates and no gaps on reconnect.

**Event types**: `assistant.delta`, `tool.started`, `tool.finished`, `draft.created`, `run.completed`, `run.failed`.

`AiStreamEventService.append()` writes to `ai_stream_event` table first, then broadcasts via `AiStreamHub`. `PersistingOrchestratorListener` (one instance per run, not a Spring bean) translates orchestrator callbacks into persisted events + real-time assistant message content updates.

### Draft confirmation flow

```
POST /ai/drafts/{id}/confirm
  → AiActionDraftService.confirmDraft()
     1. Load draft, verify ownership + PENDING status + not expired
     2. Parse payload JSON
     3. Dispatch to the appropriate business service (OrderService, CartService, MerchantService, etc.)
     4. Mark draft CONFIRMED or FAILED based on business result
```

Drafts expire after `ai.assistant.draft-expire-minutes` (default 15 min).

### AI provider communication

`AiChatClient` uses Spring WebFlux `WebClient` (separate bean with 10 MB in-memory buffer) to call an OpenAI-compatible Chat Completions API. Supports both synchronous (`chatCompletion`) and streaming (`streamChatCompletion` with SSE parsing) modes. Tool calls use OpenAI function-calling format with `tool_choice: "auto"`. The `reasoning_content` field is round-tripped for DeepSeek compatibility.

## When committing or pushing

- Do not write Claude as the author
