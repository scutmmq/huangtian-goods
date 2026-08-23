package com.scutmmq.ai.mapper;

import com.scutmmq.ai.entity.UserMemoryAuditEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * B3 step9: 用户记忆审计表 mapper。
 *
 * <p>不继承 {@code BaseMapper&lt;UserMemoryAuditEntity&gt;}(per Task 1 reviewer 决定):
 * 该表 PK 是复合键 {@code (id, created_at)},MyBatis-Plus 单列 @TableId 路径会误用,
 * 故只暴露显式 @Insert;id 与 createdAt 由 DB 自增 / DEFAULT CURRENT_TIMESTAMP 填充。
 *
 * <p>写路径:同步 insert(主流程调用,失败仅 log 不抛,见 AuditService);
 * 清理路径:异步 UPDATE(由 AuditService.purgeAuditAsync + JdbcTemplate 直接执行,不经 mapper)。
 */
@Mapper
public interface UserMemoryAuditMapper {

    /**
     * 插入 1 行审计记录。
     * id / created_at 由 DB 自动生成,Java 端无需指定。
     */
    @Insert("INSERT INTO ai_user_memory_audit "
            + "(user_id, action, fields_changed, triggered_by, token_estimate, "
            + " field_dropped, actor_ip, request_id, error_message, expires_at) "
            + "VALUES "
            + "(#{userId}, #{action}, #{fieldsChanged}, #{triggeredBy}, #{tokenEstimate}, "
            + " #{fieldDropped}, #{actorIp}, #{requestId}, #{errorMessage}, #{expiresAt})")
    void insert(UserMemoryAuditEntity entity);
}