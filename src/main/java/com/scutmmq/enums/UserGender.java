package com.scutmmq.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserGender {
    FEMALE(0,"女"),
    MALE(1,"男"),
    OTHERS(2,"其它性别");

    @EnumValue
    @JsonValue
    private final int value;
    private final String desc;
}
