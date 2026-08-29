package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.capability.CapabilityRegistry;
import com.scutmmq.ai.client.AiChatClient;
import com.scutmmq.ai.config.AiAssistantProperties;
import com.scutmmq.ai.security.ToolSecurityInterceptor;
import com.scutmmq.ai.skill.MallSkillRegistry;
import com.scutmmq.ai.skill.MallSystemPromptProvider;
import com.scutmmq.dto.UserDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 AgentOrchestrator 在主对话路径中将 userMessage 传递给 MallSystemPromptProvider 的集成测试。
 * 杜绝 P0-NEW-1（主通道 RAG 死代码）回归。
 */
class AgentOrchestratorRagIntegrationTest {

    @Test
    @DisplayName("主对话流程必须将 userMessage 传递给 buildSystemPrompt，确保 RAG 注入生效")
    void runPassesUserMessageToBuildSystemPrompt() {
        AiChatClient chatClient = Mockito.mock(AiChatClient.class);
        MallSkillRegistry skillRegistry = Mockito.mock(MallSkillRegistry.class);
        MallSystemPromptProvider promptProvider = Mockito.mock(MallSystemPromptProvider.class);
        AiAssistantProperties properties = new AiAssistantProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        CapabilityRegistry capabilityRegistry = Mockito.mock(CapabilityRegistry.class);
        ToolSecurityInterceptor toolSecurityInterceptor = Mockito.mock(ToolSecurityInterceptor.class);

        when(skillRegistry.listDefinitions()).thenReturn(Collections.emptyList());
        when(promptProvider.buildSystemPrompt(any(), any(), any()))
                .thenReturn("SYSTEM_PROMPT_WITH_RAG_KNOWLEDGE");

        when(chatClient.chatCompletion(any(), any()))
                .thenReturn(new AiChatClient.ChatCompletionResult("7天内支持退货", Collections.emptyList(), null));

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                chatClient, skillRegistry, promptProvider, properties,
                objectMapper, capabilityRegistry, toolSecurityInterceptor
        );

        UserDTO user = new UserDTO();
        user.setId(1L);
        String userQuery = "商城支持7天无理由退货吗？";

        AgentOrchestrator.AgentResult result = orchestrator.run(user, Collections.emptyList(), userQuery, null);

        assertNotNull(result);
        assertEquals("7天内支持退货", result.reply());

        // 核心验证：验证 promptProvider.buildSystemPrompt 必须接收到真实的 userQuery,且调用三参版本
        // B4 Phase 1.6:当前会话没有 currentMerchantId,传 null → KnowledgeRecallInjector 兜底走 SearchFilter.all()
        verify(promptProvider).buildSystemPrompt(eq(user), eq(userQuery), eq((Long) null));

        // 验证发送给大模型的 messages 列表中第 1 个 system 消息包含 RAG 上下文
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatClient).chatCompletion(messagesCaptor.capture(), any());

        List<Map<String, Object>> sentMessages = messagesCaptor.getValue();
        assertNotNull(sentMessages);
        assertTrue(sentMessages.size() >= 2);
        assertEquals("system", sentMessages.get(0).get("role"));
        assertEquals("SYSTEM_PROMPT_WITH_RAG_KNOWLEDGE", sentMessages.get(0).get("content"));
    }

    @Test
    @DisplayName("B4 Phase 1.6:主对话流程必须将 currentMerchantId 传递给 buildSystemPrompt,实现多租户 RAG 隔离")
    void runPassesCurrentMerchantIdToBuildSystemPrompt() {
        AiChatClient chatClient = Mockito.mock(AiChatClient.class);
        MallSkillRegistry skillRegistry = Mockito.mock(MallSkillRegistry.class);
        MallSystemPromptProvider promptProvider = Mockito.mock(MallSystemPromptProvider.class);
        AiAssistantProperties properties = new AiAssistantProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        CapabilityRegistry capabilityRegistry = Mockito.mock(CapabilityRegistry.class);
        ToolSecurityInterceptor toolSecurityInterceptor = Mockito.mock(ToolSecurityInterceptor.class);

        when(skillRegistry.listDefinitions()).thenReturn(Collections.emptyList());
        when(promptProvider.buildSystemPrompt(any(), any(), any()))
                .thenReturn("SYSTEM_PROMPT_WITH_TENANT_SCOPED_RAG");

        when(chatClient.chatCompletion(any(), any()))
                .thenReturn(new AiChatClient.ChatCompletionResult("本店铺支持7天退货", Collections.emptyList(), null));

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                chatClient, skillRegistry, promptProvider, properties,
                objectMapper, capabilityRegistry, toolSecurityInterceptor
        );

        UserDTO user = new UserDTO();
        user.setId(1L);
        Long currentMerchantId = 42L;
        String userQuery = "这家店支持7天无理由退货吗？";

        AgentOrchestrator.AgentResult result = orchestrator.run(user, Collections.emptyList(), userQuery, currentMerchantId);

        assertNotNull(result);
        // 关键断言:三参 buildSystemPrompt 接收真实 currentMerchantId(下推至 KnowledgeRecallInjector 跨租户隔离)
        verify(promptProvider).buildSystemPrompt(eq(user), eq(userQuery), eq(currentMerchantId));
    }
}
