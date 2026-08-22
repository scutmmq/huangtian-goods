package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    // C9: 多参数 mapper 必须显式 @Param,否则 XML 引用 #{level}/#{parentId} 取不到值
    List<Category> getCategories(@Param("level") Integer level, @Param("parentId") Integer parentId);
}
