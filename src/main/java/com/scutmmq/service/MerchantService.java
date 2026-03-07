package com.scutmmq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.entity.Merchant;
import com.scutmmq.entity.Result;

public interface MerchantService extends IService<Merchant> {
    Result addMerchant(Merchant merchant);

    Result getMerchant();

    Result updateMerchant(Merchant merchant);
}
