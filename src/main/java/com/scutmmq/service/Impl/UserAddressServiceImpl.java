package com.scutmmq.service.Impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.scutmmq.entity.PageResult;
import com.scutmmq.entity.Result;
import com.scutmmq.entity.User;
import com.scutmmq.entity.UserAddress;
import com.scutmmq.mapper.UserAddressMapper;
import com.scutmmq.service.UserAddressService;
import com.scutmmq.utils.RedisConstants;
import com.scutmmq.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class UserAddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress>  implements UserAddressService {

    private final UserAddressMapper userAddressMapper;

    private final StringRedisTemplate redisTemplate;

    @Override
    public Result getAddress() {

        PageHelper.startPage(1,10);

        Long id = UserHolder.getUser().getId();

        List<UserAddress> userAddressList = userAddressMapper.getAddress(id);

        Page<UserAddress> page = (Page<UserAddress>) userAddressList;

        PageResult<UserAddress> pageResult = new PageResult<>(page.getTotal(),page.getResult());

        return Result.success(pageResult);
    }

    @Override
    public Result addAddress(UserAddress address) {

        Long id = UserHolder.getUser().getId();
        address.setUserId(id);
        LocalDateTime now = LocalDateTime.now();
        address.setCreatedTime(now);
        address.setUpdatedTime(now);
        if(address.getIsDefault()!=null && address.getIsDefault()==1){
            // 将当前用户其它默认的设置为0
            lambdaUpdate().set(UserAddress::getIsDefault,0)
                    .eq(UserAddress::getUserId, id)
                    .eq(UserAddress::getIsDefault,1)
                    .update();
        }
        save(address);
        return Result.success();
    }

    @Override
    public Result updateAddress(UserAddress address) {
        Long id = UserHolder.getUser().getId();
        // 先校验该地址是否存在且属于当前用户
        UserAddress exist = lambdaQuery().eq(UserAddress::getId, address.getId()).eq(UserAddress::getUserId, id).one();
        if (exist == null) {
            return Result.error("修改失败，地址不存在或无权操作");
        }

        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            lambdaUpdate().set(UserAddress::getIsDefault, 0)
                    .eq(UserAddress::getUserId, id)
                    .eq(UserAddress::getIsDefault, 1)
                    .update();
        }
        final boolean update = lambdaUpdate()
                .set(address.getPhone() != null, UserAddress::getPhone, address.getPhone())
                .set(address.getProvince() != null, UserAddress::getProvince, address.getProvince())
                .set(address.getCity() != null, UserAddress::getCity, address.getCity())
                .set(address.getDistrict() != null, UserAddress::getDistrict, address.getDistrict())
                .set(address.getDetail() != null, UserAddress::getDetail, address.getDetail())
                .set(address.getPostalCode() != null, UserAddress::getPostalCode, address.getPostalCode())
                .set(address.getIsDefault() != null, UserAddress::getIsDefault, address.getIsDefault())
                .set(UserAddress::getUpdatedTime, LocalDateTime.now())
                .eq(UserAddress::getId, address.getId())
                .eq(UserAddress::getUserId, id)
                .update();

        if (!update) {
            return Result.error("修改失败，地址不存在或无权操作");
        }
        redisTemplate.delete(RedisConstants.CACHE_ADDRESS_KEY + address.getId());

        return Result.success();
    }

    @Override
    public Result defaultAddress(Long addressId) {
        Long id = UserHolder.getUser().getId();

        // 先校验目标地址存在且属于当前用户
        UserAddress target = lambdaQuery().eq(UserAddress::getId, addressId).eq(UserAddress::getUserId, id).one();
        if (target == null) {
            return Result.error("设置默认地址失败，地址不存在或无权操作");
        }

        // 仅将当前用户的其它默认地址重置为0
        lambdaUpdate().set(UserAddress::getIsDefault, 0)
                .eq(UserAddress::getUserId, id)
                .eq(UserAddress::getIsDefault, 1)
                .update();

        // 更新指定地址为默认地址
        boolean updated = lambdaUpdate().set(UserAddress::getIsDefault, 1)
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getUserId, id)
                .update();

        if (!updated) {
            return Result.error("设置默认地址失败");
        }

        redisTemplate.delete(RedisConstants.CACHE_ADDRESS_KEY + addressId);

        return Result.success();
    }

    @Override
    public Result getAddressById(Long addressId) {
        Long id = UserHolder.getUser().getId();
        final String jsonAddress = redisTemplate.opsForValue().get(RedisConstants.CACHE_ADDRESS_KEY + addressId);
        if (jsonAddress != null) {
            UserAddress cachedAddress = JSONUtil.toBean(jsonAddress, UserAddress.class);
            if (Objects.equals(cachedAddress.getUserId(), id)) {
                log.info("地址缓存命中 ");
                return Result.success(cachedAddress);
            }
        }
        log.info("地址缓存未命中");
        final UserAddress address = getById(addressId);

        if (address != null) {
            if (!Objects.equals(address.getUserId(), id)) {
                return Result.error("无权查看该地址");
            }
            // 写入缓存
            redisTemplate.opsForValue().set(RedisConstants.CACHE_ADDRESS_KEY + addressId, JSONUtil.toJsonStr(address), 30, java.util.concurrent.TimeUnit.MINUTES);
        }
        return Result.success(address);
    }

    @Override
    public Result deleteAddress(Long id) {
        Long userId = UserHolder.getUser().getId();
        boolean deleted = lambdaUpdate()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, userId)
                .remove();
        if (!deleted) {
            return Result.error("删除失败，地址不存在或无权操作");
        }
        redisTemplate.delete(RedisConstants.CACHE_ADDRESS_KEY + id);
        return Result.success(true);
    }

    @Override
    public Result deleteAddresses(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("待删除地址列表不能为空");
        }
        Long userId = UserHolder.getUser().getId();
        boolean deleted = lambdaUpdate()
                .in(UserAddress::getId, ids)
                .eq(UserAddress::getUserId, userId)
                .remove();
        for (Long id : ids) {
            redisTemplate.delete(RedisConstants.CACHE_ADDRESS_KEY + id);
        }
        return Result.success(deleted);
    }
}
