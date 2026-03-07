package com.scutmmq.vo;

import com.scutmmq.enums.OrderStatus;
import com.scutmmq.enums.PaymentStatus;
import com.scutmmq.enums.ShipType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MerchantOrdersVO {
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
    private String customerNickName;
    private String customerImage;
    private String remark;
    List<OrderProduct> items;

    @Data
    private static class OrderProduct{
        private Long productId;
        private String productName;
        private BigDecimal productPrice;
        private Integer quantity;
        private String imageUrl;
    }

}
