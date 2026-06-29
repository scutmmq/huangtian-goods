package com.scutmmq.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.ai.entity.AiStreamEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiStreamEventMapper extends BaseMapper<AiStreamEvent> {

    /**
     * 拉取某个 session 在 afterId 之后的事件，按 id 升序。
     * 用于 SSE 重连时根据 Last-Event-ID 补齐。
     * afterId 为 null 时退化为 0，返回该 session 的全部事件。
     * 上限 1000 条，避免单次重连把内存吃爆。
     */
    @Select("SELECT id, run_id, session_id, message_id, user_id, type, payload_json, created_at " +
            "FROM ai_stream_event " +
            "WHERE session_id = #{sessionId} AND id > #{afterId} " +
            "ORDER BY id ASC " +
            "LIMIT 1000")
    List<AiStreamEvent> selectAfterId(@Param("sessionId") String sessionId,
                                       @Param("afterId") Long afterId);

    /**
     * 查询某 session 最新的事件 id（无事件时返回 null）。
     * 用于 SSE 连接前快照最新 id，避免 replay 与 live broadcast 重复发送同一事件。
     */
    @Select("SELECT id FROM ai_stream_event WHERE session_id = #{sessionId} " +
            "ORDER BY id DESC LIMIT 1")
    Long selectLatestId(@Param("sessionId") String sessionId);
}