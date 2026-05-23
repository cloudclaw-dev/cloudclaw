package run.cloudclaw.sandbox.tool;

import run.cloudclaw.sandbox.core.SandboxContext;
import run.cloudclaw.sandbox.core.SandboxManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class SandboxInfoTool {

    private static final Logger log = LoggerFactory.getLogger(SandboxInfoTool.class);

    private final SandboxManager sandboxManager;

    public SandboxInfoTool(SandboxManager sandboxManager) {
        this.sandboxManager = sandboxManager;
    }

    @Tool(description = "Get information about the current sandbox session, including workspace directory and status.")
    public String sandbox_info() {
        String sessionId = SandboxContext.getSessionId();
        if (sessionId == null) return "No sandbox session context.";
        log.info("sandbox_info: session={}", sessionId);
        return sandboxManager.getInfo(sessionId);
    }
}
