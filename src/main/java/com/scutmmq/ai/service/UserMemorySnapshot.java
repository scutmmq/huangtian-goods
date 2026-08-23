package com.scutmmq.ai.service;

/**
 * B3 长期记忆快照:在 {@link UserMemoryBuilder} 与 {@link UserMemoryService} 之间传递
 * identity / preference 的 JSON 表示。
 *
 * <p>该 record 仅承载 JSON 字符串,不解析内容 — Builder 的职责是把内存画像转成 JSON,
 * cache 与 Service 的职责是搬运 + 序列化,不关心结构语义。
 *
 * @param identityJson   身份画像 JSON,例 {@code {"ageRange":"25-30","gender":"F"}}
 * @param preferenceJson 偏好画像 JSON,例 {@code {"priceRange":"mid","favoriteCategories":["book"]}}
 */
public record UserMemorySnapshot(String identityJson, String preferenceJson) {

    /**
     * 新用户/重算失败/JSON OVERFLOW 降级时使用的全空快照。
     * 与"已生成但内容很短"的快照语义不同,后者应返回正常 JSON。
     */
    public static UserMemorySnapshot empty() {
        return new UserMemorySnapshot("{}", "{}");
    }
}
