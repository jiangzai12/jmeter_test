# 性能调优步骤指南

本指南按照优先级从高到低排列，每一步都是**独立可验证**的，调完一个问题立刻用 JMeter 重新压测对比效果。

---

## 调优方法论

```
发现问题 → 定位根因 → 实施修复 → 验证效果 → 记录数据
```

每次只改一个变量，对比前后的 TPS、P99、错误率变化。

---

## 第 1 步: 添加数据库索引

### 发现方式
- JMeter 压测登录接口，观察响应时间
- MySQL 开启慢查询日志: `SET GLOBAL slow_query_log=ON; SET GLOBAL long_query_time=0.1;`
- 对慢 SQL 执行 `EXPLAIN`，发现 `type=ALL`（全表扫描）

### 定位根因
```sql
EXPLAIN SELECT * FROM users WHERE username = 'user01';
-- type=ALL, rows=很多 → 全表扫描
```

### 修复方案
```sql
-- 用户登录优化
ALTER TABLE users ADD INDEX idx_username (username);

-- 订单查询优化
ALTER TABLE orders ADD INDEX idx_user_id (user_id);

-- 商品分类查询优化
ALTER TABLE products ADD INDEX idx_category (category);

-- 订单项查询优化
ALTER TABLE order_items ADD INDEX idx_order_id (order_id);
```

### 验证
```sql
EXPLAIN SELECT * FROM users WHERE username = 'user01';
-- type=ref, key=idx_username → 使用索引
```
再跑一次 JMeter 对比 TPS 提升。

---

## 第 2 步: 商品列表添加分页

### 发现方式
- 压测 `GET /api/products`，观察响应体大小和响应时间
- 每次返回全部 1000 条数据，传输量大

### 定位根因
- 无分页控制，`findAll()` 全量返回

### 修复方案

**ProductController.java:**
```java
@GetMapping
public ApiResponse<Page<Product>> findAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(productService.findAll(page, size));
}
```

**ProductService.java:**
```java
public Page<Product> findAll(int page, int size) {
    return productRepository.findAll(PageRequest.of(page, size));
}
```

### 验证
对比分页前后: 响应体大小、响应时间、TPS。     tps从150提升到230，提升了50%

---

## 第 3 步: 修复 N+1 查询

### 发现方式
- 压测 `GET /api/orders/user/1`
- 开启 Hibernate SQL 日志，观察产生了大量 SELECT 语句
- 10 个订单 = 1(查订单列表) + 10(每个订单查items) = 11 次查询

### 定位根因
```
Hibernate: SELECT * FROM orders WHERE user_id = ?
Hibernate: SELECT * FROM order_items WHERE order_id = ?  -- 重复 N 次
```

### 修复方案

**OrderRepository.java:**
```java
@Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.userId = :userId")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
```

**OrderService.java:**
```java
public List<Order> findByUserId(Long userId) {
    return orderRepository.findByUserIdWithItems(userId);
}
```

### 验证
对比修复前后的 SQL 数量和响应时间。

---

## 第 4 步: 添加商品缓存

### 发现方式
- 压测 `GET /api/products/{id}`，200 并发下连接池耗尽
- HikariCP 日志出现 `Connection is not available` 警告

### 定位根因
- 每次查询商品详情都访问数据库
- 连接池只有 5 个连接，高并发下不够用

### 修复方案

**1. 添加依赖 (pom.xml):**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

**2. 启用缓存 (ShopApplication.java):**
```java
@SpringBootApplication
@EnableCaching
public class ShopApplication { ... }
```

**3. 添加缓存注解 (ProductService.java):**
```java
@Cacheable(value = "products", key = "#id")
public Product findById(Long id) {
    return productRepository.findById(id).orElseThrow(...);
}
```

> **进阶:** 后续可以将内存缓存替换为 Redis 分布式缓存。

### 验证
对比缓存前后的 TPS 和数据库连接使用率。

---

## 第 5 步: 调大连接池

### 发现方式
- 高并发下出现连接超时错误
- HikariCP 日志: `HikariPool - Connection is not available, request timed out`

### 定位根因
- `maximum-pool-size=5` 太小，高并发下连接不够分配

### 修复方案

**application.yml:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20    # 从 5 调到 20
      minimum-idle: 10
      connection-timeout: 10000
      idle-timeout: 300000
      max-lifetime: 900000
```

### 验证
对比调整前后的连接等待时间和错误率。

---

## 第 6 步: 降低日志级别

### 发现方式
- 观察 CPU 使用率和磁盘 IO
- DEBUG 级别日志每个请求产生大量输出

### 修复方案

**application.yml:**
```yaml
logging:
  level:
    root: WARN
    com.example.shop: INFO
    org.hibernate.SQL: WARN
```
同时关闭 Hibernate 统计:
```yaml
spring:
  jpa:
    show-sql: false
    properties:
      hibernate:
        generate_statistics: false
```

### 验证
对比调整前后的 TPS (通常有 10-30% 提升)。

---

## 第 7 步: 库存扣减加锁

### 发现方式
- 压测创建订单接口，并发 50 线程各买 1 件
- 检查商品库存: 应该减少 50，实际减少 < 50（超卖）

### 定位根因
- 读-改-写无锁，并发时多个线程读到相同的 stock 值

### 修复方案

**ProductRepository.java:**
```java
@Modifying
@Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
int deductStock(@Param("id") Long id, @Param("quantity") int quantity);
```

**OrderService.java:**
```java
int updated = productRepository.deductStock(product.getId(), itemReq.getQuantity());
if (updated == 0) {
    throw new RuntimeException("库存不足: " + product.getName());
}
```

### 验证
并发 50 线程各下单 1 件，检查最终库存是否准确。

---

## 第 8 步: 优化搜索（进阶）

### 修复方案
```sql
-- 前缀匹配（能走索引）
ALTER TABLE products ADD INDEX idx_name (name);
-- 将 LIKE '%keyword%' 改为 LIKE 'keyword%'

-- 或者使用全文索引
ALTER TABLE products ADD FULLTEXT INDEX ft_name_desc (name, description) WITH PARSER ngram;
```

---

## 调优效果记录表

每次调优后填写此表，建立量化对比:

| 优化项 | 调优前 TPS | 调优后 TPS | 调优前 P99 | 调优后 P99 | 提升 |
|--------|-----------|-----------|-----------|-----------|------|
| 添加索引 | | | | | |
| 分页查询 | 156| 224| | | |
| 修复 N+1 | 28.2| 195| 3750| 56| 6.8倍|
| 添加缓存 | 402| 1010|515 | 201| 2倍|
| 调大连接池 | 1010| 1065| 201| 205| |
| 降低日志 | 1065| 3986| 205| 1| |
| 库存加锁 | | | | | |      20线程并发，减少量小于20，出现超卖  修复后正常 
| 优化搜索 | | | | | |
