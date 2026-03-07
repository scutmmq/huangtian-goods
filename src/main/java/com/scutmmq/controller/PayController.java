package com.scutmmq.controller;

import com.scutmmq.dto.PayDTO;
import com.scutmmq.entity.Result;
import com.scutmmq.service.PayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    /**
     * @param payDTO 支付对象
     * @return null
     */
    @PostMapping("/confirm")
    public Result paid(@RequestBody PayDTO payDTO){
        return payService.paid(payDTO);
    }

}
