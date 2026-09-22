package com.iot.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Lớp bọc phản hồi API đồng nhất toàn hệ thống (Unified API Response Envelope).
 * Áp dụng cho tất cả REST API của Backend.
 *
 * @param <T> Kiểu dữ liệu của payload trả về trong trường `data`.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int status;

    private String message;

    private T data;

    private Map<String, String> errors;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * Tạo response thành công kèm thông điệp và dữ liệu.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Tạo response thành công với thông điệp mặc định.
     */
    public static <T> ApiResponse<T> success(T data) {
        return success("Thành công", data);
    }

    /**
     * Tạo response thông báo lỗi thông thường.
     */
    public static <T> ApiResponse<T> error(int status, String message) {
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Tạo response lỗi validation kèm chi tiết các trường bị lỗi.
     */
    public static <T> ApiResponse<T> validationError(Map<String, String> errors) {
        return ApiResponse.<T>builder()
                .status(400)
                .message("Dữ liệu đầu vào không hợp lệ")
                .errors(errors)
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
