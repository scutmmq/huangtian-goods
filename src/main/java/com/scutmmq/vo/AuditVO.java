package com.scutmmq.vo;

import com.scutmmq.enums.AuditStatus;
import com.scutmmq.enums.OrderStatus;
import com.scutmmq.enums.PaymentStatus;
import com.scutmmq.enums.ShipType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AuditVO {
    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private LocalDateTime orderedTime;
    private BigDecimal totalAmount;
    private Long shippingAddressId;
    private Long billingAddressId;
    private LocalDateTime shippedTime;
    private LocalDateTime deliveredTime;
    private ShipType shipType;
    private String trackingNumber;
    private Long merchantId;
    private String merchantName;
    private String customerNickName;
    private String customerImage;

    List<AuditVO.OrderProduct> items;

    private Audit audit;


    @Data
    private static class Audit{
        private Long auditId;
        private AuditStatus auditStatus;
        private String returnReason;
        private String returnImage;
        private String auditReason;
        private LocalDateTime applyTime;
        private LocalDateTime auditTime;
    }

    @Data
    private static class OrderProduct{
        private Long productId;
        private String productName;
        private BigDecimal productPrice;
        private Integer quantity;
        private String imageUrl;
    }
}
