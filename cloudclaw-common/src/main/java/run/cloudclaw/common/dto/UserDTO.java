package run.cloudclaw.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {
    private String id;
    private String username;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String phone;
    private String role;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private List<ChannelBindingDTO> bindings;
}
