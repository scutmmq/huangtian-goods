package com.scutmmq.ai.tool;

/**
 * 工具模式。
 *
 * READ_ONLY:  纯查询，工具可以直接执行并把结果反馈给模型。
 * DRAFT_ONLY: 写操作，工具只生成草稿并落库，必须由用户确认后才会真正调用商城业务 Service。
 */
public enum ToolMode {
    READ_ONLY,
    DRAFT_ONLY
}
