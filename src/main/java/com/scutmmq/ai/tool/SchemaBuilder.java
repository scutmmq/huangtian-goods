package com.scutmmq.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 工具 JSON Schema 构造小工具，避免每个工具重复样板代码。
 */
public class SchemaBuilder {

    private final ObjectMapper objectMapper;
    private final ObjectNode root;
    private final ObjectNode properties;
    private final ArrayNode required;

    public SchemaBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.root = objectMapper.createObjectNode();
        this.root.put("type", "object");
        this.properties = root.putObject("properties");
        this.required = root.putArray("required");
        this.root.put("additionalProperties", false);
    }

    public SchemaBuilder prop(String name, String type, String description) {
        ObjectNode field = properties.putObject(name);
        field.put("type", type);
        field.put("description", description);
        return this;
    }

    public SchemaBuilder require(String... names) {
        for (String n : names) {
            required.add(n);
        }
        return this;
    }

    public ObjectNode build() {
        return root;
    }
}
