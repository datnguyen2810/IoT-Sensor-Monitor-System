package com.iot.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

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
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private int status;

    private String message;

    private T data;

    /**
     * Tạo response thành công kèm thông điệp và dữ liệu.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return success(200, message, data);
    }

    public static <T> ApiResponse<T> success(int status, String message, T data) {
        if (status < 200 || status >= 300) {
            throw new IllegalArgumentException("Success status must be a 2xx HTTP status");
        }
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .data(data)
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
                .build();
    }

    /**
     * Tạo response lỗi validation kèm chi tiết các trường bị lỗi.
     */
    public static ApiResponse<Map<String, String>> validationError(Map<String, String> errors) {
        return ApiResponse.<Map<String, String>>builder()
                .status(400)
                .message("Dữ liệu đầu vào không hợp lệ")
                .data(errors)
                .build();
    }
}
