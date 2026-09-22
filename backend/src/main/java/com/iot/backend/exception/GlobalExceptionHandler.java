package com.iot.backend.exception;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Xử lý ngoại lệ tập trung toàn ứng dụng (Global Exception Handler).
 * Đảm bảo mọi lỗi trả về client đều có format JSON nhất quán: {"message": "..."}
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bắt lỗi khi validate Request Body không thỏa mãn các annotation Bean Validation (@NotBlank, @NotNull,...)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse("Dữ liệu đầu vào không hợp lệ");

        return ResponseEntity.badRequest().body(Map.of("message", errorMessage));
    }

    /**
     * Bắt lỗi sai thông tin đăng nhập từ Spring Security.
     */
    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleBadCredentials(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Tên đăng nhập hoặc mật khẩu không chính xác"));
    }

    /**
     * Bắt tất cả các lỗi không mong muốn khác.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGlobalException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Đã xảy ra lỗi máy chủ: " + ex.getMessage()));
    }
}
