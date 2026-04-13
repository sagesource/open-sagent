package ai.sagesource.opensagent.core.llm.exception;

import ai.sagesource.opensagent.base.exception.OpenSagentException;

/**
 * Tool模块异常类
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class OpenSagentToolException extends OpenSagentException {

    public OpenSagentToolException(String message) {
        super(message);
    }

    public OpenSagentToolException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentToolException(Throwable cause) {
        super(cause);
    }
}
