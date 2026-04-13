package ai.sagesource.opensagent.core.llm.exception;

import ai.sagesource.opensagent.base.exception.OpenSagentException;

/**
 * LLM模块异常类
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class OpenSagentLLMException extends OpenSagentException {

    public OpenSagentLLMException(String message) {
        super(message);
    }

    public OpenSagentLLMException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentLLMException(Throwable cause) {
        super(cause);
    }
}
