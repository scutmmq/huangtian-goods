package com.scutmmq.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.ai.entity.AiActionDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiActionDraftMapper extends BaseMapper<AiActionDraft> {

    /**
     * 按 assistant_message_id 取草稿。
     * 一个 assistant 消息理论最多挂一条草稿（生成结束时 persistDraftIfPresent 一次）；
     * 用 LIMIT 1 兜底历史脏数据。
     */
    @Select("SELECT * FROM ai_action_draft WHERE assistant_message_id = #{assistantMessageId} ORDER BY created_at DESC, id DESC LIMIT 1")
    AiActionDraft selectByAssistantMessageId(@Param("assistantMessageId") Long assistantMessageId);
}
