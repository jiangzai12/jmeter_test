# 电商系统 — JMeter 压测调优实战项目

一个专为 **JMeter 性能压测** 和 **性能调优学习** 设计的简易电商系统。  
系统中故意埋入了 8 个典型性能问题，供你通过 **压测发现 → 定位根因 → 实施修复 → 验证效果** 的循环掌握调优方法论。

---

## 技术栈

| 组件 | 选型 | 说明 |
|------|------|------|
| 语言 | Java 21 | LTS 版本 |
| 框架 | Spring Boot 3.4 | 主流微服务框架 |
| ORM | Spring Data JPA (Hibernate) | 对象关系映射 |
| 数据库 | MySQL 8 | 关系型数据库 |
| 构建工具 | Maven | 依赖管理与构建 |
| 连接池 | HikariCP | Spring Boot 默认连接池 |
| API 文档 | SpringDoc OpenAPI 2.8 | 自动生成 Swagger UI |
| 工具库 | Lombok | 减少样板代码 |

---

## 项目架构

### 整体架构图

```
┌─────────────┐     HTTP/REST      ┌──────────────────────────────────────┐
│   JMeter    │ ─────────────────▶ │          Spring Boot 应用             │
│  (压测工具)  │ ◀───────────────── │                                      │
└─────────────┘    JSON Response   │  ┌──────────┐                        │
                                   │  │Controller│ ← REST API 入口        │
┌─────────────┐                    │  └────┬─────┘                        │
│ Swagger UI  │ ──── :8080 ──────▶ │       │                              │
│ (API 文档)  │                    │  ┌────▼─────┐                        │
└─────────────┘                    │  │ Service  │ ← 业务逻辑(含性能问题)  │
                                   │  └────┬─────┘                        │
                                   │       │                              │
                                   │  ┌────▼──────┐                       │
                                   │  │Repository │ ← JPA 数据访问        │
                                   │  └────┬──────┘                       │
                                   │       │                              │
                                   └───────┼──────────────────────────────┘
                                           │ JDBC (HikariCP)
                                   ┌───────▼──────┐
                                   │    MySQL     │
                                   │  jmeter_shop │
                                   └──────────────┘
```

### 分层说明

| 层 | 包路径 | 职责 |
|----|--------|------|
| **Controller** | `com.example.shop.controller` | 接收 HTTP 请求，参数校验，调用 Service，返回统一响应 |
| **Service** | `com.example.shop.service` | 核心业务逻辑，事务管理。**性能问题集中在此层** |
| **Repository** | `com.example.shop.repository` | JPA 数据访问接口，自动生成 SQL |
| **Entity** | `com.example.shop.entity` | 数据库表映射的 Java 实体类 |
| **DTO** | `com.example.shop.dto` | 请求/响应数据传输对象 |
| **Config** | `com.example.shop.config` | Swagger 配置、数据初始化器 |

---

## 目录结构

```
d:\softest\jmeter_test\
│
├── pom.xml                                          # Maven 项目配置
├── README.md                                        # 本文档
│
├── src/main/java/com/example/shop/
│   ├── ShopApplication.java                         # Spring Boot 启动入口
│   │
│   ├── entity/                                      # 实体类 (对应数据库表)
│   │   ├── User.java                                #   用户表 users
│   │   ├── Product.java                             #   商品表 products
│   │   ├── Order.java                               #   订单表 orders
│   │   └── OrderItem.java                           #   订单项表 order_items
│   │
│   ├── repository/                                  # 数据访问层 (JPA Repository)
│   │   ├── UserRepository.java                      #   用户查询
│   │   ├── ProductRepository.java                   #   商品查询 (含 LIKE 搜索)
│   │   ├── OrderRepository.java                     #   订单查询
│   │   └── OrderItemRepository.java                 #   订单项查询
│   │
│   ├── service/                                     # 业务逻辑层 (含故意的性能问题)
│   │   ├── UserService.java                         #   注册、登录
│   │   ├── ProductService.java                      #   商品列表、详情、搜索
│   │   └── OrderService.java                        #   创建订单、查询订单
│   │
│   ├── controller/                                  # REST API 控制器
│   │   ├── UserController.java                      #   /api/users/**
│   │   ├── ProductController.java                   #   /api/products/**
│   │   └── OrderController.java                     #   /api/orders/**
│   │
│   ├── dto/                                         # 数据传输对象
│   │   ├── RegisterRequest.java                     #   注册请求体
│   │   ├── LoginRequest.java                        #   登录请求体
│   │   ├── CreateOrderRequest.java                  #   下单请求体
│   │   └── ApiResponse.java                         #   统一响应包装
│   │
│   └── config/                                      # 配置类
│       ├── SwaggerConfig.java                       #   OpenAPI 文档配置
│       └── DataInitializer.java                     #   启动时自动初始化测试数据
│
├── src/main/resources/
│   ├── application.yml                              # 应用配置 (DB连接/连接池/日志)
│   └── data.sql                                     # (已废弃, 由 DataInitializer 替代)
│
└── docs/                                            # 文档
    ├── jmeter-guide.md                              # JMeter 压测操作指南
    └── tuning-guide.md                              # 性能调优步骤指南
```

