package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.scutmmq.enums.MerchantStatus;
import com.scutmmq.enums.MerchantType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家表实体类
 */
@Data
@TableName("merchant") // 关联数据库表名
public class Merchant {

    /**
     * 商家ID
     */
    @TableId(type = IdType.AUTO) // 自增主键
    private Long id;

    /**
     * 商家名称
     */
    private String name;

    /**
     * 商家描述
     */
    private String description;

    /**
     * 商家Logo URL
     */
    private String logoUrl;

    /**
     * 联系人
     */
    private String contactPerson;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 商家地址
     */
    private String address;

    /**
     * 营业执照号
     */
    private String businessLicense;

    /**
     * 商家类型: 1-个人, 2-企业
     */
    private MerchantType merchantType;

    /**
     * 状态: 0-待审核, 1-正常, 2-禁用, 3-审核失败
     */
    private MerchantStatus status;

    /**
     * 商家评分
     */
    private BigDecimal rating;

    /**
     * 商家评论数
     */
    private Integer ratingCount;

    /**
     * 总销量
     */
    private Integer totalSales;

    /**
     * 是否激活
     */
    private Integer isActive;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
