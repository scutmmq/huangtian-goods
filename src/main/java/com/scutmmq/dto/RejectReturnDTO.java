package com.scutmmq.dto;

import lombok.Data;

@Data
public class RejectReturnDTO {
    private Long orderId;
    private Long userId;
    private Long merchantId;
    private Long auditId;
    private String auditReason;
}
