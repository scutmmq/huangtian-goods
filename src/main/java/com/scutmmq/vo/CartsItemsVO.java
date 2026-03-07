package com.scutmmq.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;


@Data
public class CartsItemsVO {
    private Long userId;
    private Long cartId;

    private List<Merchant> list;

    @Data
    public static  class  Merchant{
        private Long merchantId; // 商家id
        private String merchantName; //商检名称
        private List<CartsOfProduct> productList;
    }

    @Data
    public static class CartsOfProduct{
        private Long cartItemsId;
        private Long productId;
        private String name;
        private String imageUrl;
        private Long quantity;
        private BigDecimal price;
        private BigDecimal subtotal;
    }

    private BigDecimal totalPrice;
}
