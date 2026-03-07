package com.scutmmq.dto;

import lombok.Data;

@Data
public class CartsDTO {
    private Long productId;
    private Long cartItemsId;
    private Integer quantity;
}
