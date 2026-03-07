package com.scutmmq.controller;

import com.scutmmq.dto.MerchantReplyDTO;
import com.scutmmq.dto.ProductReviewDTO;
import com.scutmmq.entity.Result;
import com.scutmmq.service.ProductReviewService;
import com.scutmmq.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商品评论控制器
 */
@Slf4j
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ProductReviewController {
    
    private final ProductReviewService productReviewService;
    
    /**
     * 顾客评价订单的商品
     * @return reviewDTO
     */
    @PostMapping
    public Result addReview(@RequestBody ProductReviewDTO reviewDTO){
        return productReviewService.addReview(reviewDTO);
    }
    
    /**
     * 用户查询自己的商品评价
     * @return 评价列表
     */
    @GetMapping("/user")
    public Result getReviewsByUserId(){
        Long userId = UserHolder.getUser().getId();
        return productReviewService.getReviewsByUserId(userId);
    }
    
    /**
     * 查询商品的所有评价
     * @param productId 商品ID
     * @return 评价列表
     */
    @GetMapping("/product/{productId}")
    public Result getReviewsByProductId(@PathVariable Long productId){
        return productReviewService.getReviewsByProductId(productId);
    }
    
    /**
     * 商家查询自己收到的评价
     * @return 评价列表
     */
    @GetMapping("/merchant")
    public Result getReviewsByMerchantId(){
        return productReviewService.getReviewsByMerchantId();
    }
    
    /**
     * 商家回复用户评论
     * @param replyDTO 回复内容
     * @return 结果
     */
    @PostMapping("/merchant/reply")
    public Result replyToReview(@RequestBody MerchantReplyDTO replyDTO) {
        return productReviewService.replyToReview(replyDTO);
    }
}