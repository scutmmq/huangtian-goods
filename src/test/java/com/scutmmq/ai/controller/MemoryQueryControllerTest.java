package com.scutmmq.ai.controller;

import com.scutmmq.ai.dto.UserMemoryOverviewVO;
import com.scutmmq.ai.service.UserMemoryService;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.entity.Result;
import com.scutmmq.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B3 step8: MemoryQueryController 单元测试。
 *
 * <p>覆盖两个不变式:
 * <ul>
 *   <li>getMemoryReturnsVO — controller 把 service 的 VO 用 Result.success 包出去</li>
 *   <li>getMemoryRespectsUserIsolation — controller 不会"借"别的用户的 id,
 *       userHolder 切换后 service 必须用新 userId</li>
 * </ul>
 */
class MemoryQueryControllerTest {

    private UserMemoryService service;
    private MemoryQueryController controller;

    @BeforeEach
    void setUp() {
        service = mock(UserMemoryService.class);
        controller = new MemoryQueryController(service);

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
    void getMemoryReturnsVO() {
        UserMemoryOverviewVO stub = new UserMemoryOverviewVO(
                true, true,
                Instant.parse("2026-08-22T10:00:00Z"),
                7,
                "记忆总结",
                List.of("身份档案", "偏好画像"),
                List.of("默认地址", "偏好类目"),
                "AI 助手个性化");
        when(service.buildOverview(42L)).thenReturn(stub);

        Result result = controller.get();

        assertNotNull(result);
        assertEquals(Integer.valueOf(1), result.getCode());
        assertNotNull(result.getData(), "data should contain the VO");
        assertEquals(stub, result.getData());
        UserMemoryOverviewVO data = (UserMemoryOverviewVO) result.getData();
        assertTrue(data.isHasIdentity());
        assertEquals(Integer.valueOf(7), data.getVersion());
        assertEquals(2, data.getCategoryNames().size());
    }

    @Test
    void getMemoryRespectsUserIsolation() {
        // mock 不区分 userId,返回简单 stub 就行 — 我们关心传入 userId
        when(service.buildOverview(anyLong())).thenReturn(
                new UserMemoryOverviewVO(false, false, null, 0, "stub",
                        List.of(), List.of(), "policy"));

        // 用户 A 查询
        Result first = controller.get();
        // 切换到用户 B
        UserDTO b = new UserDTO();
        b.setId(99L);
        b.setUsername("bob");
        UserHolder.saveUser(b);
        Result second = controller.get();

        assertNotNull(first);
        assertNotNull(second);

        ArgumentCaptor<Long> idCap = ArgumentCaptor.forClass(Long.class);
        verify(service, org.mockito.Mockito.times(2)).buildOverview(idCap.capture());
        // userId 必须按调用顺序分别是 42L 和 99L — 不能串台
        java.util.List<Long> ids = idCap.getAllValues();
        assertEquals(Long.valueOf(42L), ids.get(0), "first call must use thread-local user 42");
        assertEquals(Long.valueOf(99L), ids.get(1), "second call must use thread-local user 99");
    }
}
