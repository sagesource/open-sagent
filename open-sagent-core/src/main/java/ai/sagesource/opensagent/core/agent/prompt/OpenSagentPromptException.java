package ai.sagesource.opensagent.core.agent.prompt;

import ai.sagesource.opensagent.base.exception.OpenSagentException;

/**
 * Prompt模块异常类
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public class OpenSagentPromptException extends OpenSagentException {

    public OpenSagentPromptException(String message) {
        super(message);
    }

    public OpenSagentPromptException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentPromptException(Throwable cause) {
        super(cause);
    }
}
