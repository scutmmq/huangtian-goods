package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.vo.CartsItemsVO;
import com.scutmmq.entity.Carts;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartMapper extends BaseMapper<Carts> {
    CartsItemsVO getCarts(Long userId);
}
