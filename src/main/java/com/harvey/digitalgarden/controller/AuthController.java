package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.LoginRequest;
import com.harvey.digitalgarden.dto.LoginResponse;
import com.harvey.digitalgarden.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }
}
