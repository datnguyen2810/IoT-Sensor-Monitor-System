package com.iot.backend.service;

import com.iot.backend.dto.request.LoginRequest;
import com.iot.backend.dto.response.LoginResponse;
import com.iot.backend.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Service xử lý xác thực tài khoản và cấp phát JWT token.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Xác thực thông tin đăng nhập của người dùng.
     *
     * @param request DTO chứa username và password
     * @return LoginResponse chứa JWT token và username
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        String token = jwtTokenProvider.generateToken(authentication);

        return LoginResponse.builder()
                .token(token)
                .username(request.getUsername())
                .build();
    }
}
