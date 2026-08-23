package com.scutmmq.ai.security;

/**
 * 当输入命中 DENY_LIST(DSML 标签 / 提示词覆盖指令 / 角色冒充)时抛出。
 *
 * 调用方应当把它当作"不可恢复的可疑输入"——不应被重试到模型,
 * 也不应被 safeExecute 的兜底吞掉,以免污染 LLM 上下文。
 */
public class PromptInjectionException extends RuntimeException {
    public PromptInjectionException(String msg) {
        super(msg);
    }
}