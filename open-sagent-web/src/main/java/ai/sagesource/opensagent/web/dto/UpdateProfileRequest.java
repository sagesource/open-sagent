package ai.sagesource.opensagent.web.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String nickname;
    private String password;
}
