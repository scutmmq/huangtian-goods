package com.scutmmq.ai.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GDPR Art 15 用户知情权 — 用户记忆概览响应。
 *
 * <p>设计要点(对齐 §5.1 + GDPR Art 15):
 * <ul>
 *   <li>只暴露"是否记住"等元数据,不暴露真实画像内容
 *       (身份 JSON / 偏好 JSON 可能包含敏感偏好,不出 GET 端点)</li>
 *   <li>给出字段清单,让用户知道具体记住了哪些维度,符合透明度义务</li>
 *   <li>说明数据用途,声明不分享给第三方、不用于广告投放</li>
 * </ul>
 */
public record UserMemoryOverviewVO(
        boolean hasIdentity,
        boolean hasPreference,
        LocalDateTime computedAt,
        Integer version,
        String summaryText,
        List<String> memoryCategories,
        List<String> dataFields,
        String usagePolicy) {
}
