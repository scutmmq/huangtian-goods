package com.scutmmq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.entity.OrderItems;
import com.scutmmq.entity.Result;

public interface OrderItemsService extends IService<OrderItems> {
    Result getItemsByOrderId(Long orderId);
}
