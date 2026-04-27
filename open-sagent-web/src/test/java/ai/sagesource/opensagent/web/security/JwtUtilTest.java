package ai.sagesource.opensagent.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil();

    JwtUtilTest() {
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-12345678901234567890");
        ReflectionTestUtils.setField(jwtUtil, "expireHours", 24);
    }

    @Test
    void testGenerateAndValidateToken() {
        String token = jwtUtil.generateToken(1L, "test@example.com");
        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void testGetUserId() {
        String token = jwtUtil.generateToken(42L, "user@example.com");
        Long userId = jwtUtil.getUserId(token);
        assertEquals(42L, userId);
    }

    @Test
    void testValidateInvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid.token.here"));
    }

    @Test
    void testValidateEmptyToken() {
        assertFalse(jwtUtil.validateToken(""));
    }
}
