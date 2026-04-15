package ai.sagesource.opensagent.core.agent.exception;

import ai.sagesource.opensagent.base.exception.OpenSagentException;

/**
 * Memory模块异常类
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public class OpenSagentMemoryException extends OpenSagentException {

    public OpenSagentMemoryException(String message) {
        super(message);
    }

    public OpenSagentMemoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentMemoryException(Throwable cause) {
        super(cause);
    }
}
