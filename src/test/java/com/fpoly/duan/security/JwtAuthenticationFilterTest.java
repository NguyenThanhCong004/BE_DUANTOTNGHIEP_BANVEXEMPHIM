package com.fpoly.duan.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fpoly.duan.repository.RevokedTokenRepository;

class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            mock(JwtService.class),
            mock(CustomUserDetailsService.class),
            mock(RevokedTokenRepository.class));

    @Test
    void publicMovieGetDoesNotRequireJwtFilter() {
        assertTrue(filter.shouldNotFilter(request("GET", "/api/v1/movies")));
        assertTrue(filter.shouldNotFilter(request("GET", "/api/v1/movies/1")));
    }

    @Test
    void promotionEligibleGetMustProcessJwt() {
        assertFalse(filter.shouldNotFilter(request("GET", "/api/v1/movies/promotion-eligible")));
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
