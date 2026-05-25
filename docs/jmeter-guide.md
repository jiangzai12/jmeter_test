# JMeter 压测操作指南

## 前置准备

1. **启动应用**
   ```bash
   # 先修改 application.yml 中的数据库连接信息
   # 在 MySQL 中创建数据库:
   # CREATE DATABASE jmeter_shop DEFAULT CHARSET utf8mb4;
   
   cd d:\softest\jmeter_test
   mvn spring-boot:run
   ```

2. **验证应用启动**
   - 浏览器打开: http://localhost:8080/swagger-ui.html
   - 确认 API 文档显示正常

3. **确认初始数据**
   - 10 个测试用户 (user01 ~ user10, 密码: 123456)
   - 1000 个商品 (5 个分类, 每类 200 个)

---

## 压测场景设计

### 场景 1: 用户登录（测试无索引查询）

| 参数 | 值 |
|------|-----|
| 线程数 | 100 |
| Ramp-Up | 10s |
| 循环次数 | 50 |
| 请求 | POST `/api/users/login` |

**JMeter 配置步骤:**
1. 新建 Test Plan → 添加 Thread Group
2. 设置线程数=100, Ramp-Up=10, 循环次数=50
3. 添加 HTTP Request:
   - Method: POST
   - Path: `/api/users/login`
   - Body: `{"username":"user01","password":"123456"}`
   - Content-Type: `application/json`
4. 添加 Header Manager → `Content-Type: application/json`
5. 添加监听器: View Results Tree, Summary Report, Aggregate Report

**预期瓶颈:** username 字段无索引，随并发增加响应时间显著上升。

---

### 场景 2: 商品列表（测试无分页全量查询）

| 参数 | 值 |
|------|-----|
| 线程数 | 50 |
| Ramp-Up | 5s |
| 循环次数 | 100 |
| 请求 | GET `/api/products` |

**预期瓶颈:** 每次返回全部 1000 条商品，网络传输大、序列化慢。

---

### 场景 3: 商品搜索（测试 LIKE 全表扫描）

| 参数 | 值 |
|------|-----|
| 线程数 | 50 |
| Ramp-Up | 5s |
| 循环次数 | 100 |
| 请求 | GET `/api/products/search?keyword=手机` |

**预期瓶颈:** `LIKE '%手机%'` 导致全表扫描，无法使用索引。

---

### 场景 4: 商品详情（测试无缓存高频查询）

| 参数 | 值 |
|------|-----|
| 线程数 | 200 |
| Ramp-Up | 10s |
| 循环次数 | 200 |
| 请求 | GET `/api/products/1` |

**预期瓶颈:** 同一商品高频查询全部命中数据库，连接池成为瓶颈。

---

### 场景 5: 创建订单（测试库存竞争 + 连接池压力）

| 参数 | 值 |
|------|-----|
| 线程数 | 50 |
| Ramp-Up | 5s |
| 循环次数 | 20 |
| 请求 | POST `/api/orders` |

```json
{
  "userId": 1,
  "items": [
    {"productId": 1, "quantity": 1},
    {"productId": 2, "quantity": 2}
  ]
}
```

**预期瓶颈:** 
- 库存读-改-写无锁，并发下可能超卖
- 事务持有连接时间长，连接池(仅5个)很快耗尽

---

### 场景 6: 用户订单列表（测试 N+1 查询）

| 参数 | 值 |
|------|-----|
| 线程数 | 50 |
| Ramp-Up | 5s |
| 循环次数 | 50 |
| 请求 | GET `/api/orders/user/1` |

**前提:** 先通过场景 5 创建一批订单。

**预期瓶颈:** 每个订单额外查询一次 order_items 表，订单越多查询次数越多。

---

## 关键监控指标

| 指标 | 说明 | 关注阈值 |
|------|------|----------|
| Avg Response Time | 平均响应时间 | > 500ms 需关注 |
| 99th Percentile | P99 响应时间 | > 2000ms 有问题 |
| Throughput | 吞吐量 (req/s) | 越高越好 |
| Error % | 错误率 | > 1% 需排查 |

## 辅助工具

- **MySQL 慢查询日志:** 在 MySQL 配置中开启 `slow_query_log=ON, long_query_time=0.5`
- **EXPLAIN 分析:** 对慢 SQL 执行 `EXPLAIN` 查看执行计划
- **JVM 监控:** 使用 VisualVM 或 Arthas 监控 JVM 状态
- **连接池监控:** 观察 HikariCP 日志中的连接等待时间
