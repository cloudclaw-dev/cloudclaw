package run.cloudclaw.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "username must not be blank")
    @Size(min = 2, max = 50, message = "username must be 2-50 characters")
    private String username;

    @NotBlank(message = "password must not be blank")
    @Size(min = 6, max = 100, message = "password must be 6-100 characters")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "password must contain at least one letter and one digit")
    private String password;

    @NotBlank(message = "email must not be blank")
    @Size(max = 200, message = "email must be at most 200 characters")
    private String email;
}
