package ai.sagesource.opensagent.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应包装
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * 状态码：0表示成功，非0表示失败
     */
    private int code;

    /**
     * 提示消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().code(0).message("success").data(data).build();
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder().code(code).message(message).data(null).build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(500, message);
    }
}
