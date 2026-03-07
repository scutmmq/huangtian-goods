package com.scutmmq.service.Impl;

import com.scutmmq.dto.InventoryDTO;
import com.scutmmq.dto.PayDTO;
import com.scutmmq.entity.OrderItems;
import com.scutmmq.entity.Orders;
import com.scutmmq.entity.Result;
import com.scutmmq.enums.ChangeType;
import com.scutmmq.enums.OrderStatus;
import com.scutmmq.enums.PaymentStatus;
import com.scutmmq.exception.BusinessException;
import com.scutmmq.service.OrderItemsService;
import com.scutmmq.service.OrderService;
import com.scutmmq.service.PayService;
import com.scutmmq.service.ProductService;
import com.scutmmq.service.NotificationService;
import com.scutmmq.service.UserService;
import com.scutmmq.utils.RedisConstants;
import com.scutmmq.utils.RedisUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.scutmmq.utils.RedisConstants.ORDER_TIMEOUT_TRIGGER;

@Service
@Slf4j
@AllArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PayServiceImpl implements PayService {

    private final OrderItemsService orderItemsService;

    private final ProductService productService;

    private final  OrderService orderService;

    private final StringRedisTemplate redisTemplate;

    private final RedisUtils redisUtils;

    private final UserService userService;

    private final NotificationService notificationService;
    @Override
    public Result paid(PayDTO payDTO) {

        log.info("payDTO:{}",payDTO);

        // 解除预占库存
        String tempOrderId =(String) redisTemplate.opsForHash().get(
                RedisConstants.ORDER_ID_MAP_TO_TEMP_ID, payDTO.getOrderId().toString());

        // 更新库存
        final List<OrderItems> orderItemsList = orderItemsService.lambdaQuery().eq(OrderItems::getOrderId, payDTO.getOrderId()).list();
        // 更新库存
        for(OrderItems orderItem : orderItemsList){

            // 先删除预占，再同步更新 数据库和redis库存
            Long flag = redisUtils.CancelReserveStock(orderItem.getProductId(),tempOrderId,payDTO.getOrderId());
            log.info("flag:{}",flag);
            if(flag!=1L){
                throw new BusinessException("预占库存删除失败");
            }

            InventoryDTO inventoryDTO = new InventoryDTO();
            inventoryDTO.setProductId(orderItem.getProductId());
            inventoryDTO.setReason("用户购买");
            inventoryDTO.setChangeQuantity(-orderItem.getQuantity());
            inventoryDTO.setChangeType(ChangeType.OUT);
            inventoryDTO.setReferenceId(payDTO.getOrderId());
            final Result result = productService.modifyStockQuantity(inventoryDTO);
            if(result.getCode()!=1){
                throw  new BusinessException("库存更新失败:"+result.getMsg());
            }
        }
        // 更新库存成功

        // ......

        // 更新订单状态
        final boolean updated = orderService
                .lambdaUpdate()
                .set(Orders::getPaidTime, LocalDateTime.now())
                .set(Orders::getStatus, OrderStatus.PAID)
                .set(Orders::getPaymentStatus, PaymentStatus.PAID)
                .set(Orders::getPaymentMethod,payDTO.getPaymentMethod())
                .set(Orders::getOrderedTime,LocalDateTime.now())
                .eq(Orders::getId,payDTO.getOrderId())
                .eq(Orders::getTotalAmount,payDTO.getAmount())
                .eq(Orders::getStatus,OrderStatus.PENDING)
                .update();
        if(!updated){
            throw new RuntimeException("订单异常，请联系管理员");
        }

        // 状态由待支付转变，需要删除延时检测器的订单
        redisTemplate.opsForZSet().remove(ORDER_TIMEOUT_TRIGGER,payDTO.getOrderId().toString());


        // 支付成功
        log.info("支付成功!");

        // 向商家发送系统消息：提醒发货
        Orders order = orderService.getById(payDTO.getOrderId());
        log.info("订单信息: orderId={}, merchantId={}, userId={}", order.getId(), order.getMerchantId(), order.getUserId());
        
        String nick = userService.getById(order.getUserId()).getNickName();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String content = "系统消息：顾客" + nick + "在" + now + "支付了订单#" + order.getId() + "，请尽快发货";
        log.info("准备发送消息给商家{}: {}", order.getMerchantId(), content);
        
        notificationService.sendToMerchant(order.getMerchantId(), content);
        log.info("消息发送完成");

        return Result.success(true);
    }
}
