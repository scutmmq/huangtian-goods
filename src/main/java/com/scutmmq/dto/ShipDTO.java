package com.scutmmq.dto;

import com.scutmmq.enums.ShipType;
import lombok.Data;

/**
 * 发货表单
 */
@Data
public class ShipDTO {
    private Long orderId;
    private Long addressId; // 发货地址Id
    private ShipType shipType; // 发货方式
}
