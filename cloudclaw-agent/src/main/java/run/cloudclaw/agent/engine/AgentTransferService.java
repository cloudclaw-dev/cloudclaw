package run.cloudclaw.agent.engine;

import run.cloudclaw.common.dto.AgentConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for Agent Transfer v2 — LLM-driven agent delegation.
 *
 * <p>Resolves the current active sub-agent, builds transfer tools,
 * and detects/executes transfers within the chat stream.</p>
 */
@Service
@Slf4j
public class AgentTransferService {

    /**
     * Resolved agent information for the current active path.
     */
    @Getter
    @AllArgsConstructor
    public static class ResolvedAgent {
        private final String agentName;
        private final String displayName;
        private final String systemPrompt;
        private final String modelId;
        private final List<String> mcpServerIds;
        private final List<String> skillIds;
        private final boolean root;
    }

    /**
     * Resolve the active agent based on the root agent config and active path.
     *
     * @param rootAgent  the root agent configuration
     * @param activePath e.g. "root" or "root/TravelAssistant"
     * @return resolved agent info
     */
    public ResolvedAgent resolveAgent(AgentConfig rootAgent, String activePath) {
        if (activePath == null || "root".equals(activePath)) {
            return new ResolvedAgent(
                    "root", rootAgent.getName(), rootAgent.getSystemPrompt(),
                    rootAgent.getModelId(), rootAgent.getMcpServerIds(),
                    rootAgent.getSkillIds(), true
            );
        }

        // Parse: "root/SubAgentName"
        String subName = activePath.contains("/") ? activePath.substring(activePath.lastIndexOf('/') + 1) : activePath;

        if (rootAgent.getSubAgents() == null) {
            log.warn("No sub-agents defined but activePath={}; falling back to root", activePath);
            return resolveAgent(rootAgent, "root");
        }

        AgentConfig.SubAgentDef sub = rootAgent.getSubAgents().stream()
                .filter(s -> s.getName().equals(subName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sub-agent not found: " + subName));

        return new ResolvedAgent(
                sub.getName(),
                sub.getDisplayName() != null ? sub.getDisplayName() : sub.getName(),
                sub.getSystemPrompt(),
                sub.getModelId() != null ? sub.getModelId() : rootAgent.getModelId(),
                sub.getMcpServerIds() != null ? sub.getMcpServerIds() : List.of(),
                sub.getSkillIds() != null ? sub.getSkillIds() : List.of(),
                false
        );
    }

    /**
     * Build transfer tool callbacks based on the current active agent path.
     *
     * @param rootAgent  the root agent config
     * @param activePath the current active agent path
     * @return list of transfer tool callbacks
     */
    public List<ToolCallback> buildTransferTools(AgentConfig rootAgent, String activePath) {
        List<ToolCallback> tools = new ArrayList<>();

        if (rootAgent.getSubAgents() == null || rootAgent.getSubAgents().isEmpty()) {
            return tools;
        }

        if ("root".equals(activePath)) {
            // Root → can transfer to any sub_agent
            for (AgentConfig.SubAgentDef sub : rootAgent.getSubAgents()) {
                tools.add(new TransferToolCallback(
                        "transfer_to_" + toSnakeCase(sub.getName()),
                        "Transfer conversation to " + sub.getDisplayName()
                                + ". Use when: " + sub.getDescription(),
                        sub.getName(),
                        "root/" + sub.getName()
                ));
            }
        } else {
            // Sub agent → can transfer back to parent
            tools.add(new TransferToolCallback(
                    "transfer_back_to_parent",
                    "Return control to the parent agent. Use when the current task is outside your expertise or is completed.",
                    "root",
                    "root"
            ));
        }

        return tools;
    }

    /**
     * Parse a transfer result string to extract target info.
     * Format: "TRANSFER:targetName:targetPath:reason"
     */
    public TransferInfo parseTransferResult(String result) {
        if (result == null || !result.startsWith("TRANSFER:")) return null;
        String[] parts = result.split(":", 4);
        if (parts.length < 3) return null;
        return new TransferInfo(
                parts[1],                              // targetName
                parts[2],                              // targetPath
                parts.length > 3 ? parts[3] : ""       // reason
        );
    }

    /**
     * Parse a JSON transfer result (from returnDirect TransferToolCallback).
     * Format: {"targetName":"...","targetPath":"...","reason":"..."}
     */
    public TransferInfo parseTransferJson(String json) {
        if (json == null || !json.contains("targetPath")) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    TransferToolCallback.MAPPER.readTree(json);
            if (!node.has("targetPath")) return null;
            return new TransferInfo(
                    node.has("targetName") ? node.get("targetName").asText() : "",
                    node.get("targetPath").asText(),
                    node.has("reason") ? node.get("reason").asText() : ""
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get parent path from current path.
     */
    public String getParentPath(String activePath) {
        if (activePath == null || "root".equals(activePath)) return "root";
        int idx = activePath.lastIndexOf('/');
        return idx > 0 ? activePath.substring(0, idx) : "root";
    }

    /**
     * Get the display name for an agent at the given path.
     */
    public String getDisplayName(AgentConfig rootAgent, String activePath) {
        ResolvedAgent resolved = resolveAgent(rootAgent, activePath);
        return resolved.getDisplayName();
    }

    private String toSnakeCase(String name) {
        // CamelCase/PascalCase → snake_case
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /**
     * Transfer info parsed from tool result.
     */
    @Getter
    @AllArgsConstructor
    public static class TransferInfo {
        private final String targetName;
        private final String targetPath;
        private final String reason;
    }

    /**
     * Dynamic transfer tool callback. Each instance represents one transfer option.
     */
    public static class TransferToolCallback implements ToolCallback {

        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

        private final String toolName;
        private final String targetName;
        private final String targetPath;
        private final ToolDefinition toolDefinition;

        public TransferToolCallback(String toolName, String description, String targetName, String targetPath) {
            this.toolName = toolName;
            this.targetName = targetName;
            this.targetPath = targetPath;
            this.toolDefinition = ToolDefinition.builder()
                    .name(toolName)
                    .description(description)
                    .inputSchema("{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\",\"description\":\"Why this transfer is needed\"}},\"required\":[\"reason\"]}")
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return DefaultToolMetadata.builder().returnDirect(true).build();
        }

        @Override
        public String call(String toolInput) {
            String reason = "";
            try {
                if (toolInput != null && !toolInput.isBlank()) {
                    com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(toolInput);
                    if (node.has("reason")) reason = node.get("reason").asText();
                }
            } catch (Exception e) {
                reason = toolInput != null ? toolInput : "";
            }
            log.info("Transfer triggered: {} \u2192 {} (reason: {})", toolName, targetPath, reason);
            try {
                return MAPPER.writeValueAsString(new TransferResult(targetName, targetPath, reason));
            } catch (Exception e) {
                return "{\"targetName\":\"" + targetName + "\",\"targetPath\":\"" + targetPath + "\"}";
            }
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }

        public String getTargetName() { return targetName; }
        public String getTargetPath() { return targetPath; }
    }

    /**
     * JSON result returned by TransferToolCallback (returnDirect mode).
     */
    @lombok.Data @lombok.AllArgsConstructor
    public static class TransferResult {
        private String targetName;
        private String targetPath;
        private String reason;
    }
}
