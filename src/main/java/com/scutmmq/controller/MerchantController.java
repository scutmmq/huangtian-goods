package com.scutmmq.controller;

import com.scutmmq.dto.InventoryDTO;
import com.scutmmq.entity.Merchant;
import com.scutmmq.entity.Result;
import com.scutmmq.service.InventoryLogService;
import com.scutmmq.service.MerchantService;
import com.scutmmq.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant")
public class MerchantController {

    private final MerchantService merchantService;

    private final InventoryLogService logService;

    private final ProductService  productService;

    @PostMapping("/register")
    public Result merchantRegister(@RequestBody Merchant merchant){
        return merchantService.addMerchant(merchant);
    }

    /**
     * 获取本人商户信息
     * @return 商家信息
     */
    @GetMapping
    public Result getMerchant(){
        return merchantService.getMerchant();
    }

    @PutMapping
    public Result updateMerchant(@RequestBody Merchant merchant){
        return merchantService.updateMerchant(merchant);
    }

    @GetMapping("/inventory-logs")
    public Result getInventoryLogs(
            @RequestParam(value = "changeType",required = false)String changeType,
            @RequestParam(value = "startDate",required = false)LocalDateTime startDate,
            @RequestParam(value = "endDate",required = false)LocalDateTime endDate){
        return logService.getLogs(changeType,startDate,endDate);
    }

    @PostMapping("/inventory")
    public Result modifyInventory(@RequestBody InventoryDTO inventoryDTO){
        return productService.modifyStockQuantity(inventoryDTO);
    }

}
