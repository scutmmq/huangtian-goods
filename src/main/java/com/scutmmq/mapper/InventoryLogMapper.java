package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.entity.InventoryLog;
import com.scutmmq.vo.InventoryLogVO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryLogMapper extends BaseMapper<InventoryLog> {
    List<InventoryLogVO> getLogByProductIds(List<Long> productIds, String changeType, LocalDateTime startDate, LocalDateTime endDate);
}
