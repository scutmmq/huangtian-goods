package com.scutmmq.dto;

import com.scutmmq.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayDTO {
    private PaymentMethod paymentMethod; //支付方式
    private Long orderId; // 订单号
    private BigDecimal amount; //总金额
}
