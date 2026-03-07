package com.scutmmq.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.dto.InventoryDTO;
import com.scutmmq.entity.Product;
import com.scutmmq.entity.Result;

public interface ProductService extends IService<Product> {
    Result getProducts(Long categoryId, Long merchantId, String keyword, Integer minPrice, Integer maxPrice, Integer isActive, Integer page, Integer pageSize);

    Result getProductDetail(Long productId);

    Result addProduct(Product product);

    Result updateProducts(Product product);

    Result likeProduct(Long productId);

    Result getLikedProducts();

    Result getProductsOfMe();

    Result modifyStockQuantity(InventoryDTO inventoryDTO);
}
