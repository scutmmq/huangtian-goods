package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_address")
public class UserAddress {

    /**
     * 地址ID (自增主键)
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID (关联用户表)
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 收件人
     */
    @TableField("recipient")
    private String recipient;

    /**
     * 收件电话
     */
    @TableField("phone")
    private String phone;

    /**
     * 省份
     */
    @TableField("province")
    private String province;

    /**
     * 城市
     */
    @TableField("city")
    private String city;

    /**
     * 区县 (默认空字符串)
     */
    @TableField("district")
    private String district;

    /**
     * 详细地址
     */
    @TableField("detail")
    private String detail;

    /**
     * 邮政编码 (默认空字符串)
     */
    @TableField("postal_code")
    private String postalCode;

    /**
     * 是否默认地址 (0-非默认，1-默认，默认值0)
     */
    @TableField("is_default")
    private Integer isDefault;

    /**
     * 创建时间 (默认当前时间)
     */
    @TableField(value = "created_time",fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间 (默认当前时间，更新时自动刷新)
     */
    @TableField(value = "updated_time",fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
