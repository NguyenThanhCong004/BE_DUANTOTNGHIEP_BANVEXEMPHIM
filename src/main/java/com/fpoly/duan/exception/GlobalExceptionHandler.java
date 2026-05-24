package com.fpoly.duan.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private String localizeMessage(String rawMessage, String fallback) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return fallback;
        }
        String message = rawMessage.trim();
        String lower = message.toLowerCase();

        if (lower.contains("query did not return a unique result")) {
            return "Dữ liệu bị trùng, vui lòng kiểm tra lại thông tin.";
        }
        if (lower.contains("nonuniqueresultexception")) {
            return "Dữ liệu bị trùng, vui lòng kiểm tra lại thông tin.";
        }
        if (lower.contains("constraintviolationexception") || lower.contains("duplicate key")) {
            return "Dữ liệu đã tồn tại trong hệ thống.";
        }
        if (lower.contains("could not execute statement")) {
            return "Không thể xử lý yêu cầu với dữ liệu hiện tại.";
        }
        if (lower.contains("internal server error")) {
            return "Đã xảy ra lỗi hệ thống.";
        }

        return message;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(ApiResponse.builder()
                .status(status.value())
                .message(localizeMessage(ex.getReason(), "Yêu cầu không hợp lệ"))
                .build());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoSuchElement(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .message(localizeMessage(ex.getMessage(), "Không tìm thấy dữ liệu"))
                .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(localizeMessage(ex.getMessage(), "Dữ liệu không hợp lệ"))
                .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadableJson(HttpMessageNotReadableException ex) {
        log.warn("Unreadable JSON request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Dữ liệu JSON không hợp lệ hoặc có số nhập quá lớn. Vui lòng kiểm tra lại các ô số.")
                .build());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled RuntimeException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(localizeMessage(ex.getMessage(), "Đã xảy ra lỗi hệ thống"))
                .build());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentialsException(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message("Tên đăng nhập hoặc mật khẩu không chính xác")
                .build());
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Object>> handleDisabledException(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("Tài khoản đã bị khóa")
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.<Map<String, String>>builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Lỗi xác thực dữ liệu")
                .data(errors)
                .build());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("Bạn không có quyền truy cập chức năng này: " + ex.getMessage())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled Exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Đã xảy ra lỗi hệ thống")
                .build());
    }
}
