package com.scutmmq.ai.service;

/**
 * B3 长期记忆构建器接口。
 *
 * <p>负责把分散在 orders / favorites / addresses / profile 等表里的数据
 * 聚合成 identity / preference 两个 JSON 串。
 *
 * <p>Task 5 仅声明接口 + 在 Test 里 mock;
 * Task 4 会注入真实实现(读 DB + 调 NLP / 标签服务)。
 */
public interface UserMemoryBuilder {

    /**
     * 计算用户身份画像(年龄区间、性别、注册时长、默认地址等)。
     * 失败应抛异常,由调用方决定是否降级为空。
     */
    UserMemorySnapshot computeIdentity(Long userId);

    /**
     * 计算用户偏好画像(价格区间、偏好类目、偏好商家、退货率等)。
     */
    UserMemorySnapshot computePreference(Long userId);

    /**
     * 把 snapshot 序列化成可注入 prompt 的 markdown 文本。
     * 空 snapshot(identity={} 且 preference={})应返回空串。
     */
    String renderForPrompt(UserMemorySnapshot snapshot);
}
