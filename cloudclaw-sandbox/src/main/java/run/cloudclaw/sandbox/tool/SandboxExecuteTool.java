package run.cloudclaw.sandbox.tool;

import run.cloudclaw.sandbox.core.SandboxBackend;
import run.cloudclaw.sandbox.core.SandboxContext;
import run.cloudclaw.sandbox.core.SandboxManager;
import run.cloudclaw.sandbox.core.SandboxMode;
import run.cloudclaw.sandbox.core.StatelessExecutor;
import run.cloudclaw.sandbox.config.SandboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SandboxExecuteTool {

    private static final Logger log = LoggerFactory.getLogger(SandboxExecuteTool.class);

    private final StatelessExecutor statelessExecutor;
    private final SandboxManager sandboxManager;
    private final SandboxProperties properties;

    public SandboxExecuteTool(StatelessExecutor statelessExecutor,
                              SandboxManager sandboxManager,
                              SandboxProperties properties) {
        this.statelessExecutor = statelessExecutor;
        this.sandboxManager = sandboxManager;
        this.properties = properties;
    }

    @Tool(description = "Execute code in an isolated sandbox. " +
            "Supports python, javascript, shell, and java. " +
            "Returns stdout, stderr, and exit code. " +
            "In session mode, files persist across executions.")
    public String sandbox_execute(
            @ToolParam(description = "Programming language: python, javascript, shell, or java") String language,
            @ToolParam(description = "The code to execute") String code,
            @ToolParam(description = "Timeout in seconds (optional, default 30, max 300)", required = false) Integer timeout_seconds) {

        SandboxMode mode = SandboxContext.getMode();
        SandboxBackend backend = SandboxContext.getBackend();
        String providerId = SandboxContext.getProviderId();
        if (mode == null) mode = properties.getDefaultMode();
        if (backend == null) backend = properties.getDefaultBackend();
        Duration timeout = resolveTimeout(timeout_seconds);
        log.info("sandbox_execute: language={}, timeout={}, mode={}, backend={}, provider={}", language, timeout, mode, backend, providerId);

        if (mode == SandboxMode.SESSION) {
            // Only E2B supports SESSION mode (cloud-based, no instance affinity issues)
            if (backend != SandboxBackend.E2B) {
                return "Error: SESSION mode is only supported with E2B backend. LOCAL and DOCKER backends are stateless only.";
            }
            String sessionId = SandboxContext.getSessionId();
            String agentId = SandboxContext.getAgentId();
            if (sessionId == null || agentId == null) {
                return "Error: Session context not set for session mode";
            }
            return sandboxManager.execute(sessionId, agentId, backend.name(), providerId, language, code, timeout);
        }

        return statelessExecutor.execute(backend, language, code, timeout);
    }

    private Duration resolveTimeout(Integer seconds) {
        if (seconds == null) return properties.getDefaultTimeout();
        Duration requested = Duration.ofSeconds(seconds);
        if (requested.compareTo(properties.getMaxTimeout()) > 0) return properties.getMaxTimeout();
        return requested;
    }
}
