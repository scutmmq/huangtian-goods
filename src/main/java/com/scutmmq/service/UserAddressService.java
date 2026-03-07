package com.scutmmq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.entity.Result;
import com.scutmmq.entity.UserAddress;

public interface UserAddressService extends IService<UserAddress>{
    Result getAddress();

    Result addAddress(UserAddress address);

    Result updateAddress(UserAddress address);

    Result defaultAddress(Long addressId);

    Result getAddressById(Long addressId);
}
