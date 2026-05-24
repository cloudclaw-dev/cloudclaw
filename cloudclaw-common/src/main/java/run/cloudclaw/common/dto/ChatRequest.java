package run.cloudclaw.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    // Fix: 添加 @Size 限制 message 最大长度，防止超长消息导致 token 爆炸
    @NotBlank(message = "message must not be blank")
    @Size(max = 50000, message = "message must not exceed 50000 characters")
    private String message;
}
