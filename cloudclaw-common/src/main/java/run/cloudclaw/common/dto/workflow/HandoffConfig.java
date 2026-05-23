package run.cloudclaw.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for Handoff workflow mode.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HandoffConfig {

    /** Whether sub-agents automatically return to the parent agent after completing */
    @JsonAlias("auto_return")
    private boolean autoReturn = false;
}
