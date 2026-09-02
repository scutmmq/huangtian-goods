package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scutmmq.ai.config.AiAssistantProperties;
import com.scutmmq.ai.dto.AiActionDraftVO;
import com.scutmmq.ai.dto.AiChatRequest;
import com.scutmmq.ai.dto.AiChatSubmitResponse;
import com.scutmmq.ai.dto.AiMessageVO;
import com.scutmmq.ai.dto.AiSessionVO;
import com.scutmmq.ai.entity.AiActionDraft;
import com.scutmmq.ai.entity.AiMessage;
import com.scutmmq.ai.entity.AiRun;
import com.scutmmq.ai.entity.AiSession;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.impl.DraftAddCartItemTool;
import com.scutmmq.ai.tool.impl.DraftCreateOrderTool;
import com.scutmmq.ai.tool.impl.DraftRegisterMerchantTool;
import com.scutmmq.ai.tool.impl.DraftUpdateMerchantTool;
import com.scutmmq.ai.tool.impl.DraftUpdateUserProfileTool;
import com.scutmmq.ai.util.DsmlSanitizer;
import com.scutmmq.ai.util.MallUserContextExecutor;
import com.scutmmq.dto.CartsDTO;
import com.scutmmq.dto.OrderItemsDTO;
import com.scutmmq.dto.OrdersDTO;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.entity.Merchant;
import com.scutmmq.entity.Result;
import com.scutmmq.entity.User;
import com.scutmmq.service.CartService;
import com.scutmmq.service.MerchantService;
import com.scutmmq.service.OrderService;
import com.scutmmq.service.UserService;
import com.scutmmq.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AI 助手聊天编排服务。负责：
 * - 把当前用户、历史消息、新消息送入 {@link AgentOrchestrator}
 * - 落库会话和消息记录
 * - 把工具产出的草稿落入 ai_action_draft 表
 * - 处理草稿的确认、取消，调用真正的商城 Service
 *
 * Task 3 之后 chat() 改为异步提交：控制器立刻拿到 runId 返回，
 * 真正的生成在线程池里跑。Task 5-6 会接入 SSE 流式。
 */
@Slf4j
@Service
public class AiAssistantService {

    private final AgentOrchestrator agentOrchestrator;
    private final AiSessionService aiSessionService;
    private final AiMessageService aiMessageService;
    private final AiActionDraftService aiActionDraftService;
    private final AiRunService aiRunService;
    private final AiAssistantProperties properties;
    private final ObjectMapper objectMapper;
    private final AiSessionTaskScheduler aiSessionTaskScheduler;
    private final AiStreamEventService aiStreamEventService;

    private final OrderService orderService;
    private final CartService cartService;
    private final MerchantService merchantService;
    private final UserService userService;

    public AiAssistantService(AgentOrchestrator agentOrchestrator,
                              AiSessionService aiSessionService,
                              AiMessageService aiMessageService,
                              AiActionDraftService aiActionDraftService,
                              AiRunService aiRunService,
                              AiAssistantProperties properties,
                              ObjectMapper objectMapper,
                              AiSessionTaskScheduler aiSessionTaskScheduler,
                              AiStreamEventService aiStreamEventService,
                              OrderService orderService,
                              CartService cartService,
                              MerchantService merchantService,
                              UserService userService) {
        this.agentOrchestrator = agentOrchestrator;
        this.aiSessionService = aiSessionService;
        this.aiMessageService = aiMessageService;
        this.aiActionDraftService = aiActionDraftService;
        this.aiRunService = aiRunService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.aiSessionTaskScheduler = aiSessionTaskScheduler;
        this.aiStreamEventService = aiStreamEventService;
        this.orderService = orderService;
        this.cartService = cartService;
        this.merchantService = merchantService;
        this.userService = userService;
    }

