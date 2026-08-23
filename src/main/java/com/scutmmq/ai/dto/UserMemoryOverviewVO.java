package com.scutmmq.ai.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * B3 step8: GDPR Art 15 — 用户知情权 GET /ai/memory 响应体。
 *
 * <p>字段命名对齐 task-8-brief.md,前端用同名 JSON key 消费。
 *
 * <p>设计要点:
 * <ul>
 *   <li>{@code hasIdentity} / {@code hasPreference} — 只告诉前端"有/无",不暴露内容</li>
 *   <li>{@code summary} — 摘要文案,告诉用户记住了什么</li>
 *   <li>{@code categoryNames} / {@code fieldList} — 字段清单,符合 GDPR 透明度义务</li>
 *   <li>{@code purpose} — 数据用途声明,不分享给第三方、不用于广告投放</li>
 * </ul>
 */
@Data
public class UserMemoryOverviewVO {
    private boolean hasIdentity;
    private boolean hasPreference;
    private Instant computedAt;
    private Integer version;
    private String summary;
    private List<String> categoryNames;
    private List<String> fieldList;
    private String purpose;

    public UserMemoryOverviewVO() {
    }

    public UserMemoryOverviewVO(boolean hasIdentity,
                                boolean hasPreference,
                                Instant computedAt,
                                Integer version,
                                String summary,
                                List<String> categoryNames,
                                List<String> fieldList,
                                String purpose) {
        this.hasIdentity = hasIdentity;
        this.hasPreference = hasPreference;
        this.computedAt = computedAt;
        this.version = version;
        this.summary = summary;
        this.categoryNames = categoryNames;
        this.fieldList = fieldList;
        this.purpose = purpose;
    }
}
