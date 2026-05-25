package com.example.shop.controller;

import com.example.shop.dto.ApiResponse;
import com.example.shop.dto.CreateOrderRequest;
import com.example.shop.entity.Order;
import com.example.shop.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "订单管理", description = "创建和查询订单")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单", description = "压测关注点: 库存扣减无并发控制（超卖问题）")
    @PostMapping
    public ApiResponse<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ApiResponse.success("下单成功", order);
    }

    @Operation(summary = "用户订单列表（已优化: JOIN FETCH）", description = "一次查询加载订单和订单项")
    @GetMapping("/user/{userId}")
    public ApiResponse<List<Order>> findByUserId(@PathVariable Long userId) {
        return ApiResponse.success(orderService.findByUserId(userId));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    public ApiResponse<Order> findById(@PathVariable Long id) {
        return ApiResponse.success(orderService.findById(id));
    }
}
