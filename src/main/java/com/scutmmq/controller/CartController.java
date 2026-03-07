package com.scutmmq.controller;

import com.scutmmq.dto.CartsDTO;
import com.scutmmq.entity.Result;
import com.scutmmq.service.CartItemsService;
import com.scutmmq.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    private  final CartItemsService cartItemsService;

    @GetMapping
    public Result getCarts(){
        return  cartService.getCarts();
    }


    @PostMapping("/items")
    public Result addCartItems(@RequestBody CartsDTO cartsDTO){
        return cartService.addItem(cartsDTO);
    }

    @PutMapping("/items")
    public Result modifyCartItems(@RequestBody CartsDTO cartsDTO){
        return cartService.updateCartItem(cartsDTO);
    }

    @DeleteMapping("/items/{cartItemId}")
    public Result deleteCartItemById(@PathVariable(value = "cartItemId")Long cartItemId){
        return  Result.success(cartItemsService.removeById(cartItemId));
    }

    @DeleteMapping("/items")
    public Result deleteCartItems(@RequestParam(value = "cartItemIds") List<Long> cartItemIds){
        return Result.success(cartItemsService.removeBatchByIds(cartItemIds));
    }

    @DeleteMapping("/{cartId}")
    public Result deleteCartById(@PathVariable(value = "cartId")Long cartId){
        return  Result.success(cartService.removeById(cartId));
    }

}
