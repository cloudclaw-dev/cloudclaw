package run.cloudclaw.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMcpServerRequest {

    @Size(max = 100, message = "Server name must be at most 100 characters")
    private String name;

    private String description;

    @Size(max = 20, message = "Transport type must be at most 20 characters")
    private String transport;

    @Size(max = 500, message = "URL must be at most 500 characters")
    private String url;

    private String command;

    private Map<String, Object> args;

    private Map<String, String> env;
}
