package run.cloudclaw.admin.dto;

import jakarta.validation.constraints.Email;
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
public class UpdateUserRequest {

    @Email(message = "Email must be a valid email address")
    @Size(max = 200, message = "Email must be at most 200 characters")
    private String email;

    private String role;

    private Boolean enabled;

    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    // Fix M10: Enforce password format — at least one letter and one digit
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "Password must contain at least one letter and one digit")
    private String password;
}
