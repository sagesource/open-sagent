package ai.sagesource.opensagent.web.security;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT登录态拦截器
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;

    public static final String HEADER_AUTH = "Authorization";
    public static final String ATTR_USER_ID = "userId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        Long userId = jwtUtil.getUserId(token);
        request.setAttribute(ATTR_USER_ID, userId);
        return true;
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader(HEADER_AUTH);
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // SSE/EventSource 不支持自定义 Header，支持从 URL Query Parameter 获取 token
        String token = request.getParameter("token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        return null;
    }
}
