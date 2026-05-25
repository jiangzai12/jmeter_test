package com.example.shop.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("电商系统 API - JMeter 压测专用")
                        .version("1.0.0")
                        .description("一个简单的电商系统，专为 JMeter 压测和性能调优学习设计。\n\n"
                                + "## 故意埋入的性能问题\n"
                                + "1. 无数据库索引（除主键外）\n"
                                + "2. 商品列表无分页\n"
                                + "3. 商品搜索 LIKE '%keyword%' 全表扫描\n"
                                + "4. 商品详情无缓存\n"
                                + "5. 订单列表 N+1 查询\n"
                                + "6. 连接池过小 (5)\n"
                                + "7. 库存扣减无并发控制\n"
                                + "8. 日志级别 DEBUG")
                        .contact(new Contact().name("JMeter Performance Tester")));
    }
}
