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
import com.scutmmq.utils.UserHolder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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

    private final RedissonClient redissonClient;
    @Override
    public Result paid(PayDTO payDTO) {

        log.info("payDTO:{}",payDTO);

        Long orderId = payDTO.getOrderId();
        Long currentUserId = UserHolder.getUser().getId();

        // 支付锁：防止同一订单并发重复提交支付
        RLock lock = redissonClient.getLock("lock:pay:" + orderId);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.error("支付处理异常，请重试");
        }
        if(!locked){
            return Result.error("支付处理中，请勿重复提交");
        }
        try {
            final Orders order = orderService.getById(orderId);
            if(order == null){
                return Result.error("订单不存在");
            }
            // 归属校验：只能支付本人的订单
            if(!Objects.equals(order.getUserId(), currentUserId)){
                throw new BusinessException("无权操作该订单");
            }
            // 幂等：已支付直接返回成功，避免重复扣减库存
            if(order.getStatus() == OrderStatus.PAID){
                log.info("订单{}已支付，幂等返回成功", orderId);
                return Result.success(true);
            }
            // 状态校验：只有待支付订单可以支付
            if(order.getStatus() != OrderStatus.PENDING){
                return Result.error("订单当前状态不可支付");
            }
            // 金额校验：防止前端篡改支付金额
            if(order.getTotalAmount().compareTo(payDTO.getAmount()) != 0){
                throw new BusinessException("支付金额与订单金额不一致");
            }
            return doPay(payDTO, order);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 实际支付处理：解除预占、扣减库存、更新订单状态、通知商家。
     * 由 paid() 在持有支付锁、完成归属/幂等/金额校验后调用。
     */
    private Result doPay(PayDTO payDTO, Orders order){

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
