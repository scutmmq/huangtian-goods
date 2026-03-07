package com.scutmmq.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OrderStatus {

    PENDING("pending", "待支付"),
    PAID("paid", "已支付"),
    SHIPPED("shipped", "已发货"),
    DELIVERED("delivered", "已送达"),
    CANCELLED("cancelled", "已取消"),
    RETURN_APPLIED("return_applied","申请退货");

    @EnumValue
    private final String code;
    @JsonValue
    private final String desc;


}
