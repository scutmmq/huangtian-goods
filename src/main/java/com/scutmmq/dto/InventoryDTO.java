package com.scutmmq.dto;

import com.scutmmq.enums.ChangeType;
import lombok.Data;

/**
 * 更改库存的请求参数
 */
@Data
public class InventoryDTO {
    private Long productId;
    private Integer changeQuantity;
    private String reason;
    private ChangeType changeType;
    /**
     * 商家自己修改库存时为空
     */
    private Long referenceId;
}
