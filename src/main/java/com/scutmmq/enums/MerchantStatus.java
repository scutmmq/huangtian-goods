package com.scutmmq.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MerchantStatus {
    PENDING(0, "待审核"),
    NORMAL(1, "正常"),
    DISABLED(2, "禁用"),
    REJECTED(3, "审核失败");

    @EnumValue // MyBatis-Plus注解，映射数据库值
    private final int code;
    @JsonValue
    private final String desc;
}
