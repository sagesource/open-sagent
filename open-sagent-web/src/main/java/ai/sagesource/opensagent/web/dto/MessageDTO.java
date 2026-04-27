package ai.sagesource.opensagent.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageDTO {
    private Long id;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