    /**
     * 异步提交：立刻落库 user 消息 + 一个 STREAMING 占位 assistant 消息 + 一条 QUEUED 的 Run，
     * 然后把真正的生成任务扔到 {@link AiSessionTaskScheduler}。
     * <p>
     * Task 4 之后，调度器负责"同会话串行 / 跨会话并行"，底层仍是 aiTaskExecutor。
     * 拒绝策略由底层 CallerRunsPolicy 兜底——队列打满时回退到调用线程，
     * 等于把响应变慢但不会丢任务。
     */
    public AiChatSubmitResponse chat(AiChatRequest request) {
        UserDTO currentUser = requireCurrentUser();
        Long userId = currentUser.getId();
        log.info("[AI][SVC] chat() submit user.id={} user.username={} reqSessionId={}",
                userId, currentUser.getUsername(), request.getSessionId());

        String userMessage = request.getMessage() == null ? "" : request.getMessage().trim();
        if (userMessage.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        AiSession session;
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            session = aiSessionService.createSession(userId, "新会话");
            log.info("[AI][SVC] created new session id={} for user={}", session.getId(), userId);
        } else {
            session = aiSessionService.findByIdForUser(request.getSessionId(), userId);
            if (session == null) {
                log.warn("[AI][SVC] session not found or not owned: requested={} user={}",
                        request.getSessionId(), userId);
                throw new IllegalArgumentException("会话不存在或无权访问");
            }
            log.info("[AI][SVC] reuse session id={} title=\"{}\"", session.getId(), session.getTitle());
        }

        // 1. 落库用户消息
        AiMessage userMessageRow = aiMessageService.append(session.getId(), "user", userMessage, null);
        Long userMessageId = userMessageRow.getId();
        Objects.requireNonNull(userMessageId, "user message id is null after persist");
        log.info("[AI][SVC] persisted user message session={} id={} preview=\"{}\"",
                session.getId(), userMessageId, preview(userMessage, 120));

        // 2. 落库占位 assistant 消息（STREAMING）
        AiMessage placeholder = aiMessageService.append(
                session.getId(), "assistant", "", AiMessageService.MSG_STATUS_STREAMING, null);
        Long assistantMessageId = placeholder.getId();
        Objects.requireNonNull(assistantMessageId, "assistant message id is null after persist");
        log.info("[AI][SVC] persisted placeholder assistant message id={} status=STREAMING",
                assistantMessageId);

        // 3. 创建一个 QUEUED 状态的 Run
        AiRun run = aiRunService.submit(userId, session.getId(), userMessageId, assistantMessageId);
        String runId = run.getId();

        // 3.5 把 runId 写回占位 assistant 消息的 runId 列。
        //    之前只落库了 id/status/content，runId 一直是 null，
        //    导致 listMessagesAsVO 里的 runId 分支进不去 —— STREAMING 派生 / userMessageId 都拿不到。
        AiMessage placeholderWithRun = new AiMessage();
        placeholderWithRun.setId(assistantMessageId);
        placeholderWithRun.setRunId(runId);
        placeholderWithRun.setUpdatedAt(LocalDateTime.now());
        aiMessageService.update(placeholderWithRun);
        log.info("[AI][SVC] backfilled runId={} on assistant message id={}", runId, assistantMessageId);

        // 4. 提交到后台线程池跑真正的生成
        //    AiSessionTaskScheduler 负责"同会话串行 / 跨会话并行"，
        //    底层 aiTaskExecutor 的 CallerRunsPolicy 仍负责队列打满时的回退反压。
        AiRunRunnable task = new AiRunRunnable(
                currentUser, session.getId(), runId, userMessage, userMessageId, assistantMessageId);
        aiSessionTaskScheduler.submitForSession(session.getId(), task);

        log.info("[AI][SVC] chat() submitted runId={} sessionId={} status=QUEUED",
                runId, session.getId());

        return new AiChatSubmitResponse(
                session.getId(),
                runId,
                AiRunService.STATUS_QUEUED,
                userMessageId,
                assistantMessageId,
                run.getCreatedAt());
    }

