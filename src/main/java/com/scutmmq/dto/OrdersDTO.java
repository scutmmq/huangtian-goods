package com.scutmmq.dto;

import com.scutmmq.enums.PaymentMethod;
import lombok.Data;

import java.util.List;

@Data
public class OrdersDTO {
    private String shippingMethod;
    private Long shippingAddressId;
    List<OrderItemsDTO> list;
    private String remark;
}
