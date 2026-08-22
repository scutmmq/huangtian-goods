package com.scutmmq.ai.eval;

import lombok.Data;

import java.util.List;

/**
 * 单条评估用例,从 src/test/resources/eval/*.yaml 解析。
 *
 * 字段设计:
 * - name: 用例 ID,日志里用
 * - message: 发给 AI 的用户输入
 * - expectTool: 期望模型至少调一次的工具(如 search_products);
 *   留空 = 不检查工具调用
 * - expectKeywords: 期望回复中包含的关键词(任一即可)
 * - expectNoTools: 反向断言,期望不调用任何工具(纯对话型)
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
}
