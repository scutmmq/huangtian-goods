package com.scutmmq.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.entity.OperationLog;
import com.scutmmq.mapper.OperationLogMapper;
import com.scutmmq.service.OperationLogService;
import org.springframework.stereotype.Service;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {
}