package com.scutmmq.ai.controller;

import com.scutmmq.ai.dto.UserMemoryOverviewVO;
import com.scutmmq.ai.service.UserMemoryService;
import com.scutmmq.entity.Result;
import com.scutmmq.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * B3 step8: GDPR Art 15 — 用户知情权,查看自己被记住了什么。
 *
 * <p>{@code GET /ai/memory} 返回不含敏感内容的元数据 + 字段清单 + 数据用途声明。
 *
 * <p>userId 来源:ThreadLocal {@link UserHolder#getUser()}。本 controller 不带
 * {@code @PreAuthorize} — 鉴权由拦截器 {@code LoginCertificationInterceptor} 处理。
 */
@Slf4j
@RestController
@RequestMapping("/ai/memory")
@RequiredArgsConstructor
public class MemoryQueryController {

    private final UserMemoryService service;
    private final UserHolder userHolder;

    @GetMapping("")
    public Result get() {
        Long userId = userHolder.getUser().getId();
        log.debug("[AI][CTRL] GET /ai/memory userId={}", userId);
        UserMemoryOverviewVO overview = service.buildOverview(userId);
        return Result.success(overview);
    }
}
