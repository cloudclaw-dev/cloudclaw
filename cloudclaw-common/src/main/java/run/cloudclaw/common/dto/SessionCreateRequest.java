package run.cloudclaw.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionCreateRequest {

    @NotBlank(message = "agentId must not be blank")
    private String agentId;

    private String title;
}
