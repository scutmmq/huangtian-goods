package com.scutmmq.service;


import com.scutmmq.dto.PayDTO;
import com.scutmmq.entity.Result;

public interface PayService {
    Result paid(PayDTO payDTO);
}
