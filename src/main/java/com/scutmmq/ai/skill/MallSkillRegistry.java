package com.scutmmq.ai.skill;

import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.MallAgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 商城 AI 工具注册表。Spring 自动注入所有 {@link MallAgentTool} 实例。
 *
 * 模型只能看到这里注册的工具，且只能调用它们。任何未注册的工具名一律返回“工具不存在”，
 * 不会触达业务 Service。
 */
@Slf4j
@Component
public class MallSkillRegistry {

    private final Map<String, MallAgentTool> toolsByName;

    public MallSkillRegistry(List<MallAgentTool> tools) {
        Map<String, MallAgentTool> map = new LinkedHashMap<>();
        for (MallAgentTool tool : Optional.ofNullable(tools).orElse(List.of())) {
            if (tool.isAvailable()) {
                map.put(tool.name(), tool);
            }
        }
        this.toolsByName = Map.copyOf(map);
        log.info("Registered {} mall AI tools: {}", this.toolsByName.size(), this.toolsByName.keySet());
    }

    /**
     * 拿到工具定义列表，发送给模型 tools 数组使用。
     */
    public List<AgentToolDefinition> listDefinitions() {
        return toolsByName.values().stream()
                .map(MallAgentTool::definition)
                .toList();
    }

    public MallAgentTool findByName(String name) {
        if (name == null) {
            return null;
        }
        return toolsByName.get(name);
    }
}
