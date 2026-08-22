package com.scutmmq.ai.dto;

import lombok.Data;

@Data
public class AiChatRequest {

    private String sessionId;

    private String message;

    // lombok @Data 应生成 getSessionId,但 maven-lombok-plugin 偶发不识别
    // sessionId 字段时(本次重构验证),显式补一对避免编译挂掉。
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
