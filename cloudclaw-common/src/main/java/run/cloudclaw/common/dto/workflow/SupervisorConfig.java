package run.cloudclaw.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for Supervisor workflow mode.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SupervisorConfig {

    /** Maximum number of supervisor iteration loops (prevents infinite loops) */
    @JsonAlias("max_iterations")
    private int maxIterations = 5;

    /** Prompt injected to guide the supervisor's planning behavior */
    @JsonAlias("planner_prompt")
    private String plannerPrompt;

    /** Prompt injected to guide the supervisor's review behavior */
    @JsonAlias("reviewer_prompt")
    private String reviewerPrompt;
}
