package com.iot.backend.controller;

import com.iot.backend.dto.request.LoginRequest;
import com.iot.backend.dto.response.LoginResponse;
import com.iot.backend.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
     * @param loginRequest thông tin tài khoản (username, password)
     * @return Token JWT và thông tin tài khoản nếu hợp lệ
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        if (loginRequest == null || loginRequest.getUsername() == null || loginRequest.getPassword() == null
                || loginRequest.getUsername().isBlank() || loginRequest.getPassword().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Tên đăng nhập và mật khẩu không được để trống"));
        }

        try {
            LoginResponse response = authService.login(loginRequest);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Tên đăng nhập hoặc mật khẩu không chính xác"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Đã xảy ra lỗi hệ thống khi xác thực: " + e.getMessage()));
        }
    }
}
