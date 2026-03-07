package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.scutmmq.enums.AuditStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReturnAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId; // 关联订单ID
    private Long userId; // 申请用户ID
    private Long merchantId; // 商家ID（订单所属商家）
    private String returnReason; // 用户退货理由（如：商品质量问题、尺寸不符）
    private String returnImages; // 凭证图片（可选，用户上传）
    private String auditReason; // 商家审核理由（拒绝时必须填写）
    private AuditStatus auditStatus; // 0-待审核，1-已批准，2-已拒绝
    private LocalDateTime applyTime; // 申请时间（自动填充）
    private LocalDateTime auditTime; // 审核时间（审核时填充）
}
