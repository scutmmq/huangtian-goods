package com.scutmmq.vo;

import lombok.Data;

/**
 * 分类 DTO（包含子分类嵌套结构，用于接口响应）
 */
@Data
public class CategoryVO {
    private Long id;
    private String name;
    private Long parentId;
    private Integer level;
    private Integer sort;
    private String icon;
    private String description;

    private Integer status;
}
