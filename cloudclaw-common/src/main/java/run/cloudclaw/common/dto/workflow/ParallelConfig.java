package run.cloudclaw.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for Parallel workflow mode.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParallelConfig {

    /**
     * How to merge parallel results.
     * "concat": concatenate all results with separators.
     * "summarize": use LLM to summarize all results into one.
     */
    @JsonAlias("merge_strategy")
    private String mergeStrategy = "concat";

    /** Maximum number of concurrent agent invocations */
    @JsonAlias("max_concurrent")
    private int maxConcurrent = 5;
}
