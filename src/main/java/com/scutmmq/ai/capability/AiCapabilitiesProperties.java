package com.scutmmq.ai.capability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 助手能力的开关集合。
 *
 * 默认全部禁用。开启任何能力需要修改 application.yaml 的对应 key,
 * 改错不会启动失败(未识别的 key 会用 false 兜底)。
 *
 * 设计决定:v1.1 修订版采用 Map<String,Boolean> 而非嵌套 POJO——
 * 缺点是失去类型校验,优点是新增 capability 不需要改这个类。
 * 当前阶段 capability 数量少(<5),后续 Stage 如果 >10 个再切到嵌套 POJO。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.capability")
public class AiCapabilitiesProperties {

    /**
     * 各能力开关。key 是 capability.name(),value 是启用与否。
     */
    private Map<String, Boolean> flags = new HashMap<>();

    public boolean isEnabled(String name) {
        if (name == null) return false;
        return flags.getOrDefault(name, false);
    }

    public void set(String name, boolean enabled) {
        flags.put(name, enabled);
    }
}
