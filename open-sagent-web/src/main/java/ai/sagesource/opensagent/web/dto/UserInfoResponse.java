package ai.sagesource.opensagent.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoResponse {
    private Long id;
    private String email;
    private String nickname;
}
