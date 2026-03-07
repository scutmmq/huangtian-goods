package com.scutmmq.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.entity.MerchantUser;
import com.scutmmq.mapper.MerchantUserMapper;
import com.scutmmq.service.MerchantUserService;
import org.springframework.stereotype.Service;

@Service
public class MerchantUserServiceImpl extends ServiceImpl<MerchantUserMapper, MerchantUser> implements MerchantUserService {
}
