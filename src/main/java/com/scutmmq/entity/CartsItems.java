package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cart_items")
public class CartsItems {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;

    @TableField(value = "cart_id")
    private Long cartId;

    @TableField(value = "product_id")
    private Long productId;

    @TableField(value = "quantity")
    private Integer quantity;

    @TableField(value = "created_time",fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
