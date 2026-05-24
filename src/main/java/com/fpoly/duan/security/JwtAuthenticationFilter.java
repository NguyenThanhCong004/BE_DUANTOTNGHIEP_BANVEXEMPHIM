package com.fpoly.duan.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final com.fpoly.duan.repository.RevokedTokenRepository revokedTokenRepository;
    private static final List<String> PUBLIC_GET_PREFIXES = List.of(
            "/api/v1/cinemas",
            "/api/v1/genres",
            "/api/v1/membership-ranks",
            "/api/v1/movies",
            "/api/v1/news",
            "/api/v1/product-categories",
            "/api/v1/products",
            "/api/v1/promotions",
            "/api/v1/seat-types",
            "/api/v1/seats",
            "/api/v1/showtime-seat-holds",
            "/api/v1/showtimes",
            "/api/v1/vouchers");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();

        if (HttpMethod.OPTIONS.matches(method)
                || pathMatches(path, "/api/v1/auth")
                || pathMatches(path, "/api/v1/payments/payos/webhook")
                || pathMatches(path, "/v3/api-docs")
                || pathMatches(path, "/swagger-ui")
                || "/swagger-ui.html".equals(path)) {
            return true;
        }

        if (HttpMethod.POST.matches(method) && pathMatches(path, "/api/v1/showtime-seat-holds")) {
            return true;
        }

        return HttpMethod.GET.matches(method) && PUBLIC_GET_PREFIXES.stream().anyMatch(prefix -> pathMatches(path, prefix));
    }

    private boolean pathMatches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        
        // Check if token is revoked
        if (revokedTokenRepository.existsByToken(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String accountType = null;
            try {
                accountType = jwtService.extractAccountType(jwt);
            } catch (Exception ignored) {
                // Token cũ chưa có accountType vẫn được xử lý theo cơ chế fallback.
            }
            UserDetails userDetails = this.userDetailsService.loadByUsernameAndAccountType(username, accountType);
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}
