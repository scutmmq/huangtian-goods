package com.scutmmq.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OperationType {

    INSERT("增加"),
    DELETE("删除"),
    UPDATE("修改"),
    SELECT("查询"),
    NONE("未知");

    @EnumValue
    private final String value;
}
