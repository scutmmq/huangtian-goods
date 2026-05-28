package com.scutmmq.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_action_draft")
public class AiActionDraft {

    @TableId
    private String id;

    private Long userId;

    private String sessionId;

    /**
     * CREATE_ORDER / ADD_CART_ITEM / REGISTER_MERCHANT / UPDATE_USER_PROFILE / UPDATE_MERCHANT
     */
    private String actionType;

    private String title;

    private String summary;

    /**
     * 完整 payload JSON，由后端在确认时再次校验，绝不直接信任前端传回的内容。
     */
    private String payloadJson;

    /**
     * PENDING / CONFIRMED / CANCELLED / EXPIRED / FAILED
     */
    private String status;

    private String resultJson;

    private String errorMessage;

    private LocalDateTime expiresAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
