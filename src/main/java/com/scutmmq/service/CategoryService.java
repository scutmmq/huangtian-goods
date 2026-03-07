package com.scutmmq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.entity.Category;
import com.scutmmq.entity.Result;
import org.springframework.stereotype.Service;

public interface CategoryService extends IService<Category> {
    Result getCategories(Integer level,Integer parentId);
}
