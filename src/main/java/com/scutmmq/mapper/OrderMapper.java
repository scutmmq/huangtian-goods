package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.entity.Orders;
import com.scutmmq.vo.MerchantOrdersVO;
import com.scutmmq.vo.UserOrdersVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Orders> {
    List<UserOrdersVO> getUserOrders(@Param("status")String status, @Param("userId")Long userId);
    
    List<MerchantOrdersVO> getMerchantOrders(@Param("status")String status, @Param("merchantId")Long merchantId);
    
    List<MerchantOrdersVO> getMerchantOrdersWithPagination(@Param("status")String status, @Param("merchantId")Long merchantId);
}