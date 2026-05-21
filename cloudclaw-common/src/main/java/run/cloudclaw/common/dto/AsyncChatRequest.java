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
public class AsyncChatRequest {
    @NotBlank(message = "消息不能为空")
    private String message;
    private String requestId;
}
