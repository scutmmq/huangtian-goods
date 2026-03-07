package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车与用户id绑定
 */
@Data
@TableName("carts")
public class Carts {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
