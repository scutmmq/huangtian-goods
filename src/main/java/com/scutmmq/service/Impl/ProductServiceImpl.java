package com.scutmmq.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.scutmmq.anno.LogAnnotation;
import com.scutmmq.dto.InventoryDTO;
import com.scutmmq.entity.*;
import com.scutmmq.enums.ChangeType;
import com.scutmmq.enums.OperationType;
import com.scutmmq.exception.BusinessException;
import com.scutmmq.mapper.MerchantMapper;
import com.scutmmq.service.InventoryLogService;
import com.scutmmq.utils.RedisConstants;
import com.scutmmq.utils.RedisUtils;
import com.scutmmq.vo.ProductVO;
import com.scutmmq.vo.ProductDetailVO;
import com.scutmmq.mapper.MerchantUserMapper;
import com.scutmmq.mapper.ProductMapper;
import com.scutmmq.service.MerchantService;
import com.scutmmq.service.ProductService;
import com.scutmmq.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static com.scutmmq.utils.RedisConstants.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
@LogAnnotation(module = "商品管理")
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService  {

    private final  StringRedisTemplate stringRedisTemplate;
    
    private final ProductMapper productMapper;

    private final MerchantUserMapper merchantUserMapper;

    private final InventoryLogService logService;

    private final RedisUtils redisUtils;

    @Override
    @LogAnnotation(type = OperationType.SELECT,description = "获取商品列表")
    public Result getProducts(Long categoryId, Long merchantId, String keyword, Integer minPrice, Integer maxPrice, Integer isActive, Integer page, Integer pageSize) {

        PageHelper.startPage(page,pageSize);

        List<ProductVO> dtoList = productMapper.getProducts(categoryId,merchantId,keyword,minPrice,maxPrice,isActive);

        Page<ProductVO> p = (Page<ProductVO>) dtoList;

        return  Result.success(new PageResult<ProductVO>(p.getTotal(),p.getResult()));

    }

    /**
     * 获取商品详细信息
     * @param productId 商品id
     * @return productDetail
     */
    @Override
    @LogAnnotation(type = OperationType.SELECT,description = "获取商品详细信息")
    public Result getProductDetail(Long productId) {
        final String jsonProduct = stringRedisTemplate.opsForValue().get(CACHE_PRODUCT_DETAIL + productId);

        if(jsonProduct==null||jsonProduct.isEmpty()){
            log.info("商品详情缓存未命中");
            ProductDetailVO productDetail= productMapper.getProductDetail(productId);
            if(productDetail!=null){
                // 更新是否被喜欢
                productDetail.setIsFavorite(isProductLiked(productId));
                // 存入redis
                stringRedisTemplate
                        .opsForValue()
                        .set(CACHE_PRODUCT_DETAIL+productId
                        , JSONUtil.toJsonStr(productDetail),CACHE_PRODUCT_DETAIL_EXPIRATION,CACHE_PRODUCT_DETAIL_UNIT);
                System.out.println(isProductLiked(productId));
                return Result.success(productDetail);
            }
            return Result.error("获取商品失败");
        }

        log.info("商品详情缓存命中");
        final ProductDetailVO productDetailVO = JSONUtil.toBean(jsonProduct, ProductDetailVO.class);
        productDetailVO.setIsFavorite(isProductLiked(productDetailVO.getId()));
        return  Result.success(productDetailVO);
    }

    /**
     * 商家添加商品
     * @param product 商品
     * @return null
     */
    @Override
    @LogAnnotation(type = OperationType.INSERT,description = "添加商品")
    public Result addProduct(Product product) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 获取用户所持有的商家id
        Long merchant_id = merchantUserMapper.getMerchantIdByUserId(userId);

        if(merchant_id == null){
            return Result.error("您未注册商户,请先注册商户");
        }

        final Integer stockQuantity = product.getStockQuantity();
        try {
            // 插入数据
            product.setMerchantId(merchant_id);
            final boolean saved = save(product);
            if(!saved){
                throw  new BusinessException("添加商品出错");
            }
            //成功，添加库存
            InventoryLog inventoryLog = new InventoryLog();
            inventoryLog.setProductId(product.getId());
            inventoryLog.setCurrentQuantity(stockQuantity);
            inventoryLog.setReason("新商品入库");
            inventoryLog.setOperatorId(userId);
            inventoryLog.setChangeType(ChangeType.IN);
            inventoryLog.setChangeQuantity(stockQuantity);
            final boolean saved1 = logService.save(inventoryLog);
            if(!saved1){
                throw  new BusinessException("添加商品日志失败");
            }
            // 成功添加日志,添加redis
            String key =  PRODUCT_STOCK_AVAILABLE + product.getId();
            stringRedisTemplate.opsForValue().set(key,String.valueOf(stockQuantity));

        }catch (Exception e){
            throw new BusinessException(e.getMessage());
        }

        return Result.success();
    }

    /**
     * 更新商品
     * @param product 商品表单
     * @return
     */
    @Override
    @LogAnnotation(type = OperationType.UPDATE,description = "更新商品")
    public Result updateProducts(Product product) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 获取用户所持有的商家id
        Long merchant_id = merchantUserMapper.getMerchantIdByUserId(userId);
        if(merchant_id == null){
            return Result.error("您未注册商户,请先注册商户");
        }
        // 更新数据
        final boolean updated = lambdaUpdate()
                .eq(Product::getId, product.getId())
                .set(product.getName() != null && !product.getName().isEmpty(), Product::getName, product.getName())
                .set(product.getDescription() != null, Product::getDescription, product.getDescription())
                .set(product.getPrice() != null, Product::getPrice, product.getPrice())
                .set(product.getCategoryId() != null, Product::getCategoryId, product.getCategoryId())
                .set(product.getSku() != null && !product.getSku().isEmpty(), Product::getSku, product.getSku())
                .set(product.getImageUrl() != null&&!product.getImageUrl().isEmpty(), Product::getImageUrl, product.getImageUrl())
                .set(product.getIsActive()!=null,Product::getIsActive,product.getIsActive())
                .update();
        if(!updated){
            return Result.error("更新失败，请查询错误");
        }
        // 删除商品详情缓存
        stringRedisTemplate.delete(CACHE_PRODUCT_DETAIL+product.getId());

        return  Result.success();
    }

    @Override
    @LogAnnotation(type = OperationType.SELECT,description = "获取商家商品")
    public Result getProductsOfMe() {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 获取用户所持有的商家id
        Long merchantId = merchantUserMapper.getMerchantIdByUserId(userId);
        List<ProductVO> productVOS =productMapper.getProductsOfMe(merchantId);
        return Result.success(productVOS);
    }

    /**
     * 更新库存
     * @param inventoryDTO 参数
     * @return true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogAnnotation(type = OperationType.UPDATE,description = "更新商品库存")
    public Result modifyStockQuantity(InventoryDTO inventoryDTO) {
        // 获取商品
        final Product product = getById(inventoryDTO.getProductId());
        if(inventoryDTO.getChangeType()== ChangeType.OUT&&inventoryDTO.getChangeQuantity()+product.getStockQuantity()<0){
            return Result.error("库存不足！");
        }
        final boolean updated = lambdaUpdate()
                .set(Product::getStockQuantity, product.getStockQuantity() + inventoryDTO.getChangeQuantity())
                .eq(Product::getId, inventoryDTO.getProductId())
                .update();
        if(!updated){
            return Result.error("更改失败!");
        }
        log.info("商品{}库存{}成功!",product.getName(),inventoryDTO.getChangeType().getDesc());

        // 添加日志
        InventoryLog inventoryLog = BeanUtil.copyProperties(inventoryDTO,InventoryLog.class);
        inventoryLog.setCurrentQuantity(product.getStockQuantity()+inventoryLog.getChangeQuantity());
        inventoryLog.setOperatorId(UserHolder.getUser().getId());
        final boolean saved = logService.save(inventoryLog);
        if(!saved){
            return Result.error("添加库存日志失败");
        }
        // 更新成功 删除商品响应缓存 删除店铺所有商品缓存
        stringRedisTemplate.delete(CACHE_PRODUCT_DETAIL+inventoryDTO.getProductId());
        // 更新redis活跃库存
        redisUtils.synchronizeUpdateStock(inventoryLog.getProductId(),inventoryLog.getCurrentQuantity());

        return Result.success(true);
    }

    /**
     * 收藏商品
     * @param productId 商品id
     * @return null
     */
    @Override
    public Result likeProduct(Long productId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        String key = PRODUCT_LIKED_USER + userId;

        // 获取商品
        final Product product = getById(productId);

        // 是否点赞
        boolean isMember = stringRedisTemplate.opsForZSet().score(key,String.valueOf(productId))!=null;

        if(!isMember){
            // 未点赞
            log.info("点赞商品:{}",productId);
            lambdaUpdate()
                    .set(Product::getFavorite,product.getFavorite()+1)
                    .eq(Product::getId,productId)
                    .update();
            // 保存商品到到用户点赞列表的redis的set集合
            stringRedisTemplate.opsForZSet().add(key,String.valueOf(productId),System.currentTimeMillis());
            // 删除商品缓存
            stringRedisTemplate.delete(CACHE_PRODUCT_DETAIL+productId);
            return  Result.success();
        }
        log.info("取消点赞商品:{}",productId);
        // 已经点赞，取消点赞,数据库点赞数-1
        lambdaUpdate()
                .set(Product::getFavorite,product.getFavorite()-1)
                .eq(Product::getId,productId)
                .update();
        // redis的set集合移除
        stringRedisTemplate.opsForZSet().remove(key,String.valueOf(productId));
        // 删除商品缓存
        stringRedisTemplate.delete(CACHE_PRODUCT_DETAIL+productId);
        return Result.success();
    }

    /**
     * 获取喜欢的商品
     * @return productList
     */
    @Override
    public Result getLikedProducts() {

        Long userId = UserHolder.getUser().getId();
        final Set<String> strings = stringRedisTemplate.opsForZSet().reverseRange(PRODUCT_LIKED_USER + userId, 0, -1);
        if(strings==null||strings.isEmpty()){
            return Result.success();
        }
        final List<Long>  productIds = strings.stream().map(Long::parseLong).toList();
        log.info("用户{}收藏的商品id{}",userId,productIds);
        final List<ProductVO> products = productMapper.getListByIds(productIds);

        return Result.success(products);
    }


    /**
     * 辅助函数，商品是否被喜欢
     * @param productId
     */
    private Boolean isProductLiked(Long productId) {
        Long userId = UserHolder.getUser().getId();
        // 查看用户的收藏列表是否存在商品
        return stringRedisTemplate.opsForZSet().score(PRODUCT_LIKED_USER + userId, String.valueOf(productId)) != null;
    }
}
