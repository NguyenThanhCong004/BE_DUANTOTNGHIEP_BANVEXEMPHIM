package com.fpoly.duan.security;

import java.security.Key;
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

    @Value("${application.security.jwt.secret-key:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey;

    @Value("${application.security.jwt.expiration:7200000}") // 2 hours
    private long jwtExpiration;

    @Value("${application.security.jwt.refresh-token.expiration:864000000}") // 10 days
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

    public Date extractExpirationAllowExpired(String token) {
        return extractClaimAllowExpired(token, Claims::getExpiration);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token) && extractTokenType(token) == TokenType.ACCESS;
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token) && extractTokenType(token) == TokenType.REFRESH;
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
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
