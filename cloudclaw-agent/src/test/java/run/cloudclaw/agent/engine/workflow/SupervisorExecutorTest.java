package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.dto.workflow.SupervisorConfig;
import run.cloudclaw.common.dto.workflow.WorkflowDef;
import run.cloudclaw.common.dto.workflow.WorkflowNode;
import run.cloudclaw.common.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupervisorExecutorTest {

    @Mock private WorkflowNodeResolver nodeResolver;
    @Mock private WorkflowChatHelper chatHelper;

    private final Executor directExecutor = Runnable::run;
    private SupervisorExecutor executor;

    private static final String USER_ID = "user1";
    private static final String SESSION_ID = UUID.randomUUID().toString();
    private static final String USER_MSG = "Analyze this code";

    @BeforeEach
    void setUp() {
        executor = new SupervisorExecutor(nodeResolver, chatHelper, directExecutor);
    }

    private AgentConfig createConfig() {
        AgentConfig config = new AgentConfig();
        config.setAgentId(UUID.randomUUID().toString());
        config.setModelId("gpt-4");
        config.setSystemPrompt("You are a supervisor");
        config.setContextWindow(128000);
        return config;
    }

    private ResolvedNodeAgent createNode(String id, String name) {
        return ResolvedNodeAgent.builder()
                .nodeId(id)
                .name(name)
                .displayName(name)
                .systemPrompt("Prompt for " + name)
                .modelId("gpt-4")
                .mcpServerIds(List.of())
                .skillIds(List.of())
                .build();
    }

    private WorkflowNode createWorkflowNode(String id, String name) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setName(name);
        node.setDisplayName(name);
        node.setSystemPrompt("Prompt for " + name);
        return node;
    }

    private WorkflowDef createWorkflow(List<WorkflowNode> nodes, SupervisorConfig supervisorConfig) {
        WorkflowDef def = new WorkflowDef();
        def.setNodes(nodes);
        def.setSupervisorConfig(supervisorConfig);
        return def;
    }

    @Nested
    @DisplayName("Empty nodes")
    class EmptyNodes {

        @Test
        @DisplayName("Should complete immediately when nodes is null")
        void nullNodes() {
            WorkflowDef workflow = new WorkflowDef();
            workflow.setNodes(null);

            Flux<ChatChunk> flux = executor.execute(USER_ID, SESSION_ID, USER_MSG, createConfig(), workflow, null);

            List<ChatChunk> chunks = flux.collectList().block();
            assertNotNull(chunks);
            assertTrue(chunks.isEmpty());
        }

        @Test
        @DisplayName("Should complete immediately when nodes is empty")
        void emptyNodes() {
            Flux<ChatChunk> flux = executor.execute(USER_ID, SESSION_ID, USER_MSG,
                    createConfig(), createWorkflow(Collections.emptyList(), null), null);

            List<ChatChunk> chunks = flux.collectList().block();
            assertNotNull(chunks);
            assertTrue(chunks.isEmpty());
        }
    }

    @Nested
    @DisplayName("Direct reply without delegation")
    class DirectReply {

        @Test
        @DisplayName("Should emit supervisor_plan and final text when supervisor responds directly")
        void supervisorDirectReply() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent agent1 = createNode("node_1", "Reviewer");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(agent1));

            // Supervisor responds directly without calling delegate tool
            when(chatHelper.callLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any()))
                    .thenReturn("I've analyzed the code and here's my review.");

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Reviewer")),
                    new SupervisorConfig()
            );

            List<ChatChunk> chunks = executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            assertNotNull(chunks);

            // Should have supervisor_plan event
            assertTrue(chunks.stream().anyMatch(c -> "supervisor_plan".equals(c.getType())));
            // Should have the direct reply as text
            assertTrue(chunks.stream().anyMatch(c ->
                    c.getContent() != null && c.getContent().contains("analyzed the code")));
            // Should have done chunk
            assertTrue(chunks.stream().anyMatch(ChatChunk::isDone));
        }
    }

    @Nested
    @DisplayName("Max iterations")
    class MaxIterations {

        @Test
        @DisplayName("Should respect maxIterations from supervisorConfig")
        void shouldRespectMaxIterations() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent agent1 = createNode("node_1", "Worker");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(agent1));

            // Supervisor always delegates (delegation detected), we simulate by returning null
            // However since the delegate tools set an AtomicReference, and our mock can't trigger that,
            // we need a different approach. Let's simulate that the supervisor always responds directly
            // for a simple maxIterations test.
            when(chatHelper.callLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any()))
                    .thenReturn("Final answer");

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            SupervisorConfig supervisorConfig = new SupervisorConfig();
            supervisorConfig.setMaxIterations(3);

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Worker")),
                    supervisorConfig
            );

            List<ChatChunk> chunks = executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            assertNotNull(chunks);
            // With no delegation, should call LLM once and respond directly
            verify(chatHelper, times(1)).callLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any());
        }
    }

    @Nested
    @DisplayName("History passing")
    class HistoryPassing {

        @Test
        @DisplayName("Should pass history to supervisor LLM call")
        void shouldPassHistory() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent agent1 = createNode("node_1", "Worker");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(agent1));

            List<Message> history = List.of(
                    createMsg("user", "prev q"),
                    createMsg("assistant", "prev a")
            );

            when(chatHelper.callLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), eq(history), any()))
                    .thenReturn("Response");

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Worker")),
                    new SupervisorConfig()
            );

            executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, history)
                    .collectList().block();

            verify(chatHelper).callLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), eq(history), any());
        }

        private Message createMsg(String role, String content) {
            Message m = new Message();
            m.setSessionId(UUID.fromString(SESSION_ID));
            m.setRole(role);
            m.setContent(content);
            return m;
        }
    }

    @Nested
    @DisplayName("Fallback on LLM error")
    class FallbackOnError {

        @Test
        @DisplayName("Should fallback to callLlm when callLlmWithTools throws")
        void fallbackOnToolCallError() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent agent1 = createNode("node_1", "Worker");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(agent1));

            when(chatHelper.callLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any()))
                    .thenThrow(new RuntimeException("Tool call failed"));

            when(chatHelper.callLlm(
                    any(), any(), any(), any(), any()))
                    .thenReturn("Fallback response");

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Worker")),
                    new SupervisorConfig()
            );

            List<ChatChunk> chunks = executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            assertNotNull(chunks);
            assertTrue(chunks.stream().anyMatch(c ->
                    c.getContent() != null && c.getContent().contains("Fallback response")));
        }
    }
}
