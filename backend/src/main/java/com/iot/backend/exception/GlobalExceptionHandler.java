package com.iot.backend.exception;

import com.iot.backend.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/** Chuẩn hóa cả lỗi Spring MVC và lỗi nghiệp vụ về status, message, data. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return new ResponseEntity<>(ApiResponse.validationError(errors), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (ex.isForReturnValue()) {
            return handleExceptionInternal(ex, null, headers, status, request);
        }
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            String key = name != null ? name : "arg" + result.getMethodParameter().getParameterIndex();
            result.getResolvableErrors().forEach(error ->
                    errors.putIfAbsent(key, error.getDefaultMessage()));
        });
        return new ResponseEntity<>(ApiResponse.validationError(errors), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Lỗi xử lý API: {}", request.getDescription(false), ex);
        }
        String message = messageFor(status.value());
        if (ex instanceof ResponseStatusException responseStatus && !status.is5xxServerError()
                && responseStatus.getReason() != null) {
            message = responseStatus.getReason();
        }
        return super.handleExceptionInternal(ex,
                ApiResponse.error(status.value(), message), headers, status, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return ResponseEntity.badRequest().body(ApiResponse.validationError(errors));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không chính xác");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, messageFor(403));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataConflict(DataIntegrityViolationException ex) {
        log.warn("Xung đột ràng buộc dữ liệu", ex);
        return error(HttpStatus.CONFLICT, messageFor(409));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        if (ex instanceof ErrorResponse response) {
            return handleExceptionInternal(ex, null, response.getHeaders(), response.getStatusCode(), request);
        }
        return handleExceptionInternal(ex, null, HttpHeaders.EMPTY,
                HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }

    public static String messageFor(int status) {
        return switch (status) {
            case 400 -> "Dữ liệu đầu vào không hợp lệ";
            case 401 -> "Token không hợp lệ hoặc đã hết hạn";
            case 403 -> "Bạn không có quyền truy cập tài nguyên này";
            case 404 -> "Không tìm thấy tài nguyên";
            case 405 -> "Phương thức HTTP không được hỗ trợ";
            case 406 -> "Định dạng phản hồi không được hỗ trợ";
            case 409 -> "Dữ liệu hoặc trạng thái hiện tại bị xung đột";
            case 415 -> "Định dạng dữ liệu gửi lên không được hỗ trợ";
            case 503 -> "Dịch vụ tạm thời không khả dụng";
            default -> status >= 500 ? "Đã xảy ra lỗi máy chủ" : "Yêu cầu không thể được xử lý";
        };
    }
}
