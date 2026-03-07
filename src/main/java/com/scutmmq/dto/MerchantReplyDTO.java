package com.scutmmq.dto;

import lombok.Data;

/**
 * 商家回复评论DTO
 */
@Data
public class MerchantReplyDTO {
    /**
     * 评论ID
     */
    private Long reviewId;

    /**
     * 商家回复内容
     */
    private String merchantReply;
}