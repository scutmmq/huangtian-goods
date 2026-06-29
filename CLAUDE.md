# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build (skips tests)
mvn clean package -DskipTests

# Run (dev profile is active by default)
mvn spring-boot:run

# Run a single test
mvn test -Dtest=JwtTokenTest

# Build the release assembly (fat JAR + scripts)
mvn clean package
```

The app starts on port 8080. The `dev` profile is active by default (`application.yaml` → `spring.profiles.active: dev`).

## Required Infrastructure

- **MySQL** — default connects to `119.23.76.234:3306/online_mall` (overridable via `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`)
- **Redis** — default `localhost:6379` db 1 (overridable via `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_DATABASE`)
- **Redis Stream consumer group** — must exist before startup; create manually:
  ```
  XGROUP CREATE order:timeout:stream order:timeout:group $ MKSTREAM
  XGROUP CREATECONSUMER order:timeout:stream order:timeout:group c1
  ```
- **AI provider** — set `AI_API_KEY` env var (defaults to DeepSeek). Override model/URL via `AI_API_URL`, `AI_API_MODEL`.
- **Aliyun OSS** — credentials via `aliyun.oss.*` config or env vars (used only for image upload).

## Architecture Overview

Spring Boot 3.5 / Java 17 monolith with a classic layered structure plus an embedded AI assistant module.

### Request flow

```
HTTP → RefreshInterceptor (JWT parse + token refresh) → LoginCertificationInterceptor (UserHolder check) → Controller → Service → Mapper (MyBatis-Plus + XML)
```

`RefreshInterceptor` runs on all paths and stores the parsed user in `UserHolder` (ThreadLocal). `LoginCertificationInterceptor` rejects requests with no user, except `/user/login`, `/user/register`, `/image/upload`.

### Main domain packages (`com.scutmmq`)

| Package | Responsibility |
|---|---|
| `controller` | REST endpoints: User, Product, Category, Cart, Merchant, Pay, Image |
| `service/Impl` | Business logic; `PayServiceImpl` owns the order→payment→inventory flow |
| `mapper` | MyBatis-Plus mappers; complex queries use XML in `resources/com/scutmmq/mapper/` |
| `entity` | JPA/MP entities: `Orders`, `Product`, `Merchant`, `MerchantUser`, `CartItems`, `ProductReview`, `ReturnAudit`, … |
| `dto` / `vo` | Input DTOs and response VOs; never expose entities directly |
| `utils` | `RedisIdWorker` (snowflake-style IDs), `RedisUtils` (Lua script runner), `JwtUtils`, `UserHolder`, `OrderTimeOutTask` |
| `interceptor` | `RefreshInterceptor`, `LoginCertificationInterceptor` |
| `config` | `WebConfig` (interceptor registry), `WebSocketConfig`, `RedissonConfig` |
| `exception` | `BusinessException`, `AuthorizeException` |
| `enums` | `OrderStatus`, `PaymentStatus`, `ChangeType`, `LoginType`, `MerchantStatus`, … |

### Redis usage

- **Token store**: `login:token:<token>` (1 h TTL, refreshed when < 20 min left)
- **Product stock**: `product:stock:available:<productId>` — Lua scripts in `resources/lua/` atomically manage pre-reservation, cancellation, rollback, and sync
- **Order IDs**: `RedisIdWorker` — timestamp (32-bit shift) OR incremented counter per day key `icr:shopping:yyyy:MM:dd`
- **Timeout orders**: sorted set `order:timeout:trigger` scanned every 10 s by `OrderTimeOutTask`; expired orders pushed to Stream `order:timeout:stream` consumed by a single-thread consumer
- **Notifications**: Redis Streams `notify:user:<id>` and `notify:merchant:<id>` bridged to WebSocket via `NotifyWebSocketHandler`
- **Redisson**: distributed lock `lock:stock:<productId>` guards stock writes

### AI Assistant module (`com.scutmmq.ai`)

Self-contained package. Entry point: `AiAssistantController` → `AiAssistantService` → `AgentOrchestrator`.

`AgentOrchestrator` runs a tool-call loop (max iterations from `ai.assistant.max-history-messages` config):
1. Sends system prompt + conversation history + user message to the AI provider (OpenAI-compatible HTTP via `AiChatClient` using WebFlux `WebClient`).
2. If the model returns `tool_calls`, executes them via `MallSkillRegistry` and feeds results back.
3. Repeats until the model responds with plain text or the iteration cap is reached.

**Tool modes** (`ToolMode`):
- `READ_ONLY` — executes immediately and returns data to the model (e.g., `SearchProductsTool`, `GetMyOrdersTool`).
- `DRAFT_ONLY` — generates an `AiActionDraft` persisted to DB; the actual action runs only after the user confirms via a separate endpoint (e.g., `DraftCreateOrderTool`, `DraftAddCartItemTool`).

Add a new tool by implementing `MallAgentTool` and annotating with `@Component`; `MallSkillRegistry` auto-discovers it.

`MallUserContextExecutor.runAs(user, ...)` sets `UserHolder` before tool execution so tools can call the same services as normal requests.

### Key design decisions

- **Inventory pre-reservation**: placing an order calls `RedisUtils.ReserveStock` (Lua) to atomically pre-reserve. Payment calls `CancelReserveStock` then updates the DB. Timeout/cancel calls `rollBackReserveStock`.
- **Token refresh**: the `RefreshInterceptor` slides expiry when < 20 min remains; the new token is returned in the `Authorization` response header.
- **AI provider is pluggable**: all config (`url`, `key`, `model`, `auth-header`, `auth-scheme`) is in `application.yaml` under `ai.api.*` and overridable by env vars, so swapping from DeepSeek to any OpenAI-compatible provider requires only env var changes.
