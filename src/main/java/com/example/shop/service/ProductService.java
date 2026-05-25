package com.example.shop.service;

import com.example.shop.entity.Product;
import com.example.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * ===== 已优化: 添加分页查询 =====
     * 使用 Spring Data 的 Pageable 分页，每次只返回指定数量的商品。
     */
    public Page<Product> findAll(int page, int size) {
        log.debug("查询商品列表: page={}, size={}", page, size);
        return productRepository.findAll(PageRequest.of(page, size));
    }

    /**
     * ===== 已优化: 添加缓存 =====
     * 使用 Spring Cache 缓存商品详情，相同 id 的第二次查询直接命中缓存。
     */
    @Cacheable(value = "products", key = "#id")
    public Product findById(Long id) {
        log.debug("查询商品详情: id={}", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在: " + id));
    }

    /**
     * ===== 性能问题 #3: LIKE '%keyword%' 全表扫描 =====
     * 使用双 % 通配符无法利用 B-Tree 索引，
     * MySQL 只能逐行扫描匹配，数据量大时非常慢。
     */
    public List<Product> search(String keyword) {
        log.debug("搜索商品: keyword={}", keyword);
        return productRepository.searchByKeyword(keyword);
    }

    public List<Product> findByCategory(String category) {
        log.debug("按分类查询商品: category={}", category);
        return productRepository.findByCategory(category);
    }
}
