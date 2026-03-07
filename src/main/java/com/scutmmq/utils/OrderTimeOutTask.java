package com.scutmmq.utils;

import com.esotericsoftware.minlog.Log;
import com.scutmmq.entity.Orders;
import com.scutmmq.enums.OrderStatus;
import com.scutmmq.enums.PaymentStatus;
import com.scutmmq.exception.BusinessException;
import com.scutmmq.mapper.OrderItemsMapper;
import com.scutmmq.service.NotificationService;
import com.scutmmq.service.OrderService;
import com.scutmmq.vo.OrderItemsVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class OrderTimeOutTask {

    private final StringRedisTemplate redisTemplate;

    private final ScheduledExecutorService GET_TIME_OUT_ORDER_EXECUTORS = Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"order-timeout-scan-thread"));

    private final ExecutorService HANDLE_TIMEOUT_ORDER_EXECUTORS = Executors.newSingleThreadExecutor(r->new Thread(r,"order-timeout-handle-thread"));

    private static final DefaultRedisScript<Long> REMOVE_ZSET_MEMBERS;

    private final OrderItemsMapper orderItemsMapper;

    private final OrderService orderService;

    private final RedisUtils redisUtils;

    private final NotificationService notificationService;
    static {
        REMOVE_ZSET_MEMBERS = new DefaultRedisScript<>();
        REMOVE_ZSET_MEMBERS.setResultType(Long.class);
        REMOVE_ZSET_MEMBERS.setLocation(new ClassPathResource("lua/remove-zset-members.lua"));
    }

    @PostConstruct
    void init(){
        GET_TIME_OUT_ORDER_EXECUTORS.scheduleAtFixedRate(
                new GetTimeOutOrderHandler(),
                0,
                10,
                TimeUnit.SECONDS
        );
        HANDLE_TIMEOUT_ORDER_EXECUTORS.submit(new HandlerTimeoutOrderMQ());
    }

    private  final class GetTimeOutOrderHandler implements Runnable{
        @Override
        public void run() {
            // TODO 获取超时订单

            //获取当前时间戳
            final long now = System.currentTimeMillis();

            // 获取超时订单
            final Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().rangeByScoreWithScores(
                    RedisConstants.ORDER_TIMEOUT_TRIGGER,
                    0, now, 0, 3);
            if (typedTuples == null || typedTuples.isEmpty()) {
                return;
            }

            // 获取订单id批量在redis删除
            List<String> timeoutOrderIds = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                String orderId = tuple.getValue();
                Double timeoutTime = tuple.getScore();
                if (orderId != null && timeoutTime != null) {
                    timeoutOrderIds.add(orderId);
                    log.info("获取到超时的订单,ID为:{}, 超时时间戳:{}", orderId, timeoutTime.longValue());
                }
            }
            // 批量删除订单
            Long flag = redisTemplate.execute(
                    REMOVE_ZSET_MEMBERS,
                    Collections.emptyList(),
                    timeoutOrderIds.toArray(new String[0])
            );
            if (flag != 1) {
                log.error("zset订单成员删除失败!");
            }
            log.info("延时检测器订单成员删除成功,即将发送信息到消息队列中");
            // TODO 发送消息给消息队列,消息队列处理消息(订单状态更新为取消，回退预占库存)
            for (String orderId : timeoutOrderIds) {
                redisTemplate.opsForStream().add(
                        StreamRecords.newRecord()
                                .ofMap(Map.of("orderId", orderId))
                                .withStreamKey(RedisConstants.ORDER_TIMEOUT_STREAM)
                );
            }
            log.info("延时检测器成功向消息队列发送信息处理超时订单");

        }
    }

    private final class HandlerTimeoutOrderMQ implements Runnable{
        //    XGROUP CREATE order:timeout:stream order:timeout:group $ MKSTREAM 创建stream和消费者组
        //    xgroup createconsumer order:timeout:stream order:timeout:group c1 添加消费者
        @Override
        public void run() {
            log.info("消息队列开始获取消息");
            while (true){
                try {
                    // 不断从消息队列读取信息 阻塞5秒读一次
                    final List<MapRecord<String, Object, Object>> mapRecordList = redisTemplate.opsForStream().read(
                            Consumer.from(RedisConstants.ORDER_TIMEOUT_GROUP, "c1"),
                            StreamReadOptions.empty().count(5).block(Duration.ofSeconds(5)),
                            StreamOffset.create(RedisConstants.ORDER_TIMEOUT_STREAM,ReadOffset.lastConsumed())
                    );

                    if(mapRecordList==null||mapRecordList.isEmpty()){
                        continue;
                    }

                    log.info("消息队列获取到信息:{},进行超时订单的处理",mapRecordList);
                    for (MapRecord<String, Object, Object> entries : mapRecordList) {
                        // TODO 处理超时订单：1.设置为已经取消 2.回退订单项的预占库存 3.若成功则ACK信息，若失败，则处理pending信息
                        cancelTimeoutOrder(entries.getId(),entries.getValue().get("orderId"));
                    }

                }catch (Exception e){
                    handlePendingList();
                }
            }
        }

        private void cancelTimeoutOrder(RecordId id, Object orderId) {

            final Orders orders = orderService.getById(Long.parseLong(orderId.toString()));

            if(orders.getStatus()!= OrderStatus.PENDING){
                log.error("此超时订单并非处于未支付状态!");
                return;
            }

            // 更新订单状态
            orders.setStatus(OrderStatus.CANCELLED);
            orders.setPaymentStatus(PaymentStatus.CANCELLED);
            orders.setOrderedTime(LocalDateTime.now());
            orders.setRemark("订单已超时，自动取消");
            final boolean updated = orderService.updateById(orders);
            if(!updated){
                throw new BusinessException("超时订单更新过程出现异常");
            }

            final List<OrderItemsVO> orderItemsVOS = orderItemsMapper.getItemsByOrderId(Long.parseLong(orderId.toString()));

            // 回退预占库存 数据库+redis
            String tempOrderId =(String) redisTemplate.opsForHash().get(
                    RedisConstants.ORDER_ID_MAP_TO_TEMP_ID, orderId.toString());
            for (OrderItemsVO vo : orderItemsVOS) {
                final Long flag = redisUtils.rollBackReserveStock(vo.getProductId(), tempOrderId, vo.getOrderId());
                if(flag!=1){
                    throw new BusinessException("处理超时订单时回退预占库存失效");
                }
            }
            // 确认消息
            redisTemplate.opsForStream().acknowledge(
                    RedisConstants.ORDER_TIMEOUT_STREAM,
                    RedisConstants.ORDER_TIMEOUT_GROUP,
                    id
            );

            notificationService.sendToUser(orders.getUserId(), "系统消息：您的订单#" + orders.getId() + "已超时，系统已经自动取消，时间" + LocalDateTime.now() + "。请重新下单！");

        }

        private void handlePendingList() {
        }
    }
}
