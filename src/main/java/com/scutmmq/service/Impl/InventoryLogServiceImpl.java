package com.scutmmq.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.entity.InventoryLog;
import com.scutmmq.entity.Result;
import com.scutmmq.mapper.InventoryLogMapper;
import com.scutmmq.mapper.MerchantUserMapper;
import com.scutmmq.mapper.ProductMapper;
import com.scutmmq.service.InventoryLogService;
import com.scutmmq.utils.RedisConstants;
import com.scutmmq.utils.UserHolder;
import com.scutmmq.vo.InventoryLogVO;
import com.scutmmq.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryLogServiceImpl extends ServiceImpl<InventoryLogMapper, InventoryLog> implements InventoryLogService {

    private final MerchantUserMapper merchantUserMapper;

    private final ProductMapper productMapper;

    private final InventoryLogMapper inventoryLogMapper;

    private final StringRedisTemplate redisTemplate;
    @Override
    public Result getLogs(String changeType, LocalDateTime startDate, LocalDateTime endDate) {

        // 获取本人id
        Long userId = UserHolder.getUser().getId();

//        final String jsonLogs = redisTemplate.opsForValue().get(RedisConstants.CACHE_INVENTORY_LOG_KEY + userId);
//
//        if(jsonLogs!=null&&!jsonLogs.isEmpty()){
//            log.info("库存日志缓存命中");
//            return  Result.success(JSONUtil.toList(jsonLogs,InventoryLogVO.class));
//        }
//
//        log.info("库存日志缓存未命中");

        // 获取本人名下的店铺id
        final Long merchantId = merchantUserMapper.getMerchantIdByUserId(userId);

        // 获取本人店铺下的所有商品id
        final List<ProductVO> productsOfMe = productMapper.getProductsOfMe(merchantId);

        final List<Long> productIds = productsOfMe.stream().map(ProductVO::getId).toList();

        if(productIds.isEmpty()){
            return Result.success();
        }

        final List<InventoryLogVO> inventoryLogVOS = inventoryLogMapper.getLogByProductIds(productIds,changeType,startDate,endDate);

//        // 存入redis
//        redisTemplate.opsForValue().set(RedisConstants.CACHE_INVENTORY_LOG_KEY+userId,
//                JSONUtil.toJsonStr(inventoryLogVOS),30, TimeUnit.MINUTES);

        return Result.success(inventoryLogVOS);
    }
}
