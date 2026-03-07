package com.scutmmq.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PaymentStatus {
    PENDING("pending", "待支付"),
    PAID("paid", "已支付"),
    FAILED("failed", "支付失败"),
    REFUNDED("refunded", "已退款"),
    CANCELLED("cancelled","已取消支付");

    @EnumValue
    private final String code;
    @JsonValue
    private final String desc;
}
