package com.scutmmq.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.entity.InventoryLog;
import com.scutmmq.entity.Result;

import java.time.LocalDateTime;

public interface InventoryLogService extends IService<InventoryLog> {
    Result getLogs(String changeType, LocalDateTime startDate, LocalDateTime endDate);
}
