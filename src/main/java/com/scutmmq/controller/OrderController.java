package com.scutmmq.controller;

import com.scutmmq.dto.*;
import com.scutmmq.entity.Result;
import com.scutmmq.service.OrderItemsService;
import com.scutmmq.service.OrderService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@AllArgsConstructor
public class OrderController {

    private final OrderService orderService;

    private final OrderItemsService orderItemsService;
    /**
     * 添加订单
     * @param ordersDTO 添加订单DTO
     * @return null
     */
    @PostMapping
    public Result addOrder(@RequestBody OrdersDTO ordersDTO){
        return  orderService.addOrder(ordersDTO);
    }

    /**
     * 获取用户所有订单
     * @param status 订单状态
     * @return pageResult
     */
    @GetMapping
    public Result getOrders(
            @RequestParam(value = "status",required = false)String status){
     return orderService.getUserOrders(status);
    }

    @PostMapping("/cancel")
    public Result cancelOrder(@RequestBody ReturnApplyDTO dto){
        return  orderService.cancelOrder(dto);
    }

    /**
     * 根据订单id查询订单表项
     * @param orderId 订单id
     * @return orderItemsList
     */
    @GetMapping("/{orderId}")
    public Result getOrderItems(@PathVariable(value = "orderId")Long orderId){
        return Result.success(orderItemsService.getItemsByOrderId(orderId));
    }


    /**
     * 用户确认收获
     * @param orderId 订单id
     * @return null
     */
    @PutMapping("/{orderId}/confirm")
    public Result confirmOrder(@PathVariable(value = "orderId")Long orderId){
        return orderService.confirmOrder(orderId);
    }

    /**
     * 商家确认发货
     * @param shipDTO 发货表单
     * @return null
     */
    @PostMapping("/ship")
    public Result shipProducts(@RequestBody ShipDTO shipDTO){
        return orderService.ship(shipDTO);
    }

    @GetMapping("/merchant")
    public Result getMerchantOrders(@RequestParam(value = "status",required = false)String status){
        return orderService.getMerchantOrder(status);
    }

    /**
     * 商家同意退货
     * @param dto 同意申请表单
     * @return null
     */
    @PostMapping("/approve")
    public Result approveReturn(@RequestBody ApproveReturnDTO dto){
        return orderService.approveReturn(dto);
    }

    @PostMapping("/reject")
    public Result rejectReturn(@RequestBody RejectReturnDTO dto){
        return orderService.rejectReturn(dto);
    }



}
