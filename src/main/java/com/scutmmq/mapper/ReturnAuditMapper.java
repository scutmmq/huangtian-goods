package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.entity.ReturnAudit;
import com.scutmmq.vo.AuditVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReturnAuditMapper extends BaseMapper<ReturnAudit> {
    List<AuditVO> getAudits(Long userId, Long auditStatus);

    List<AuditVO> getMerchantAudits(Long merchantId, Long auditStatus);
}
