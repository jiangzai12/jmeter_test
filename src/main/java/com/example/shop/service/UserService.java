package com.example.shop.service;

import com.example.shop.dto.LoginRequest;
import com.example.shop.dto.RegisterRequest;
import com.example.shop.entity.User;
import com.example.shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User register(RegisterRequest request) {
        log.debug("开始注册用户: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在: " + request.getUsername());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        // ===== 简化处理: 明文存储密码（非本次重点） =====
        user.setPassword(request.getPassword());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());

        User saved = userRepository.save(user);
        log.debug("用户注册成功: id={}, username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    public User login(LoginRequest request) {
        log.debug("用户登录: {}", request.getUsername());

        // ===== 性能问题 #1: 查询无索引的 username =====
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在: " + request.getUsername()));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        log.debug("用户登录成功: id={}", user.getId());
        return user;
    }
}
