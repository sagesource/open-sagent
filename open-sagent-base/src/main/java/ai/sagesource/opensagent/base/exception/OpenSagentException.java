package ai.sagesource.opensagent.base.exception;

/**
 * OpenSagent全局异常基类
 * <p>
 * 所有自定义异常都必须继承此类
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class OpenSagentException extends RuntimeException {

    public OpenSagentException(String message) {
        super(message);
    }

    public OpenSagentException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentException(Throwable cause) {
        super(cause);
    }
}
