package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.dto.workflow.RouterConfig;
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
class RouterExecutorTest {

    @Mock private WorkflowNodeResolver nodeResolver;
    @Mock private WorkflowChatHelper chatHelper;

    private final Executor directExecutor = Runnable::run;
    private RouterExecutor executor;

    private static final String USER_ID = "user1";
    private static final String SESSION_ID = UUID.randomUUID().toString();
    private static final String USER_MSG = "I need help with coding";

    @BeforeEach
    void setUp() {
        executor = new RouterExecutor(nodeResolver, chatHelper, directExecutor);
    }

    private AgentConfig createConfig() {
        AgentConfig config = new AgentConfig();
        config.setAgentId(UUID.randomUUID().toString());
        config.setModelId("gpt-4");
        config.setSystemPrompt("You are a router");
        config.setContextWindow(128000);
        return config;
    }

    private ResolvedNodeAgent createNode(String id, String name, String description) {
        return ResolvedNodeAgent.builder()
                .nodeId(id)
                .name(name)
                .displayName(name)
                .systemPrompt("Prompt for " + name)
                .modelId("gpt-4")
                .mcpServerIds(List.of())
                .skillIds(List.of())
                .description(description)
                .build();
    }

    private WorkflowNode createWorkflowNode(String id, String name, String description) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setName(name);
        node.setDisplayName(name);
        node.setSystemPrompt("Prompt for " + name);
        node.setDescription(description);
        return node;
    }

    private WorkflowDef createWorkflow(List<WorkflowNode> nodes, RouterConfig routerConfig) {
        WorkflowDef def = new WorkflowDef();
        def.setNodes(nodes);
        def.setRouterConfig(routerConfig);
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
    @DisplayName("Routing selection")
    class RoutingSelection {

        @Test
        @DisplayName("Should fallback to direct response when no route selected and allowFallback=true")
        void fallbackDirectResponse() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent agent1 = createNode("node_1", "Coder", "Writes code");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(agent1));
            when(chatHelper.buildNodeConfig(eq(config), any())).thenReturn(config);

            // Router LLM returns direct response (no tool call)
            when(chatHelper.callLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any()))
                    .thenReturn("I can help directly");

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            RouterConfig routerConfig = new RouterConfig();
            routerConfig.setAllowFallback(true);
            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Coder", "Writes code")),
                    routerConfig);

            List<ChatChunk> chunks = executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            assertNotNull(chunks);
            assertTrue(chunks.stream().anyMatch(c -> "I can help directly".equals(c.getContent())));
            // Should NOT call streamLlmWithTools since no route was selected
            verify(chatHelper, never()).streamLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("Should default to first agent when no route selected and allowFallback=false")
        void defaultToFirstWhenNoFallback() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent agent1 = createNode("node_1", "Coder", "Writes code");
            ResolvedNodeAgent agent2 = createNode("node_2", "Writer", "Writes docs");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(agent1, agent2));
            when(chatHelper.buildNodeConfig(eq(config), any())).thenReturn(config);

            // Router LLM returns direct response (no tool call)
            when(chatHelper.callLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any()))
                    .thenReturn("Direct response");

            when(chatHelper.resolveToolCallbacks(any(), any())).thenReturn(List.of());
            when(chatHelper.streamLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Flux.just("Code result"));

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            RouterConfig routerConfig = new RouterConfig();
            routerConfig.setAllowFallback(false);
            WorkflowDef workflow = createWorkflow(
                    List.of(
                            createWorkflowNode("node_1", "Coder", "Writes code"),
                            createWorkflowNode("node_2", "Writer", "Writes docs")
                    ),
                    routerConfig);

            List<ChatChunk> chunks = executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            assertNotNull(chunks);
            // Should have streamed response from first node
            verify(chatHelper).streamLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any());
        }
    }

    @Nested
    @DisplayName("History passing")
    class HistoryPassing {

        @Test
        @DisplayName("Should pass history to routing LLM call")
        void shouldPassHistory() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent agent1 = createNode("node_1", "Coder", "Writes code");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(agent1));
            when(chatHelper.buildNodeConfig(eq(config), any())).thenReturn(config);

            List<Message> history = List.of(
                    createMsg("user", "previous question"),
                    createMsg("assistant", "previous answer")
            );

            when(chatHelper.callLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), eq(history), any()))
                    .thenReturn("Direct response");

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            RouterConfig routerConfig = new RouterConfig();
            routerConfig.setAllowFallback(true);
            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Coder", "Writes code")),
                    routerConfig);

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
    @DisplayName("Router fallback on LLM error")
    class RouterFallbackOnError {

        @Test
        @DisplayName("Should fallback to callLlm when callLlmWithTools throws")
        void fallbackOnToolCallError() {
            AgentConfig config = createConfig();
            ResolvedNodeAgent agent1 = createNode("node_1", "Coder", "Writes code");

            when(nodeResolver.resolveAll(anyList(), eq(config))).thenReturn(List.of(agent1));

            // First call with tools throws
            when(chatHelper.callLlmWithTools(
                    any(), any(), any(), anyList(), anyInt(), any(), any()))
                    .thenThrow(new RuntimeException("Tool call failed"));

            // Fallback to direct LLM call
            when(chatHelper.callLlm(
                    any(), any(), any(), any(), any()))
                    .thenReturn("Direct fallback response");

            when(chatHelper.buildDoneChunk(eq(config), anyString(), anyString()))
                    .thenReturn(ChatChunk.done());

            RouterConfig routerConfig = new RouterConfig();
            routerConfig.setAllowFallback(true);
            WorkflowDef workflow = createWorkflow(
                    List.of(createWorkflowNode("node_1", "Coder", "Writes code")),
                    routerConfig);

            List<ChatChunk> chunks = executor.execute(USER_ID, SESSION_ID, USER_MSG, config, workflow, List.of())
                    .collectList().block();

            assertNotNull(chunks);
            assertTrue(chunks.stream().anyMatch(c -> "Direct fallback response".equals(c.getContent())));
        }
    }
}
