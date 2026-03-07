package com.scutmmq.controller;

import cn.hutool.core.bean.BeanUtil;
import com.scutmmq.vo.CategoryVO;
import com.scutmmq.entity.Result;
import com.scutmmq.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private  final CategoryService CategoryService;

    /**
     * 根据父种类的id和子类的等级查询分类列表
     * 使用示例：
     * 想展示所有一级分类（带二级子分类）：
     * &#064;request  /categories?parentId=0&level=1
     * 响应会返回所有一级分类，每个一级分类的 children 字段包含它的二级分类。
     * 想展示某个二级分类下的三级分类：
     * 请求：/categories?parentId=2&level=3（假设二级分类 ID=2）
     * 响应返回该二级分类下的所有三级分类
     * @param level 产品分类等级  1 2 3 级
     * @return 指定等级的产品种类列表
     */
    @GetMapping
    public Result getCategories(@RequestParam(value = "level", required = false) Integer level, @RequestParam(value = "parentId",required = false) Integer parentId){
        return CategoryService.getCategories(level,parentId);
    }

    /**
     * 根据Id查询分类详情
     * @param categoryId
     * @return
     */
    @GetMapping("/{categoryId}")
    public Result getById(@PathVariable Long categoryId){
        return Result.success(BeanUtil.copyProperties(CategoryService.getById(categoryId), CategoryVO.class));
    }
}