    /**
     * 后台 Run 执行体。每个 Run 一个实例，承载当前用户、Run ID、最新消息 ID 等上下文。
     * <p>
     * 注意：这里不需要捕获/恢复 UserHolder——{@link MallUserContextExecutor#runAs} 会自己做。
     */
    private final class AiRunRunnable implements Runnable {

        private final UserDTO user;
        private final String sessionId;
        private final String runId;
        private final String userMessage;
        private final Long userMessageId;
        private final Long assistantMessageId;

        AiRunRunnable(UserDTO user,
                      String sessionId,
                      String runId,
                      String userMessage,
                      Long userMessageId,
                      Long assistantMessageId) {
            this.user = user;
            this.sessionId = sessionId;
            this.runId = runId;
            this.userMessage = userMessage;
            this.userMessageId = userMessageId;
            this.assistantMessageId = assistantMessageId;
        }

        @Override
        public void run() {
            log.info("[AI][RUN] worker begin runId={} sessionId={} userMsgId={} assistantMsgId={}",
                    runId, sessionId, userMessageId, assistantMessageId);

            // 把 UserHolder 显式设上：商城 Service 链里很多地方直接 getUser()，
            // 而 RefreshInterceptor 的设置已经在 HTTP 线程结束时清掉了。
            MallUserContextExecutor.runAs(user, () -> {
                PersistingOrchestratorListener listener = new PersistingOrchestratorListener(
                        runId, sessionId, user.getId(), userMessageId, assistantMessageId,
                        aiStreamEventService, aiMessageService, aiRunService, objectMapper);
                try {
                    aiRunService.start(runId);

                    // 历史要在 worker 里读，因为从 submit 到真正执行可能跨了别的请求，
                    // 这里拿到的就是"开始执行那一刻"的真实历史——已经包含刚写入的 userMessage。
                    List<AgentOrchestrator.HistoryMessage> history = loadHistoryExcluding(userMessageId);

                    long t0 = System.currentTimeMillis();
                    AgentOrchestrator.AgentResult result;
                    try {
                        // B2:带 runId/sessionId 的入口,让 CapabilityRegistry 发布的事件能精确关联到 ai_run 表
                        // B4 Phase 1.6:currentMerchantId 暂传 null — session 上下文未接入,KnowledgeRecallInjector 兜底走 SearchFilter.all()
                        result = agentOrchestrator.runStreamingWithRun(user, history, userMessage,
                                listener, runId, sessionId, null);
                    } catch (Exception e) {
                        // runStreaming 自身异常路径里已经回调过 listener.onRunFailed()；
                        // 这里再保险一次——避免 orchestrator 抛 exception 在 listener 正常路径之外。
                        if (!listener.isFailed()) {
                            listener.onRunFailed(e);
                        }
                        throw e;
                    }
                    log.info("[AI][RUN] worker orchestrator done in {}ms runId={} toolExecs={} draft={} replyLen={}",
                            System.currentTimeMillis() - t0,
                            runId,
                            result.toolExecutions().size(),
                            result.draft() == null ? "none" : result.draft().getActionType(),
                            result.reply() == null ? 0 : result.reply().length());

                    // 工具执行记录 + 草稿行也要落库（同步路径原本就在这做的）。
                    // 助手消息 content/status 已经被 listener 写到对应的状态，不在这里二次覆盖。
                    persistToolExecutions(result);
                    persistDraftIfPresent(result);
                    finalizeSession(result);

                    // listener 已经把 content/status 落到 db 里的对应状态；这里只标 Run 自身。
                    final String terminalStatus;
                    if (listener.isFailed()) {
                        aiRunService.fail(runId, listener.getFailureReason());
                        terminalStatus = AiRunService.STATUS_FAILED;
                    } else {
                        aiRunService.complete(runId);
                        terminalStatus = AiRunService.STATUS_COMPLETED;
                    }
                    log.info("[AI][RUN] worker DONE runId={} status={}",
                            runId, terminalStatus);

                    // ── 这里才 emit run.completed / run.failed SSE 事件 ──
                    // 之前 listener.onRunCompleted 是在 runStreaming 内部 emit，
                    // 那时 ai_run.status 还是 RUNNING —— race condition：
                    // 前端 loadSessions() 会看到 RUNNING → "生成中" badge 卡住。
                    // 在 aiRunService.complete/fail 之后再 emit，
                    // 前端 loadSessions() 拿到的就是 IDLE / FAILED，正确。
                    com.fasterxml.jackson.databind.node.ObjectNode terminalPayload = objectMapper.createObjectNode();
                    terminalPayload.put("replyLength", result.reply() == null ? 0 : result.reply().length());
                    terminalPayload.put("hasDraft", result.draft() != null);
                    if (AiRunService.STATUS_FAILED.equals(terminalStatus)) {
                        terminalPayload.put("error", listener.getFailureReason());
                    }
                    aiStreamEventService.append(
                            runId, sessionId, assistantMessageId, user.getId(),
                            AiRunService.STATUS_FAILED.equals(terminalStatus)
                                    ? AiStreamEventService.TYPE_RUN_FAILED
                                    : AiStreamEventService.TYPE_RUN_COMPLETED,
                            terminalPayload);
                } catch (Exception e) {
                    // listener 没机会正常回调 onRunFailed（比如生成之前的早期异常）时兜底。
                    if (!listener.isFailed()) {
                        listener.onRunFailed(e);
                    }
                    String reason = listener.getFailureReason() != null
                            ? listener.getFailureReason()
                            : (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    log.error("[AI][RUN] worker FAILED runId={}: {}", runId, reason, e);
                    aiRunService.fail(runId, reason);
                }
            });
        }

        /**
         * 取最近 N 条历史，但排除刚写入的 userMessage——因为它会被显式传给 orchestrator.run()，
         * 不需要在历史里再塞一遍。
         */
        private List<AgentOrchestrator.HistoryMessage> loadHistoryExcluding(Long excludeUserMsgId) {
            List<AiMessage> recent = aiMessageService.listRecentBySession(
                    sessionId, Math.max(1, properties.getMaxHistoryMessages()));
            List<AgentOrchestrator.HistoryMessage> history = new ArrayList<>();
            for (AiMessage m : recent) {
                if (excludeUserMsgId != null && excludeUserMsgId.equals(m.getId())) {
                    continue;
                }
                String role = m.getRole();
                String content = DsmlSanitizer.strip(m.getContent());
                if ("user".equals(role)) {
                    if (!content.isBlank()) {
                        history.add(new AgentOrchestrator.HistoryMessage("user", content));
                    }
                } else if ("assistant".equals(role)) {
                    if (!content.isBlank()) {
                        history.add(new AgentOrchestrator.HistoryMessage("assistant", content));
                    }
                }
            }
            log.info("[AI][RUN] history loaded runId={} db={} sentToModel={}",
                    runId, recent.size(), history.size());
            return history;
        }

        private void persistToolExecutions(AgentOrchestrator.AgentResult result) {
            for (AgentOrchestrator.ToolExecutionRecord exec : result.toolExecutions()) {
                // 跳过空名字的工具执行 —— 可能是 AI 流式 tool_calls 拼接的边缘情况。
                // 不写库就不显示 "[] 工具不存在:" 这种噪音。
                if (exec.name() == null || exec.name().isBlank()) continue;
                aiMessageService.append(sessionId, "tool",
                        "[" + exec.name() + "] " + exec.content(),
                        toJsonSafe(exec.arguments()));
            }
        }

        private void persistDraftIfPresent(AgentOrchestrator.AgentResult result) {
            if (result.draft() == null) {
                return;
            }
            AgentToolResult.DraftPayload p = result.draft();
            // B0:写入 assistantMessageId,让 listMessagesAsVO 回查草稿时拿得到,
            //    修复历史对话恢复时确认卡片丢失的问题。
            AiActionDraft draft = aiActionDraftService.create(
                    user.getId(), sessionId, p.getActionType(),
                    p.getTitle(), p.getSummary(), p.getPayload(),
                    assistantMessageId);
            log.info("[AI][RUN] persisted draft id={} type={} assistantMessageId={} expiresAt={}",
                    draft.getId(), draft.getActionType(), assistantMessageId, draft.getExpiresAt());

            // C12 修复:持久化后 emit 第二个 draft.created SSE 事件,带 id 和 expiresAt。
            // 第一个 SSE 事件在 PersistingOrchestratorListener.onDraftCreated 里 emit,
            // 那时 draft 还没持久化,id/expiresAt 不可知。前端拿到第一事件后 msg.draft.id 是 undefined,
            // 点击确认按钮 → store.confirmDraft(undefined) 直接返回 null → 不发请求 → "执行失败"。
            // 这个补发事件让前端拿到完整 draft(id + expiresAt + actionType + ...),
            // 前端 attachMetaToAssistant 会覆盖式赋值,补齐 msg.draft。
            try {
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("id", draft.getId());
                payload.put("actionType", draft.getActionType() == null ? "" : draft.getActionType());
                payload.put("title", draft.getTitle() == null ? "" : draft.getTitle());
                payload.put("summary", draft.getSummary() == null ? "" : draft.getSummary());
                if (draft.getExpiresAt() != null) {
                    payload.put("expiresAt", draft.getExpiresAt().toString());
                }
                if (p.getPayload() != null) {
                    payload.set("payload", p.getPayload());
                }
                aiStreamEventService.append(
                        runId, sessionId, assistantMessageId, user.getId(),
                        AiStreamEventService.TYPE_DRAFT_CREATED, payload);
            } catch (Exception e) {
                log.warn("[AI][RUN] C12 emit follow-up draft.created failed draftId={} reason={}",
                        draft.getId(), e.getMessage());
            }
        }

        private void finalizeSession(AgentOrchestrator.AgentResult result) {
            aiSessionService.renameIfDefault(sessionId, userMessage);
            // 1 assistant + N tool executions
            aiSessionService.touch(sessionId, 1 + result.toolExecutions().size());
        }
    }

