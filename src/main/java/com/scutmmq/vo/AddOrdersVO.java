package com.scutmmq.vo;

import com.scutmmq.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddOrdersVO {
    private Long orderId ;
    private String orderNumber;
    private BigDecimal totalAmount;
    private BigDecimal paymentAmount;
}
