package run.cloudclaw.common.dto.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Workflow 配置 JSON 反序列化")
class WorkflowDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Nested
    @DisplayName("RouterConfig")
    class RouterConfigTests {

        @Test
        @DisplayName("应正确反序列化 allow_fallback")
        void allowFallback() throws Exception {
            String json = """
                { "allow_fallback": false }
                """;
            RouterConfig config = objectMapper.readValue(json, RouterConfig.class);
            assertFalse(config.isAllowFallback());
        }

        @Test
        @DisplayName("allow_fallback 默认值应为 true")
        void allowFallbackDefault() throws Exception {
            String json = "{}";
            RouterConfig config = objectMapper.readValue(json, RouterConfig.class);
            assertTrue(config.isAllowFallback());
        }

        @Test
        @DisplayName("应正确序列化 allow_fallback 为 snake_case")
        void serialize() throws Exception {
            RouterConfig config = new RouterConfig(false);
            String json = objectMapper.writeValueAsString(config);
            assertTrue(json.contains("allow_fallback"));
            assertFalse(json.contains("allowFallback"));
        }
    }

    @Nested
    @DisplayName("PipelineConfig")
    class PipelineConfigTests {

        @Test
        @DisplayName("应正确反序列化 passthrough_mode")
        void passthroughMode() throws Exception {
            String json = """
                { "passthrough_mode": "replace" }
                """;
            PipelineConfig config = objectMapper.readValue(json, PipelineConfig.class);
            assertEquals("replace", config.getPassthroughMode());
        }

        @Test
        @DisplayName("passthrough_mode 默认值应为 append")
        void passthroughModeDefault() throws Exception {
            String json = "{}";
            PipelineConfig config = objectMapper.readValue(json, PipelineConfig.class);
            assertEquals("append", config.getPassthroughMode());
        }
    }

    @Nested
    @DisplayName("ParallelConfig")
    class ParallelConfigTests {

        @Test
        @DisplayName("应正确反序列化 merge_strategy 和 max_concurrent")
        void parallelConfig() throws Exception {
            String json = """
                { "merge_strategy": "summarize", "max_concurrent": 3 }
                """;
            ParallelConfig config = objectMapper.readValue(json, ParallelConfig.class);
            assertEquals("summarize", config.getMergeStrategy());
            assertEquals(3, config.getMaxConcurrent());
        }

        @Test
        @DisplayName("默认值应为 concat 和 5")
        void parallelConfigDefault() throws Exception {
            String json = "{}";
            ParallelConfig config = objectMapper.readValue(json, ParallelConfig.class);
            assertEquals("concat", config.getMergeStrategy());
            assertEquals(5, config.getMaxConcurrent());
        }
    }

    @Nested
    @DisplayName("SupervisorConfig")
    class SupervisorConfigTests {

        @Test
        @DisplayName("应正确反序列化 max_iterations、planner_prompt、reviewer_prompt")
        void supervisorConfig() throws Exception {
            String json = """
                {
                  "max_iterations": 10,
                  "planner_prompt": "Plan carefully",
                  "reviewer_prompt": "Review thoroughly"
                }
                """;
            SupervisorConfig config = objectMapper.readValue(json, SupervisorConfig.class);
            assertEquals(10, config.getMaxIterations());
            assertEquals("Plan carefully", config.getPlannerPrompt());
            assertEquals("Review thoroughly", config.getReviewerPrompt());
        }

        @Test
        @DisplayName("max_iterations 默认值应为 5")
        void supervisorConfigDefault() throws Exception {
            String json = "{}";
            SupervisorConfig config = objectMapper.readValue(json, SupervisorConfig.class);
            assertEquals(5, config.getMaxIterations());
            assertNull(config.getPlannerPrompt());
        }
    }

    @Nested
    @DisplayName("HandoffConfig")
    class HandoffConfigTests {

        @Test
        @DisplayName("应正确反序列化 auto_return")
        void handoffConfig() throws Exception {
            String json = """
                { "auto_return": true }
                """;
            HandoffConfig config = objectMapper.readValue(json, HandoffConfig.class);
            assertTrue(config.isAutoReturn());
        }

        @Test
        @DisplayName("auto_return 默认值应为 false")
        void handoffConfigDefault() throws Exception {
            String json = "{}";
            HandoffConfig config = objectMapper.readValue(json, HandoffConfig.class);
            assertFalse(config.isAutoReturn());
        }
    }

    @Nested
    @DisplayName("WorkflowDef")
    class WorkflowDefTests {

        @Test
        @DisplayName("完整工作流定义应正确反序列化")
        void fullWorkflowDef() throws Exception {
            String json = """
                {
                  "mode": "pipeline",
                  "nodes": [
                    {
                      "id": "node_1",
                      "name": "Drafter",
                      "display_name": "起草助手",
                      "system_prompt": "You are a drafter.",
                      "description": "Drafts documents"
                    },
                    {
                      "id": "node_2",
                      "name": "Reviewer",
                      "display_name": "审核助手",
                      "system_prompt": "You are a reviewer.",
                      "description": "Reviews documents"
                    }
                  ],
                  "pipeline_config": {
                    "passthrough_mode": "append"
                  }
                }
                """;
            WorkflowDef def = objectMapper.readValue(json, WorkflowDef.class);
            assertEquals(WorkflowMode.PIPELINE, def.getMode());
            assertEquals(2, def.getNodes().size());
            assertEquals("node_1", def.getNodes().get(0).getId());
            assertEquals("起草助手", def.getNodes().get(0).getDisplayName());
            assertNotNull(def.getPipelineConfig());
            assertEquals("append", def.getPipelineConfig().getPassthroughMode());
        }

        @Test
        @DisplayName("Router 模式工作流应正确反序列化")
        void routerWorkflow() throws Exception {
            String json = """
                {
                  "mode": "router",
                  "nodes": [
                    { "id": "n1", "name": "A", "description": "Handles A" },
                    { "id": "n2", "name": "B", "description": "Handles B" }
                  ],
                  "router_config": { "allow_fallback": true }
                }
                """;
            WorkflowDef def = objectMapper.readValue(json, WorkflowDef.class);
            assertEquals(WorkflowMode.ROUTER, def.getMode());
            assertNotNull(def.getRouterConfig());
            assertTrue(def.getRouterConfig().isAllowFallback());
        }

        @Test
        @DisplayName("WorkflowMode 应支持大小写不敏感")
        void workflowModeCaseInsensitive() throws Exception {
            String json = """
                { "mode": "SUPERVISOR" }
                """;
            WorkflowDef def = objectMapper.readValue(json, WorkflowDef.class);
            assertEquals(WorkflowMode.SUPERVISOR, def.getMode());
        }

        @Test
        @DisplayName("WorkflowNode 应正确反序列化 snake_case 字段")
        void workflowNodeSnakeCase() throws Exception {
            String json = """
                {
                  "id": "n1",
                  "name": "Test",
                  "display_name": "测试节点",
                  "ref_agent_id": "agent-123",
                  "system_prompt": "Hello",
                  "model_id": "model-456",
                  "mcp_server_ids": ["mcp1", "mcp2"],
                  "skill_ids": ["skill1"],
                  "description": "A test node"
                }
                """;
            WorkflowNode node = objectMapper.readValue(json, WorkflowNode.class);
            assertEquals("n1", node.getId());
            assertEquals("测试节点", node.getDisplayName());
            assertEquals("agent-123", node.getRefAgentId());
            assertEquals("model-456", node.getModelId());
            assertEquals(2, node.getMcpServerIds().size());
            assertEquals("skill1", node.getSkillIds().get(0));
        }
    }

    @Nested
    @DisplayName("WorkflowMode 序列化")
    class WorkflowModeTests {

        @Test
        @DisplayName("应序列化为小写字符串")
        void serializeToLowercase() throws Exception {
            String json = objectMapper.writeValueAsString(WorkflowMode.PARALLEL);
            assertEquals("\"parallel\"", json);
        }

        @Test
        @DisplayName("未知模式应抛出异常")
        void unknownModeThrows() {
            assertThrows(Exception.class, () ->
                objectMapper.readValue("\"unknown_mode\"", WorkflowMode.class));
        }
    }
}