---

## 数据库设计

### ER 关系

```
User (1) ──────── (N) Order (1) ──────── (N) OrderItem
                                                 │
Product (1) ─────────────────────────────── (N) ──┘
```

### 表结构

#### users — 用户表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK, 自增) | 用户ID |
| username | VARCHAR(50) | 用户名（**无索引** ⚠️） |
| password | VARCHAR(100) | 密码（明文存储） |
| email | VARCHAR(100) | 邮箱 |
| phone | VARCHAR(20) | 手机号 |
| created_at | DATETIME | 创建时间 |

#### products — 商品表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK, 自增) | 商品ID |
| name | VARCHAR(200) | 商品名称（**无索引** ⚠️） |
| description | TEXT | 商品描述 |
| price | DECIMAL(10,2) | 价格 |
| stock | INT | 库存（**无并发控制** ⚠️） |
| category | VARCHAR(50) | 分类（**无索引** ⚠️） |
| created_at | DATETIME | 创建时间 |

#### orders — 订单表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK, 自增) | 订单ID |
| user_id | BIGINT | 用户ID（**无索引** ⚠️） |
| order_no | VARCHAR(50) | 订单编号 (UUID) |
| total_amount | DECIMAL(10,2) | 总金额 |
| status | INT | 0待支付 1已支付 2已发货 3已完成 4已取消 |
| created_at | DATETIME | 创建时间 |

#### order_items — 订单项表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK, 自增) | 订单项ID |
| order_id | BIGINT | 关联订单ID |
| product_id | BIGINT | 关联商品ID |
| product_name | VARCHAR(200) | 商品名称（冗余存储） |
| price | DECIMAL(10,2) | 下单时价格 |
| quantity | INT | 购买数量 |

> ⚠️ 标记的字段在初始版本中故意没有添加索引，这是压测要发现的性能瓶颈之一。

---

## REST API 接口

### 用户模块 `/api/users`

| 方法 | 路径 | 说明 | 请求体示例 |
|------|------|------|-----------|
| POST | `/api/users/register` | 用户注册 | `{"username":"test","password":"123456","email":"t@t.com","phone":"138xxx"}` |
| POST | `/api/users/login` | 用户登录 | `{"username":"user01","password":"123456"}` |

### 商品模块 `/api/products`

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `/api/products` | 商品列表（全量无分页） | 无 |
| GET | `/api/products/{id}` | 商品详情（无缓存） | 路径参数: id |
| GET | `/api/products/search?keyword=xxx` | 商品搜索（LIKE全表扫描） | 查询参数: keyword |
| GET | `/api/products/category/{category}` | 按分类查询 | 路径参数: category (手机/电脑/家电/服装/食品) |

### 订单模块 `/api/orders`

| 方法 | 路径 | 说明 | 请求体示例 |
|------|------|------|-----------|
| POST | `/api/orders` | 创建订单 | 见下方 |
| GET | `/api/orders/{id}` | 订单详情 | 路径参数: id |
| GET | `/api/orders/user/{userId}` | 用户订单列表（N+1查询） | 路径参数: userId |

**创建订单请求体:**
```json
{
  "userId": 1,
  "items": [
    { "productId": 1, "quantity": 1 },
    { "productId": 2, "quantity": 2 }
  ]
}
```

**统一响应格式:**
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

---

## 快速开始

### 前置要求

- JDK 21 已安装
- Maven 已安装并配置环境变量
- MySQL 8 已安装并运行

### 第 1 步: 创建数据库

```sql
CREATE DATABASE jmeter_shop DEFAULT CHARSET utf8mb4;
```

### 第 2 步: 修改数据库配置

