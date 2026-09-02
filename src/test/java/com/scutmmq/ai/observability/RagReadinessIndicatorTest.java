package com.scutmmq.ai.observability;

import com.scutmmq.ai.config.AiRagProperties;
import com.scutmmq.ai.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 知识库就绪探针 RagReadinessIndicator 单元测试。
 */
class RagReadinessIndicatorTest {

    private AiRagProperties props;
    private KnowledgeChunkMapper mapper;
    private RagReadinessIndicator indicator;

    @BeforeEach
    void setUp() {
        props = new AiRagProperties();
        mapper = Mockito.mock(KnowledgeChunkMapper.class);
        indicator = new RagReadinessIndicator(props, mapper);
    }

    @Test
    @DisplayName("RAG 未启用时探针应报告 UP (DISABLED)")
    void disabledReportsUp() {
        props.setEnabled(false);
        Health health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("DISABLED", health.getDetails().get("status"));
    }

    @Test
    @DisplayName("RAG 启用但切片数为 0 时探针应报告 OUT_OF_SERVICE 拦截流量")
    void enabledWithZeroChunksReportsOutOfService() {
        props.setEnabled(true);
        when(mapper.selectCount(any())).thenReturn(0L);

        Health health = indicator.health();
        assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
        assertEquals(0L, health.getDetails().get("activeChunks"));
    }

    @Test
    @DisplayName("RAG 启用且有活跃切片时探针应报告 UP 放行流量")
    void enabledWithActiveChunksReportsUp() {
        props.setEnabled(true);
        when(mapper.selectCount(any())).thenReturn(150L);

        Health health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals(150L, health.getDetails().get("activeChunks"));
    }
}
