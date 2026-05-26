package run.cloudclaw.agent.engine;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.agent.engine.AgentTransferService.ResolvedAgent;
import run.cloudclaw.agent.engine.AgentTransferService.TransferInfo;
import run.cloudclaw.agent.engine.AgentTransferService.TransferToolCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentTransferServiceTest {

    private AgentTransferService service;

    @BeforeEach
    void setUp() {
        service = new AgentTransferService();
    }

    private AgentConfig createRootConfig() {
        AgentConfig config = new AgentConfig();
        config.setAgentId(UUID.randomUUID().toString());
        config.setName("RootAgent");
        config.setSystemPrompt("Root system prompt");
        config.setModelId("gpt-4");
        config.setMcpServerIds(List.of("mcp1"));
        config.setSkillIds(List.of("skill1"));
        return config;
    }

    private AgentConfig createRootConfigWithSubAgents() {
        AgentConfig config = createRootConfig();

        AgentConfig.SubAgentDef sub1 = new AgentConfig.SubAgentDef();
        sub1.setName("TravelBot");
        sub1.setDisplayName("Travel Assistant");
        sub1.setDescription("Helps with travel planning");
        sub1.setSystemPrompt("You are a travel expert.");
        sub1.setModelId("gpt-4-turbo");
        sub1.setMcpServerIds(List.of("mcp2"));
        sub1.setSkillIds(List.of("skill2"));

        AgentConfig.SubAgentDef sub2 = new AgentConfig.SubAgentDef();
        sub2.setName("CodeBot");
        sub2.setDisplayName("Code Assistant");
        sub2.setDescription("Helps with coding");
        sub2.setSystemPrompt("You are a coding expert.");
        sub2.setModelId(null); // inherit from root
        sub2.setMcpServerIds(null);
        sub2.setSkillIds(null);

        config.setSubAgents(List.of(sub1, sub2));
        return config;
    }

    @Nested
    @DisplayName("resolveAgent")
    class ResolveAgent {

        @Test
        @DisplayName("Should resolve root agent when activePath is null")
        void nullPath() {
            AgentConfig root = createRootConfig();
            ResolvedAgent resolved = service.resolveAgent(root, null);

            assertTrue(resolved.isRoot());
            assertEquals("RootAgent", resolved.getDisplayName());
            assertEquals("Root system prompt", resolved.getSystemPrompt());
            assertEquals("gpt-4", resolved.getModelId());
        }

        @Test
        @DisplayName("Should resolve root agent when activePath is 'root'")
        void rootPath() {
            AgentConfig root = createRootConfig();
            ResolvedAgent resolved = service.resolveAgent(root, "root");

            assertTrue(resolved.isRoot());
            assertEquals("RootAgent", resolved.getDisplayName());
        }

        @Test
        @DisplayName("Should resolve sub-agent by name")
        void subAgentPath() {
            AgentConfig root = createRootConfigWithSubAgents();
            ResolvedAgent resolved = service.resolveAgent(root, "root/TravelBot");

            assertFalse(resolved.isRoot());
            assertEquals("TravelBot", resolved.getAgentName());
            assertEquals("Travel Assistant", resolved.getDisplayName());
            assertEquals("You are a travel expert.", resolved.getSystemPrompt());
            assertEquals("gpt-4-turbo", resolved.getModelId());
            assertEquals(List.of("mcp2"), resolved.getMcpServerIds());
            assertEquals(List.of("skill2"), resolved.getSkillIds());
        }

        @Test
        @DisplayName("Should inherit modelId from root when sub-agent has none")
        void inheritModelId() {
            AgentConfig root = createRootConfigWithSubAgents();
            ResolvedAgent resolved = service.resolveAgent(root, "root/CodeBot");

            assertEquals("gpt-4", resolved.getModelId());
        }

        @Test
        @DisplayName("Should inherit empty lists for null mcpServerIds/skillIds")
        void inheritEmptyLists() {
            AgentConfig root = createRootConfigWithSubAgents();
            ResolvedAgent resolved = service.resolveAgent(root, "root/CodeBot");

            assertEquals(List.of(), resolved.getMcpServerIds());
            assertEquals(List.of(), resolved.getSkillIds());
        }

        @Test
        @DisplayName("Should throw when sub-agent not found")
        void subAgentNotFound() {
            AgentConfig root = createRootConfigWithSubAgents();

            assertThrows(IllegalArgumentException.class,
                    () -> service.resolveAgent(root, "root/NonExistent"));
        }

        @Test
        @DisplayName("Should fallback to root when no sub-agents defined but activePath is set")
        void noSubAgentsFallback() {
            AgentConfig root = createRootConfig(); // no subAgents

            ResolvedAgent resolved = service.resolveAgent(root, "root/SomeAgent");

            assertTrue(resolved.isRoot());
        }

        @Test
        @DisplayName("Should handle path without slash prefix")
        void pathWithoutRoot() {
            AgentConfig root = createRootConfigWithSubAgents();
            ResolvedAgent resolved = service.resolveAgent(root, "TravelBot");

            assertFalse(resolved.isRoot());
            assertEquals("TravelBot", resolved.getAgentName());
        }
    }

    @Nested
    @DisplayName("buildTransferTools")
    class BuildTransferTools {

        @Test
        @DisplayName("Should return empty list when no sub-agents defined")
        void noSubAgents() {
            AgentConfig root = createRootConfig();

            List<org.springframework.ai.tool.ToolCallback> tools = service.buildTransferTools(root, "root");

            assertTrue(tools.isEmpty());
        }

        @Test
        @DisplayName("Should build transfer_to_xxx tools when at root")
        void rootTransferTools() {
            AgentConfig root = createRootConfigWithSubAgents();

            List<org.springframework.ai.tool.ToolCallback> tools = service.buildTransferTools(root, "root");

            assertEquals(2, tools.size());

            // Check tool names
            String name1 = tools.get(0).getToolDefinition().name();
            String name2 = tools.get(1).getToolDefinition().name();

            assertEquals("transfer_to_travel_bot", name1);
            assertEquals("transfer_to_code_bot", name2);
        }

        @Test
        @DisplayName("Should build transfer_back_to_parent tool when at sub-agent")
        void subAgentTransferBackTool() {
            AgentConfig root = createRootConfigWithSubAgents();

            List<org.springframework.ai.tool.ToolCallback> tools = service.buildTransferTools(root, "root/TravelBot");

            assertEquals(1, tools.size());
            assertEquals("transfer_back_to_parent", tools.get(0).getToolDefinition().name());
        }
    }

    @Nested
    @DisplayName("parseTransferResult")
    class ParseTransferResult {

        @Test
        @DisplayName("Should parse valid transfer result")
        void validResult() {
            TransferInfo info = service.parseTransferResult("TRANSFER:TravelBot:root/TravelBot:User wants travel help");

            assertNotNull(info);
            assertEquals("TravelBot", info.getTargetName());
            assertEquals("root/TravelBot", info.getTargetPath());
            assertEquals("User wants travel help", info.getReason());
        }

        @Test
        @DisplayName("Should parse transfer result without reason")
        void noReason() {
            TransferInfo info = service.parseTransferResult("TRANSFER:TravelBot:root/TravelBot");

            assertNotNull(info);
            assertEquals("TravelBot", info.getTargetName());
            assertEquals("root/TravelBot", info.getTargetPath());
            assertEquals("", info.getReason());
        }

        @Test
        @DisplayName("Should return null for null input")
        void nullInput() {
            assertNull(service.parseTransferResult(null));
        }

        @Test
        @DisplayName("Should return null for non-TRANSFER prefix")
        void wrongPrefix() {
            assertNull(service.parseTransferResult("HANDOFF:TravelBot:root/TravelBot"));
        }

        @Test
        @DisplayName("Should return null for insufficient parts")
        void insufficientParts() {
            assertNull(service.parseTransferResult("TRANSFER:onlyone"));
        }
    }

    @Nested
    @DisplayName("parseTransferJson")
    class ParseTransferJson {

        @Test
        @DisplayName("Should parse valid JSON transfer result")
        void validJson() {
            String json = "{\"targetName\":\"TravelBot\",\"targetPath\":\"root/TravelBot\",\"reason\":\"Travel help\"}";
            TransferInfo info = service.parseTransferJson(json);

            assertNotNull(info);
            assertEquals("TravelBot", info.getTargetName());
            assertEquals("root/TravelBot", info.getTargetPath());
            assertEquals("Travel help", info.getReason());
        }

        @Test
        @DisplayName("Should return null for null input")
        void nullInput() {
            assertNull(service.parseTransferJson(null));
        }

        @Test
        @DisplayName("Should return null for JSON without targetPath")
        void noTargetPath() {
            assertNull(service.parseTransferJson("{\"targetName\":\"TravelBot\"}"));
        }

        @Test
        @DisplayName("Should return null for invalid JSON")
        void invalidJson() {
            assertNull(service.parseTransferJson("not json"));
        }
    }

    @Nested
    @DisplayName("getParentPath")
    class GetParentPath {

        @Test
        @DisplayName("Should return root for null path")
        void nullPath() {
            assertEquals("root", service.getParentPath(null));
        }

        @Test
        @DisplayName("Should return root for root path")
        void rootPath() {
            assertEquals("root", service.getParentPath("root"));
        }

        @Test
        @DisplayName("Should return parent for nested path")
        void nestedPath() {
            assertEquals("root", service.getParentPath("root/TravelBot"));
        }

        @Test
        @DisplayName("Should handle deeply nested path")
        void deepPath() {
            assertEquals("root/TravelBot", service.getParentPath("root/TravelBot/HotelBot"));
        }
    }

    @Nested
    @DisplayName("getDisplayName")
    class GetDisplayName {

        @Test
        @DisplayName("Should return root display name for root path")
        void rootDisplayName() {
            AgentConfig root = createRootConfig();
            assertEquals("RootAgent", service.getDisplayName(root, "root"));
        }

        @Test
        @DisplayName("Should return sub-agent display name for sub-agent path")
        void subAgentDisplayName() {
            AgentConfig root = createRootConfigWithSubAgents();
            assertEquals("Travel Assistant", service.getDisplayName(root, "root/TravelBot"));
        }
    }

    @Nested
    @DisplayName("TransferToolCallback")
    class TransferToolCallbackTest {

        @Test
        @DisplayName("Should return JSON with transfer info when called with valid input")
        void callWithValidInput() {
            TransferToolCallback callback = new TransferToolCallback(
                    "transfer_to_travel_bot", "Transfer to Travel", "TravelBot", "root/TravelBot");

            String result = callback.call("{\"reason\":\"User wants travel\"}");

            assertTrue(result.contains("TravelBot"));
            assertTrue(result.contains("root/TravelBot"));
            assertTrue(result.contains("User wants travel"));
        }

        @Test
        @DisplayName("Should handle null input")
        void callWithNullInput() {
            TransferToolCallback callback = new TransferToolCallback(
                    "transfer_to_bot", "Transfer", "Bot", "root/Bot");

            String result = callback.call(null);
            assertNotNull(result);
            assertTrue(result.contains("Bot"));
        }

        @Test
        @DisplayName("Should have returnDirect metadata")
        void returnDirectMetadata() {
            TransferToolCallback callback = new TransferToolCallback(
                    "transfer_to_bot", "Transfer", "Bot", "root/Bot");

            assertNotNull(callback.getToolMetadata());
            assertTrue(callback.getToolMetadata().returnDirect());
        }
    }
}
