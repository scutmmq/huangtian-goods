package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.config.AiAssistantProperties;
import com.scutmmq.ai.dto.AiChatRequest;
import com.scutmmq.ai.dto.AiChatResponse;
import com.scutmmq.ai.entity.AiActionDraft;
import com.scutmmq.ai.entity.AiMessage;
import com.scutmmq.ai.entity.AiSession;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.impl.DraftAddCartItemTool;
import com.scutmmq.ai.tool.impl.DraftCreateOrderTool;
import com.scutmmq.ai.tool.impl.DraftRegisterMerchantTool;
import com.scutmmq.ai.tool.impl.DraftUpdateMerchantTool;
import com.scutmmq.ai.tool.impl.DraftUpdateUserProfileTool;
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

import java.util.ArrayList;
import java.util.List;

/**
 * AI 助手聊天编排服务。负责：
 * - 把当前用户、历史消息、新消息送入 {@link AgentOrchestrator}
 * - 落库会话和消息记录
 * - 把工具产出的草稿落入 ai_action_draft 表
 * - 处理草稿的确认、取消，调用真正的商城 Service
 */
@Slf4j
@Service
public class AiAssistantService {

    private final AgentOrchestrator agentOrchestrator;
    private final AiSessionService aiSessionService;
    private final AiMessageService aiMessageService;
    private final AiActionDraftService aiActionDraftService;
    private final AiAssistantProperties properties;
    private final ObjectMapper objectMapper;

    private final OrderService orderService;
    private final CartService cartService;
    private final MerchantService merchantService;
    private final UserService userService;

    public AiAssistantService(AgentOrchestrator agentOrchestrator,
                              AiSessionService aiSessionService,
                              AiMessageService aiMessageService,
                              AiActionDraftService aiActionDraftService,
                              AiAssistantProperties properties,
                              ObjectMapper objectMapper,
                              OrderService orderService,
                              CartService cartService,
                              MerchantService merchantService,
                              UserService userService) {
        this.agentOrchestrator = agentOrchestrator;
        this.aiSessionService = aiSessionService;
        this.aiMessageService = aiMessageService;
        this.aiActionDraftService = aiActionDraftService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.orderService = orderService;
        this.cartService = cartService;
        this.merchantService = merchantService;
        this.userService = userService;
    }

    public AiChatResponse chat(AiChatRequest request) {
        UserDTO currentUser = requireCurrentUser();
        Long userId = currentUser.getId();
        log.info("[AI][SVC] chat() user.id={} user.username={} reqSessionId={}",
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

        // 加载最近历史消息。
        // 工具消息（role=tool）不能直接当 OpenAI tool 消息发回去——OpenAI 要求 tool 消息必须紧跟在
        // 包含 tool_calls 的 assistant 消息后。所以这里把 tool 结果重新包装成一条 user 角色的
        // 系统提示，让模型在下一轮看见“上一次工具的真实结果”，避免不停重复搜索。
        List<AiMessage> recent = aiMessageService.listRecentBySession(
                session.getId(), Math.max(1, properties.getMaxHistoryMessages()));
        List<AgentOrchestrator.HistoryMessage> history = new ArrayList<>();
        int toolPiggybackCount = 0;
        for (AiMessage m : recent) {
            if ("user".equals(m.getRole()) || "assistant".equals(m.getRole())) {
                history.add(new AgentOrchestrator.HistoryMessage(m.getRole(), m.getContent()));
            } else if ("tool".equals(m.getRole())) {
                // m.content 形如 "[search_products] 找到 5 件商品 ..."，可直接喂给模型
                String wrapped = "[上一轮工具调用结果，可直接复用，不要重新搜索] " + safeTruncate(m.getContent(), 1200);
                history.add(new AgentOrchestrator.HistoryMessage("user", wrapped));
                toolPiggybackCount++;
            }
        }
        log.info("[AI][SVC] history loaded: db={} sentToModel={} (含 {} 条工具结果回放)",
                recent.size(), history.size(), toolPiggybackCount);

        // 先把当前用户消息落库
        aiMessageService.append(session.getId(), "user", userMessage, null);
        log.info("[AI][SVC] persisted user message session={} preview=\"{}\"",
                session.getId(), preview(userMessage, 120));

        // 跑一次完整对话
        long t0 = System.currentTimeMillis();
        AgentOrchestrator.AgentResult result = agentOrchestrator.run(currentUser, history, userMessage);
        log.info("[AI][SVC] orchestrator done in {}ms toolExecs={} draft={} replyLen={}",
                System.currentTimeMillis() - t0,
                result.toolExecutions().size(),
                result.draft() == null ? "none" : result.draft().getActionType(),
                result.reply() == null ? 0 : result.reply().length());

        // 把助手回复落库
        aiMessageService.append(session.getId(), "assistant", result.reply(), null);

        // 把工具执行作为 tool 记录落库
        for (AgentOrchestrator.ToolExecutionRecord exec : result.toolExecutions()) {
            aiMessageService.append(session.getId(), "tool",
                    "[" + exec.name() + "] " + exec.content(),
                    toJsonSafe(exec.arguments()));
        }

        // 如果会话标题还是默认值，用用户首条消息生成一个标题
        aiSessionService.renameIfDefault(session.getId(), userMessage);
        aiSessionService.touch(session.getId(), 2 + result.toolExecutions().size());

        // 处理草稿
        AiChatResponse.ActionDraftVO draftVO = null;
        if (result.draft() != null) {
            AgentToolResult.DraftPayload p = result.draft();
            AiActionDraft draft = aiActionDraftService.create(
                    userId, session.getId(), p.getActionType(),
                    p.getTitle(), p.getSummary(), p.getPayload());
            draftVO = new AiChatResponse.ActionDraftVO(
                    draft.getId(), draft.getActionType(),
                    draft.getTitle(), draft.getSummary(),
                    p.getPayload(), draft.getExpiresAt());
            log.info("[AI][SVC] persisted draft id={} type={} expiresAt={}",
                    draft.getId(), draft.getActionType(), draft.getExpiresAt());
        }

        List<AiChatResponse.ToolExecutionVO> toolVOs = new ArrayList<>();
        for (AgentOrchestrator.ToolExecutionRecord exec : result.toolExecutions()) {
            String preview = exec.content() == null ? "" : exec.content();
            if (preview.length() > 200) {
                preview = preview.substring(0, 200) + "...";
            }
            toolVOs.add(new AiChatResponse.ToolExecutionVO(exec.name(), preview));
        }

        return new AiChatResponse(session.getId(), result.reply(), draftVO, toolVOs);
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
