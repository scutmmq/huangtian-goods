package com.scutmmq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.entity.Result;
import com.scutmmq.entity.ReturnAudit;

public interface ReturnAuditService extends IService<ReturnAudit> {
    Result getAudits(Long status);

    Result getMerchantAudits(Long status);
}
