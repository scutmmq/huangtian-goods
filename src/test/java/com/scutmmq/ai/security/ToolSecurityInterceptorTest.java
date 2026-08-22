package com.scutmmq.ai.security;

import com.scutmmq.ai.skill.MallSkillRegistry;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.tool.ToolMode;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B2.Checkpoint2:工具权限拦截器测试。
 * 覆盖 4 个场景:工具不存在 / 用户未登录 / 角色不在白名单 / 默认全开放行。
 */
class ToolSecurityInterceptorTest {

    private MallSkillRegistry skillRegistry;
    private ToolSecurityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        skillRegistry = mock(MallSkillRegistry.class);
        interceptor = new ToolSecurityInterceptor(skillRegistry);
        UserHolder.saveUser(userWithRole(7L, null));
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void rejects_unknownTool() {
        when(skillRegistry.findByName("ghost_tool")).thenReturn(null);
        assertThrows(ToolAccessDeniedException.class,
                () -> interceptor.preCheck("ghost_tool"));
    }

    @Test
    void rejects_roleNotInAllowed() {
        MallAgentTool merchantOnly = stubTool("merchant_only",
                EnumSet.of(com.scutmmq.ai.tool.UserRole.MERCHANT));
        when(skillRegistry.findByName("merchant_only")).thenReturn(merchantOnly);

        // 当前用户是 USER(null role → parse 后默认 USER)
        assertThrows(ToolAccessDeniedException.class,
                () -> interceptor.preCheck("merchant_only"));
    }

    @Test
    void allows_roleInAllowed() {
        MallAgentTool merchantOnly = stubTool("merchant_only",
                EnumSet.of(com.scutmmq.ai.tool.UserRole.MERCHANT));
        when(skillRegistry.findByName("merchant_only")).thenReturn(merchantOnly);

        // 改成 MERCHANT 角色
        UserHolder.saveUser(userWithRole(7L, "MERCHANT"));
        assertDoesNotThrow(() -> interceptor.preCheck("merchant_only"));
    }

    @Test
    void allows_defaultAll_whenNotOverridden() {
        MallAgentTool publicTool = stubTool("public_tool",
                EnumSet.allOf(com.scutmmq.ai.tool.UserRole.class));
        when(skillRegistry.findByName("public_tool")).thenReturn(publicTool);
        // 默认 USER 角色
        assertDoesNotThrow(() -> interceptor.preCheck("public_tool"));
    }

    @Test
    void rejects_noLoginUser() {
        UserHolder.removeUser();
        MallAgentTool publicTool = stubTool("public_tool",
                EnumSet.allOf(com.scutmmq.ai.tool.UserRole.class));
        when(skillRegistry.findByName("public_tool")).thenReturn(publicTool);
        assertThrows(ToolAccessDeniedException.class,
                () -> interceptor.preCheck("public_tool"));
    }

    private static MallAgentTool stubTool(String name, Set<com.scutmmq.ai.tool.UserRole> allowed) {
        return new MallAgentTool() {
            @Override public String name() { return name; }
            @Override public ToolMode mode() { return ToolMode.READ_ONLY; }
            @Override public com.scutmmq.ai.tool.AgentToolDefinition definition() { return null; }
            @Override public com.scutmmq.ai.tool.AgentToolResult execute(com.fasterxml.jackson.databind.JsonNode args) { return null; }
            @Override public Set<com.scutmmq.ai.tool.UserRole> allowedRoles() { return allowed; }
        };
    }

    private static UserDTO userWithRole(Long id, String role) {
        UserDTO u = new UserDTO();
        u.setId(id);
        u.setRole(role);
        return u;
    }
}
