package com.scutmmq.utils;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@RequiredArgsConstructor
@Data
@Component
public class RedisUtils {

    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> RESERVE_STOCK_SCRIPT;

    private static final DefaultRedisScript<Long> CANCEL_RESERVE_STOCK_SCRIPT;

    private static final  DefaultRedisScript<Long> ROLLBACK_RESERVE_STOCK_SCRIPT;
    //synchronizeUpdateStock
    private static final DefaultRedisScript<Long> SYNCHRONIZE_UPDATE_STOCK_SCTIPT;

    static {
        RESERVE_STOCK_SCRIPT = new DefaultRedisScript<>();
        RESERVE_STOCK_SCRIPT.setLocation(new ClassPathResource("lua/reserve-stock.lua"));
        RESERVE_STOCK_SCRIPT.setResultType(Long.class);

        CANCEL_RESERVE_STOCK_SCRIPT = new DefaultRedisScript<>();
        CANCEL_RESERVE_STOCK_SCRIPT.setLocation(new ClassPathResource("lua/cancel-reserve-stock.lua"));
        CANCEL_RESERVE_STOCK_SCRIPT.setResultType(Long.class);

        ROLLBACK_RESERVE_STOCK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_RESERVE_STOCK_SCRIPT.setLocation(new ClassPathResource("lua/rollback-reserve-stock.lua"));
        ROLLBACK_RESERVE_STOCK_SCRIPT.setResultType(Long.class);

        SYNCHRONIZE_UPDATE_STOCK_SCTIPT=new DefaultRedisScript<>();
        SYNCHRONIZE_UPDATE_STOCK_SCTIPT.setResultType(Long.class);
        SYNCHRONIZE_UPDATE_STOCK_SCTIPT.setLocation(new ClassPathResource("lua/update-stock.lua"));
    }

    /**
     * 下单前校验库存(预占)
     * @param productId 商品id
     * @param quantity 购买数量
     * @return 1 校验通过 0 校验不通过,已被他人下单
     */
    public  Long ReserveStock(Long productId,Integer quantity,String tempOrderId){
        return redisTemplate.execute(
                RESERVE_STOCK_SCRIPT,
                Collections.emptyList(),
                String.valueOf(productId),String.valueOf(quantity),tempOrderId);
    }

    public Long CancelReserveStock(Long productId, String tempOrderId,Long orderId) {
        return redisTemplate.execute(
                CANCEL_RESERVE_STOCK_SCRIPT,
                Collections.emptyList(),
                String.valueOf(productId),tempOrderId,String.valueOf(orderId)
        );
    }

    public Long rollBackReserveStock(Long productId, String tempOrderId, Long orderId) {
        return redisTemplate.execute(
                ROLLBACK_RESERVE_STOCK_SCRIPT,
                Collections.emptyList(),
                String.valueOf(productId),tempOrderId,String.valueOf(orderId)
        );
    }

    public void synchronizeUpdateStock(Long productId, Integer currentQuantity) {
        redisTemplate.execute(
                SYNCHRONIZE_UPDATE_STOCK_SCTIPT,
                Collections.emptyList(),
                String.valueOf(productId),currentQuantity.toString()
        );
    }
}
