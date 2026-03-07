package com.scutmmq.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.vo.CategoryVO;
import com.scutmmq.entity.Category;
import com.scutmmq.entity.Result;
import com.scutmmq.mapper.CategoryMapper;
import com.scutmmq.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public Result getCategories(Integer level,Integer parentId) {
        List<Category> list = lambdaQuery()
                .eq(parentId != null, Category::getParentId, parentId)
                .eq(level != null, Category::getLevel, level)
                .list();
        List<CategoryVO> dtoList = list.stream().map(category -> BeanUtil.copyProperties(category, CategoryVO.class)).toList();

        return Result.success(dtoList);
    }
}
