package com.scutmmq.controller;

import com.scutmmq.entity.Result;
import com.scutmmq.enums.AuditStatus;
import com.scutmmq.service.ReturnAuditService;
import lombok.RequiredArgsConstructor;
import org.redisson.liveobject.misc.AdvBeanCopy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuditController {

    private final ReturnAuditService auditService;

    @GetMapping("/audit")
    public Result getAudits(@RequestParam(value = "auditStatus",required = false)Long status){
        return auditService.getAudits(status);
    }

    @GetMapping("/audit/merchant")
    public Result getMerchantAudits(@RequestParam(value = "auditStatus",required = false)Long status){
        return auditService.getMerchantAudits(status);
    }
}
