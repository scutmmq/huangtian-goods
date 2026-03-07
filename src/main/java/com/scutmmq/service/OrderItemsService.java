package com.scutmmq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.entity.OrderItems;

public interface OrderItemsService extends IService<OrderItems> {
    Object getItemsByOrderId(Long orderId);
}
