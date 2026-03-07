package com.scutmmq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.dto.CartsDTO;
import com.scutmmq.entity.Carts;
import com.scutmmq.entity.Result;

public interface CartService extends IService<Carts> {
    Result getCarts();

    Result addItem(CartsDTO cartsDTO);

    Result updateCartItem(CartsDTO cartsDTO);
}
