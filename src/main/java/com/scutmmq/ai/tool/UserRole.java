package com.scutmmq.ai.tool;

/**
 * AI 助手用户的角色,用于工具权限校验。
 *
 * 简单的三角色模型:USER / MERCHANT / ADMIN。
 * 通过 UserDTO.getRole() 字符串映射,大小写不敏感。
 * 兜底:无法识别时按 USER 处理(策略文档 §7A.2 安全章节)。
 *
 * 添加角色时请同步评估 {@link com.scutmmq.ai.tool.MallAgentTool#allowedRoles()} 的覆盖情况。
 */
public enum UserRole {
    USER,
    MERCHANT,
    ADMIN;

    /**
     * 大小写不敏感地解析角色字符串,默认 USER。
     */
    public static UserRole parse(Object value) {
        if (value == null) return USER;
        String s = value.toString().trim();
        if (s.isEmpty()) return USER;
        try {
            return UserRole.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }
}
