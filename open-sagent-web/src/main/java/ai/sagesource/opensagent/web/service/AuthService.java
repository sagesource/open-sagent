package ai.sagesource.opensagent.web.service;

import ai.sagesource.opensagent.web.entity.User;
import ai.sagesource.opensagent.web.repository.UserRepository;
import ai.sagesource.opensagent.web.security.JwtUtil;
import ai.sagesource.opensagent.web.security.PasswordEncoder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 认证业务逻辑
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Slf4j
@Service
public class AuthService {

    @Resource
    private UserRepository userRepository;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;

    /**
     * 用户注册
     */
    public User register(String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .nickname(email.substring(0, email.indexOf('@')))
                .build();
        return userRepository.save(user);
    }

    /**
     * 用户登录
     */
    public String login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("邮箱或密码错误"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }
        return jwtUtil.generateToken(user.getId(), user.getEmail());
    }

    /**
     * 获取用户信息
     */
    public User getUserInfo(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    /**
     * 更新个人信息
     */
    public User updateProfile(Long userId, String nickname, String newPassword) {
        User user = getUserInfo(userId);
        if (nickname != null && !nickname.isBlank()) {
            user.setNickname(nickname.trim());
        }
        if (newPassword != null && !newPassword.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }
        return userRepository.save(user);
    }
}
