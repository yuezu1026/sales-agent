package com.aicustomer.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 工具：签发与解析
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final Duration expire;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-hours:72}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expire = Duration.ofHours(expireHours);
    }

    public String generate(String username, Long tenantId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("tenantId", tenantId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expire)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，失败返回 null
     */
    public String parseUsername(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 token 中的租户 id（平台级账号可能为 null），失败返回 null
     */
    public Long parseTenantId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object value = claims.get("tenantId");
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.valueOf(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
