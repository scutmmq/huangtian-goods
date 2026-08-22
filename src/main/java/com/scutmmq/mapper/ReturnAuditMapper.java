package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.entity.ReturnAudit;
import com.scutmmq.vo.AuditVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReturnAuditMapper extends BaseMapper<ReturnAudit> {
    // C9: 多参数 mapper 必须显式 @Param
    List<AuditVO> getAudits(@Param("userId") Long userId, @Param("auditStatus") Long auditStatus);

    List<AuditVO> getMerchantAudits(@Param("merchantId") Long merchantId, @Param("auditStatus") Long auditStatus);
}
