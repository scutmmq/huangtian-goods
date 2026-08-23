package com.scutmmq.ai.controller;

import com.scutmmq.ai.service.UserMemoryService;
import com.scutmmq.entity.Result;
import com.scutmmq.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * B3 step8: GDPR Art 17 — 用户主动重置长期记忆。
 *
 * <p>{@code POST /ai/memory/reset} 清空当前用户的身份画像 + 偏好画像,
 * 并异步清理审计日志。controller 不写业务,只拿到 userId 委托给 {@link UserMemoryService#reset}。
 *
 * <p>userId 来源:ThreadLocal {@link UserHolder#getUser()}。如果拦截器没塞 user
 * (未登录路径),{@code Assert.isTrue} 会抛 IllegalArgumentException →
 * {@code GlobalExceptionHandler} 包成 {@code Result.error}。
 */
@Slf4j
@RestController
@RequestMapping("/ai/memory")
@RequiredArgsConstructor
public class MemoryResetController {

    private final UserMemoryService service;
    private final UserHolder userHolder;

    @PostMapping("/reset")
    public Result reset() {
        Long userId = userHolder.getUser().getId();
        Assert.isTrue(userId != null, "userId must not be null");
        log.info("[AI][CTRL] POST /ai/memory/reset userId={}", userId);
        service.reset(userId);
        return Result.success(null);
    }
}
