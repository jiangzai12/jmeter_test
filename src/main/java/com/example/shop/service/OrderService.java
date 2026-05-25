package com.example.shop.service;

import com.example.shop.dto.CreateOrderRequest;
import com.example.shop.entity.Order;
import com.example.shop.entity.OrderItem;
import com.example.shop.entity.Product;

import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

import java.util.List;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    private final ProductRepository productRepository;

    /**
     * 创建订单
     * ===== 性能问题 #7: 库存扣减无并发控制 =====
     * 直接读取 stock 再写回，高并发下会出现超卖。
     * 优化方案：使用乐观锁 (@Version) 或 SQL 原子操作 (UPDATE SET stock=stock-N WHERE stock>=N)
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        log.debug("创建订单: userId={}, itemCount={}", request.getUserId(), request.getItems().size());

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setStatus(0);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("商品不存在: " + itemReq.getProductId()));

            // ===== 已优化: 原子操作扣减库存 =====
            int updated = productRepository.deductStock(product.getId(), itemReq.getQuantity());
            if (updated == 0) {
                throw new RuntimeException("库存不足: " + product.getName());
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setPrice(product.getPrice());
            orderItem.setQuantity(itemReq.getQuantity());

            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
            orderItems.add(orderItem);
        }

        order.setTotalAmount(totalAmount);
        order.setItems(orderItems);
        Order savedOrder = orderRepository.save(order);

        log.debug("订单创建成功: orderNo={}, totalAmount={}", savedOrder.getOrderNo(), savedOrder.getTotalAmount());
        return savedOrder;
    }

    /**
     * 查询用户订单列表
     * ===== 已优化: 使用 JOIN FETCH 一次加载, 解决 N+1 =====
     */
    public List<Order> findByUserId(Long userId) {
        log.debug("查询用户订单: userId={}", userId);
        List<Order> orders = orderRepository.findByUserIdWithItems(userId);
        log.debug("查询到 {} 个订单", orders.size());
        return orders;
    }

    public Order findById(Long id) {
        log.debug("查询订单详情: id={}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在: " + id));
        // 触发 lazy 加载
        order.getItems().size();
        return order;
    }
}
