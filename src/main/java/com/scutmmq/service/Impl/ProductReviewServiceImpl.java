package com.scutmmq.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.anno.LogAnnotation;
import com.scutmmq.dto.MerchantReplyDTO;
import com.scutmmq.dto.ProductReviewDTO;
import com.scutmmq.entity.*;
import com.scutmmq.enums.OperationType;
import com.scutmmq.enums.OrderStatus;
import com.scutmmq.exception.BusinessException;
import com.scutmmq.mapper.*;
import com.scutmmq.service.NotificationService;
import com.scutmmq.service.ProductReviewService;
import com.scutmmq.utils.RedisConstants;
import com.scutmmq.utils.UserHolder;
import com.scutmmq.vo.ProductReviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 商品评论服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
@LogAnnotation(module = "商品评论管理")
public class ProductReviewServiceImpl extends ServiceImpl<ProductReviewMapper, ProductReview> implements ProductReviewService {

    private final NotificationService notificationService;

    private final ProductMapper productMapper;

    private final OrderMapper orderMapper;

    private final MerchantMapper merchantMapper;

    private final OrderItemsMapper orderItemsMapper;

    private final StringRedisTemplate redisTemplate;

    private final MerchantUserMapper merchantUserMapper;
    
    @Override
    @LogAnnotation(type = OperationType.INSERT,description = "添加商品评论")
    public Result addReview(ProductReviewDTO reviewDTO) {

        final boolean exists = lambdaQuery().eq(ProductReview::getOrderItemId, reviewDTO.getOrderItemId()).exists();

        if(exists){
            return Result.error("您已经评价过该商品了");
        }

        final Orders orders = orderMapper.selectById(reviewDTO.getOrderId());

        if(orders.getStatus()!= OrderStatus.DELIVERED){
            return Result.error("该商品不允许评价");
        }

        final Product product = productMapper.selectById(reviewDTO.getProductId());

        final Merchant merchant = merchantMapper.selectById(reviewDTO.getMerchantId());

        final OrderItems orderItems = orderItemsMapper.selectById(reviewDTO.getOrderItemId());

        if(!Objects.equals(product.getMerchantId(), merchant.getId())){
            return Result.error("该商品不允许评价");
        }

        Long userId = UserHolder.getUser().getId();

        ProductReview review = BeanUtil.copyProperties(reviewDTO,ProductReview.class);
        review.setReviewTime(LocalDateTime.now());
        review.setUserId(userId);

        final boolean saved = save(review);

        if(!saved){
            throw new BusinessException("评论保存失败!");
        }

        // TODO 计算商品和商家评分

        // 更新商家评分
        // 当前评分*评分数量 + 新评分 / (评分数量+1)
        BigDecimal newRating =
                merchant.getRating().multiply(new BigDecimal(merchant.getRatingCount()))
                .add(review.getRating())
                .divide(new BigDecimal(merchant.getRatingCount()+1), 2, RoundingMode.HALF_UP);
        merchant.setRatingCount(merchant.getRatingCount()+1);
        merchant.setRating(newRating);
        final int updated = merchantMapper.updateById(merchant);
        if(updated != 1){
            throw new BusinessException("评论失败");
        }

        // 更新商品评分
        BigDecimal newProductRating =
                product.getRating().multiply(new BigDecimal(product.getRatingCount()))
                .add(review.getRating())
                .divide(new BigDecimal(product.getRatingCount()+1), 2, RoundingMode.HALF_UP);
        product.setRatingCount(product.getRatingCount()+1);
        product.setRating(newProductRating);
        final int updated1 = productMapper.updateById(product);
        if(updated1 != 1){
            throw new BusinessException("评论失败");
        }

        orderItems.setIsReviewed(true);
        orderItemsMapper.updateById(orderItems);

        // 删除商品缓存，因为评分变化了
        redisTemplate.delete(RedisConstants.CACHE_PRODUCT_DETAIL+product.getId());

        notificationService.sendToMerchant(reviewDTO.getMerchantId(),
                "系统消息:用户"+UserHolder.getUser().getNickName()+"评价了您的商品,请即使查看回复哦！");

        return Result.success();
    }
    
    @Override
    @LogAnnotation(type = OperationType.SELECT,description = "查询用户评价")
    public Result getReviewsByUserId(Long userId) {
        List<ProductReviewVO> reviews = baseMapper.getReviewsByUserId(userId);
        return Result.success(reviews);
    }

    @Override
    @LogAnnotation(type = OperationType.SELECT,description = "查询商品评价")
    public Result getReviewsByProductId(Long productId) {
        List<ProductReviewVO> reviews = baseMapper.getReviewsByProductId(productId);
        return Result.success(reviews);
    }

    @Override
    @LogAnnotation(type = OperationType.SELECT,description = "查询商家评价")
    public Result getReviewsByMerchantId() {
        Long userId = UserHolder.getUser().getId();
        Long merchantId = merchantUserMapper.getMerchantIdByUserId(userId);
        List<ProductReviewVO> reviews = baseMapper.getReviewsByMerchantId(merchantId);
        return Result.success(reviews);
    }
    
    @Override
    @LogAnnotation(type = OperationType.UPDATE,description = "回复用户评价")
    public Result replyToReview(MerchantReplyDTO replyDTO) {
        // 获取当前商家ID
        Long userId = UserHolder.getUser().getId();
        Long merchantId = merchantUserMapper.getMerchantIdByUserId(userId);
        
        // 检查评论是否存在
        ProductReview review = getById(replyDTO.getReviewId());
        if (review == null) {
            return Result.error("评论不存在");
        }
        
        // 检查该评论是否属于该商家的商品
        Product product = productMapper.selectById(review.getProductId());
        if (!product.getMerchantId().equals(merchantId)) {
            return Result.error("您无权回复此评论");
        }
        
        // 更新评论的商家回复内容和回复时间
        review.setMerchantReply(replyDTO.getMerchantReply());
        review.setReplyTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());
        
        // 保存更新
        boolean updated = updateById(review);
        if (!updated) {
            throw new BusinessException("回复失败");
        }
        
        // 发送通知给用户
        notificationService.sendToUser(review.getUserId(), 
                "商家回复了您的评论: " + replyDTO.getMerchantReply());
        
        return Result.success();
    }
}