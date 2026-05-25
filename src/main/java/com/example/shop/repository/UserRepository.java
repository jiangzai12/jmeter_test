package com.example.shop.repository;

import com.example.shop.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 查询无索引的 username 字段 → 全表扫描
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
