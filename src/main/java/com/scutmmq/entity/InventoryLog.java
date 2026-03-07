package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.scutmmq.enums.ChangeType;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 库存变更记录表实体类
 * 对应表：inventory_logs
 */
@Data
@TableName("inventory_logs")
public class InventoryLog {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商品ID
     */
    @TableField("product_id")
    private Long productId;

    /**
     * 变更类型（in:入库, out:出库, adjust:调整）
     */
    @TableField("change_type")
    private ChangeType changeType;

    /**
     * 变更数量（正数表示增加，负数表示减少）
     */
    @TableField("change_quantity")
    private Integer changeQuantity;

    /**
     * 变更后库存
     */
    @TableField("current_quantity")
    private Integer currentQuantity;

    /**
     * 变更原因
     */
    @TableField("reason")
    private String reason;

    /**
     * 关联ID（订单ID等）
     */
    @TableField("reference_id")
    private Long referenceId;

    /**
     * 操作人ID
     */
    @TableField("operator_id")
    private Long operatorId;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;
}
