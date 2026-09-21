package com.iot.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Cấu hình CORS mở cho Frontend truy cập từ localhost / 127.0.0.1.
 * Cho phép mọi port (ví dụ: localhost:5500 Live Server, localhost:3000, v.v.)
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Cho phép các origin phổ biến khi phát triển
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://localhost",
                "http://127.0.0.1"
        ));

        // Cho phép tất cả các HTTP method
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Cho phép tất cả các header
        configuration.setAllowedHeaders(List.of("*"));

        // Cho phép gửi credentials (cookies, Authorization header)
        configuration.setAllowCredentials(true);

        // Cache preflight response 1 giờ
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
