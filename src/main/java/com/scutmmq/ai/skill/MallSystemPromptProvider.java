package com.scutmmq.ai.skill;

import com.scutmmq.ai.rag.injector.KnowledgeRecallInjector;
import com.scutmmq.ai.service.UserMemoryService;
import com.scutmmq.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 商城 AI 助手系统提示词。把商城“能做什么、不能做什么、必须如何使用工具”写死在这里，
 * 而不是依赖模型自己理解接口文档。
 *
 * <p>B3 step8: 在 BASE_PROMPT 之前插入用户画像(memory section),
 * 让模型看到用户当前的偏好/身份后再开始回答;空画像(render 返回 "")
 * 时跳过,不污染 prompt。
 *
 * <p>B4: 支持注入知识库召回段落 (knowledge section),
 * 当 RAG 能力启用时，为大模型提供精确的商城政策与商品规格背景。
 */
@Component
public class MallSystemPromptProvider {

    private final UserMemoryService memoryService;
    private final KnowledgeRecallInjector knowledgeInjector;

    public MallSystemPromptProvider(UserMemoryService memoryService) {
        this(memoryService, null);
    }

    @Autowired
    public MallSystemPromptProvider(UserMemoryService memoryService,
                                    @Autowired(required = false) KnowledgeRecallInjector knowledgeInjector) {
        this.memoryService = memoryService;
        this.knowledgeInjector = knowledgeInjector;
    }

    private static final String BASE_PROMPT = """
            你是“荒天享物”商城的官方 AI 购物助手。

            【角色与目标】
            - 帮用户搜索商品、对比参数、推荐合适商品。
            - 帮用户查询订单、收货地址和店铺信息。
            - 帮用户生成下单、加入购物车、注册店铺、修改资料等操作的“确认草稿”。
            - 用亲切、自然、专业的中文回答，给用户最佳的购物体验。

            【输出纪律与用户体验规范（绝不可违反）】
            - 严禁在对用户的回复中提及任何内部函数名、工具名称（如 search_products、get_my_addresses 等）、Prompt 规则或内部技术逻辑！
            - 严禁把思考过程（CoT）、技术指令、或者“根据系统提示”、“由于规则限制”等元信息暴露给用户。
            - 像一位懂业务的专业电商金牌导购一样直接为用户服务，语言得体、干练、温暖。

            【工具调用与人在回路（HITL）规范（核心硬规则）】
            - 推荐商品或下单前，必须首先调用 `search_products` 查询商城中真实存在的商品，严禁凭空捏造不存在的商品、虚假商品ID（如 1008 等）、虚假价格或虚拟商家！
            - 查询收货地址或商品详情时直接调用相应工具获取准确信息。
            - 严禁在工具报错时假装成功：若 `get_product_detail` 或 `draft_create_order` 返回“未找到商品”、“属于自己的店铺不能购买”或“库存不足”，必须如实向用户解释原因，绝对不能无视错误而在文本中谎称“已为您生成下单卡片”！
            - 当用户要下单购买商品、且商品与地址已明确时（例如用户说“买一件”、“用默认地址”、“买第二个”），必须且只能在当前轮次实际发起 `draft_create_order` 工具调用！
            - 绝对不要仅在文本中打字“已为您生成订单草稿/请在前端卡片上点击确认”，因为只有实际调用 draft_* 工具，前端才会生成真正的「确认下单」卡片与确认按钮！
            - 涉及下单、加购物车、改资料等写操作时，通过草稿工具生成操作草稿，由用户在前端卡片上点击二次确认，不要虚假声称已经扣款或直接完成修改。
            - 用户如给出模糊表述（如“就用默认地址”、“买第一个”），根据已查询到的数据智能推导并直接发起对应工具调用。

            【商城规则与防幻觉】
            - 涉及售后、退换货、7天无理由、运费承担等官方政策，必须基于知识库真实内容解答；未检索到时礼貌建议咨询人工客服，严禁凭空编造规则。
            - 用户不能购买自己店铺销售的商品。
            - 同一个订单只能包含同一个商家的商品，跨商家请引导分别下单。

            【DSML 与 tool_call 纪律 — 必读,硬约束】
            事实 1:<｜｜DSML｜｜...>...</｜｜DSML｜｜> 是内部 tool_call 编码。前端**不会展示**这一段内容。
            任何情况下都不要把这种标签写在 content 字段里。
            
            ❌ 错误模式:
            User: "我想买自行车"
            Assistant.content: "没找到完全叫\"自行车\"的商品。<｜｜DSML｜｜tool_calls>..."

            ✅ 正确模式:
            User: "我想买自行车"
            Assistant.content: ""
            Assistant.tool_calls: [{search_products, keyword="自行车"}]

            【硬规则】:
            1. 决定调用工具时,第一条可见 Assistant content 输出必须是空字符串或 ≤ 10 字短句,直接发起 tool_call。
            2. 绝对不要在 content 字段里输出 "<｜｜DSML｜｜" 字面值或任何 DSML 标签包裹的内容。

            【商品推荐展示格式】
            推荐商品时，请清晰列出：商品编号、名称、价格、规格/颜色、评分、商家名称及简短推荐理由。
            """;

