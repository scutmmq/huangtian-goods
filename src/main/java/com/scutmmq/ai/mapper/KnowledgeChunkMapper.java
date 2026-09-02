package com.scutmmq.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.ai.entity.KnowledgeChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识库切片 Mapper 接口。
 * 基于 MyBatis-Plus 提供基本的 CRUD 操作，并扩展定制化的向量检索候选项查询与批量维护操作。
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkEntity> {

    /**
     * 查询所有启用的知识库切片（用于在内存/向量存储引擎中执行向量余弦相似度计算）。
     *
     * @param sourceType 可选的源类型（为 null 时查询所有类型）
     * @return 启用的知识库切片列表
     */
    @Select("<script>" +
            "SELECT id, source_type, source_id, chunk_index, title, content, metadata_json, embedding_json, status, created_at, updated_at " +
            "FROM ai_knowledge_chunk " +
            "WHERE status = 1 " +
            "<if test='sourceType != null and sourceType != \"\"'>" +
            "  AND source_type = #{sourceType} " +
            "</if>" +
            "</script>")
    List<KnowledgeChunkEntity> selectActiveChunks(@Param("sourceType") String sourceType);

    /**
     * 根据知识来源（如特定商品 ID 或店铺 ID）批量逻辑删除或物理删除旧分块。
     * 用于数据增量同步时保持幂等（先删后插）。
     *
     * @param sourceType 知识源类型
     * @param sourceId   知识源实体 ID
     * @return 受影响行数
     */
    @Update("DELETE FROM ai_knowledge_chunk WHERE source_type = #{sourceType} AND source_id = #{sourceId}")
    int deleteBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);
}
