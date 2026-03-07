package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.entity.ProductReview;
import com.scutmmq.vo.ProductReviewVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 商品评论Mapper接口
 */
@Mapper
public interface ProductReviewMapper extends BaseMapper<ProductReview> {
    /**
     * 根据用户ID查询该用户的所有商品评价
     * @param userId 用户ID
     * @return 评价列表
     */
    List<ProductReviewVO> getReviewsByUserId(Long userId);

    /**
     * 根据商品ID查询该商品的所有评价
     * @param productId 商品ID
     * @return 评价列表
     */
    List<ProductReviewVO> getReviewsByProductId(Long productId);

    /**
     * 根据商家ID查询该商家收到的所有评价
     * @param merchantId 商家ID
     * @return 评价列表
     */
    List<ProductReviewVO> getReviewsByMerchantId(Long merchantId);
}