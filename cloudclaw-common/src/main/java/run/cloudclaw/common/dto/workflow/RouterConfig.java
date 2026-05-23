package run.cloudclaw.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for Router workflow mode.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouterConfig {

    /** Whether to fall back to the root agent if routing fails */
    @JsonProperty("allow_fallback")
    private boolean allowFallback = true;
}
