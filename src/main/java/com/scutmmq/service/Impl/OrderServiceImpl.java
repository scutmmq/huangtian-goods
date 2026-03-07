package com.scutmmq.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.anno.LogAnnotation;
import com.scutmmq.dto.*;
import com.scutmmq.entity.*;
import com.scutmmq.enums.*;
import com.scutmmq.exception.BusinessException;
import com.scutmmq.mapper.*;
import com.scutmmq.service.OrderItemsService;
import com.scutmmq.service.OrderService;
import com.scutmmq.service.ProductService;
import com.scutmmq.service.ReturnAuditService;
import com.scutmmq.service.NotificationService;
import com.scutmmq.service.UserService;
import com.scutmmq.utils.*;
import com.scutmmq.vo.AddOrdersVO;
import com.scutmmq.vo.MerchantOrdersVO;
import com.scutmmq.vo.OrderItemsVO;
import com.scutmmq.vo.UserOrdersVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.scutmmq.utils.RedisConstants.*;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
@Slf4j
@LogAnnotation(module = "订单管理")
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Orders> implements OrderService{

    private final OrderItemsService orderItemsService;

    private final ProductService productService;

    private final UserAddressMapper userAddressMapper;

    private final RedisIdWorker redisIdWorker;

    private final  OrderMapper orderMapper;

    private final MerchantUserMapper merchantUserMapper;

    private final ReturnAuditService auditService;

    private final OrderItemsMapper orderItemsMapper;

    private final RedissonClient redissonClient;

    private final RedisUtils redisUtils;

    private final StringRedisTemplate redisTemplate;

    private final NotificationService notificationService;

    private final UserService userService;

    private final MerchantMapper merchantMapper;
    @Override
    @LogAnnotation(type = OperationType.INSERT,description = "添加订单")
    public Result addOrder(OrdersDTO ordersDTO) {
        // 1.获取用户信息
        Long userId = UserHolder.getUser().getId();

        Long myMerchantId = merchantUserMapper.getMerchantIdByUserId(userId);

        // 2.获取订单购物商品的列表
        List<OrderItemsDTO> orderItemsDTOS = ordersDTO.getList();

        // 订单的临时id
        String tempOrderId = UUID.randomUUID().toString();

        // 3. 校验订单

        // 3.1 校验订单是否为空
        if(orderItemsDTOS==null||orderItemsDTOS.isEmpty()){
            return Result.error("订单不能为空!");
        }

        // 3.2 校验收获地址是否存在
        Long shippingAddressId = ordersDTO.getShippingAddressId();
        UserAddress shippingAddress = userAddressMapper.selectById(shippingAddressId);
        if (shippingAddress == null || !shippingAddress.getUserId().equals(userId)) {
            return Result.error("收货地址不存在或不属于当前用户");
        }

        // 3.3 校验库存是否充足
        List<OrderItems> orderItemsList = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO; // 订单总金额
        Long merchantId = null; // 假设订单中所有商品属于同一商家（若支持多商家需调整逻辑）


        for(OrderItemsDTO orderItemsDTO:orderItemsDTOS){

            // 查询商品
            Product product = productService.lambdaQuery().eq(Product::getId, orderItemsDTO.getProductId()).one();

            // 校验库存 lock+lua
            String key = LOCK_STOCK + orderItemsDTO.getProductId();
            final RLock lock = redissonClient.getLock(key);
            boolean isLock = false;

            try {
                if (merchantId == null ? (merchantId = product.getMerchantId()) == null : !merchantId.equals(product.getMerchantId())) {
                    throw  new BusinessException("暂不支持跨商家下单，请分开结算");
                }

                if(merchantId.equals(myMerchantId)){
                    throw new BusinessException("不能购买自己售卖的的商品");
                }

                isLock = lock.tryLock(3, TimeUnit.SECONDS);
                if(!isLock){
                    log.info("获取锁{}失败",key);
                    return Result.error("下单过忙，请重试");
                }
                log.info("获取锁{}成功",key);
                // TODO 调用LUA脚本查询库存，预占库存
                Long flag = redisUtils.ReserveStock(
                        orderItemsDTO.getProductId(), orderItemsDTO.getQuantity(), tempOrderId);
                if(flag != 1L){
                    throw  new BusinessException("非常抱歉，商品已被他人下单");
                }
            } catch (Exception e) {
                // 回退redis
                // 回退订单 此时没有映射表，orderId随意给
                rollBackReserveStock(orderItemsDTOS,tempOrderId,0L);
                throw new BusinessException(e.getMessage());
            } finally {
                if(isLock){
                    lock.unlock();
                    log.info("释放锁{}成功",key);
                }
            }
            log.info("订单商品预占库存校验成功");
            // 4 计算商品小计，累加总金额,生成订单项
            BigDecimal productPrice = product.getPrice(); // 下单时的单价快照
            Integer quantity = orderItemsDTO.getQuantity();
            BigDecimal subtotal = productPrice.multiply(new BigDecimal(quantity)); // 单价×数量

            // 5. 创建订单项实体类
            OrderItems orderItems = new OrderItems();
            orderItems.setProductId(product.getId());
            orderItems.setMerchantId(merchantId);
            orderItems.setQuantity(quantity);
            orderItems.setSubtotal(subtotal);
            orderItems.setProductName(product.getName());
            orderItems.setProductPrice(productPrice);
            // 注意： 还有orderId没有添加，需要在添加订单后获取
            orderItemsList.add(orderItems);

            // 计算总价格
            totalAmount = totalAmount.add(subtotal);
        }

        log.info("订单校验成功，开始计算金额生成订单");

        // 获取订单信息
        Orders orders = BeanUtil.copyProperties(ordersDTO,Orders.class);
        // 获取订单号
        long orderNumber = redisIdWorker.nextId(RedisConstants.SHOPPING_PREFIX);
        orders.setOrderNumber(String.valueOf(orderNumber));
        orders.setOrderedTime(LocalDateTime.now());
        orders.setMerchantId(merchantId);
        orders.setUserId(userId);
        orders.setStatus(OrderStatus.PENDING);// 生成订单，待支付
        orders.setTotalAmount(totalAmount);
        orders.setPaymentStatus(PaymentStatus.PENDING);
        orders.setRemark(ordersDTO.getRemark()); //订单备注

        // 6. 事务内保存订单和订单项（确保数据一致性）
        try {
            this.save(orders);
            final Long id = orders.getId();
            if(id==null){
                throw new BusinessException("订单添加失败");
            }

            // 将orderId 加入 tempOrderId map映射表
            redisTemplate.opsForHash().put(ORDER_ID_MAP_TO_TEMP_ID,String.valueOf(id),tempOrderId);

            // 将orderId 和 过期时间戳加入延时检测器 10分钟后过期
            redisTemplate.opsForZSet().add(ORDER_TIMEOUT_TRIGGER,String.valueOf(id),System.currentTimeMillis()+1000*60*10);

            orderItemsList.forEach(item -> item.setOrderId(id));
            final boolean saved = orderItemsService.saveBatch(orderItemsList);
            if(!saved) throw new BusinessException("订单添加失败");

            // 创建订单成功,设置

            // 返回信息给前端
            AddOrdersVO addOrdersVO = new AddOrdersVO();
            addOrdersVO.setOrderId(id);
            addOrdersVO.setOrderNumber(String.valueOf(orderNumber));
            addOrdersVO.setTotalAmount(totalAmount);
            addOrdersVO.setPaymentAmount(totalAmount);
            return Result.success(addOrdersVO);
        }catch (Exception e){
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 辅助函数，当途中报错时，transactional事务生效，redis的预占库存也需要回退
     * @param orderItemsDTOS 订单项
     * @param tempOrderId 订单的映射id
     * @param orderId 订单id
     */
    private void rollBackReserveStock(List<OrderItemsDTO> orderItemsDTOS, String tempOrderId, Long orderId) {
        for(OrderItemsDTO dto:orderItemsDTOS){
            redisUtils.rollBackReserveStock(dto.getProductId(),tempOrderId,orderId);
        }
    }

    @Override
    @LogAnnotation(type = OperationType.SELECT,description = "查询用户订单")
    public Result getUserOrders(String status) {

        Long userId = UserHolder.getUser().getId();

        List<UserOrdersVO> userOrdersVOList = orderMapper.getUserOrders(status,userId);


        return Result.success(userOrdersVOList);
    }

    @Override
    @LogAnnotation(type = OperationType.UPDATE,description = "确认收货")
    public Result confirmOrder(Long orderId) {
        final Orders order = getById(orderId);
        if(order.getStatus()==OrderStatus.SHIPPED){
            order.setStatus(OrderStatus.DELIVERED);
            order.setDeliveredTime(LocalDateTime.now());
            order.setOrderedTime(LocalDateTime.now());

            // 更新商家销量
            final Merchant merchant = merchantMapper.selectById(order.getMerchantId());
            final List<OrderItems> list = orderItemsService.lambdaQuery().eq(OrderItems::getOrderId, orderId).list();
            for (OrderItems orderItems : list) {
                merchant.setTotalSales(merchant.getTotalSales()+orderItems.getQuantity());
            }
            merchantMapper.updateById(merchant);
            notificationService.sendToMerchant(merchant.getId(),"您有一笔订单已完成");
            return Result.success(updateById(order));
        }else{
            return Result.error("商品未发货");
        }

    }

    @Override
    @LogAnnotation(type = OperationType.UPDATE,description = "发货")
    public Result ship(ShipDTO shipDTO) {

        // 生成物流流水号
        String tracking_number = shipDTO.getShipType().getPrefix() + RandomUtil.randomNumbers(12);
        log.info("订单{}发{}快递，流水号为:{}",shipDTO.getOrderId(),shipDTO.getShipType(),tracking_number);

        final boolean updated = lambdaUpdate()
                .set(Orders::getStatus, OrderStatus.SHIPPED)
                .set(Orders::getShippingMethod, shipDTO.getShipType())
                .set(Orders::getTrackingNumber, tracking_number)
                .set(Orders::getBillingAddressId, shipDTO.getAddressId())
                .set(Orders::getShippedTime,LocalDateTime.now())
                .set(Orders::getOrderedTime,LocalDateTime.now())
                .eq(Orders::getId, shipDTO.getOrderId())
                .eq(Orders::getStatus, OrderStatus.PAID)
                .update();
        if(!updated){
            return Result.error("发货失败，请联系管理员!");
        }
        final Orders orders = getById(shipDTO.getOrderId());

        notificationService.sendToUser(orders.getUserId(), "您有订单已发货,请注意查看,订单号:" + orders.getOrderNumber());
        return Result.success();
    }

    @Override
    @LogAnnotation(type = OperationType.SELECT,description = "查询商家订单")
    public Result getMerchantOrder(String status) {

        Long userId = UserHolder.getUser().getId();

        // 获取本人商家id
        Long merchantId = merchantUserMapper.getMerchantIdByUserId(userId);


        List<MerchantOrdersVO> userOrdersVOList = orderMapper.getMerchantOrdersWithPagination(status, merchantId);


        return Result.success(userOrdersVOList);
    }

    @Override
    @LogAnnotation(type = OperationType.UPDATE,description = "取消订单")
    public Result cancelOrder(ReturnApplyDTO dto) {

        Long orderId = dto.getOrderId();

        final Orders orders = getById(orderId);

        if(orders.getStatus()==OrderStatus.CANCELLED){
            return Result.error("此订单已经取消过了");
        }

        // 取消支付
        if(orders.getStatus()==OrderStatus.PENDING){
            orders.setStatus(OrderStatus.CANCELLED);
            orders.setPaymentStatus(PaymentStatus.CANCELLED);
            orders.setOrderedTime(LocalDateTime.now());

            final boolean updated = updateById(orders);
            if(!updated){
                throw new BusinessException("取消支付失败!");
            }
            // 回退预占库存 数据库+redis
            String tempOrderId =(String) redisTemplate.opsForHash().get(
                    RedisConstants.ORDER_ID_MAP_TO_TEMP_ID, orderId.toString());
            final List<OrderItemsVO> orderItemsVOS = orderItemsMapper.getItemsByOrderId(orderId);
            for(OrderItemsVO vo : orderItemsVOS){
                Long flag = redisUtils.rollBackReserveStock(vo.getProductId(),tempOrderId,orderId);
                if(flag!=1L){
                    throw new BusinessException("预占库存回退失败！");
                }
            }

            // 状态由待支付转变，需要删除延时检测器的订单
            redisTemplate.opsForZSet().remove(ORDER_TIMEOUT_TRIGGER,dto.getOrderId().toString());

            log.info("已经取消订单，取消支付");
            return Result.success(true);
        }

        if(!Objects.equals(orders.getMerchantId(), dto.getMerchantId()) ||
                !Objects.equals(orders.getUserId(), dto.getUserId())){
            throw new BusinessException("订单状态出现异常");
        }

        if(!Arrays.asList(OrderStatus.PAID,OrderStatus.SHIPPED,OrderStatus.DELIVERED).contains(orders.getStatus())){
            return Result.error("该订单状态不允许退货！");
        }

        if(auditService.lambdaQuery().eq(ReturnAudit::getOrderId,orderId).exists()){
            return Result.error("该订单已经审核处理，请查看售后详情！");
        }

        // 退货
        // 创建退货记录 修改订单状态 保证事务一致
        try {
            ReturnAudit audit = new ReturnAudit();
            audit.setOrderId(orderId);
            audit.setUserId(dto.getUserId());
            audit.setMerchantId(dto.getMerchantId());
            audit.setReturnReason(dto.getReturnReason());
            audit.setReturnImages(dto.getReturnImages());
            audit.setAuditStatus(AuditStatus.PENDING); // 待审核
            audit.setApplyTime(LocalDateTime.now());
            final boolean saved = auditService.save(audit); //保存退货记录

            if(!saved){
                throw new  BusinessException("退货记录更新失败");
            }
            // 保存退货记录成功 更新订单状态
            orders.setStatus(OrderStatus.RETURN_APPLIED); // 退货申请
            final boolean updated = updateById(orders);
            if(!updated){
                throw new BusinessException("退货申请失败");
            }
            // 保存记录成功

            // 通知商家处理退货（系统消息）
            String nick = userService.getById(dto.getUserId()).getNickName();
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String content = "系统消息：顾客" + nick + "在" + now + "申请了订单#" + orderId + "的退货，请尽快审核处理";
            notificationService.sendToMerchant(dto.getMerchantId(), content);

            return  Result.success();
        }catch (Exception e){
            throw new BusinessException("订单异常");
        }
    }

    @Override
    @LogAnnotation(type = OperationType.UPDATE,description = "处理退货")
    public Result approveReturn(ApproveReturnDTO dto) {
        // 1.校验审核记录
        final ReturnAudit audit = auditService.getById(dto.getAuditId());
        if(audit==null||audit.getAuditStatus()!=AuditStatus.PENDING){
            throw  new BusinessException("审核记录不存在或已经处理");
        }
        // 2.订单商家是否正确
        final Orders orders = orderMapper.selectById(dto.getOrderId());
        if(!Objects.equals(orders.getMerchantId(), dto.getMerchantId())){
            log.info("无权处理:{}",dto);
            throw  new BusinessException("无权处理该订单");
        }

        // 3.更新审核记录（以批准） 更新订单状态（退货已批准）
        try {
            // 3.1 更新审核记录
            audit.setAuditStatus(AuditStatus.APPROVE);
            audit.setAuditTime(LocalDateTime.now());
            audit.setAuditReason(dto.getAuditReason());//可选
            final boolean updated = auditService.updateById(audit);
            if(!updated){
                throw new BusinessException("审核记录更新异常");
            }
            // 3.2 更新订单状态
            orders.setStatus(OrderStatus.CANCELLED);
            orders.setPaymentStatus(PaymentStatus.REFUNDED); //直接退款
            final boolean updated1 = updateById(orders);
            if(!updated1){
                throw new BusinessException("订单更新异常");
            }

            // 3.3 库存回退
            final List<OrderItemsVO> orderItems = orderItemsMapper.getItemsByOrderId(orders.getId());
            for(OrderItemsVO vo : orderItems){
                InventoryDTO inventoryDTO = new InventoryDTO();
                inventoryDTO.setReferenceId(vo.getOrderId());
                inventoryDTO.setChangeType(ChangeType.IN);
                inventoryDTO.setReason("顾客退货");
                inventoryDTO.setProductId(vo.getProductId());
                inventoryDTO.setChangeQuantity(vo.getQuantity());
                final Result result = productService.modifyStockQuantity(inventoryDTO);
                if(result.getCode()!=1){
                    throw new BusinessException("退货库存出现异常");
                }
            }

            // 通知用户：退货审核已批准（系统消息）
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String content = "系统消息：您的订单#" + orders.getId() + "退货申请已批准，时间" + now + "。退款将尽快处理";
            notificationService.sendToUser(orders.getUserId(), content);

            return  Result.success();
        }catch (Exception e){
            throw new BusinessException(e.getMessage());
        }

    }


    @Override
    @LogAnnotation(type = OperationType.UPDATE,description = "拒绝退货")
    public Result rejectReturn(RejectReturnDTO dto) {

        // 1.校验审核记录
        final ReturnAudit audit = auditService.getById(dto.getAuditId());
        if(audit==null||audit.getAuditStatus()!=AuditStatus.PENDING){
            throw  new BusinessException("审核记录不存在或已经处理");
        }
        // 2.订单商家是否正确
        final Orders orders = orderMapper.selectById(dto.getOrderId());
        if(!Objects.equals(orders.getMerchantId(), dto.getMerchantId())){
            log.info("无权处理:{}",dto);
            throw  new BusinessException("无权处理该订单");
        }

        // 3.更新审核记录 更新订单状态
        try {

            audit.setAuditStatus(AuditStatus.REJECT);
            audit.setAuditReason(dto.getAuditReason());
            audit.setAuditTime(LocalDateTime.now());
            final boolean updated = auditService.updateById(audit);
            if(!updated){
                throw new BusinessException("审核记录更新异常");
            }
            // 3.2 更新订单状态
            orders.setStatus(this.getOriginalStatus(orders));
            final boolean updated1 = updateById(orders);
            if(!updated1){
                throw new BusinessException("订单更新异常");
            }

            // 通知用户：退货申请已被拒绝（系统消息）
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String content = "系统消息：您的订单#" + orders.getId() + "退货申请已被拒绝，时间" + now + "。如有疑问请联系商家";
            notificationService.sendToUser(orders.getUserId(), content);

            return  Result.success();

        }catch (Exception e){
            throw new BusinessException(e.getMessage());
        }

    }

    /**
     * 辅助函数：拒绝退货后订单回滚到退货前的状态
     * @param orders 账单
     * @return OrderStatus
     */
    private OrderStatus getOriginalStatus(Orders orders){
        if(orders.getDeliveredTime()!=null){
            // 已送达（已完成）
            return OrderStatus.DELIVERED;
        }
        else if(orders.getShippedTime()!=null){
            // 送达时间为空但是发货时间不为空，已发货
            return OrderStatus.SHIPPED;
        }
        else {
            // 发货时间 送达时间都为空，已支付（待发货）
            return OrderStatus.PAID;
        }
    }

}
