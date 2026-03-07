package com.scutmmq.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ReviewType {
    ACCEPT(0,"通过"),
    REJECT(1,"不通过");

    @EnumValue
    private final Integer value;
    private final String desc;

}
