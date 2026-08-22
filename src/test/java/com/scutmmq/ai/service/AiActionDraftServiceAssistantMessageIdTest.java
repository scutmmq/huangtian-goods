package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scutmmq.ai.config.AiAssistantProperties;
import com.scutmmq.ai.entity.AiActionDraft;
import com.scutmmq.ai.mapper.AiActionDraftMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * B0:草稿回挂 assistant_message_id。
 * 验证 create(...) 接收 assistantMessageId 并把它写入即将 INSERT 的草稿行。
 * 关联待解决问题文档 P0 #1。
 */
class AiActionDraftServiceAssistantMessageIdTest {

    @Test
    void create_persistsAssistantMessageId_whenProvided() {
        AiActionDraftMapper mapper = mock(AiActionDraftMapper.class);
        AiAssistantProperties props = new AiAssistantProperties();
        ObjectMapper om = new ObjectMapper();
        AiActionDraftService svc = new AiActionDraftService(mapper, props, om);

        ObjectNode payload = om.createObjectNode();
        payload.put("productId", 1);
        payload.put("quantity", 2);

        Long assistantMessageId = 42L;
        AiActionDraft returned = svc.create(
                7L, "sess-1", "ADD_CART_ITEM", "title", "summary",
                payload, assistantMessageId);

        assertNotNull(returned, "create() should return the persisted draft entity");
        assertEquals(assistantMessageId, returned.getAssistantMessageId(),
                "返回的 draft 应带上 assistantMessageId");

        ArgumentCaptor<AiActionDraft> captor = ArgumentCaptor.forClass(AiActionDraft.class);
        verify(mapper).insert(captor.capture());
        AiActionDraft inserted = captor.getValue();
        assertEquals(assistantMessageId, inserted.getAssistantMessageId(),
                "mapper.insert 的草稿行应带上 assistantMessageId");
    }

    @Test
    void create_acceptsNullAssistantMessageId_forLegacyCallers() {
        AiActionDraftMapper mapper = mock(AiActionDraftMapper.class);
        AiAssistantProperties props = new AiAssistantProperties();
        ObjectMapper om = new ObjectMapper();
        AiActionDraftService svc = new AiActionDraftService(mapper, props, om);

        ObjectNode payload = om.createObjectNode();
        payload.put("productId", 1);

        AiActionDraft returned = svc.create(
                7L, "sess-1", "ADD_CART_ITEM", "title", "summary",
                payload, null);

        // 显式传 null 时不抛异常,DB 列允许 null,不破坏历史数据兼容性
        assertNotNull(returned);
        assertEquals(null, returned.getAssistantMessageId());
    }
}
