package com.scutmmq.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.anno.LogAnnotation;
import com.scutmmq.dto.CartsDTO;
import com.scutmmq.enums.OperationType;
import com.scutmmq.vo.CartsItemsVO;
import com.scutmmq.entity.Carts;
import com.scutmmq.entity.CartsItems;
import com.scutmmq.entity.Result;
import com.scutmmq.mapper.CartMapper;
import com.scutmmq.service.CartItemsService;
import com.scutmmq.service.CartService;
import com.scutmmq.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@LogAnnotation(module = "购物车管理")
public class CartServiceImpl extends ServiceImpl<CartMapper, Carts> implements CartService {

    private final CartMapper cartMapper;

    private final CartItemsService cartItemsService;

    @Override
    @LogAnnotation(type = OperationType.SELECT,description = "查询购物车信息")
    public Result getCarts() {
        Long userId = UserHolder.getUser().getId();
        CartsItemsVO cartsItemsVO = cartMapper.getCarts(userId);
        return Result.success(cartsItemsVO);
    }

    @Override
    @LogAnnotation(type = OperationType.INSERT,description = "添加购物车")
    public Result addItem(CartsDTO cartsDTO) {

        Long userId = UserHolder.getUser().getId();

        Carts carts = lambdaQuery().eq(Carts::getUserId, userId).one();

        if(carts==null){
            log.info("该用户购物车为空,创建购物车");
            carts = new Carts();
            carts.setUserId(userId);
            carts.setCreateTime(LocalDateTime.now());
            carts.setUpdateTime(LocalDateTime.now());
            save(carts);
        }

        // 如果购物车有这个产品，就+1，没有就保存
        CartsItems cartsItems1 = cartItemsService.lambdaQuery()
                .eq(CartsItems::getCartId,carts.getId())
                .eq(CartsItems::getProductId,cartsDTO.getProductId()).one();

        if(cartsItems1 !=null){
            log.info("购物车已有此物品，+"+cartsDTO.getQuantity());
            cartItemsService.lambdaUpdate()
                    .eq(CartsItems::getCartId,carts.getId())
                    .eq(CartsItems::getProductId,cartsDTO.getProductId())
                    .set(CartsItems::getQuantity,cartsItems1.getQuantity()+cartsDTO.getQuantity())
                    .update();
            return Result.success();
        }


        log.info("购物车没有此物品,新增");
        // 添加购物商品
        CartsItems cartsItems = new CartsItems();
        cartsItems.setCartId(carts.getId());
        cartsItems.setQuantity(cartsDTO.getQuantity());
        cartsItems.setProductId(cartsDTO.getProductId());
        cartsItems.setCreatedTime(LocalDateTime.now());
        cartsItems.setUpdatedTime(LocalDateTime.now());
        cartItemsService.save(cartsItems);

        return Result.success();
    }

    @Override
    @LogAnnotation(type = OperationType.UPDATE,description = "更新购物车")
    public Result updateCartItem(CartsDTO cartsDTO) {
        final boolean updated = cartItemsService
                .lambdaUpdate()
                .set(CartsItems::getQuantity, cartsDTO.getQuantity())
                .eq(CartsItems::getId, cartsDTO.getCartItemsId())
                .eq(CartsItems::getProductId, cartsDTO.getProductId())
                .update();
        if(!updated){
            return Result.error("更新失败!");
        }
        return Result.success();
    }
}