    public List<AiSession> listSessions() {
        return aiSessionService.listByUser(requireCurrentUser().getId());
    }

    public List<AiMessage> listMessages(String sessionId) {
        UserDTO currentUser = requireCurrentUser();
        AiSession session = aiSessionService.findByIdForUser(sessionId, currentUser.getId());
        if (session == null) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        return aiMessageService.listBySession(sessionId);
    }

    /**
     * 列表视图：会话列表，每条带运行态 + activeRunId。
     *
     * 运行态派生规则（见 AiRunService.RUN_STATE_*）：
     * - 没 Run               -> IDLE
     * - 最近 Run = QUEUED    -> QUEUED
     * - 最近 Run = RUNNING   -> RUNNING
     * - 最近 Run = FAILED    -> FAILED
     * - 最近 Run = COMPLETED -> IDLE
     * - 最近 Run = CANCELLED -> IDLE（CANCELLED 也算"没在跑了"）
     *
     * activeRunId：runState != IDLE 时填最近 Run 的 id，否则 null。
     * 性能说明：典型 N+1（1 查 sessions + N 查最新 Run）。单用户会话数一般不多，先这样。
     */
    public List<AiSessionVO> listSessionsAsVO() {
        List<AiSession> sessions = aiSessionService.listByUser(requireCurrentUser().getId());
        List<AiSessionVO> out = new ArrayList<>(sessions.size());
        for (AiSession s : sessions) {
            AiRun latest = aiRunService.findLatestBySessionId(s.getId());
            String state = AiRunService.RUN_STATE_IDLE;
            String activeRunId = null;
            if (latest != null) {
                String runStatus = latest.getStatus();
                if (AiRunService.STATUS_QUEUED.equals(runStatus)) {
                    state = AiRunService.RUN_STATE_QUEUED;
                    activeRunId = latest.getId();
                } else if (AiRunService.STATUS_RUNNING.equals(runStatus)) {
                    state = AiRunService.RUN_STATE_RUNNING;
                    activeRunId = latest.getId();
                } else if (AiRunService.STATUS_FAILED.equals(runStatus)) {
                    state = AiRunService.RUN_STATE_FAILED;
                    activeRunId = latest.getId();
                } else {
                    // COMPLETED / CANCELLED -> IDLE
                    state = AiRunService.RUN_STATE_IDLE;
                }
            }
            out.add(new AiSessionVO(
                    s.getId(),
                    s.getTitle(),
                    s.getMessageCount(),
                    state,
                    activeRunId,
                    s.getCreatedAt(),
                    s.getUpdatedAt()));
        }
        log.info("[AI][SVC] listSessionsAsVO count={} withActive={}",
                out.size(),
                out.stream().filter(v -> v.getActiveRunId() != null).count());
        return out;
    }

