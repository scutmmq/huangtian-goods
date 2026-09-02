package com.scutmmq.ai.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DsmlSanitizerTest {

    @Test
    void testStripThinkTags() {
        String input = "<think>这是思考过程\n第二行思考</think>这是给用户的正文";
        String clean = DsmlSanitizer.strip(input);
        assertEquals("这是给用户的正文", clean);
    }

    @Test
    void testStripUnclosedThinkTags() {
        String input = "<think>思考到一半...";
        String clean = DsmlSanitizer.strip(input);
        assertEquals("", clean);
    }

    @Test
    void testStripDsmlAndThink() {
        String input = "<think>思考</think>你好！<｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name=\"search\"><｜｜DSML｜｜parameter>1</｜｜DSML｜｜parameter></｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>";
        String clean = DsmlSanitizer.strip(input);
        assertEquals("你好！", clean);
    }

    @Test
    void testStreamingThinkFilter() {
        StreamingThinkFilter filter = new StreamingThinkFilter();
        StringBuilder reasoning = new StringBuilder();

        assertEquals("", filter.filter("<think>用户想买裙子", reasoning));
        assertEquals("", filter.filter("，让我搜一下", reasoning));
        assertEquals("为你找到以下商品：", filter.filter("</think>为你找到以下商品：", reasoning));
        assertEquals(" 连衣裙A", filter.filter(" 连衣裙A", reasoning));

        assertEquals("用户想买裙子，让我搜一下", reasoning.toString());
    }
}
