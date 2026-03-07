package com.scutmmq.vo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemsVO {
        private Long id;
        private Long orderId;
        private Long productId;
        private String productName;
        private BigDecimal productPrice;
        private Integer quantity;
        private BigDecimal subtotal;
        private Long merchantId;
        private String orderNumber;
        private String imageUrl;
        private Long shippingAddressId;
        private Long billingAddressId;
}
