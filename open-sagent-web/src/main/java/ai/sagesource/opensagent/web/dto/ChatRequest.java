package ai.sagesource.opensagent.web.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId;
    private String message;
    private String agentVersion;
}
