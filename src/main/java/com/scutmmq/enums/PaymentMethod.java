package com.scutmmq.enums;


import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PaymentMethod {
    ALIPAY(0,"支付宝支付"),
    WECHATPAY(1,"微信支付");

    @EnumValue
    private final Integer value;
    @JsonValue
    private final String desc;
}
