package com.scutmmq.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.entity.Merchant;
import com.scutmmq.entity.MerchantUser;
import com.scutmmq.entity.Result;
import com.scutmmq.mapper.MerchantMapper;
import com.scutmmq.mapper.MerchantUserMapper;
import com.scutmmq.mapper.ProductMapper;
import com.scutmmq.service.MerchantService;
import com.scutmmq.service.MerchantUserService;
import com.scutmmq.utils.RedisConstants;
import com.scutmmq.utils.UserHolder;
import com.scutmmq.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class MerchantServiceImpl extends ServiceImpl<MerchantMapper, Merchant> implements MerchantService {

    private final MerchantUserService merchantUserService;

    private final MerchantUserMapper merchantUserMapper;

    private final ProductMapper productMapper;

    private final StringRedisTemplate redisTemplate;

    @Override
    public Result addMerchant(Merchant merchant) {

        // 获取当前用户id
        Long userId = UserHolder.getUser().getId();

        // 查询是否已经注册商户
        final MerchantUser merchantUser1 = merchantUserService.lambdaQuery().eq(MerchantUser::getUserId, userId).one();

        if(merchantUser1!=null){
            return Result.error("您已经注册过商户!");
        }

        Long merchantId = merchantUserMapper.getMerchantIdByUserId(userId);

        if(merchantId!=null){
            return Result.error("您已经拥有商店了!");
        }

        save(merchant);

        MerchantUser merchantUser = new MerchantUser();
        merchantUser.setMerchantId(merchant.getId());
        merchantUser.setUserId(userId);
        merchantUserService.save(merchantUser);

        return Result.success();
    }

    @Override
    public Result getMerchant() {
        Long userId = UserHolder.getUser().getId();
        Long merchantId = merchantUserMapper.getMerchantIdByUserId(userId);
        Merchant merchant = getById(merchantId);
        return Result.success(merchant);
    }

    @Override
    public Result updateMerchant(Merchant merchant) {
        final boolean updated = lambdaUpdate()
                .eq(Merchant::getId, merchant.getId())
                .set(merchant.getName() != null && !merchant.getName().isEmpty(),
                        Merchant::getName, merchant.getName())
                .set(merchant.getDescription() != null,
                        Merchant::getDescription, merchant.getDescription())
                .set(merchant.getContactPerson() != null && !merchant.getContactPerson().isEmpty(),
                        Merchant::getContactPerson, merchant.getContactPerson())
                .set(merchant.getContactPhone() != null && !merchant.getContactPhone().isEmpty(),
                        Merchant::getContactPhone, merchant.getContactPhone())
                .set(merchant.getEmail() != null,
                        Merchant::getEmail, merchant.getEmail())
                .set(merchant.getAddress() != null,
                        Merchant::getAddress, merchant.getAddress())
                .set(merchant.getBusinessLicense() != null,
                        Merchant::getBusinessLicense, merchant.getBusinessLicense())
                .set(merchant.getMerchantType() != null,
                        Merchant::getMerchantType, merchant.getMerchantType())
                .set(merchant.getLogoUrl() != null, Merchant::getLogoUrl, merchant.getLogoUrl())
                .update();
        if(!updated){
            return Result.error("修改失败!");
        }
        // 需要删除缓存此商家的商品缓存
        final List<ProductVO> productsOfMe = productMapper.getProductsOfMe(merchant.getId());
        for (ProductVO productVO : productsOfMe) {
            redisTemplate.delete(RedisConstants.CACHE_PRODUCT_DETAIL+productVO.getId());
        }

        return Result.success();
    }
}
