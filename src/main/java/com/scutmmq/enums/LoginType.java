package com.scutmmq.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum LoginType {

    USERNAME(0,"用户名登录"),
    EMAIL(1,"邮箱登录"),
    PHONE(2,"电话号码登录");

    @EnumValue
    private final int value;
    private final String desc;

    LoginType(int value,String desc){
        this.value=value;
        this.desc=desc;
    }
}