编辑 `src/main/resources/application.yml`，修改数据库连接信息:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/jmeter_shop?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: 你的MySQL密码
```

### 第 3 步: 编译项目

```bash
cd d:\softest\jmeter_test
mvn clean compile
```

### 第 4 步: 启动应用

```bash
mvn spring-boot:run
```

启动后会自动:
1. 根据 Entity 类创建数据库表 (`ddl-auto: update`)
2. 通过 `DataInitializer` 插入 10 个测试用户 + 1000 个商品

### 第 5 步: 验证

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API 文档 JSON**: http://localhost:8080/v3/api-docs

---

## 常用命令参考

### Maven 命令

```bash
# 编译项目（不运行测试）
mvn clean compile

# 打包为可执行 JAR
mvn clean package -DskipTests

# 运行应用（开发模式）
mvn spring-boot:run

# 运行打包后的 JAR
java -jar target/shop-1.0.0.jar

# 运行时指定配置参数（覆盖 application.yml）
java -jar target/shop-1.0.0.jar --server.port=9090
java -jar target/shop-1.0.0.jar --spring.datasource.password=newpwd

# 查看依赖树
mvn dependency:tree

# 清理构建产物
mvn clean
```

### MySQL 诊断命令

```sql
-- 查看所有表
USE jmeter_shop;
SHOW TABLES;

-- 查看表结构
DESC users;
DESC products;
DESC orders;
DESC order_items;

-- 查看表数据量
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM products;
SELECT COUNT(*) FROM orders;

-- 查看表索引（初始应该只有主键索引）
SHOW INDEX FROM users;
SHOW INDEX FROM products;
SHOW INDEX FROM orders;

-- 开启慢查询日志（定位慢 SQL）
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.1;    -- 超过 0.1 秒即记录
SHOW VARIABLES LIKE 'slow_query_log_file';  -- 查看日志文件路径

-- 分析 SQL 执行计划（核心调优手段）
EXPLAIN SELECT * FROM users WHERE username = 'user01';
EXPLAIN SELECT * FROM products WHERE name LIKE '%手机%';
EXPLAIN SELECT * FROM orders WHERE user_id = 1;

-- 查看当前数据库连接数
SHOW STATUS LIKE 'Threads_connected';
SHOW PROCESSLIST;

-- 添加索引（调优时使用）
ALTER TABLE users ADD INDEX idx_username (username);
ALTER TABLE orders ADD INDEX idx_user_id (user_id);
ALTER TABLE products ADD INDEX idx_category (category);
ALTER TABLE order_items ADD INDEX idx_order_id (order_id);
```

### curl 快速测试命令

```bash
# 用户注册
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","password":"123456","email":"new@test.com"}'

# 用户登录
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user01","password":"123456"}'

# 商品列表
curl http://localhost:8080/api/products

# 商品详情
curl http://localhost:8080/api/products/1

# 商品搜索
curl "http://localhost:8080/api/products/search?keyword=手机"

# 按分类查询
curl http://localhost:8080/api/products/category/电脑

# 创建订单
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"items":[{"productId":1,"quantity":1},{"productId":2,"quantity":2}]}'

# 用户订单列表
curl http://localhost:8080/api/orders/user/1

