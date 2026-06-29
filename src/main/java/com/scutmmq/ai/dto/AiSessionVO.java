package com.scutmmq.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话视图对象。给前端 /ai/sessions 列表用。
 *
 * 与 {@link com.scutmmq.ai.entity.AiSession} 的差异：
 * - 多了 runState：基于"该会话最近一条 Run 的状态"派生，前端用这个跑 spinner / 标红。
 * - 多了 activeRunId：runState 不为 IDLE 时指向最近那条 Run，便于前端去订 SSE。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSessionVO {

    private String id;

    private String title;

    private Integer messageCount;

    /**
     * IDLE / QUEUED / RUNNING / FAILED。COMPLETED 映射为 IDLE（没有进行中的 Run）。
     */
    private String runState;

    /**
     * runState != IDLE 时为最近那条 Run 的 ID，IDLE 时为 null。
     */
    private String activeRunId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
