package ai.sagesource.opensagent.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationDTO {
    private Long id;
    private String sessionId;
    private String title;
    private String agentVersion;
    private LocalDateTime updatedAt;
}
