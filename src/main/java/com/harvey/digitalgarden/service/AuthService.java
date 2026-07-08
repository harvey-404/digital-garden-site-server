package com.harvey.digitalgarden.service;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.common.ResultCode;
import com.harvey.digitalgarden.dto.LoginRequest;
import com.harvey.digitalgarden.dto.LoginResponse;
import com.harvey.digitalgarden.repository.AdminUserRepository;
import com.harvey.digitalgarden.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AdminUserRepository repository;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthService(AdminUserRepository repository, PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.repository = repository;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest req) {
        var admin = repository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!encoder.matches(req.getPassword(), admin.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(admin.getUsername());
        return new LoginResponse(token, admin.getUsername());
    }
}
