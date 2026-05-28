package com.scutmmq.ai.util;

import com.scutmmq.dto.UserDTO;
import com.scutmmq.utils.UserHolder;

import java.util.function.Supplier;

/**
 * 在 AI 工具执行时保证 UserHolder 中存有当前用户。
 * 商城已有的 RefreshInterceptor 在每次 HTTP 请求开始时设置 UserHolder，
 * 但工具执行可能跨方法调用或线程切换，这里负责显式保存/恢复。
 */
public final class MallUserContextExecutor {

    private MallUserContextExecutor() {
    }

    public static <T> T runAs(UserDTO user, Supplier<T> action) {
        UserDTO previous = UserHolder.getUser();
        try {
            UserHolder.saveUser(user);
            return action.get();
        } finally {
            if (previous != null) {
                UserHolder.saveUser(previous);
            } else {
                UserHolder.removeUser();
            }
        }
    }

    public static void runAs(UserDTO user, Runnable action) {
        runAs(user, () -> {
            action.run();
            return null;
        });
    }
}
