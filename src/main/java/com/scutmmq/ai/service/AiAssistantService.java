package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.config.AiAssistantProperties;
import com.scutmmq.ai.dto.AiChatRequest;
import com.scutmmq.ai.dto.AiChatSubmitResponse;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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

    /**
     * 流式占位助手消息的初始状态，与 ai_message.status 字段约定一致。
     */
    private static final String MSG_STATUS_STREAMING = "STREAMING";
    private static final String MSG_STATUS_COMPLETED = "COMPLETED";
    private static final String MSG_STATUS_FAILED = "FAILED";

    private final AgentOrchestrator agentOrchestrator;
    private final AiSessionService aiSessionService;
    private final AiMessageService aiMessageService;
    private final AiActionDraftService aiActionDraftService;
    private final AiRunService aiRunService;
    private final AiAssistantProperties properties;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor aiTaskExecutor;

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
                              @Qualifier("aiTaskExecutor") ThreadPoolTaskExecutor aiTaskExecutor,
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
        this.aiTaskExecutor = aiTaskExecutor;
        this.orderService = orderService;
        this.cartService = cartService;
        this.merchantService = merchantService;
        this.userService = userService;
    }

    /**
     * 异步提交：立刻落库 user 消息 + 一个 STREAMING 占位 assistant 消息 + 一条 QUEUED 的 Run，
     * 然后把真正的生成任务扔到 aiTaskExecutor 线程池里。
     * 失败兜底：如果线程池拒绝（CallerRunsPolicy 下一般不会），在调用线程里同步跑一遍，
     * 避免用户消息已经写库却没有 worker 的尴尬情况。
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
                session.getId(), "assistant", "", MSG_STATUS_STREAMING, null);
        Long assistantMessageId = placeholder.getId();
        Objects.requireNonNull(assistantMessageId, "assistant message id is null after persist");
        log.info("[AI][SVC] persisted placeholder assistant message id={} status=STREAMING",
                assistantMessageId);

        // 3. 创建一个 QUEUED 状态的 Run
        AiRun run = aiRunService.submit(userId, session.getId(), userMessageId, assistantMessageId);
        String runId = run.getId();

        // 4. 提交到后台线程池跑真正的生成
        AiRunRunnable task = new AiRunRunnable(
                currentUser, session.getId(), runId, userMessage, userMessageId, assistantMessageId);
        try {
            aiTaskExecutor.execute(task);
        } catch (Exception e) {
            // CallerRunsPolicy 下基本不会到这里；保险起见同步兜底，
            // 至少保证用户能看到一个最终回复而不是卡死。
            log.error("[AI][SVC] executor rejected task runId={}, fallback to caller thread: {}",
                    runId, e.getMessage(), e);
            task.run();
        }

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
                try {
                    aiRunService.start(runId);

                    // 历史要在 worker 里读，因为从 submit 到真正执行可能跨了别的请求，
                    // 这里拿到的就是“开始执行那一刻”的真实历史——已经包含刚写入的 userMessage。
                    List<AgentOrchestrator.HistoryMessage> history = loadHistoryExcluding(userMessageId);

                    long t0 = System.currentTimeMillis();
                    AgentOrchestrator.AgentResult result = agentOrchestrator.run(user, history, userMessage);
                    log.info("[AI][RUN] worker orchestrator done in {}ms runId={} toolExecs={} draft={} replyLen={}",
                            System.currentTimeMillis() - t0,
                            runId,
                            result.toolExecutions().size(),
                            result.draft() == null ? "none" : result.draft().getActionType(),
                            result.reply() == null ? 0 : result.reply().length());

                    persistToolExecutions(result);
                    persistDraftIfPresent(result);
                    finalizeAssistantMessage(result);
                    finalizeSession(result);

                    aiRunService.complete(runId);
                    log.info("[AI][RUN] worker COMPLETED runId={}", runId);
                } catch (Exception e) {
                    log.error("[AI][RUN] worker FAILED runId={}: {}", runId, e.getMessage(), e);
                    markAssistantMessageFailed(e.getMessage());
                    aiRunService.fail(runId, e.getMessage());
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
            int toolPiggybackCount = 0;
            for (AiMessage m : recent) {
                if (excludeUserMsgId != null && excludeUserMsgId.equals(m.getId())) {
                    continue;
                }
                if ("user".equals(m.getRole()) || "assistant".equals(m.getRole())) {
                    history.add(new AgentOrchestrator.HistoryMessage(m.getRole(), m.getContent()));
                } else if ("tool".equals(m.getRole())) {
                    String wrapped = "[上一轮工具调用结果，可直接复用，不要重新搜索] " + safeTruncate(m.getContent(), 1200);
                    history.add(new AgentOrchestrator.HistoryMessage("user", wrapped));
                    toolPiggybackCount++;
                }
            }
            log.info("[AI][RUN] history loaded runId={} db={} sentToModel={} (含 {} 条工具结果回放)",
                    runId, recent.size(), history.size(), toolPiggybackCount);
            return history;
        }

        private void persistToolExecutions(AgentOrchestrator.AgentResult result) {
            for (AgentOrchestrator.ToolExecutionRecord exec : result.toolExecutions()) {
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
            AiActionDraft draft = aiActionDraftService.create(
                    user.getId(), sessionId, p.getActionType(),
                    p.getTitle(), p.getSummary(), p.getPayload());
            log.info("[AI][RUN] persisted draft id={} type={} expiresAt={}",
                    draft.getId(), draft.getActionType(), draft.getExpiresAt());
        }

        private void finalizeAssistantMessage(AgentOrchestrator.AgentResult result) {
            AiMessage assistant = new AiMessage();
            assistant.setId(assistantMessageId);
            assistant.setContent(result.reply() == null ? "" : result.reply());
            assistant.setStatus(MSG_STATUS_COMPLETED);
            assistant.setUpdatedAt(LocalDateTime.now());
            aiMessageService.update(assistant);
        }

        private void markAssistantMessageFailed(String reason) {
            try {
                AiMessage assistant = new AiMessage();
                assistant.setId(assistantMessageId);
                assistant.setStatus(MSG_STATUS_FAILED);
                assistant.setContent("[生成失败] " + safeTruncate(reason, 500));
                assistant.setUpdatedAt(LocalDateTime.now());
                aiMessageService.update(assistant);
            } catch (Exception e) {
                log.warn("[AI][RUN] failed to mark assistant message FAILED id={}: {}",
                        assistantMessageId, e.getMessage());
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