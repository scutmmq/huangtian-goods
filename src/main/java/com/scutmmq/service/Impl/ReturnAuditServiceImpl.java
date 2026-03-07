package com.scutmmq.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.entity.Result;
import com.scutmmq.entity.ReturnAudit;
import com.scutmmq.mapper.MerchantUserMapper;
import com.scutmmq.mapper.ReturnAuditMapper;
import com.scutmmq.service.ReturnAuditService;
import com.scutmmq.utils.UserHolder;
import com.scutmmq.vo.AuditVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReturnAuditServiceImpl extends ServiceImpl<ReturnAuditMapper, ReturnAudit> implements ReturnAuditService {

    private final ReturnAuditMapper auditMapper;

    private final MerchantUserMapper merchantUserMapper;

    @Override
    public Result getAudits(Long status) {
        Long userId = UserHolder.getUser().getId();
        List<AuditVO> list = auditMapper.getAudits(userId,status);
        return Result.success(list);
    }

    @Override
    public Result getMerchantAudits(Long status) {
        Long userId = UserHolder.getUser().getId();
        final Long merchantId = merchantUserMapper.getMerchantIdByUserId(userId);
        List<AuditVO> list =  auditMapper.getMerchantAudits(merchantId,status);
        return Result.success(list);
    }
}
