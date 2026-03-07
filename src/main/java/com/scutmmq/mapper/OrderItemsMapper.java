package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.entity.OrderItems;
import com.scutmmq.vo.OrderItemsVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderItemsMapper extends BaseMapper<OrderItems> {
    List<Long> getProductIdsByOrderId(Long orderId);

    List<OrderItemsVO> getItemsByOrderId(Long orderId);
}
