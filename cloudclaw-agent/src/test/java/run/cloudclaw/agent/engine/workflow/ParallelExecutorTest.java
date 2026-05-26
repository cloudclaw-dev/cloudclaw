package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.dto.workflow.ParallelConfig;
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
class ParallelExecutorTest {

    @Mock private WorkflowNodeResolver nodeResolver;
    @Mock private WorkflowChatHelper chatHelper;

    private final Executor directExecutor = Runnable::run;
    private ParallelExecutor executor;

    private static final String USER_ID = "user1";
    private static final String SESSION_ID = UUID.randomUUID().toString();
    private static final String USER_MSG = "Analyze from multiple angles";

    @BeforeEach
    void setUp() {
        executor = new ParallelExecutor(nodeResolver, chatHelper, directExecutor, directExecutor);
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

    private WorkflowDef createWorkflow(List<WorkflowNode> nodes, ParallelConfig parallelConfig) {
        WorkflowDef def = new WorkflowDef();
        def.setNodes(nodes);
        def.setParallelConfig(parallelConfig);
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
    @DisplayName("Parallel execution with concat merge")
    class ConcatMerge {

        @Test
        @DisplayName("Should execute all nodes and concat results")
        void concatResults() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent node1 = createNode("node_1", "Analyst1");
            ResolvedNodeAgent node2 = createNode("node_2", "Analyst2");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(node1, node2));
            when(chatHelper.buildNodeConfig(eq(config), any())).thenReturn(config);
            when(chatHelper.resolveToolCallbacks(any(), any())).thenReturn(List.of());

            when(chatHelper.callLlm(
                    any(), any(), any(), any(), any()))
                    .thenReturn("Analysis from node1")
                    .thenReturn("Analysis from node2");

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            ParallelConfig parallelConfig = new ParallelConfig();
            parallelConfig.setMergeStrategy("concat");

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Analyst1"), createWorkflowNode("node_2", "Analyst2")),
                    parallelConfig
            );

            List<ChatChunk> chunks = executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            assertNotNull(chunks);

            // Should have parallel_start event
            assertTrue(chunks.stream().anyMatch(c -> "parallel_start".equals(c.getType())));
            // Should have parallel_merge event
            assertTrue(chunks.stream().anyMatch(c -> "parallel_merge".equals(c.getType())));
            // Should have done
            assertTrue(chunks.stream().anyMatch(ChatChunk::isDone));

            // Both nodes should be called
            verify(chatHelper, times(2)).callLlm(
                    any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Parallel execution with summarize merge")
    class SummarizeMerge {

        @Test
        @DisplayName("Should summarize results when mergeStrategy is 'summarize'")
        void summarizeResults() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent node1 = createNode("node_1", "Analyst1");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(node1));
            when(chatHelper.buildNodeConfig(eq(config), any())).thenReturn(config);
            when(chatHelper.resolveToolCallbacks(any(), any())).thenReturn(List.of());

            when(chatHelper.callLlm(
                    any(), any(), any(), any(), any()))
                    .thenReturn("Analysis result");

            // Extra call for summarization
            when(chatHelper.callLlm(
                    eq("gpt-4"), any(), contains("Analysis result"), any()))
                    .thenReturn("Summarized analysis");

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            ParallelConfig parallelConfig = new ParallelConfig();
            parallelConfig.setMergeStrategy("summarize");

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Analyst1")),
                    parallelConfig
            );

            List<ChatChunk> chunks = executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            assertNotNull(chunks);
            assertTrue(chunks.stream().anyMatch(ChatChunk::isDone));
        }
    }

    @Nested
    @DisplayName("History passing")
    class HistoryPassing {

        @Test
        @DisplayName("Should pass history to each parallel node")
        void shouldPassHistory() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent node1 = createNode("node_1", "Analyst1");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(node1));
            when(chatHelper.buildNodeConfig(eq(config), any())).thenReturn(config);
            when(chatHelper.resolveToolCallbacks(any(), any())).thenReturn(List.of());

            List<Message> history = List.of(
                    createMsg("user", "prev"), createMsg("assistant", "ans")
            );

            when(chatHelper.callLlm(
                    any(), any(), any(), eq(history), any()))
                    .thenReturn("Result");

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            ParallelConfig parallelConfig = new ParallelConfig();
            parallelConfig.setMergeStrategy("concat");

            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Analyst1")),
                    parallelConfig
            );

            executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, history)
                    .collectList().block();

            verify(chatHelper).callLlm(
                    any(), any(), any(), eq(history), any());
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
