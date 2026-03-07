package com.scutmmq.dto;

import lombok.Data;

@Data
public class ReturnApplyDTO {
    private Long orderId;
    private Long userId;
    private Long merchantId;
    private String returnReason;
    private String returnImages;
}