# 订单详情
curl http://localhost:8080/api/orders/1
```

> **Windows PowerShell 用户注意**: 如果 curl 命令报错，可以换用 Swagger UI 页面直接测试，或使用 Postman。

---

## 故意埋入的 8 个性能问题

### 问题总览

| # | 问题 | 影响 | 所在文件 | 调优难度 |
|---|------|------|----------|---------|
| 1 | 无数据库索引 | 全表扫描，查询慢 | Entity 类 / DDL | ⭐ |
| 2 | 商品列表无分页 | 数据量大时响应慢、内存高 | `ProductService.findAll()` | ⭐ |
| 3 | LIKE '%keyword%' | 无法使用索引，全表扫描 | `ProductRepository.searchByKeyword()` | ⭐⭐ |
| 4 | 商品详情无缓存 | 热点数据反复查库 | `ProductService.findById()` | ⭐⭐ |
| 5 | 订单列表 N+1 查询 | N 个订单产生 N+1 次 SQL | `OrderService.findByUserId()` | ⭐⭐ |
| 6 | 连接池过小 (5) | 高并发连接等待超时 | `application.yml` | ⭐ |
| 7 | 库存扣减无锁 | 并发超卖 | `OrderService.createOrder()` | ⭐⭐⭐ |
| 8 | 全局 DEBUG 日志 | CPU/磁盘IO开销大 | `application.yml` | ⭐ |

### 问题详解

#### 问题 1: 无数据库索引
- **位置**: 所有 Entity 类都未添加 `@Index` 注解
- **表现**: 用户登录按 username 查询、订单按 user_id 查询等都会全表扫描
- **验证**: `EXPLAIN SELECT * FROM users WHERE username='user01'` → `type=ALL`

#### 问题 2: 商品列表无分页
- **位置**: `ProductService.findAll()` 直接调用 `productRepository.findAll()`
- **表现**: 每次请求返回全部 1000 条商品，响应体约 200KB+
- **验证**: 观察响应体大小和响应时间

#### 问题 3: LIKE '%keyword%' 全表扫描
- **位置**: `ProductRepository.searchByKeyword()` 使用 `LIKE %:keyword%`
- **表现**: 双 % 通配符导致 MySQL 无法使用 B-Tree 索引
- **验证**: `EXPLAIN` 该 SQL，`type=ALL`

#### 问题 4: 商品详情无缓存
- **位置**: `ProductService.findById()` 每次都查数据库
- **表现**: 热点商品被大量请求访问时，全部命中数据库
- **验证**: 高并发压测时观察数据库连接等待

#### 问题 5: N+1 查询
- **位置**: `OrderService.findByUserId()` 先查订单列表，再逐个查订单项
- **表现**: 10 个订单 = 1(查列表) + 10(查items) = 11 次 SQL
- **验证**: 开启 `show-sql: true` 观察 Hibernate 输出的 SQL 数量

#### 问题 6: 连接池过小
- **位置**: `application.yml` 中 `hikari.maximum-pool-size: 5`
- **表现**: 5 个连接在高并发下被快速耗尽，后续请求排队等待
- **验证**: 日志中出现 `Connection is not available, request timed out`

#### 问题 7: 库存扣减无并发控制
- **位置**: `OrderService.createOrder()` 中先读 stock 再写回
- **表现**: 50 个线程同时购买 1 件，实际扣减 < 50（超卖）
- **验证**: 记录初始库存，并发下单后检查实际库存

#### 问题 8: 全局 DEBUG 日志
- **位置**: `application.yml` 中 `logging.level.root: DEBUG`
- **表现**: 每个请求产生大量日志输出，消耗 CPU 和磁盘 IO
- **验证**: 关闭 DEBUG 后对比 TPS（通常提升 10-30%）

---

## 配置文件说明

### application.yml 关键配置

```yaml
server:
  port: 8080          # 应用端口

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/jmeter_shop?...  # 数据库连接
    username: root
    password: 123456
    hikari:
      maximum-pool-size: 5   # ⚠️ 故意设小, 调优时改为 20-50
      minimum-idle: 2
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: update        # 自动建表/更新表结构
    show-sql: true             # 打印 SQL (调优后建议关闭)
    properties:
      hibernate:
        format_sql: true
        generate_statistics: true  # ⚠️ 性能统计, 调优后关闭

logging:
  level:
    root: DEBUG                # ⚠️ 故意设为 DEBUG, 调优时改为 WARN/INFO
```

### pom.xml 核心依赖

| 依赖 | 用途 |
|------|------|
| `spring-boot-starter-web` | Web MVC, 内嵌 Tomcat |
| `spring-boot-starter-data-jpa` | JPA + Hibernate ORM |
| `mysql-connector-j` | MySQL JDBC 驱动 |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI API 文档 |
| `lombok` | @Data, @Slf4j 等注解减少样板代码 |

---

## 学习路线建议

```
1. 阅读本文档 → 理解项目架构
        │
2. 启动项目 → Swagger UI 手动测试每个接口
        │
3. 阅读 docs/jmeter-guide.md → 学习 JMeter 基本用法
        │
4. 按 6 个场景逐一压测 → 记录基线数据
        │
5. 阅读 docs/tuning-guide.md → 按步骤逐一调优
        │
6. 每次调优后重新压测 → 量化对比效果
        │
7. 整理调优报告 → 形成自己的方法论
```

---

## 相关文档

| 文档 | 路径 | 说明 |
|------|------|------|
| JMeter 压测指南 | `docs/jmeter-guide.md` | 6 个压测场景的 JMeter 配置步骤 |
| 性能调优指南 | `docs/tuning-guide.md` | 8 个问题的发现→定位→修复→验证全流程 |
