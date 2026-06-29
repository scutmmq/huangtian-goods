package com.scutmmq.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.ai.entity.AiRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiRunMapper extends BaseMapper<AiRun> {

    /**
     * 取某会话最近一条 Run（按 created_at desc，id desc 兜底）。
     * 没数据时返回 null。
     */
    @Select("SELECT * FROM ai_run WHERE session_id = #{sessionId} ORDER BY created_at DESC, id DESC LIMIT 1")
    AiRun selectLatestBySessionId(@Param("sessionId") String sessionId);
}
