package com.scutmmq.ai.controller;

import com.scutmmq.dto.UserDTO;
import com.scutmmq.entity.Result;
import com.scutmmq.ai.service.UserMemoryService;
import com.scutmmq.utils.UserHolder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * B3 step8: MemoryResetController 单元测试。
 *
 * <p>不启动 Spring 容器,直接 mock UserMemoryService + 手动管理
 * {@link UserHolder} 的 ThreadLocal。覆盖:
 * <ul>
 *   <li>resetIdempotent — 两次 reset 同一用户都成功,不抛</li>
 *   <li>resetReturnsAudit — reset 必须把 UserHolder 的 userId 传给 service,
 *       controller 返回 Result.success</li>
 * </ul>
 */
class MemoryResetControllerTest {

    private UserMemoryService service;
    private MemoryResetController controller;

    @BeforeEach
    void setUp() {
        service = mock(UserMemoryService.class);
        // UserHolder 是 static 工具类,这里 new 一个实例只是为了满足构造签名
        controller = new MemoryResetController(service, new UserHolder(), new SimpleMeterRegistry());
        // ThreadLocal 准备:把当前线程的 user 设为 42L
        UserDTO user = new UserDTO();
        user.setId(42L);
        user.setUsername("alice");
        user.setRole("USER");
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void resetIdempotent() {
        // 连续 reset 两次都不抛
        Result first = controller.reset();
        Result second = controller.reset();

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(Integer.valueOf(1), first.getCode());
        assertEquals(Integer.valueOf(1), second.getCode());
        // service.reset 必须被调用两次,且都是同一个 userId
        verify(service, times(2)).reset(eq(42L));
    }

    @Test
    void resetReturnsAudit() {
        Result result = controller.reset();

        assertNotNull(result);
        // B3 step5 要求 reset 真正到 service(里面会写审计),不能用 doNothing 走空路径
        verify(service, times(1)).reset(eq(42L));
        // Result 包装 — code=1,data=null(Void)
        assertEquals(Integer.valueOf(1), result.getCode());
    }
}
