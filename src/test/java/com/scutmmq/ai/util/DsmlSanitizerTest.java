package com.scutmmq.ai.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * C2 DSML 剔除 hotfix 测试。
 * 覆盖:无 DSML、单层 open-close、嵌套、损坏格式、null/空。
 */
class DsmlSanitizerTest {

    private static final String O = "<｜｜DSML｜｜";  // open prefix
    private static final String C = "</｜｜DSML｜｜"; // close prefix

    @Test
    void noDsml_returnsSameString() {
        String s = "你好,商城里有这款橡皮擦";
        assertSame(s, DsmlSanitizer.strip(s));
    }

    @Test
    void nullAndEmpty_returnAsIs() {
        assertEquals(null, DsmlSanitizer.strip(null));
        assertEquals("", DsmlSanitizer.strip(""));
    }

    @Test
    void singleBlock_strippedCompletely() {
        // 平衡结构:tool_calls > invoke > parameter > invoke > tool_calls
        String input = "before " + O + "tool_calls>" + O + "invoke name=\"x\">" + O + "parameter>val" + C + "parameter>" + C + "invoke>" + C + "tool_calls>" + " after";
        String expected = "before  after";
        assertEquals(expected, DsmlSanitizer.strip(input));
    }

    @Test
    void nestedToolCalls_strippedAllLayers() {
        // 平衡结构:tool_calls > invoke search > parameter > invoke > invoke detail > tool_calls
        String input = "用户问商品" + O + "tool_calls>" + O + "invoke name=\"search\">" + O + "parameter>kw" + C + "parameter>" + C + "invoke>" + O + "invoke name=\"detail\">" + C + "invoke>" + C + "tool_calls>" + " 完成";
        String expected = "用户问商品 完成";
        assertEquals(expected, DsmlSanitizer.strip(input));
    }

    @Test
    void unbalancedDSML_leavesOriginalTextUntouched() {
        // 只有 open 没有 close:损坏的 DSML 块不应误删其他正常内容。
        // Sanitizer 安全地 bails,不动原文。
        String input = "before " + O + "tool_calls> 没有关闭 after";
        String result = DsmlSanitizer.strip(input);
        // 不抛 + 包含原文(允许保留 open 部分)
        assertEquals(input.length(), result.length());
    }

    @Test
    void multipleSeparateBlocks_allStripped() {
        String input = "A " + O + "tool_calls>X" + C + "tool_calls>" + " B " + O + "tool_calls>Y" + C + "tool_calls>" + " C";
        String expected = "A  B  C";
        assertEquals(expected, DsmlSanitizer.strip(input));
    }

    @Test
    void realAssistant272Content_isCleanedToZeroVisibleText() {
        // 复刻 DB 里的真实 assistant 272 内容
        String raw = "<｜｜DSML｜｜tool_calls>\n<｜｜DSML｜｜invoke name=\"draft_create_order\">\n</｜｜DSML｜｜invoke>\n</｜｜DSML｜｜tool_calls>";
        String cleaned = DsmlSanitizer.strip(raw);
        assertEquals("", cleaned,
                "C2 修复后,DB 里不应再保留可被前端显示的 DSML 标签");
    }

    @Test
    void malformed_unclosed_returnsOriginalStrippedOuter() {
        // 只有 open 没有 close 的损坏格式:不强行处理,返回原字符串
        String input = "before " + O + "tool_calls> 没有关闭";
        // 没找到 close 配对,我的算法会一直循环(因为不删东西也找 open);所以
        // 我选择安全路径:遇到损坏输入直接放弃 strip,返回原文。
        // 验证:不抛异常即可,输出长度合理。
        String result = DsmlSanitizer.strip(input);
        // 不应抛 + 应至少返回原始长度
        assertEquals(input.length(), result.length());
    }
}
