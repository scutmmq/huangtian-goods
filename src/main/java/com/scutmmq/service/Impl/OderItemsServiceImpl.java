package com.scutmmq.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.entity.OrderItems;
import com.scutmmq.entity.Result;
import com.scutmmq.mapper.OrderItemsMapper;
import com.scutmmq.service.OrderItemsService;
import com.scutmmq.vo.OrderItemsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OderItemsServiceImpl extends ServiceImpl<OrderItemsMapper, OrderItems> implements OrderItemsService  {

    private final OrderItemsMapper orderItemsMapper;

    @Override
    public Result getItemsByOrderId(Long orderId) {
        List<OrderItemsVO> orderItemsVOS = orderItemsMapper.getItemsByOrderId(orderId);
        return Result.success(orderItemsVOS);
    }
}
