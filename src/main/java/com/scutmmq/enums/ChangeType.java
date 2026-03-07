package com.scutmmq.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ChangeType {
    /**
     * 变更类型（in:入库, out:出库, adjust:调整）
     */
    IN("in","入库"),
    OUT("out","出库"),
    ADJUST("adjust","调整");

    @EnumValue
    private final String value;
    @JsonValue
    private final String desc;

}
