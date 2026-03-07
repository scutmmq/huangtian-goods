package com.scutmmq.controller;

import com.scutmmq.entity.Result;
import com.scutmmq.entity.UserAddress;
import com.scutmmq.service.UserAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserAddressController {

    private final UserAddressService userAddressService;

    /**
     * 获取地址列表
     * @return 地址列表
     */
    @GetMapping("/address")
    public Result getAddress(){
        return userAddressService.getAddress();
    }

    /**
     * 添加地址
     * @param address 地址表单信息
     * @return null
     */
    @PostMapping("/address")
    public Result addAddress(@RequestBody UserAddress address){
        return userAddressService.addAddress(address);
    }

    /**
     * 更新地址
     * @param address 表单数据,从查询到的address中获取到id
     * @return null
     */
    @PutMapping("/address")
    public Result updateAddress(@RequestBody UserAddress address){
        return userAddressService.updateAddress(address);
    }

    /**
     * 删除地址
     * @param id 地址id
     * @return true false
     */
    @DeleteMapping("/address/{id:\\d+}")
    public Result deleteAddress(@PathVariable Long id){
        return Result.success(userAddressService.removeById(id));
    }

    /**
     * 批量删除
     * @param ids ?ids=1,2,3
     * @return true false
     */
    @DeleteMapping("/address")
    public Result deleteAddresses(@RequestParam String ids){
        log.info("收到批量删除地址:{}",ids);
        List<Long> listId = Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
        return Result.success(userAddressService.removeBatchByIds(listId));
    }

    /**
     * 设置默认地址
     * @param addressId
     * @return
     */
    @PutMapping("/address/{addressId}/default")
    public Result defaultAddress(@PathVariable(value = "addressId") Long addressId){
        return userAddressService.defaultAddress(addressId);
    }

    /**
     * 根据Id查询地址
     * @param addressId 地址id
     * @return  地址
     */
    @GetMapping("/address/{addressId}")
    public Result getAddressById(@PathVariable(value = "addressId")Long addressId){
        return userAddressService.getAddressById(addressId);
    }

}
