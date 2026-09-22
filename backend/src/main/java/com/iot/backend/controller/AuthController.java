package com.iot.backend.controller;

import com.iot.backend.dto.request.LoginRequest;
import com.iot.backend.dto.response.LoginResponse;
import com.iot.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý các yêu cầu liên quan đến xác thực (Auth).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Endpoint đăng nhập tài khoản hệ thống.
     * POST /api/auth/login
     *
     * @param loginRequest thông tin tài khoản hợp lệ (@Valid)
     * @return Token JWT và thông tin tài khoản nếu hợp lệ
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }
}
