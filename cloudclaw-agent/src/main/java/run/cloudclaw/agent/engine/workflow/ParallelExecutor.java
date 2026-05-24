package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.dto.workflow.ParallelConfig;
import run.cloudclaw.common.dto.workflow.WorkflowDef;
import run.cloudclaw.common.dto.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Parallel executor: runs all nodes concurrently, then merges results.
 *
 * <p>Uses {@link CompletableFuture} for parallel execution with a {@link Semaphore}
 * to enforce the maxConcurrent limit. Results are collected and merged either by
 * concatenation or LLM summarization.</p>
 */
@Component
@Slf4j
public class ParallelExecutor {

    private final WorkflowNodeResolver nodeResolver;
    private final WorkflowChatHelper chatHelper;
    private final Executor workflowExecutor;

    public ParallelExecutor(WorkflowNodeResolver nodeResolver,
                           WorkflowChatHelper chatHelper,
                           @Qualifier("workflowExecutor") Executor workflowExecutor) {
        this.nodeResolver = nodeResolver;
        this.chatHelper = chatHelper;
        this.workflowExecutor = workflowExecutor;
    }

    public Flux<ChatChunk> execute(String userId, String sessionId,
                                    String userMessage, AgentConfig config, WorkflowDef workflow) {
        Sinks.Many<ChatChunk> sink = Sinks.many().multicast().onBackpressureBuffer(256);

        List<WorkflowNode> nodes = workflow.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            sink.tryEmitComplete();
            return sink.asFlux();
        }

        List<ResolvedNodeAgent> resolvedNodes = nodeResolver.resolveAll(nodes, config);

        ParallelConfig parallelConfig = workflow.getParallelConfig();
        String mergeStrategy = parallelConfig != null && parallelConfig.getMergeStrategy() != null
                ? parallelConfig.getMergeStrategy() : "concat";
        int maxConcurrent = parallelConfig != null ? parallelConfig.getMaxConcurrent() : 5;

        Runnable parallelThread = () -> {
            try {
                // Emit parallel_start event
                List<String> nodeNames = resolvedNodes.stream()
                        .map(ResolvedNodeAgent::getDisplayName)
                        .collect(Collectors.toList());
                sink.tryEmitNext(ChatChunk.parallelStart(nodeNames));

                // Run nodes in parallel with semaphore for concurrency control
                Semaphore semaphore = new Semaphore(maxConcurrent);
                List<CompletableFuture<String>> futures = new ArrayList<>();

                for (ResolvedNodeAgent node : resolvedNodes) {
                    CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                        List<McpSyncClient> mcpClients = new ArrayList<>();
                        try {
                            semaphore.acquire();
                            sink.tryEmitNext(ChatChunk.parallelProgress(node.getDisplayName(), "Processing..."));

                            // Build prompt using PromptAssembler
                            String systemPrompt = chatHelper.buildNodeSystemPrompt(node, config, userId, sessionId, userMessage);

                            // Resolve tools for this node
                            AgentConfig nodeConfig = chatHelper.buildNodeConfig(config, node);
                            List<ToolCallback> toolCallbacks = new ArrayList<>(chatHelper.resolveToolCallbacks(nodeConfig, mcpClients));

                            int maxToolCalls = config.getMaxToolCalls() != null ? config.getMaxToolCalls() : 50;
                            int maxToolResultChars = config.getMaxToolResultChars() != null ? config.getMaxToolResultChars() : 3000;
                            List<ToolCallback> truncatedCallbacks = toolCallbacks.stream()
                                    .map(cb -> (ToolCallback) new run.cloudclaw.agent.engine.TruncatingToolCallback(cb, maxToolResultChars))
                                    .toList();

                            // Call LLM with tools (blocking — each parallel node runs independently)
                            WorkflowChatHelper.LogContext logCtx = new WorkflowChatHelper.LogContext(
                                    sessionId, config.getAgentId().toString(), userId, node.getDisplayName());
                            String result;
                            if (truncatedCallbacks.isEmpty()) {
                                result = chatHelper.callLlm(node.getModelId(), systemPrompt, userMessage, logCtx);
                            } else {
                                result = chatHelper.callLlmWithTools(node.getModelId(), systemPrompt, userMessage, truncatedCallbacks, maxToolCalls, logCtx);
                            }

                            sink.tryEmitNext(ChatChunk.parallelComplete(node.getDisplayName(), result));
                            return result;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return "[Error: interrupted]";
                        } catch (Exception e) {
                            log.error("Parallel node {} error: {}", node.getName(), e.getMessage());
                            sink.tryEmitNext(ChatChunk.parallelComplete(node.getName(), "[Error: " + e.getMessage() + "]"));
                            return "[Error: " + e.getMessage() + "]";
                        } finally {
                            semaphore.release();
                            // Close MCP clients
                            for (McpSyncClient client : mcpClients) {
                                try { client.close(); } catch (Exception ignored) {}
                            }
                        }
                    }, workflowExecutor);
                    futures.add(future);
                }

                // Wait for all to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // Collect results in order
                List<String> results = new ArrayList<>();
                for (CompletableFuture<String> f : futures) {
                    results.add(f.get());
                }

                // Merge results
                sink.tryEmitNext(ChatChunk.parallelMerge(mergeStrategy));

                String mergedResult;
                if ("summarize".equals(mergeStrategy)) {
                    mergedResult = summarizeResults(results, resolvedNodes, config, sessionId, userId);
                } else {
                    mergedResult = concatResults(results, resolvedNodes);
                }

                // Emit merged result as text
                sink.tryEmitNext(ChatChunk.text(mergedResult));

                // Done
                sink.tryEmitNext(chatHelper.buildDoneChunk(config, userMessage, mergedResult));
                sink.tryEmitComplete();

            } catch (Exception e) {
                log.error("Parallel execution error: {}", e.getMessage(), e);
                sink.tryEmitError(e);
            }
        };

        workflowExecutor.execute(parallelThread);

        return sink.asFlux();
    }

    private String concatResults(List<String> results, List<ResolvedNodeAgent> nodes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            String name = nodes.get(i).getDisplayName();
            sb.append("=== ").append(name).append(" ===\n");
            sb.append(results.get(i));
            if (i < results.size() - 1) sb.append("\n\n");
        }
        return sb.toString();
    }

    private String summarizeResults(List<String> results, List<ResolvedNodeAgent> nodes, AgentConfig config,
                                     String sessionId, String userId) {
        StringBuilder input = new StringBuilder();
        input.append("以下是多个专家的分析结果，请综合整理为一份统一的回复：\n\n");
        for (int i = 0; i < results.size(); i++) {
            input.append("### ").append(nodes.get(i).getDisplayName()).append("\n");
            input.append(results.get(i)).append("\n\n");
        }

        String systemPrompt = "你是一个综合分析助手。请将多个专家的分析结果综合整理为一份清晰、统一的回复。保留各专家的核心观点，去除重复内容。";
        try {
            WorkflowChatHelper.LogContext logCtx = new WorkflowChatHelper.LogContext(
                    sessionId, config.getAgentId().toString(), userId, "parallel-merge");
            return chatHelper.callLlm(config.getModelId(), systemPrompt, input.toString(), logCtx);
        } catch (Exception e) {
            log.warn("Summarize failed, falling back to concat: {}", e.getMessage());
            return concatResults(results, nodes);
        }
    }
}
