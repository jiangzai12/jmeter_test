package com.example.shop.config;

import com.example.shop.entity.Product;
import com.example.shop.entity.User;
import com.example.shop.repository.ProductRepository;
import com.example.shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 应用启动时自动初始化测试数据。
 * 仅在数据库为空时插入，避免重复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    private static final String[] CATEGORIES = { "手机", "电脑", "家电", "服装", "食品" };
    private static final String[][] PRODUCT_TEMPLATES = {
            { "智能手机", "这是一款高性能智能手机，配备高清屏幕、大容量电池、高速处理器。支持5G网络，拍照功能强大。" },
            { "笔记本电脑", "高性能笔记本电脑，搭载最新处理器，超大内存，高速固态硬盘。轻薄便携，续航持久。" },
            { "智能家电", "智能家电产品，采用先进技术，节能环保，操作简便。支持手机远程控制，智能联动。" },
            { "时尚服装", "时尚服装，采用优质面料，舒适透气，做工精细。适合各种场合穿着，时尚百搭。" },
            { "精选食品", "精选食品，选用优质原料，绿色健康，口感极佳。通过多项质量认证，安全放心。" }
    };

    @Override
    public void run(String... args) {
        initUsers();
        initProducts();
    }

    private void initUsers() {
        if (userRepository.count() > 0) {
            log.info("用户数据已存在, 跳过初始化");
            return;
        }

        log.info("开始初始化测试用户...");
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            User user = new User();
            user.setUsername(String.format("user%02d", i));
            user.setPassword("123456");
            user.setEmail(String.format("user%02d@test.com", i));
            user.setPhone(String.format("138%08d", i));
            users.add(user);
        }
        userRepository.saveAll(users);
        log.info("初始化 {} 个测试用户完成", users.size());
    }

    private void initProducts() {
        if (productRepository.count() > 0) {
            log.info("商品数据已存在, 跳过初始化");
            return;
        }

        log.info("开始初始化测试商品...");
        Random random = new Random(42);
        List<Product> products = new ArrayList<>();

        for (int c = 0; c < CATEGORIES.length; c++) {
            String category = CATEGORIES[c];
            String namePrefix = PRODUCT_TEMPLATES[c][0];
            String descTemplate = PRODUCT_TEMPLATES[c][1];

            // 每个分类 200 个商品, 共 1000 个
            double minPrice = switch (c) {
                case 0 -> 1000; // 手机
                case 1 -> 3000; // 电脑
                case 2 -> 500; // 家电
                case 3 -> 50; // 服装
                case 4 -> 10; // 食品
                default -> 100;
            };
            double maxPrice = switch (c) {
                case 0 -> 10000;
                case 1 -> 15000;
                case 2 -> 5000;
                case 3 -> 2000;
                case 4 -> 500;
                default -> 1000;
            };

            for (int i = 1; i <= 200; i++) {
                Product product = new Product();
                product.setName(String.format("%s-型号%04d", namePrefix, i));
                product.setDescription(descTemplate + " 编号: " + category + "-" + i);
                product.setPrice(BigDecimal.valueOf(minPrice + random.nextDouble() * (maxPrice - minPrice))
                        .setScale(2, RoundingMode.HALF_UP));
                product.setStock(100 + random.nextInt(900));
                product.setCategory(category);
                products.add(product);
            }
        }

        // 批量保存, 每 100 条一批
        for (int i = 0; i < products.size(); i += 100) {
            int end = Math.min(i + 100, products.size());
            productRepository.saveAll(products.subList(i, end));
        }
        log.info("初始化 {} 个测试商品完成 (5个分类, 每类200个)", products.size());
    }
}
