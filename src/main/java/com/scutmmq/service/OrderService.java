package com.scutmmq.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.dto.*;
import com.scutmmq.entity.Orders;
import com.scutmmq.entity.Result;

public interface OrderService extends IService<Orders> {
    Result addOrder(OrdersDTO ordersDTO);

    Result getUserOrders(String status);

    Result cancelOrder(ReturnApplyDTO dto);

    Result confirmOrder(Long orderId);

    Result ship(ShipDTO shipDTO);

    Result getMerchantOrder(String status);

    Result approveReturn(ApproveReturnDTO dto);

    Result rejectReturn(RejectReturnDTO dto);
}
