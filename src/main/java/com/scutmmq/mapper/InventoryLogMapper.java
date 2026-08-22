package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.entity.InventoryLog;
import com.scutmmq.vo.InventoryLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryLogMapper extends BaseMapper<InventoryLog> {
    // C9: 多参数 mapper 必须显式 @Param,否则 XML 引用 #{changeType}/#{startDate}/#{endDate} 取不到值
    List<InventoryLogVO> getLogByProductIds(@Param("productIds") List<Long> productIds,
                                            @Param("changeType") String changeType,
                                            @Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);
}
