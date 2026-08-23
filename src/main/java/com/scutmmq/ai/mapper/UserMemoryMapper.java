package com.scutmmq.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.ai.entity.UserMemoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * B3 step5: 用户长期记忆主表 mapper。
 *
 * <p>继承 {@link BaseMapper}(因为 {@link UserMemoryEntity} 是单列 PK @TableId(INPUT))
 * + 7 个定制方法处理"列级更新 + 乐观锁"语义:
 *
 * <ul>
 *   <li>{@link #updateIdentity} / {@link #updatePreference} — 写单列 JSON,期望 version(MP 自动 +1)</li>
 *   <li>{@link #bumpComputeSeq} — +1 compute_seq 并同步版本号</li>
 *   <li>{@link #incrementFailCount} — 失败计数 +1 (无 version 校验)</li>
 *   <li>{@link #markDisabled} — recompute_status=0 (熔断,无 version 校验)</li>
 *   <li>{@link #resetMemory} — 把 JSON 字段清空 + version+1</li>
 *   <li>{@link #getComputeSeq} — 渲染路径 seq 校验</li>
 * </ul>
 *
 * <p>所有 UPDATE 返回受影响行数;0 行时调用方决定是重试(@Version 冲突)还是 fail_count++。
 */
@Mapper
public interface UserMemoryMapper extends BaseMapper<UserMemoryEntity> {

    @Update("UPDATE ai_user_memory SET identity_json=#{identityJson}, "
            + "version=version+1, updated_at=NOW() "
            + "WHERE user_id=#{userId} AND version=#{version}")
    int updateIdentity(@Param("userId") Long userId,
                       @Param("identityJson") String identityJson,
                       @Param("version") int version);

    @Update("UPDATE ai_user_memory SET preference_json=#{preferenceJson}, "
            + "version=version+1, updated_at=NOW() "
            + "WHERE user_id=#{userId} AND version=#{version}")
    int updatePreference(@Param("userId") Long userId,
                         @Param("preferenceJson") String preferenceJson,
                         @Param("version") int version);

    @Update("UPDATE ai_user_memory SET compute_seq=compute_seq+1, "
            + "version=version+1, updated_at=NOW() "
            + "WHERE user_id=#{userId} AND version=#{version}")
    int bumpComputeSeq(@Param("userId") Long userId,
                       @Param("version") int version);

    @Update("UPDATE ai_user_memory SET fail_count=fail_count+1 "
            + "WHERE user_id=#{userId}")
    int incrementFailCount(@Param("userId") Long userId);

    @Update("UPDATE ai_user_memory SET recompute_status=0 "
            + "WHERE user_id=#{userId}")
    int markDisabled(@Param("userId") Long userId);

    @Update("UPDATE ai_user_memory SET identity_json='{}', preference_json='{}', "
            + "version=version+1, updated_at=NOW() "
            + "WHERE user_id=#{userId}")
    int resetMemory(@Param("userId") Long userId);

    @Select("SELECT compute_seq FROM ai_user_memory WHERE user_id=#{userId}")
    Long getComputeSeq(@Param("userId") Long userId);

    /**
     * B3 step10:统计 {@code recompute_status=0}(DISABLED)用户数,用于
     * {@code ai_memory_fail_users} gauge 初值填充与定期刷新。
     */
    @Select("SELECT COUNT(*) FROM ai_user_memory WHERE recompute_status=0")
    Long countDisabledUsers();

    /**
     * B3 step7: cron 游标扫描 — 找"陈旧且需重算"的用户 ID(见 spec §3.5,NOT EXISTS 避免 N+1)。
     *
     * <p>过滤条件:
     * <ul>
     *   <li>{@code recompute_status=1} — 未熔断</li>
     *   <li>{@code computed_at < cutoff} — 7 天内未被重算过</li>
     *   <li>{@code user_id > lastUserId} — 游标分批,保证不重复不漏扫</li>
     *   <li>{@code NOT EXISTS ... action='RESET' ...} — DSR-excluded 用户跳过
     *       (在 retention 天内主动 reset 过的不重算,避免画像污染)</li>
     * </ul>
     *
     * @param lastUserId  上一批最后一个 user_id(首次传 0)
     * @param cutoffSeconds 阈值秒(epoch seconds),{@code computed_at < FROM_UNIXTIME(cutoffSeconds)}
     * @param batchSize  本批上限(LIMIT)
     */
    @Select("SELECT user_id FROM ai_user_memory m "
            + "WHERE m.recompute_status = 1 "
            + "AND m.computed_at < FROM_UNIXTIME(#{cutoffSeconds}) "
            + "AND m.user_id > #{lastUserId} "
            + "AND NOT EXISTS ( "
            + "  SELECT 1 FROM ai_user_memory_audit a "
            + "  WHERE a.user_id = m.user_id AND a.action = 'RESET' "
            + "  AND a.created_at > DATE_SUB(NOW(), INTERVAL #{resetRetentionDays} DAY) "
            + ") "
            + "ORDER BY m.user_id "
            + "LIMIT #{batchSize}")
    List<Long> findStaleUserIds(@Param("lastUserId") long lastUserId,
                                @Param("cutoffSeconds") long cutoffSeconds,
                                @Param("resetRetentionDays") int resetRetentionDays,
                                @Param("batchSize") int batchSize);
}
