package com.fpoly.duan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Envelope JSON thống nhất cho FE (Vite) và Swagger.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ApiResponse", description = "Mọi endpoint thành công thường trả về: status + message + data")
public class ApiResponse<T> {

    @Schema(description = "Mã trạng thái (thường trùng HTTP: 200, 201, …)", example = "200")
    private int status;

    @Schema(description = "Thông báo tiếng Việt", example = "Thành công")
    private String message;

    @Schema(description = "Dữ liệu nghiệp vụ (object, mảng hoặc null)")
    private T data;
}
