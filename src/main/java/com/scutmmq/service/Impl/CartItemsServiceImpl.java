package com.scutmmq.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.entity.CartsItems;
import com.scutmmq.mapper.CartItemsMapper;
import com.scutmmq.service.CartItemsService;
import org.springframework.stereotype.Service;

@Service
public class CartItemsServiceImpl extends ServiceImpl<CartItemsMapper, CartsItems> implements CartItemsService {
}
