package ai.sagesource.opensagent.web.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT工具类
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${sagent.jwt.secret}")
    private String secret;

    @Value("${sagent.jwt.expire-hours:24}")
    private int expireHours;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT Token
     */
    public String generateToken(Long userId, String email) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireHours * 60L * 60L * 1000L);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(now)
                .expiration(expire)
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析Token获取用户ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("> JwtUtil | Token已过期 <");
            return false;
        } catch (JwtException e) {
            log.warn("> JwtUtil | Token验证失败: {} <", e.getMessage());
            return false;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
