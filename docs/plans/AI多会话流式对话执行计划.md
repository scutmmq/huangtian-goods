# AI 多会话流式对话执行计划

## 总结

当前后端 `POST /ai/chat` 是同步阻塞式：请求进来后完整跑完 ReAct 工具循环，最后一次性返回 `reply`。这无法满足“切换会话、离开页面、退出登录后仍继续生成”。

推荐方案：把 AI 生成改成“后端异步任务 + 持久化流事件 + 前端可重连订阅”。

用户发送消息后，接口立即返回 `sessionId/runId/messageId`；AI 生成在后端线程池继续执行。前端通过可重连的流接口接收增量 token；断开、切换会话、退出登录都只影响前端连接，不影响后端生成任务。重新进入会话时，先拉取数据库里的当前消息快照，再继续订阅后续事件。

## 方案评估

- 前端假流式：不推荐。只能把一次性回复逐字显示，不能支持离开页面后继续生成。
- 请求内 SSE 流式：不推荐。页面关闭或注销会断开请求，生成也容易被中断。
- 后端异步任务 + SSE/fetch 流事件：推荐。生成生命周期独立于页面，支持多会话并发和断线续接。
- WebSocket：可行但不是首选。当前 `/ws/notify` 是通知通道，不具备 AI 会话游标和事件重放能力；AI 输出是单向流，SSE/fetch stream 更简单。

## 后端关键改造

- 保留 `/ai/chat` 路径，但语义改为“提交消息并创建运行任务”，立即返回：
  `sessionId`、`runId`、`userMessage`、`assistantMessage`、`status=QUEUED/RUNNING`。
- 新增流接口：
  `GET /ai/sessions/{sessionId}/events?afterId=xxx`，返回 `text/event-stream`。
  前端用 `fetch` 读取流，这样可以继续携带 `Authorization` 请求头。
- 新增或调整数据结构：
  `ai_run`：记录每次 AI 回合，字段包含 `id`、`user_id`、`session_id`、`user_message_id`、`assistant_message_id`、`status`、`error_message`、时间字段。
  `ai_stream_event`：记录可重放事件，字段包含自增 `id`、`run_id`、`session_id`、`message_id`、`user_id`、`type`、`payload_json`、`created_at`。
  `ai_message` 增加 `run_id`、`status`、`updated_at`，`content` 建议改为 `MEDIUMTEXT`。
  `ai_action_draft` 增加 `assistant_message_id`，保证刷新后草稿卡片能恢复。
- 新增 `AiRunService` / `AiStreamEventService` / `AiStreamHub`：
  `AiRunService` 负责提交、排队、启动、完成、失败。
  `AiStreamEventService` 负责先落库事件再广播。
  `AiStreamHub` 管理当前在线的 SSE 连接。
- 新增 `AiTaskExecutor` 线程池配置：
  多个不同会话可并行运行；同一会话按用户确认采用排队策略，一次只执行一个 AI 回合。
- 改造 `AiChatClient`：
  增加 `streamChatCompletion`，请求体带 `stream: true`。
  支持解析 OpenAI/DeepSeek 兼容 SSE chunk，包括 `delta.content`、`delta.reasoning_content`、`delta.tool_calls` 和 `[DONE]`。
  保留现有同步方法作为测试/降级路径。
- 改造 `AgentOrchestrator`：
  增加 `runStreaming(...)`，在工具循环中持续发事件。
  内容增量事件为 `assistant.delta`，payload 包含 `delta` 和 `offset`，前端用 offset 避免断线重放造成重复文本。
  工具调用事件为 `tool.started/tool.finished`，草稿事件为 `draft.created`，结束事件为 `run.completed`，异常为 `run.failed`。
- 改造消息查询：
  `GET /ai/sessions/{sessionId}/messages` 返回 VO，而不是直接返回实体。
  VO 包含 `id`、`role`、`content`、`status`、`runId`、`draft`、`createdAt`、`updatedAt`。
- 改造会话列表：
  `GET /ai/sessions` 增加每个会话的运行态：`IDLE/QUEUED/RUNNING/FAILED`，用于侧边栏显示“生成中”。

## 前端关键改造

- 在 `E:\Study\IT\Vue\online-mall\src\api\ai.js` 增加：
  `submitAiMessage`、`streamAiSessionEvents`、新的消息/会话查询封装。
- 新建轻量全局状态模块，例如 `src/stores/aiChatStore.js`：
  按 `sessionId` 存储消息、运行状态、最后事件游标、当前 AbortController。
  不引入 Pinia，沿用当前 Vue 组合式风格。
- 改造 `AiAssistantDrawer.vue`：
  移除全局 `sending`，改为每个会话独立的 `runStatus`。
  发送消息后立即展示用户消息和空的 assistant 气泡。
  收到 `assistant.delta` 时增量追加到对应消息。
  切换会话时，停止当前可见会话的流连接，但不取消后端任务。
  打开某会话时，先拉取消息快照，再从本地 cursor 继续订阅事件。
- 注销处理：
  不清除 AI 会话事件 cursor，只清除 token/userData。
  注销会断开前端流连接，但后端 `ai_run` 继续生成。
  重新登录同一账号后，拉会话列表和消息快照即可恢复。
- 草稿卡片恢复：
  不再只依赖 `/ai/chat` 的一次性响应。
  草稿从消息 VO 或 `draft.created` 事件进入消息对象，刷新/切换后仍可确认或取消。

## 执行步骤

1. 添加数据库迁移 SQL 和对应实体字段。
2. 实现 `AiRunService`、`AiStreamEventService`、`AiStreamHub`。
3. 把 `/ai/chat` 改为异步提交接口，创建 user 消息、run、assistant 占位消息。
4. 实现同会话排队，不同会话并发执行。
5. 实现 provider 流式解析，并让 `AgentOrchestrator` 支持 streaming listener。
6. 在生成过程中持续更新 `ai_message.content/status`，并写入 `ai_stream_event`。
7. 新增 SSE/fetch 流接口，支持按 `afterId` 重放历史事件后继续等待实时事件。
8. 改造消息/会话返回 VO，补齐运行态和草稿信息。
9. 前端抽出 `aiChatStore`，让会话状态脱离抽屉组件生命周期。
10. 改造抽屉发送、切换会话、流式追加、重连、草稿展示逻辑。
11. 补充构建和测试。

## 测试计划

- 后端单测：
  流式 chunk 解析：普通文本、tool_calls 分片、reasoning_content、`[DONE]`、异常 chunk。
  同会话排队：第二条消息必须等第一条完成后再运行。
  多会话并发：两个 session 可同时进入 RUNNING。
  断线重放：`afterId` 后的事件可恢复，offset 不产生重复文本。
  草稿恢复：生成 draft 后，重新拉消息能拿到草稿卡片数据。
- 前端验证：
  一个会话生成时切到另一个会话，两个会话都能继续运行。
  切回原会话时，先显示当前已生成内容，再继续追加。
  关闭抽屉、跳转页面、刷新页面后能恢复当前输出。
  退出登录后等待一段时间，重新登录同一账号，AI 回复已继续生成或完成。
- 构建验证：
  后端运行 `mvn -Dmaven.repo.local=.m2repo -DskipTests compile`。
  前端运行 `npm run build`。

## 明确默认

- 同一个会话内 AI 回合排队执行；多个不同会话可以并行。
- 页面断开只断开流连接，不取消后端 run。
- 退出登录不会取消已提交的 run；重新登录同一账号后按数据库状态恢复。
- 暂不复用 `/ws/notify`，AI 单独使用可重放事件流。
