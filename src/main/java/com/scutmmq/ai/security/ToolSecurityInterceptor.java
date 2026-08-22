package com.scutmmq.ai.security;

import com.scutmmq.ai.skill.MallSkillRegistry;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.tool.UserRole;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具执行前的权限拦截器。Strategy 文档 §7A.2 安全模型 第一层(L1)实现。
 *
 * 调用时机:AgentOrchestrator.safeExecute 调用 tool.execute(...) 之前。
 *
 * 行为:
 * - 工具不存在 → ToolAccessDeniedException(异常信息回喂模型让其自然回复"未知工具")
 * - 用户未登录 → ToolAccessDeniedException
 * - 用户角色不在工具的 allowedRoles 中 → ToolAccessDeniedException
 *
 * 失败安全:AgentOrchestrator 的 safeExecute 会 try/catch 转成 tool-result 文案,
 * 所以本类抛异常不会让 Run 整体失败,只让这条工具调用变成"模型能识别的拒绝回复"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolSecurityInterceptor {

    private final MallSkillRegistry skillRegistry;

    /**
     * 工具调用前的权限预检。
     *
     * @throws ToolAccessDeniedException 当不允许调用时
     */
    public void preCheck(String toolName) {
        MallAgentTool tool = skillRegistry.findByName(toolName);
        if (tool == null) {
            throw new ToolAccessDeniedException(toolName, null);
        }
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new ToolAccessDeniedException(toolName, null);
        }
        UserRole role = resolveRole(user);
        if (!tool.allowedRoles().contains(role)) {
            log.info("[AI][SEC] tool={} denied for user={} role={} allowed={}",
                    toolName, user.getId(), role, tool.allowedRoles());
            throw new ToolAccessDeniedException(toolName, role);
        }
    }

    /**
     * 从 UserDTO 解析角色。UserDTO 当前没有 role 字段;后续 B2+ 接入用户角色数据源。
     * 默认 USER(策略文档 §7A.2 兜底策略:无法识别视为普通用户)。
     */
    private UserRole resolveRole(UserDTO user) {
        // 反射/getter 都试一下,保证 forward-compatibility
        try {
            var roleMethod = user.getClass().getMethod("getRole");
            Object role = roleMethod.invoke(user);
            if (role != null) return UserRole.parse(role);
        } catch (NoSuchMethodException ignored) {
            // UserDTO 还没加 role 字段,默认 USER
        } catch (Exception e) {
            log.debug("[AI][SEC] getRole reflect failed: {}", e.getMessage());
        }
        return UserRole.USER;
    }
}