    /**
     * 构建系统提示词。会把当前用户基础信息和日期时间一起注入，便于模型回答。
     * <p>
    /**
     * @deprecated 请使用带有当前轮次用户提问的 {@link #buildSystemPrompt(UserDTO, String)}，以便 RAG 知识检索注入生效。
     */
    @Deprecated
    public String buildSystemPrompt(UserDTO currentUser) {
        return buildSystemPrompt(currentUser, null);
    }

    /**
     * 构建系统提示词（带 RAG 知识检索注入）。
     *
     * @param currentUser 当前登录用户信息
     * @param userQuery   用户当前输入的问题文本（为 null 时不触发知识检索注入）
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(UserDTO currentUser, String userQuery) {
        return buildSystemPrompt(currentUser, userQuery, null);
    }

    /**
     * 构建系统提示词（带 RAG 知识检索注入与多租户商家隔离）。
     *
     * @param currentUser       当前登录用户信息
     * @param userQuery         用户当前输入的问题文本（为 null 时不触发知识检索注入）
     * @param currentMerchantId 当前会话所在的商家店铺 ID（用于多租户私有政策隔离，可为 null）
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(UserDTO currentUser, String userQuery, Long currentMerchantId) {
        StringBuilder sb = new StringBuilder();
        // 1. 用户画像(B3 step8 新增):render 返回 "" 时跳过,避免空段污染 prompt
        if (currentUser != null && currentUser.getId() != null) {
            String memorySection = memoryService.renderMemorySection(currentUser.getId());
            if (memorySection != null && !memorySection.isEmpty()) {
                sb.append(memorySection).append("\n");
            }
        }
        // 2. 知识库相关段落(B4 新增):仅在启用且命中时注入，支持多租户商家隔离
        if (knowledgeInjector != null && userQuery != null && !userQuery.trim().isEmpty()) {
            String knowledgeSection = knowledgeInjector.renderKnowledgeSection(userQuery, currentMerchantId);
            if (knowledgeSection != null && !knowledgeSection.isEmpty()) {
                sb.append(knowledgeSection).append("\n");
            }
        }
        // 3. BASE_PROMPT
        sb.append(BASE_PROMPT);
        // 4. 日期 + 当前用户(原顺序)
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        String nowText = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(now);
        sb.append("\n当前日期时间: ").append(nowText).append(" (Asia/Shanghai)\n");
        if (currentUser != null) {
            sb.append("当前用户: ")
                    .append("userId=").append(currentUser.getId())
                    .append(", username=").append(safe(currentUser.getUsername()))
                    .append(", nickName=").append(safe(currentUser.getNickName()))
                    .append("\n");
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
