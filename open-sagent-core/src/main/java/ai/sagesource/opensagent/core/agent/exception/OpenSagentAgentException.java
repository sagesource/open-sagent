package ai.sagesource.opensagent.core.agent.exception;

import ai.sagesource.opensagent.base.exception.OpenSagentException;

/**
 * Agent模块异常类
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
public class OpenSagentAgentException extends OpenSagentException {

    public OpenSagentAgentException(String message) {
        super(message);
    }

    public OpenSagentAgentException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentAgentException(Throwable cause) {
        super(cause);
    }
}
