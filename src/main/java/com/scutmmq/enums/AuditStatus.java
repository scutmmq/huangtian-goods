package com.scutmmq.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AuditStatus {
    PENDING(0,"待审核"),
    APPROVE(1,"已批准"),
    REJECT(2,"已拒绝");

    @EnumValue
    private final Integer value;
    @JsonValue
    private final String desc;
}