    /**
     * 消息列表视图：每条 assistant 消息带 runId / userMessageId / 派生 status / draft。
     *
     * 派生 status 规则：
     * - role != assistant        -> 直接用 message.status（user / tool 一般为 null）
     * - assistant + 有 runId     -> 查 Run；若 Run 仍处于 QUEUED/RUNNING，则把 message 的 status 改成
     *                               "STREAMING"（即使 listener 已经把它写成 COMPLETED，前端还是该跟着 Run 走）
     * - assistant + Run 终态     -> 用 message 自己的 status
     * - assistant + runId 为空   -> 用 message 自己的 status
     *
     * 这样后端 "Run 已 COMPLETED + message 已 COMPLETED" 时不会出现"显示 STREAMING"的鬼影；
     * "Run 还在跑"时，前端拿到的 status 永远跟 Run 同步。
     */
    public List<AiMessageVO> listMessagesAsVO(String sessionId) {
        UserDTO currentUser = requireCurrentUser();
        AiSession session = aiSessionService.findByIdForUser(sessionId, currentUser.getId());
        if (session == null) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        List<AiMessage> messages = aiMessageService.listBySession(sessionId);
        List<AiMessageVO> out = new ArrayList<>(messages.size());
        for (AiMessage m : messages) {
            out.add(toMessageVO(m));
        }
        return out;
    }

