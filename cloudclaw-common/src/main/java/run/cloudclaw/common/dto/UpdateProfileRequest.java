package run.cloudclaw.common.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(max = 100, message = "displayName must be at most 100 characters")
    private String displayName;

    @Size(max = 500, message = "avatarUrl must be at most 500 characters")
    private String avatarUrl;

    @Size(max = 200, message = "email must be at most 200 characters")
    private String email;

    @Size(max = 50, message = "phone must be at most 50 characters")
    private String phone;
}
