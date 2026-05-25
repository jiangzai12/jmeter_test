package com.example.shop.controller;

import com.example.shop.dto.ApiResponse;
import com.example.shop.entity.Product;
import com.example.shop.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "商品管理", description = "商品查询和搜索")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "商品列表（已优化: 分页查询）", description = "参数: page=页码(从0开始), size=每页数量(默认20)")
    @GetMapping
    public ApiResponse<Page<Product>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(productService.findAll(page, size));
    }

    @Operation(summary = "商品详情", description = "压测关注点: 无缓存, 每次查库")
    @GetMapping("/{id}")
    public ApiResponse<Product> findById(@PathVariable Long id) {
        return ApiResponse.success(productService.findById(id));
    }

    @Operation(summary = "商品搜索", description = "压测关注点: LIKE '%keyword%' 全表扫描")
    @GetMapping("/search")
    public ApiResponse<List<Product>> search(@RequestParam String keyword) {
        return ApiResponse.success(productService.search(keyword));
    }

    @Operation(summary = "按分类查询")
    @GetMapping("/category/{category}")
    public ApiResponse<List<Product>> findByCategory(@PathVariable String category) {
        return ApiResponse.success(productService.findByCategory(category));
    }
}