    private AiMessageVO toMessageVO(AiMessage m) {
        String role = m.getRole();
        String status = m.getStatus();
        String runId = m.getRunId();
        Long userMessageId = null;
        AiActionDraftVO draftVO = null;

        if ("assistant".equals(role)) {
            // 草稿查找：只要是 assistant 消息就无条件查。
            // 之前错误地把它和 runId != null 绑在一起，导致 runId 缺失时草稿永远查不到。
            // 草稿的关联键是 assistant_message_id（= m.getId()），跟 runId 无关。
            AiActionDraft draft = aiActionDraftService.findByAssistantMessageId(m.getId());
            if (draft != null) {
                draftVO = toDraftVO(draft);
            }

            // Run-based status / userMessageId 派生：仅在 runId 已写入时才有意义。
            // 旧代码把这一段和上面的草稿查找绑在一起，导致 runId 没落库时整个 if 块全跳过。
            if (runId != null) {
                AiRun run = aiRunService.findById(runId);
                if (run != null) {
                    String runStatus = run.getStatus();
                    if (AiRunService.STATUS_QUEUED.equals(runStatus)
                            || AiRunService.STATUS_RUNNING.equals(runStatus)) {
                        // Run 还在跑，前端看到的就是 STREAMING
                        status = AiMessageService.MSG_STATUS_STREAMING;
                    }
                    // 其它状态（COMPLETED / FAILED / CANCELLED）以 message.status 为准
                    // userMessageId 在 ai_message 实体上没存，但 ai_run 上有。
                    if (run.getUserMessageId() != null) {
                        userMessageId = run.getUserMessageId();
                    }
                }
            }
        }

        return new AiMessageVO(
                m.getId(),
                m.getSessionId(),
                role,
                m.getContent(),
                status,
                runId,
                userMessageId,
                draftVO,
                m.getCreatedAt(),
                m.getUpdatedAt());
    }

