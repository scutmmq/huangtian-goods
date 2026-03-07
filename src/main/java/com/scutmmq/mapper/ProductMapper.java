package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.vo.ProductVO;
import com.scutmmq.vo.ProductDetailVO;
import com.scutmmq.entity.Product;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    List<ProductVO> getProducts(Long categoryId, Long merchantId, String keyword, Integer minPrice, Integer maxPrice, Integer isActive);

    ProductDetailVO getProductDetail(Long productId);

    List<ProductVO> getListByIds(List<Long> productIds);

    List<ProductVO> getProductsOfMe(Long merchantId);
}
