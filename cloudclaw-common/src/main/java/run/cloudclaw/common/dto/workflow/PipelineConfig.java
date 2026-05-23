package run.cloudclaw.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for Pipeline (serial) workflow mode.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineConfig {

    /**
     * How the previous step's output is passed to the next step.
     * "append": append previous result to user message.
     * "replace": replace user message with previous result.
     */
    @JsonAlias("passthrough_mode")
    private String passthroughMode = "append";
}
