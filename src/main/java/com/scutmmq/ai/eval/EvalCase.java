package com.scutmmq.ai.eval;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 单条评估用例,从 src/main/resources/eval/*.yaml 解析。
 *
 * <p>字段设计:
 * <ul>
 *   <li>name: 用例 ID,日志里用</li>
 *   <li>message: 发给 AI 的用户输入</li>
 *   <li>expectTool: 期望模型至少调一次的工具(如 search_products);留空 = 不检查工具调用</li>
 *   <li>expectKeywords: 期望回复中包含的关键词(任一即可)</li>
 *   <li>expectNoTools: 反向断言,期望不调用任何工具(纯对话型)</li>
 *   <li>expectToolArgsContains: 期望特定工具调用的 args 含某些字段(C11 phantom 跨污染回归)</li>
 *   <li>expectReplyNoDsml: 期望 reply 不包含 DSML 标签(C7 回归)</li>
 *   <li>expectDraft: 期望产出 draft(草稿场景)</li>
 *   <li>expectDraftArgsContains: 期望 draft payload 含某些字段</li>
 *   <li>expectMaxToolExecutions: 期望工具执行次数 ≤ 此值(C8 死循环回归)</li>
 * </ul>
 *
 * 简化策略:不做断言组合(and/or),只支持 expectKeywords 任一满足 + 工具调用检查。
 * 后续 Stage 6 接 Grafana 时再升级为完整 AssertStrategy。
 */
@Data
public class EvalCase {

    private String name;
    private String message;
    private String expectTool;
    private List<String> expectKeywords;
    private Boolean expectNoTools;
    private Long userId;

    // C0-C12 回归断言(2026-08-23 凌晨事故复盘)
    private Map<String, Map<String, Object>> expectToolArgsContains;
    private Boolean expectReplyNoDsml;
    private Boolean expectDraft;
    private Map<String, Object> expectDraftArgsContains;
    private Integer expectMaxToolExecutions;
}
