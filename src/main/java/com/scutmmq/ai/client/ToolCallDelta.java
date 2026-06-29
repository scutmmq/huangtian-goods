package com.scutmmq.ai.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式工具调用分片。OpenAI / DeepSeek streaming 协议里，tool_calls 是分片到达的：
 * - 第一个分片通常带完整 id + name + 空 arguments
 * - 后续分片只携带 arguments 的字符串增量（JSON 字符累加，不是完整 JSON）
 *
 * 调用方需要按 index 分组累积：id/name 取首次非空值，arguments_delta 拼接。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallDelta {

    /** tool_calls 数组中的位置（用于多并发工具调用） */
    private int index;

    /** 工具调用 id（仅第一个 chunk 有完整 id，后续可能为空字符串） */
    private String id;

    /** function 名称（仅第一个 chunk 有完整 name） */
    private String name;

    /** arguments 字符串增量（要拼接累积，不是完整 JSON） */
    private String argumentsDelta;
}
