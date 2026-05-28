package com.scutmmq.ai.controller;

import com.scutmmq.ai.dto.AiChatRequest;
import com.scutmmq.ai.service.AiAssistantService;
import com.scutmmq.entity.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    @PostMapping("/chat")
    public Result chat(@RequestBody AiChatRequest request) {
        log.info("[AI][CTRL] POST /ai/chat sessionId={} messageLen={} preview=\"{}\"",
                request.getSessionId(),
                request.getMessage() == null ? 0 : request.getMessage().length(),
                preview(request.getMessage(), 80));
        long t0 = System.currentTimeMillis();
        try {
            Object data = aiAssistantService.chat(request);
            log.info("[AI][CTRL] POST /ai/chat OK in {}ms", System.currentTimeMillis() - t0);
            return Result.success(data);
        } catch (Exception e) {
            log.error("[AI][CTRL] POST /ai/chat FAIL in {}ms: {}",
                    System.currentTimeMillis() - t0, e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/sessions")
    public Result listSessions() {
        log.info("[AI][CTRL] GET /ai/sessions");
        return Result.success(aiAssistantService.listSessions());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result listMessages(@PathVariable String sessionId) {
        log.info("[AI][CTRL] GET /ai/sessions/{}/messages", sessionId);
        return Result.success(aiAssistantService.listMessages(sessionId));
    }

    @PostMapping("/actions/{draftId}/confirm")
    public Result confirmDraft(@PathVariable String draftId) {
        log.info("[AI][CTRL] POST /ai/actions/{}/confirm", draftId);
        long t0 = System.currentTimeMillis();
        Result r = aiAssistantService.confirmDraft(draftId);
        log.info("[AI][CTRL] confirm draftId={} code={} msg={} in {}ms",
                draftId, r == null ? null : r.getCode(), r == null ? null : r.getMsg(),
                System.currentTimeMillis() - t0);
        return r;
    }

    @PostMapping("/actions/{draftId}/cancel")
    public Result cancelDraft(@PathVariable String draftId) {
        log.info("[AI][CTRL] POST /ai/actions/{}/cancel", draftId);
        return aiAssistantService.cancelDraft(draftId);
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String t = s.replace("\n", " ").replace("\r", " ");
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
