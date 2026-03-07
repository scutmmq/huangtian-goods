package com.scutmmq.service;
import org.springframework.stereotype.Service;


@Service

public interface NotificationService {
    void sendToMerchant(Long merchantId, String content);

    void sendToUser(Long userId, String content);
}