    private AiActionDraftVO toDraftVO(AiActionDraft d) {
        JsonNode payload = null;
        if (d.getPayloadJson() != null) {
            try {
                payload = objectMapper.readTree(d.getPayloadJson());
            } catch (Exception e) {
                log.warn("[AI][SVC] draft payload unparseable, skip. id={} err={}", d.getId(), e.getMessage());
            }
        }
        return new AiActionDraftVO(
                d.getId(),
                d.getActionType(),
                d.getTitle(),
                d.getSummary(),
                payload,
                d.getStatus(),
                d.getExpiresAt(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    public Result confirmDraft(String draftId) {
        UserDTO currentUser = requireCurrentUser();
        Long userId = currentUser.getId();
        log.info("[AI][SVC] confirmDraft start draftId={} userId={}", draftId, userId);

        AiActionDraft draft = aiActionDraftService.findByIdForUser(draftId, userId);
        if (draft == null) {
            log.warn("[AI][SVC] confirmDraft: not found or not owned. draftId={} userId={}", draftId, userId);
            return Result.error("草稿不存在或无权访问");
        }
        log.info("[AI][SVC] draft loaded id={} type={} status={} expiresAt={} payloadLen={}",
                draft.getId(), draft.getActionType(), draft.getStatus(),
                draft.getExpiresAt(), draft.getPayloadJson() == null ? 0 : draft.getPayloadJson().length());

        if (!AiActionDraftService.STATUS_PENDING.equals(draft.getStatus())) {
            log.warn("[AI][SVC] draft status not PENDING: {}", draft.getStatus());
            return Result.error("草稿状态不允许执行: " + draft.getStatus());
        }
        if (aiActionDraftService.isExpired(draft)) {
            log.warn("[AI][SVC] draft expired: id={} expiresAt={}", draft.getId(), draft.getExpiresAt());
            aiActionDraftService.markExpired(draft);
            return Result.error("草稿已过期，请重新生成");
        }

        JsonNode payload = aiActionDraftService.readPayload(draft);
        if (payload == null) {
            log.error("[AI][SVC] draft payload unparseable: id={}", draft.getId());
            aiActionDraftService.markFailed(draft, "无法解析草稿数据");
            return Result.error("无法解析草稿数据");
        }
        log.info("[AI][SVC] draft payload parsed: {}", payload);

        Result actionResult;
        long t0 = System.currentTimeMillis();
        try {
            log.info("[AI][SVC] dispatch action type={} id={}", draft.getActionType(), draft.getId());
            actionResult = dispatch(draft.getActionType(), payload);
            log.info("[AI][SVC] dispatch returned in {}ms code={} msg={} dataPresent={}",
                    System.currentTimeMillis() - t0,
                    actionResult == null ? null : actionResult.getCode(),
                    actionResult == null ? null : actionResult.getMsg(),
                    actionResult != null && actionResult.getData() != null);
        } catch (Exception e) {
            log.error("[AI][SVC] dispatch failed draftId={} type={}: {}",
                    draft.getId(), draft.getActionType(), e.getMessage(), e);
            aiActionDraftService.markFailed(draft, e.getMessage());
            return Result.error("执行失败: " + e.getMessage());
        }

        if (actionResult != null && actionResult.getCode() != null && actionResult.getCode() == 1) {
            aiActionDraftService.markConfirmed(draft, actionResult.getData());
            log.info("[AI][SVC] draft CONFIRMED id={} type={}", draft.getId(), draft.getActionType());
        } else {
            String msg = actionResult == null ? "执行返回为空" : actionResult.getMsg();
            aiActionDraftService.markFailed(draft, msg);
            log.warn("[AI][SVC] draft FAILED id={} type={} reason={}",
                    draft.getId(), draft.getActionType(), msg);
        }
        return actionResult;
    }

    public Result cancelDraft(String draftId) {
        UserDTO currentUser = requireCurrentUser();
        AiActionDraft draft = aiActionDraftService.findByIdForUser(draftId, currentUser.getId());
        if (draft == null) {
            return Result.error("草稿不存在或无权访问");
        }
        if (!AiActionDraftService.STATUS_PENDING.equals(draft.getStatus())) {
            return Result.error("草稿状态不允许取消: " + draft.getStatus());
        }
        aiActionDraftService.markCancelled(draft);
        return Result.success();
    }

    private Result dispatch(String actionType, JsonNode payload) throws Exception {
        return switch (actionType) {
            case DraftCreateOrderTool.ACTION_TYPE -> doCreateOrder(payload);
            case DraftAddCartItemTool.ACTION_TYPE -> doAddCartItem(payload);
            case DraftRegisterMerchantTool.ACTION_TYPE -> doRegisterMerchant(payload);
            case DraftUpdateUserProfileTool.ACTION_TYPE -> doUpdateUserProfile(payload);
            case DraftUpdateMerchantTool.ACTION_TYPE -> doUpdateMerchant(payload);
            default -> Result.error("未知的草稿类型: " + actionType);
        };
    }

    private Result doCreateOrder(JsonNode payload) {
        OrdersDTO dto = new OrdersDTO();
        dto.setShippingAddressId(payload.path("shippingAddressId").asLong());
        if (payload.hasNonNull("remark")) {
            dto.setRemark(payload.get("remark").asText());
        }
        OrderItemsDTO item = new OrderItemsDTO();
        item.setProductId(payload.path("productId").asLong());
        item.setQuantity(payload.path("quantity").asInt());
        dto.setList(List.of(item));
        return orderService.addOrder(dto);
    }

    private Result doAddCartItem(JsonNode payload) {
        CartsDTO dto = new CartsDTO();
        dto.setProductId(payload.path("productId").asLong());
        dto.setQuantity(payload.path("quantity").asInt());
        return cartService.addItem(dto);
    }

    private Result doRegisterMerchant(JsonNode payload) throws Exception {
        Merchant merchant = objectMapper.treeToValue(payload, Merchant.class);
        merchant.setId(null);
        merchant.setStatus(null);
        merchant.setRating(null);
        merchant.setRatingCount(null);
        merchant.setTotalSales(null);
        merchant.setIsActive(null);
        return merchantService.addMerchant(merchant);
    }

    private Result doUpdateUserProfile(JsonNode payload) throws Exception {
        // 只允许下面这些字段
        com.fasterxml.jackson.databind.node.ObjectNode filtered = objectMapper.createObjectNode();
        for (String key : new String[]{"nickName", "email", "phone", "birthday", "gender", "address", "image"}) {
            if (payload.hasNonNull(key)) {
                filtered.set(key, payload.get(key));
            }
        }
        User user = objectMapper.treeToValue(filtered, User.class);
        // userId 由 Service 内部从 UserHolder 取，避免越权
        user.setId(null);
        user.setPassword(null);
        user.setIsActive(null);
        return userService.updateUser(user);
    }

    private Result doUpdateMerchant(JsonNode payload) throws Exception {
        Merchant merchant = objectMapper.treeToValue(payload, Merchant.class);
        merchant.setStatus(null);
        merchant.setRating(null);
        merchant.setRatingCount(null);
        merchant.setTotalSales(null);
        merchant.setIsActive(null);
        return merchantService.updateMerchant(merchant);
    }

    private UserDTO requireCurrentUser() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("未登录");
        }
        return user;
    }

    private String toJsonSafe(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String t = s.replace("\n", " ").replace("\r", " ");
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private static String safeTruncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...(截断)";
    }
}