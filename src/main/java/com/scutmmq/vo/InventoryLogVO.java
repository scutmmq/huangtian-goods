package com.scutmmq.vo;

import com.scutmmq.enums.ChangeType;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class InventoryLogVO {
    private String productName;
    private ChangeType changeType;
    private Integer changeQuantity;
    private Integer currentQuantity;
    private String reason;
    private Long referenceID;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
}
