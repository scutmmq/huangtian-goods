package com.scutmmq.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ShipType {
    SF(0,"顺丰","SF_"),
    YD(1,"韵达","YD_"),
    YT(2,"圆通","YT_"),
    ZT(3,"中通","ZT_"),
    JT(4,"极兔","JT_"),
    CNYZ(5,"中国","CNYZ_");

    @EnumValue
    private final Integer value;
    @JsonValue
    private final String desc;
    private final String prefix;
}
