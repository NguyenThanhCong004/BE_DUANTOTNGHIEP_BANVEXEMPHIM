package com.fpoly.duan.security;

import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.access-expiration-ms:7200000}") // 2 hours
    private long jwtExpiration;

    @Value("${app.jwt.refresh-expiration-ms:864000000}") // 10 days
    private long refreshExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration, TokenType.ACCESS);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, refreshExpiration, TokenType.REFRESH);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration, TokenType tokenType) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        if (userDetails instanceof CustomUserDetails customUser) {
            claims.put("userId", customUser.getUserId());
            claims.put("accountType", customUser.getAccountType());
            if (customUser.getSessionVersion() != null) {
                claims.put("sv", customUser.getSessionVersion());
            }
        }
        // Thêm danh sách quyền vào claim "authorities"
        claims.put("authorities", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        claims.put("type", tokenType.name());
        
        return Jwts
                .builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsernameAllowExpired(String token) {
        return extractClaimAllowExpired(token, Claims::getSubject);
    }

    public TokenType extractTokenType(String token) {
        String type = extractClaim(token, claims -> (String) claims.get("type"));
        return TokenType.valueOf(type);
    }

    public TokenType extractTokenTypeAllowExpired(String token) {
        String type = extractClaimAllowExpired(token, claims -> (String) claims.get("type"));
        return TokenType.valueOf(type);
    }

    public String extractAccountType(String token) {
        return extractClaim(token, this::resolveAccountType);
    }

    public String extractAccountTypeAllowExpired(String token) {
        return extractClaimAllowExpired(token, this::resolveAccountType);
    }

    public Date extractExpirationAllowExpired(String token) {
        return extractClaimAllowExpired(token, Claims::getExpiration);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername())
                && sameAccountType(token, userDetails)
                && sameSessionVersion(token, userDetails)
                && !isTokenExpired(token)
                && extractTokenType(token) == TokenType.ACCESS;
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername())
                && sameAccountType(token, userDetails)
                && sameSessionVersion(token, userDetails)
                && !isTokenExpired(token)
                && extractTokenType(token) == TokenType.REFRESH;
    }

    /**
     * Gioi han 1 tai khoan chi dang nhap 1 thiet bi: token mang theo claim "sv" (session
     * version) tai thoi diem phat hanh. Neu tai khoan da dang nhap noi khac sau do,
     * session_version trong DB doi khac -> token cu (kem ca token da refresh tu no) bi
     * tu choi ngay, khong can cho het han. Token/tai khoan chua tung dang nhap lai sau
     * khi trien khai tinh nang nay (ca hai deu null) van duoc coi la hop le.
     */
    private boolean sameSessionVersion(String token, UserDetails userDetails) {
        if (!(userDetails instanceof CustomUserDetails customUser)) {
            return true;
        }
        String tokenSessionVersion = extractClaim(token, claims -> (String) claims.get("sv"));
        return java.util.Objects.equals(tokenSessionVersion, customUser.getSessionVersion());
    }

    private boolean sameAccountType(String token, UserDetails userDetails) {
        String tokenAccountType = extractAccountType(token);
        if (tokenAccountType == null || tokenAccountType.isBlank()) {
            return true;
        }
        if (userDetails instanceof CustomUserDetails customUser) {
            return tokenAccountType.equalsIgnoreCase(customUser.getAccountType());
        }
        return true;
    }

    private String resolveAccountType(Claims claims) {
        String accountType = (String) claims.get("accountType");
        if (accountType != null && !accountType.isBlank()) {
            return accountType;
        }
        Object authorities = claims.get("authorities");
        if (authorities instanceof Collection<?> roles) {
            boolean onlyUser = roles.size() == 1 && roles.stream().anyMatch("ROLE_USER"::equals);
            if (onlyUser) {
                return "USER";
            }
            if (!roles.isEmpty()) {
                return "STAFF";
            }
        }
        return null;
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private <T> T extractClaimAllowExpired(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaimsAllowExpired(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaimsAllowExpired(String token) {
        try {
            return extractAllClaims(token);
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            return ex.getClaims();
        }
    }

    private Key getSignInKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("JWT_SECRET chưa được cấu hình");
        }
        final byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKey.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SECRET phải là chuỗi Base64 hợp lệ", ex);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET phải giải mã ra ít nhất 32 bytes để ký HS256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
