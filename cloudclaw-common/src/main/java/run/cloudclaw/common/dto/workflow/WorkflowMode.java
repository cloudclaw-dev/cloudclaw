package run.cloudclaw.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Workflow orchestration mode for multi-agent coordination.
 */
public enum WorkflowMode {
    PIPELINE,
    PARALLEL,
    ROUTER,
    SUPERVISOR,
    HANDOFF;

    @JsonCreator
    public static WorkflowMode fromString(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase()) {
            case "pipeline" -> PIPELINE;
            case "parallel" -> PARALLEL;
            case "router" -> ROUTER;
            case "supervisor" -> SUPERVISOR;
            case "handoff" -> HANDOFF;
            default -> throw new IllegalArgumentException("Unknown workflow mode: " + value);
        };
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}
