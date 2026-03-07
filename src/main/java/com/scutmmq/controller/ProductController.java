package com.scutmmq.controller;

import cn.hutool.http.useragent.OS;
import com.scutmmq.entity.Product;
import com.scutmmq.entity.Result;
import com.scutmmq.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {


    private  final  ProductService productService;

    /**
     * 查询商品
     * @param categoryId 分类id
     * @param keyword 关键词搜索
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param isActive 是否上架
     * @param page 页码
     * @param pageSize 一页有几条
     * @return pageResult
     */
    @GetMapping
    public Result getProducts(
            @RequestParam(value = "categoryId",required = false) Long categoryId,
            @RequestParam(value = "merchantId",required = false) Long merchantId,
            @RequestParam(value = "keyword",required = false)String keyword,
            @RequestParam(value = "minPrice",required = false)Integer minPrice,
            @RequestParam(value = "maxPrice",required = false)Integer maxPrice,
            @RequestParam(value = "isActive",required = false)Integer isActive,
            @RequestParam(value = "page",defaultValue = "1")Integer page,
            @RequestParam(value = "pageSize",defaultValue = "10")Integer pageSize
    ){
        return productService.getProducts(categoryId,merchantId,keyword,minPrice,maxPrice,isActive,page,pageSize);
    }

    /**
     * 商品详情
     * @param productId 商品id
     * @return ProductDetailVO
     */
    @GetMapping("/{productId}")
    public Result getProductDetail(@PathVariable Long productId){
        return productService.getProductDetail(productId);
    }


    /**
     * 添加商品
     * @param product
     *   "name": "string, required, 商品名称",
     *   "description": "string, optional, 商品描述",
     *   "price": "number, required, 价格",
     *   "stockQuantity": "number, required, 库存",
     *   "categoryId": "number, required, 分类ID",
     *   "sku": "string, required, SKU",
     *   "imageUrl": "string, optional, 图片URL"
     * @return true
     */
    @PostMapping
    public Result addProducts(@RequestBody Product product){
        return productService.addProduct(product);
    }

    /**
     * 更新商品
     * @param product productForm
     * @return null
     */
    @PutMapping
    public Result updateProducts(@RequestBody Product product){
        return productService.updateProducts(product);
    }

    /**
     * 收藏商品
     * @param productId 商品id
     * @return true
     */
    @PutMapping("/like/{productId}")
    public Result likeProduct(@PathVariable Long productId){
        return productService.likeProduct(productId);
    }


    /**
     * 查看收藏的商品
     * @return ProductVoList
     */
    @GetMapping("/likes")
    public Result getLikedProducts(){
        return productService.getLikedProducts();
    }

    /**
     * 查看本人店铺的商品
     * @return ProductVOList
     */
    @GetMapping("/of/me")
    public Result getProductsOfMe(){
        return productService.getProductsOfMe();
    }
}
