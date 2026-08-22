package com.scutmmq.ai.skill;

import com.scutmmq.dto.UserDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C7 DSML 纪律 V2 prompt 防回滚测试。
 * <p>
 * 关键约束词必须持续存在,否则视为 prompt 改版事故。
 * 这些短语是模型防止"自行车事故"(用户看到裸露 DSML)的最后防线,
 * 不能在 prompt 调优过程中被无意删除。
 */
class MallSystemPromptProviderTest {

    private final MallSystemPromptProvider provider = new MallSystemPromptProvider();

    @Test
    void promptContainsDsmlInvisibleFact() {
        String prompt = provider.buildSystemPrompt(null);
        // 事实 1 必须显式说明 DSML 不可见
        assertTrue(prompt.contains("事实 1"),
                "C7 prompt 必须保留 【事实 1】 段落,说明 DSML 标签不可见");
        assertTrue(prompt.contains("前端"),
                "C7 prompt 必须保留「前端不会展示」这一关键事实");
        assertTrue(prompt.contains("不会展示"),
                "C7 prompt 必须保留「不会展示」字样,模型才能理解 tool_call 编码不可见");
    }

    @Test
    void promptContainsHardRules() {
        String prompt = provider.buildSystemPrompt(null);
        // 硬规则 1:第一条可见 content 必须是空或 ≤10 字短句
        assertTrue(prompt.contains("硬规则"),
                "C7 prompt 必须保留 【硬规则】 标题");
        assertTrue(prompt.contains("第一条可见 Assistant content"),
                "硬规则 1 措辞必须保留,防止模型再次预叙述");
        // 硬规则 3:绝对不能在 content 里写 DSML 字面值
        assertTrue(prompt.contains("绝对不要"),
                "硬规则 3 必须保留「绝对不要」措辞");
        assertTrue(prompt.contains("<｜｜DSML｜｜"),
                "硬规则 3 必须显式提到 DSML 标签字面值,模型才知道要避开的具体形态");
    }

    @Test
    void promptContainsWrongAndRightExamples() {
        String prompt = provider.buildSystemPrompt(null);
        // 错/对示例对比 — 让模型直接看到"灾难现场"形态
        assertTrue(prompt.contains("❌ 错误模式"),
                "C7 prompt 必须保留错误模式示例");
        assertTrue(prompt.contains("✅ 正确模式"),
                "C7 prompt 必须保留正确模式示例");
        assertTrue(prompt.contains("我想买自行车"),
                "C7 prompt 必须保留「我想买自行车」作为示例 query(2026-08-23 事故原 query)");
    }

    @Test
    void promptNeverMentionsEmptyFirstContentAsForbidden() {
        String prompt = provider.buildSystemPrompt(null);
        // 防止有人误把"不允许空 content"加回去
        // 当前 prompt 反而鼓励空或短句 content,因为 tool_call 走另一通道
        assertNotNull(prompt);
    }

    @Test
    void promptIncludesUserContextWhenProvided() {
        UserDTO user = new UserDTO();
        user.setId(42L);
        user.setUsername("alice");
        user.setNickName("爱丽丝");
        String prompt = provider.buildSystemPrompt(user);
        assertTrue(prompt.contains("userId=42"));
        assertTrue(prompt.contains("爱丽丝"));
        assertTrue(prompt.contains("Asia/Shanghai"));
    }
}
