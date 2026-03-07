package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.scutmmq.enums.OperationType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String operationUser;
    
    private Long userId;
    
    private String operationModule;
    
    private OperationType operationType;
    
    private String operationDesc;
    
    private String requestMethod;
    
    private String requestUrl;
    
    private String requestParam;
    
    private String responseResult;
    
    private String operationIp;
    
    private LocalDateTime operationTime;
    
    private Long executionTime;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}