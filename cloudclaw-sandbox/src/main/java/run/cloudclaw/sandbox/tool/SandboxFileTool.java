package run.cloudclaw.sandbox.tool;

import run.cloudclaw.sandbox.core.SandboxBackend;
import run.cloudclaw.sandbox.core.SandboxContext;
import run.cloudclaw.sandbox.core.SandboxManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class SandboxFileTool {

    private static final Logger log = LoggerFactory.getLogger(SandboxFileTool.class);

    private final SandboxManager sandboxManager;

    public SandboxFileTool(SandboxManager sandboxManager) {
        this.sandboxManager = sandboxManager;
    }

    private String backend() {
        SandboxBackend b = SandboxContext.getBackend();
        return b != null ? b.name() : "LOCAL";
    }

    @Tool(description = "Read a file from the sandbox workspace. Only available in session mode.")
    public String sandbox_file_read(
            @ToolParam(description = "File path in the sandbox") String path) {
        String sessionId = SandboxContext.getSessionId();
        String agentId = SandboxContext.getAgentId();
        if (sessionId == null) return "Error: No session context";
        log.info("sandbox_file_read: session={}, path={}", sessionId, path);
        return sandboxManager.readFile(sessionId, agentId, backend(), SandboxContext.getProviderId(), path);
    }

    @Tool(description = "Write a file to the sandbox workspace. Only available in session mode.")
    public String sandbox_file_write(
            @ToolParam(description = "File path in the sandbox") String path,
            @ToolParam(description = "File content to write") String content) {
        String sessionId = SandboxContext.getSessionId();
        String agentId = SandboxContext.getAgentId();
        if (sessionId == null) return "Error: No session context";
        log.info("sandbox_file_write: session={}, path={}", sessionId, path);
        return sandboxManager.writeFile(sessionId, agentId, backend(), SandboxContext.getProviderId(), path, content);
    }

    @Tool(description = "List files in the sandbox workspace directory. Only available in session mode.")
    public String sandbox_file_list(
            @ToolParam(description = "Directory path (default: root)", required = false) String directory) {
        String sessionId = SandboxContext.getSessionId();
        String agentId = SandboxContext.getAgentId();
        if (sessionId == null) return "Error: No session context";
        log.info("sandbox_file_list: session={}, dir={}", sessionId, directory);
        return sandboxManager.listFiles(sessionId, agentId, backend(), SandboxContext.getProviderId(), directory);
    }
}
