package ai.sagesource.opensagent.web.controller;

import ai.sagesource.opensagent.web.dto.*;
import ai.sagesource.opensagent.web.entity.User;
import ai.sagesource.opensagent.web.security.JwtInterceptor;
import ai.sagesource.opensagent.web.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口控制器
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.getEmail(), request.getPassword());
        return ApiResponse.success();
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.getEmail(), request.getPassword());
        return ApiResponse.success(LoginResponse.builder().token(token).build());
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        User user = authService.getUserInfo(userId);
        return ApiResponse.success(UserInfoResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build());
    }

    @PutMapping("/profile")
    public ApiResponse<UserInfoResponse> updateProfile(
            HttpServletRequest request,
            @RequestBody UpdateProfileRequest body) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        User user = authService.updateProfile(userId, body.getNickname(), body.getPassword());
        return ApiResponse.success(UserInfoResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build());
    }
}
