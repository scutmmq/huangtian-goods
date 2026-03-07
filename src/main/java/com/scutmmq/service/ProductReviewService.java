package com.scutmmq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.dto.MerchantReplyDTO;
import com.scutmmq.dto.ProductReviewDTO;
import com.scutmmq.entity.ProductReview;
import com.scutmmq.entity.Result;
import com.scutmmq.vo.ProductReviewVO;

import java.util.List;

/**
 * 商品评论服务接口
 */
public interface ProductReviewService extends IService<ProductReview> {
    Result addReview(ProductReviewDTO reviewDTO);
    
    /**
     * 根据用户ID查询该用户的所有商品评价
     * @param userId 用户ID
     * @return 评价列表
     */
    Result getReviewsByUserId(Long userId);

    /**
     * 根据商品ID查询该商品的所有评价
     * @param productId 商品ID
     * @return 评价列表
     */
    Result getReviewsByProductId(Long productId);

    /**
     * 根据商家ID查询该商家收到的所有评价
     * @return 评价列表
     */
    Result getReviewsByMerchantId();
    
    /**
     * 商家回复用户评论
     * @param replyDTO 回复内容
     * @return 结果
     */
    Result replyToReview(MerchantReplyDTO replyDTO);
}