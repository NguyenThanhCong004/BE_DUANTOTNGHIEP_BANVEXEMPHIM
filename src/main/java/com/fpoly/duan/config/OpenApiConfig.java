package com.fpoly.duan.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Swagger UI: {@code /swagger-ui.html} · OpenAPI JSON: {@code /v3/api-docs}.
 * FE (Vite): đặt {@code VITE_API_BASE_URL} trùng cổng BE (mặc định {@code http://localhost:8080}).
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI cinemaBookingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Bán vé xem phim (JAVA6)")
                        .version("1.0.0")
                        .description("""
                                REST API Spring Boot cho ứng dụng Vite/React.

                                **Chuẩn response:** hầu hết endpoint trả về JSON bọc trong `ApiResponse`:
                                ```json
                                { "status": 200, "message": "...", "data": { } }
                                ```
                                - `status`: HTTP semantic (thường trùng mã HTTP).
                                - `message`: mô tả tiếng Việt.
                                - `data`: payload (object, array hoặc `null`).

                                **Đăng nhập:** `POST /api/v1/auth/login` → nhận `data.token` (JWT). \
                                Các API cần bảo vệ: gửi header `Authorization: Bearer <token>`.

                                **CORS (dev):** BE cho phép origin `http://localhost:5173`, `http://localhost:5174`.
                                """)
                        .contact(new Contact()
                                .name("JAVA6 — Dự án tốt nghiệp")
                                .email("dev@local.test"))
                        .license(new License()
                                .name("Nội bộ")
                                .url("https://localhost")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local (mặc định cùng FE .env)"),
                        new Server()
                                .url("/")
                                .description("Cùng origin (reverse proxy)")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "JWT từ `POST /api/v1/auth/login` hoặc `/register`. "
                                                        + "Dán token vào nút **Authorize** trên Swagger UI.")));
    }
}
