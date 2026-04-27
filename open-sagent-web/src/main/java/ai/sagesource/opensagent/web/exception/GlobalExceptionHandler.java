package ai.sagesource.opensagent.web.exception;

import ai.sagesource.opensagent.web.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 将所有后端异常统一包装为JSON格式返回给前端
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("> GlobalExceptionHandler | 业务异常: {} <", e.getMessage());
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("> GlobalExceptionHandler | 系统异常 <", e);
        return ApiResponse.error(500, "服务器内部错误");
    }
}
