package com.fpoly.duan.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.security.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    private void writeJsonApiResponse(HttpServletResponse response, int httpStatus, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .status(httpStatus)
                .message(message)
                .build();
        objectMapper.writeValue(response.getOutputStream(), body);
        response.flushBuffer();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // Kích hoạt CORS với cấu hình bên dưới
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> writeJsonApiResponse(response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Chưa đăng nhập hoặc token không hợp lệ. Vui lòng đăng nhập tài khoản khách và gửi Bearer token."))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeJsonApiResponse(
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                "Không có quyền truy cập API này.")))
                .authorizeHttpRequests(auth -> auth
                        // Cho phép các yêu cầu tiền kiểm (Preflight) của trình duyệt
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/payments/payos/webhook").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // API công khai cho khách xem nội dung và chọn ghế.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/cinemas/**",
                                "/api/v1/genres/**",
                                "/api/v1/membership-ranks/**",
                                "/api/v1/movies/**",
                                "/api/v1/news/**",
                                "/api/v1/product-categories/**",
                                "/api/v1/products/**",
                                "/api/v1/promotions/**",
                                "/api/v1/seat-types/**",
                                "/api/v1/seats/**",
                                "/api/v1/showtime-seat-holds/**",
                                "/api/v1/showtimes/**",
                                "/api/v1/vouchers/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/showtime-seat-holds/**").permitAll()

                        // Khách đăng nhập.
                        .requestMatchers("/api/v1/me/**").hasAuthority("ROLE_USER")
                        .requestMatchers("/api/v1/ticket-orders/**").hasAuthority("ROLE_USER")
                        .requestMatchers("/api/v1/food-orders/**").hasAuthority("ROLE_USER")
                        .requestMatchers("/api/v1/payments/payos/create-link").hasAuthority("ROLE_SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/{id}")
                        .hasAnyAuthority("ROLE_USER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/{id}", "/api/v1/users/{id}/password")
                        .hasAnyAuthority("ROLE_USER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")

                        // Nhân viên quầy và quản trị rạp.
                        .requestMatchers("/api/v1/counter-orders/**")
                        .hasAnyAuthority("ROLE_STAFF", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers("/api/v1/counter-orders/export-pdf/**")
                        .hasAnyAuthority("ROLE_STAFF", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers("/api/v1/staff/dashboard-stats", "/api/v1/staff/dashboard-stats/**")
                        .hasAnyAuthority("ROLE_STAFF", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers("/api/v1/shifts/me", "/api/v1/shifts/active")
                        .hasAnyAuthority("ROLE_STAFF", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/staff/super-admin-view")
                        .hasAuthority("ROLE_SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/staff/**")
                        .hasAuthority("ROLE_SUPER_ADMIN")
                        .requestMatchers("/api/v1/staff/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")

                        // Quản lý trong phạm vi rạp.
                        .requestMatchers("/api/v1/rooms/**",
                                "/api/v1/seats/**",
                                "/api/v1/seat-types/**",
                                "/api/v1/showtimes/**",
                                "/api/v1/shifts/**",
                                "/api/v1/promotions/**",
                                "/api/v1/users/**",
                                "/api/v1/orders-online/**",
                                "/api/v1/cinemas/*/product-menu/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")

                        // Quản lý toàn hệ thống.
                        .requestMatchers("/api/v1/cinemas/**",
                                "/api/v1/genres/**",
                                "/api/v1/membership-ranks/**",
                                "/api/v1/movies/**",
                                "/api/v1/news/**",
                                "/api/v1/product-categories/**",
                                "/api/v1/products/**",
                                "/api/v1/vouchers/**",
                                "/api/v1/super-admin/**")
                        .hasAuthority("ROLE_SUPER_ADMIN")

                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = new ArrayList<>(Arrays.asList(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:3000",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:5174",
                "http://127.0.0.1:3000"));
        if (frontendBaseUrl != null && !frontendBaseUrl.isBlank()) {
            allowedOrigins.add(frontendBaseUrl.trim());
        }
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
