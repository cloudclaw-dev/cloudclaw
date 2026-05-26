package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.dto.workflow.PipelineConfig;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineExecutorTest {

    @Mock private WorkflowNodeResolver nodeResolver;
    @Mock private WorkflowChatHelper chatHelper;

    private final Executor directExecutor = Runnable::run;
    private PipelineExecutor executor;

    private static final String USER_ID = "user1";
    private static final String SESSION_ID = UUID.randomUUID().toString();
    private static final String USER_MSG = "Analyze this data";

    @BeforeEach
    void setUp() {
        executor = new PipelineExecutor(nodeResolver, chatHelper, directExecutor);
    }

    private AgentConfig createConfig() {
        AgentConfig config = new AgentConfig();
        config.setAgentId(UUID.randomUUID().toString());
        config.setModelId("gpt-4");
        config.setSystemPrompt("You are helpful");
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

    private WorkflowDef createWorkflow(List<WorkflowNode> nodes, PipelineConfig pipelineConfig) {
        WorkflowDef def = new WorkflowDef();
        def.setNodes(nodes);
        def.setPipelineConfig(pipelineConfig);
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
    @DisplayName("Sequential pipeline execution")
    class SequentialExecution {

        @Test
        @DisplayName("Should execute steps sequentially and emit pipeline_step events")
        void sequentialSteps() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent step1 = createNode("node_1", "Drafter");
            ResolvedNodeAgent step2 = createNode("node_2", "Reviewer");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(step1, step2));
            when(chatHelper.buildNodeConfig(eq(config), any())).thenReturn(config);
            when(chatHelper.resolveToolCallbacks(any(), any())).thenReturn(List.of());

            when(chatHelper.streamLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Flux.just("Draft result"))
                    .thenReturn(Flux.just("Review result"));

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Drafter"), createWorkflowNode("node_2", "Reviewer")),
                    new PipelineConfig()
            );

            List<ChatChunk> chunks = executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            assertNotNull(chunks);

            // Should have pipeline_step events for each step
            List<ChatChunk> stepEvents = chunks.stream()
                    .filter(c -> "pipeline_step".equals(c.getType()))
                    .toList();
            assertEquals(2, stepEvents.size());
            assertEquals(1, stepEvents.get(0).getStep());
            assertEquals(2, stepEvents.get(1).getStep());

            // Should have done chunk
            assertTrue(chunks.stream().anyMatch(ChatChunk::isDone));
        }

        @Test
        @DisplayName("Should pass original message to first step")
        void firstStepGetsOriginalMessage() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent step1 = createNode("node_1", "Step1");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(step1));
            when(chatHelper.buildNodeConfig(eq(config), any())).thenReturn(config);
            when(chatHelper.resolveToolCallbacks(any(), any())).thenReturn(List.of());
            when(chatHelper.streamLlmWithTools(
                    any(), any(), eq(USER_MSG), anyList(), anyInt(), any(), any()))
                    .thenReturn(Flux.just("Result"));
            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Step1")),
                    new PipelineConfig()
            );

            executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            verify(chatHelper).streamLlmWithTools(
                    any(), any(), eq(USER_MSG), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("Should append previous step result in append passthrough mode")
        void appendPassthrough() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent step1 = createNode("node_1", "Step1");
            ResolvedNodeAgent step2 = createNode("node_2", "Step2");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(step1, step2));
            when(chatHelper.buildNodeConfig(eq(config), any())).thenReturn(config);
            when(chatHelper.resolveToolCallbacks(any(), any())).thenReturn(List.of());

            when(chatHelper.streamLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Flux.just("Step1 output"))
                    .thenReturn(Flux.just("Step2 output"));

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            PipelineConfig pipelineConfig = new PipelineConfig();
            pipelineConfig.setPassthroughMode("append");

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Step1"), createWorkflowNode("node_2", "Step2")),
                    pipelineConfig
            );

            executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            // Second call should contain "Previous step result" appended
            verify(chatHelper, times(1)).streamLlmWithTools(
                    any(), any(), contains("Previous step result"), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("Should replace user message in replace passthrough mode")
        void replacePassthrough() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent step1 = createNode("node_1", "Step1");
            ResolvedNodeAgent step2 = createNode("node_2", "Step2");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(step1, step2));
            when(chatHelper.buildNodeConfig(eq(config), any())).thenReturn(config);
            when(chatHelper.resolveToolCallbacks(any(), any())).thenReturn(List.of());

            when(chatHelper.streamLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Flux.just("Step1 output"))
                    .thenReturn(Flux.just("Step2 output"));

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            PipelineConfig pipelineConfig = new PipelineConfig();
            pipelineConfig.setPassthroughMode("replace");

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Step1"), createWorkflowNode("node_2", "Step2")),
                    pipelineConfig
            );

            executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            // Second call's input should be just "Step1 output" (not containing user msg)
            verify(chatHelper).streamLlmWithTools(
                    any(), any(), eq("Step1 output"), anyList(), anyInt(), any(), any());
        }
    }

    @Nested
    @DisplayName("History passing")
    class HistoryPassing {

        @Test
        @DisplayName("Should pass history to each step")
        void shouldPassHistory() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent step1 = createNode("node_1", "Step1");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(step1));
            when(chatHelper.buildNodeConfig(eq(config), any())).thenReturn(config);
            when(chatHelper.resolveToolCallbacks(any(), any())).thenReturn(List.of());

            List<Message> history = List.of(
                    createMsg("user", "prev"), createMsg("assistant", "ans")
            );

            when(chatHelper.streamLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), eq(history), any()))
                    .thenReturn(Flux.just("Result"));

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Step1")),
                    new PipelineConfig()
            );

            executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, history)
                    .collectList().block();

            verify(chatHelper).streamLlmWithTools(
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
}
