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

    /**
     * 反序列化：同时兼容
     *  - 数字编码 1 / 2（前端 el-select 传的值）
     *  - 数字字符串 "1" / "2"
     *  - 英文名 INDIVIDUAL / PERSONAL / ENTERPRISE / COMPANY（AI 助手可能传 PERSONAL）
     *  - 中文描述 个人 / 企业（@JsonValue 序列化后的值，保证可往返）
     * 用 Object 形参让 Jackson 把原始 JSON 值（数字→Integer，字符串→String）原样传入。
     */
    @JsonCreator
    public static MerchantType fromValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            return fromCode(((Number) raw).intValue());
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.matches("-?\\d+")) {
            return fromCode(Integer.parseInt(s));
        }
        switch (s.toUpperCase()) {
            case "INDIVIDUAL":
            case "PERSONAL":
            case "个人":
                return INDIVIDUAL;
            case "ENTERPRISE":
            case "COMPANY":
            case "企业":
                return ENTERPRISE;
            default:
                throw new IllegalArgumentException("无效的商家类型：" + raw);
        }
    }

    private static MerchantType fromCode(int code) {
        for (MerchantType type : MerchantType.values()) {
            if (type.value == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的商家类型：" + code);
    }
}
