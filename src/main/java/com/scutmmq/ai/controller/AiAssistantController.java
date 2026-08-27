package com.scutmmq.ai.controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.scutmmq.ai.dto.AiChatRequest;
import com.scutmmq.ai.dto.AiChatSubmitResponse;
import com.scutmmq.ai.entity.AiSession;
import com.scutmmq.ai.entity.AiStreamEvent;
import com.scutmmq.ai.service.AiAssistantService;
import com.scutmmq.ai.service.AiSessionService;
import com.scutmmq.ai.service.AiStreamEventService;
import com.scutmmq.ai.service.AiStreamHub;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.entity.Result;
import com.scutmmq.utils.UserHolder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final AiSessionService aiSessionService;
    private final AiStreamEventService aiStreamEventService;
    private final AiStreamHub aiStreamHub;

    @PostMapping("/chat")
    public Result chat(@RequestBody AiChatRequest request) {
        log.info("[AI][CTRL] POST /ai/chat sessionId={} messageLen={} preview=\"{}\"",
                request.getSessionId(),
                request.getMessage() == null ? 0 : request.getMessage().length(),
                preview(request.getMessage(), 80));
        long t0 = System.currentTimeMillis();
        try {
            AiChatSubmitResponse data = aiAssistantService.chat(request);
            log.info("[AI][CTRL] POST /ai/chat SUBMITTED runId={} sessionId={} status={} in {}ms",
                    data.getRunId(), data.getSessionId(), data.getStatus(),
                    System.currentTimeMillis() - t0);
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
        return Result.success(aiAssistantService.listSessionsAsVO());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result listMessages(@PathVariable String sessionId) {
        log.info("[AI][CTRL] GET /ai/sessions/{}/messages", sessionId);
        return Result.success(aiAssistantService.listMessagesAsVO(sessionId));
    }

    /**
     * SSE 流式事件端点（text/event-stream）。
     *
     * 流程：
     *  1. 鉴权 + 会话归属校验
     *  2. 先注册 SSE emitter，再 queryLatestId 拿 DB 快照 N
     *  3. 异步从 DB 补齐 afterId 之后且 id < N 的历史事件（replay）
     *  4. 之后 AiStreamHub.broadcast 会持续把 id >= N 的新事件推到这个 emitter
     *
     * 无丢无重的关键顺序：register 必须先于 queryLatestId。
     * register 之后任何 append() 都能找到监听者，被 broadcast 推给本 emitter；
     * replay 只回放 id > afterId 且 id < N 的事件，id >= N 一律留给 broadcast，
     * 既不会因为「snapshot 与 register 之间插入的事件」漏推，也不会重复发送。
     */
    @GetMapping(value = "/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String sessionId,
                             @RequestParam(value = "afterId", required = false) Long afterId) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) {
            log.warn("[AI][CTRL] SSE events rejected: no current user sessionId={}", sessionId);
            throw new IllegalArgumentException("未登录");
        }

        AiSession session = aiSessionService.findByIdForUser(sessionId, currentUser.getId());
        if (session == null) {
            log.warn("[AI][CTRL] SSE events rejected: session not found or not owned sessionId={} user={}",
                    sessionId, currentUser.getId());
            throw new IllegalArgumentException("会话不存在或无权访问");
        }

        long replayAfterId = afterId == null ? 0L : afterId;

        // 顺序很关键：必须先 register，再 queryLatestId。
        // - register 之后任何 append() 都能找到监听者，被 broadcast 立即推给本 emitter；
        // - queryLatestId 给出「此刻 DB 状态」的快照 N；
        // - replay 只发送 id > afterId 且 id < N 的事件（id >= N 一律留给 broadcast），
        //   这样 register 与 snapshot 之间插入的事件（如 id=N）已被 broadcast 推过，
        //   不会被 replay 再发一遍；snapshot 与 replay 查询之间插入的事件（id>N）也不在
        //   replay 责任范围，由 broadcast 继续推。无丢、无重。
        // - DB 为空时 snapshot = 0，replay 范围为空 (id > afterId 且 id < 0)，
        //   后续事件由 broadcast 全权负责。
        SseEmitter emitter = aiStreamHub.register(sessionId);
        Long latestIdAfterRegister = aiStreamEventService.queryLatestId(sessionId);
        long latestSnapshot = latestIdAfterRegister == null ? 0L : latestIdAfterRegister;
        log.info("[AI][CTRL] SSE events opened sessionId={} user={} afterId={} latestIdAfter={}",
                sessionId, currentUser.getId(), replayAfterId, latestIdAfterRegister);

        // 异步 replay（HTTP 线程立即返回，emitter 已经被 Spring 接管）
        CompletableFuture.runAsync(() -> {
            try {
                List<AiStreamEvent> history = aiStreamEventService.queryAfter(sessionId, replayAfterId);
                if (history.isEmpty()) {
                    log.debug("[AI][SSE] replay no history sessionId={} afterId={}", sessionId, replayAfterId);
                    return;
                }
                int sent = 0;
                for (AiStreamEvent event : history) {
                    if (event.getId() == null || event.getId() >= latestSnapshot) {
                        // id >= 快照的事件归 broadcast 管（要么已推过，要么在 snapshot 之后才到）
                        break;
                    }
                    try {
                        emitter.send(aiStreamHub.buildSseEvent(event));
                        sent++;
                    } catch (Exception e) {
                        log.debug("[AI][SSE] replay send failed eventId={} sessionId={}: {} (likely client gone)",
                                event.getId(), sessionId, e.getMessage());
                        break;
                    }
                }
                log.info("[AI][SSE] replay done sessionId={} afterId={} sent={} total={}",
                        sessionId, replayAfterId, sent, history.size());
            } catch (Exception e) {
                log.error("[AI][SSE] replay failed sessionId={} afterId={}", sessionId, replayAfterId, e);
            }
        });

        return emitter;
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
