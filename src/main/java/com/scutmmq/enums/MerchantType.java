package com.scutmmq.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MerchantType {
    INDIVIDUAL(1,"个人"),
    ENTERPRISE(2,"企业");

    @EnumValue
    private final Integer value;
    @JsonValue
    private final String desc;

    // 核心：添加反序列化方法，按value字段解析前端传的数字
    @JsonCreator
    public static MerchantType fromValue(Integer value) {
        for (MerchantType type : MerchantType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        // 无匹配值时抛异常，也可返回默认值（根据业务调整）
        throw new IllegalArgumentException("无效的商家类型：" + value);
    }
}
