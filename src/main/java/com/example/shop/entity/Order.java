package com.example.shop.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== 性能问题 #1: user_id 无索引 =====
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_no", nullable = false, length = 50)
    private String orderNo;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消
    @Column(nullable = false)
    private Integer status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ===== 性能问题 #5: 默认 LAZY 加载会导致 N+1 =====
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> items;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = 0;
        }
    }
}
