package com.scutmmq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scutmmq.vo.ProductVO;
import com.scutmmq.vo.ProductDetailVO;
import com.scutmmq.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    /**
     * 多参数方法必须显式 @Param,否则 MyBatis 编译时不报、运行时按 arg0/arg1/...
     * 绑定,XML 里的 #{categoryId}/#{keyword} 等名字引用取不到值,
     * <if test="keyword != null"> 这类动态 SQL 条件全部静默失败(变成 null → 跳过 WHERE)。
     *
     * <p>C9 事故复盘:2026-08-23 自行车场景下,搜 "自行车" 返回全表前 5(橡皮擦/毛衣/...)
     * 因为 keyword filter 被默默跳过,isActive 仍然生效是因为参数位置刚好被某种 fallback
     * 解析到了。所有带动态 WHERE 的多参数 mapper 都必须 @Param。
     */
    List<ProductVO> getProducts(@Param("categoryId") Long categoryId,
                                @Param("merchantId") Long merchantId,
                                @Param("keyword") String keyword,
                                @Param("minPrice") Integer minPrice,
                                @Param("maxPrice") Integer maxPrice,
                                @Param("isActive") Integer isActive);

    ProductDetailVO getProductDetail(@Param("productId") Long productId);

    List<ProductVO> getListByIds(@Param("productIds") List<Long> productIds);

    List<ProductVO> getProductsOfMe(@Param("merchantId") Long merchantId);
}
