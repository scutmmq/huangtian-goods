package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.entity.MerchantUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantUserMapper extends BaseMapper<MerchantUser> {
    Long getMerchantIdByUserId(Long userId);
}
