package com.scutmmq.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.entity.OrderItems;
import com.scutmmq.entity.Orders;
import com.scutmmq.entity.Result;
import com.scutmmq.mapper.MerchantUserMapper;
import com.scutmmq.mapper.OrderItemsMapper;
import com.scutmmq.mapper.OrderMapper;
import com.scutmmq.service.OrderItemsService;
import com.scutmmq.utils.UserHolder;
import com.scutmmq.vo.OrderItemsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OderItemsServiceImpl extends ServiceImpl<OrderItemsMapper, OrderItems> implements OrderItemsService {

    private final OrderItemsMapper orderItemsMapper;
    private final OrderMapper orderMapper;
    private final MerchantUserMapper merchantUserMapper;

    @Override
    public Result getItemsByOrderId(Long orderId) {
        if (orderId == null) {
            return Result.error("订单ID不能为空");
        }
        Orders order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.error("订单不存在");
        }

        Long currentUserId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        if (currentUserId == null) {
            return Result.error("未登录");
        }

        Long myMerchantId = merchantUserMapper.getMerchantIdByUserId(currentUserId);
        boolean isBuyer = Objects.equals(order.getUserId(), currentUserId);
        boolean isSeller = myMerchantId != null && Objects.equals(order.getMerchantId(), myMerchantId);

        if (!isBuyer && !isSeller) {
            return Result.error("无权查看该订单详情");
        }

        List<OrderItemsVO> orderItemsVOS = orderItemsMapper.getItemsByOrderId(orderId);
        return Result.success(orderItemsVOS);
    }
}
