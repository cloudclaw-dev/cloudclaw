package run.cloudclaw.agent.engine;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.agent.config.AgentConfigService;
import run.cloudclaw.agent.engine.AgentTransferService.ResolvedAgent;
import run.cloudclaw.agent.engine.AgentTransferService.TransferInfo;
import run.cloudclaw.agent.engine.AgentTransferService.TransferToolCallback;
import run.cloudclaw.agent.engine.workflow.WorkflowEngine;
import run.cloudclaw.agent.tools.SkillTools;
import run.cloudclaw.agent.tools.MemoryTools;
import run.cloudclaw.sandbox.tool.SandboxExecuteTool;
import run.cloudclaw.sandbox.tool.SandboxFileTool;
import run.cloudclaw.sandbox.tool.SandboxInfoTool;
import run.cloudclaw.sandbox.core.SandboxBackend;
import run.cloudclaw.sandbox.core.SandboxContext;
import run.cloudclaw.sandbox.core.SandboxMode;
import run.cloudclaw.agent.prompt.PromptAssembler;
import run.cloudclaw.common.dto.AsyncChatResult;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.McpServer;
import run.cloudclaw.common.model.Message;
import run.cloudclaw.common.model.Session;
import run.cloudclaw.common.model.Skill;
import run.cloudclaw.llm.service.LlmRouteService;
import run.cloudclaw.llm.service.LlmUsageService;
import run.cloudclaw.mcp.repository.McpServerRepository;
import run.cloudclaw.agent.engine.SessionCompressor;
import run.cloudclaw.session.service.SessionService;
import run.cloudclaw.skill.service.SkillService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Core chat engine that orchestrates the full conversation flow.
 *
 * <p>Uses Spring AI's {@link ToolCallAdvisor} for automatic tool calling
 * in both streaming and non-streaming modes.</p>
 *
 * <p>Agent Transfer v2: supports sub-agent transfer via dynamically generated
 * transfer_to_xxx tools. Transfer happens within the same session.</p>
 */
@Service
@Slf4j
public class ChatEngine {

    private final LlmRouteService llmRouteService;
    private final SessionService sessionService;
    private final AgentConfigService agentConfigService;
    private final PromptAssembler promptAssembler;
    private final LlmUsageService llmUsageService;
    private final SkillService skillService;
    private final SkillTools skillTools;
    private final McpServerRepository mcpServerRepository;
    private final MemoryTools memoryTools;
    private final SessionCompressor sessionCompressor;
    private final ContextCompressor contextCompressor;
    private final Executor chatExecutor;
    private final run.cloudclaw.agent.prompt.PromptLogService promptLogService;
    private final run.cloudclaw.memory.injector.MemoryInjector memoryInjector;
    private final SandboxExecuteTool sandboxExecuteTool;
    private final SandboxFileTool sandboxFileTool;
    private final SandboxInfoTool sandboxInfoTool;
    private final AgentTransferService agentTransferService;
    private final WorkflowEngine workflowEngine;

    public ChatEngine(LlmRouteService llmRouteService,
                      SessionService sessionService,
                      AgentConfigService agentConfigService,
                      PromptAssembler promptAssembler,
                      LlmUsageService llmUsageService,
                      SkillService skillService,
                      SkillTools skillTools,
                      McpServerRepository mcpServerRepository,
                      MemoryTools memoryTools,
                      SessionCompressor sessionCompressor,
                      ContextCompressor contextCompressor,
                      @Qualifier("chatExecutor") Executor chatExecutor,
                      run.cloudclaw.agent.prompt.PromptLogService promptLogService,
                      run.cloudclaw.memory.injector.MemoryInjector memoryInjector,
                      SandboxExecuteTool sandboxExecuteTool,
                      SandboxFileTool sandboxFileTool,
                      SandboxInfoTool sandboxInfoTool,
                      AgentTransferService agentTransferService,
                      WorkflowEngine workflowEngine) {
        this.llmRouteService = llmRouteService;
        this.sessionService = sessionService;
        this.agentConfigService = agentConfigService;
        this.promptAssembler = promptAssembler;
        this.llmUsageService = llmUsageService;
        this.skillService = skillService;
        this.skillTools = skillTools;
        this.mcpServerRepository = mcpServerRepository;
        this.memoryTools = memoryTools;
        this.sessionCompressor = sessionCompressor;
        this.contextCompressor = contextCompressor;
        this.chatExecutor = chatExecutor;
        this.promptLogService = promptLogService;
        this.memoryInjector = memoryInjector;
        this.sandboxExecuteTool = sandboxExecuteTool;
        this.sandboxFileTool = sandboxFileTool;
        this.sandboxInfoTool = sandboxInfoTool;
        this.agentTransferService = agentTransferService;
        this.workflowEngine = workflowEngine;
    }

    /**
     * Execute a chat request and return the response as a streaming Flux.
     * Uses ToolCallAdvisor for automatic tool calling in stream mode.
     * Supports Agent Transfer v2: detects transfer tool calls and re-invokes LLM
     * with the target sub-agent's configuration.
     */
    public Flux<ChatChunk> chat(String userId, String sessionId, String userMessage) {
        log.info("Chat request: userId={}, sessionId={}, messageLength={}", userId, sessionId, userMessage.length());

        // 1. Load session context (with summary compression)
        List<Message> rawHistory = sessionCompressor.loadContextWithSummary(sessionId);

        // 2. Get session to find agentId
        Session session = sessionService.getSession(userId, sessionId);

        // 3. Load agent config
        AgentConfig config = agentConfigService.getAgentConfig(session.getAgentId().toString());

        // 3.1 Workflow dispatch: if agent has a workflow configured, delegate to WorkflowEngine
        if (workflowEngine.hasWorkflow(config)) {
            log.info("Agent {} has workflow mode={}, delegating to WorkflowEngine",
                    config.getAgentId(), config.getWorkflowMode());
            // Save user message first
            Message userMsg = new Message();
            userMsg.setSessionId(UUID.fromString(sessionId));
            userMsg.setRole("user");
            userMsg.setContent(userMessage);
            sessionService.saveMessage(userMsg);

            // Log user prompt
            String agentIdStr = config.getAgentId() != null ? config.getAgentId().toString() : "unknown";
            promptLogService.logAsync(sessionId, agentIdStr, userId,
                    config.getModelId(), "user", userMessage,
                    estimateTokens(userMessage), null, null);
            if (config.getSystemPrompt() != null && !config.getSystemPrompt().isEmpty()) {
                promptLogService.logAsync(sessionId, agentIdStr, userId,
                        config.getModelId(), "system", config.getSystemPrompt(),
                        estimateTokens(config.getSystemPrompt()), null, null);
            }

            Flux<ChatChunk> workflowFlux = workflowEngine.execute(userId, sessionId, userMessage, config);

            // Collect final response and save as assistant message
            StringBuilder workflowResponse = new StringBuilder();
            return workflowFlux.doOnNext(chunk -> {
                if ("text".equals(chunk.getType()) && chunk.getContent() != null) {
                    workflowResponse.append(chunk.getContent());
                }
            }).doOnComplete(() -> {
                String responseText = workflowResponse.toString();
                if (!responseText.isBlank()) {
                    Message assistantMsg = new Message();
                    assistantMsg.setSessionId(UUID.fromString(sessionId));
                    assistantMsg.setRole("assistant");
                    assistantMsg.setContent(responseText);
                    sessionService.saveMessage(assistantMsg);
                }
                // Generate title if this is the first message in the session
                if (rawHistory.isEmpty() && !responseText.isBlank()) {
                    try {
                        ChatClient chatClient = llmRouteService.getChatClient(config.getModelId());
                        generateTitleAsync(sessionId, userMessage, chatClient);
                    } catch (Exception e) {
                        log.debug("Workflow title generation failed (non-critical): {}", e.getMessage());
                    }
                }
                try {
                    int approxTokensIn = estimateTokens(userMessage) + estimateTokens(responseText);
                    llmUsageService.recordUsage(config.getModelId(), config.getModelId(), userId,
                            approxTokensIn, estimateTokens(responseText));
                } catch (Exception e) {
                    log.warn("Failed to record workflow usage: {}", e.getMessage());
                }
            });
        }

        // 3.5 Resolve active agent path
        String activePath = session.getActiveAgentPath() != null ? session.getActiveAgentPath() : "root";
        ResolvedAgent activeAgent = agentTransferService.resolveAgent(config, activePath);

        // 4. Assemble system prompt (base prompt + skills + memory)
        String systemPrompt = promptAssembler.assembleSystemPrompt(
                config, userId, sessionId, userMessage);

        // Override system prompt if we're in a sub-agent
        if (!activeAgent.isRoot() && activeAgent.getSystemPrompt() != null) {
            // Use sub-agent's system prompt, but still append skills/memory from assembler
            String basePrompt = activeAgent.getSystemPrompt();
            // Re-assemble with sub-agent's prompt
            systemPrompt = promptAssembler.assembleSubAgentSystemPrompt(
                    activeAgent, config, userId, sessionId, userMessage);
        }

        // 4.5 Dynamic context compression: trim history if exceeding token budget
        int contextWindow = config.getContextWindow() != null ? config.getContextWindow() : 128000;
        List<Message> history = contextCompressor.compress(rawHistory, systemPrompt, userMessage, contextWindow, config.getContextUsageThreshold());

        // 5. Save user message
        Message userMsg = new Message();
        userMsg.setSessionId(UUID.fromString(sessionId));
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setAgentName("root".equals(activePath) ? null : activeAgent.getAgentName());
        sessionService.saveMessage(userMsg);

        // 6. Resolve ChatClient — use sub-agent's model if specified
        String effectiveModelId = activeAgent.getModelId();
        ChatClient chatClient = llmRouteService.getChatClient(effectiveModelId);
        log.info("Resolved modelId={} for agent={} (activePath={})", effectiveModelId, config.getAgentId(), activePath);

        // 6.5 Set memory tools context for this request
        String agentIdStr = session.getAgentId() != null ? session.getAgentId().toString() : null;
        MemoryTools.setContext(userId, agentIdStr, sessionId, config.getMemoryProfileMaxTokens(), config.getMemoryTaskMaxTokens());

        // 6.6 Set sandbox tool context if sandbox is enabled
        if (Boolean.TRUE.equals(config.getSandboxEnabled())) {
            SandboxMode smode = "SESSION".equalsIgnoreCase(config.getSandboxMode()) ? SandboxMode.SESSION : SandboxMode.STATELESS;
            SandboxBackend sbackend = config.getSandboxBackend() != null ? SandboxBackend.valueOf(config.getSandboxBackend()) : SandboxBackend.LOCAL;
            String sproviderId = config.getSandboxProviderId();
            SandboxContext.set(sessionId, agentIdStr, smode, sbackend, sproviderId);
        }

        // 7. Build tool callbacks from providers that have @Tool methods
        List<McpSyncClient> createdMcpClients = new ArrayList<>();
        // Use sub-agent's mcpServerIds and skillIds if in sub-agent
        AgentConfig effectiveConfig = activeAgent.isRoot() ? config : buildSubAgentConfig(config, activeAgent);
        List<ToolCallback> toolCallbacks = resolveToolCallbacks(effectiveConfig, createdMcpClients);

        // 7.5 Add transfer tools
        List<ToolCallback> transferTools = agentTransferService.buildTransferTools(config, activePath);
        toolCallbacks.addAll(transferTools);

        // 8. Build Spring AI messages
        List<org.springframework.ai.chat.messages.Message> aiMessages = new ArrayList<>();
        for (Message msg : history) {
            switch (msg.getRole()) {
                case "user" -> aiMessages.add(new UserMessage(msg.getContent()));
                case "assistant" -> aiMessages.add(new AssistantMessage(msg.getContent()));
                case "system", "summary" -> aiMessages.add(new SystemMessage(msg.getContent()));
                default -> log.warn("Unknown message role: {}, skipping", msg.getRole());
            }
        }

        String effectiveSystemPrompt = systemPrompt;

        // 8.5 Append memory tool usage guide if memory tools are enabled
        if (!Boolean.FALSE.equals(config.getEnableMemoryTools())) {
            String memoryGuide = "\n\n## Memory\n\n" +
                    "You have persistent memory via 2 tools. Use them proactively — don't wait to be asked.\n\n" +
                    "Core principle: Save only facts that will still matter in future sessions.\n" +
                    "The most valuable memory prevents the user from having to repeat themselves.\n\n" +
                    "Two targets:\n" +
                    "- memory_profile: Who the user IS — name, role, preferences, habits, corrections.\n" +
                    "  Persists across all sessions. 1000 token limit.\n" +
                    "- memory_session: Current task context — goals, progress, agreements, constraints.\n" +
                    "  This session only. 2000 token limit.\n\n" +
                    "WHEN TO SAVE (proactive):\n" +
                    "- User corrects you or says 'remember this' / 'don't do that again'\n" +
                    "- User shares a preference, habit, or personal detail\n" +
                    "- User mentions their name, role, timezone, or communication style\n" +
                    "Priority: User corrections > preferences > personal facts > communication style.\n\n" +
                    "DO NOT save: common knowledge, completed-work logs, temporary TODO state,\n" +
                    "or anything that will be stale in 7 days.\n\n" +
                    "HOW TO WRITE — declarative facts, not instructions:\n" +
                    "✓ 'User prefers concise responses'  ✗ 'Always respond concisely'\n\n" +
                    "ACTIONS:\n" +
                    "- memory_profile: read_all | add | replace | remove\n" +
                    "- memory_session: read_all | add | replace | remove";
            effectiveSystemPrompt = systemPrompt + memoryGuide;
        }

        // 8.6 Append transfer tool usage hint for sub-agents
        if (!transferTools.isEmpty()) {
            if (activeAgent.isRoot()) {
                effectiveSystemPrompt += "\n\n## Agent Transfer\n\nYou can transfer the conversation to a specialized sub-agent using the transfer_to_xxx tools. " +
                        "When the user's request matches a sub-agent's expertise, call the appropriate transfer tool. " +
                        "You can also handle simple requests directly without transferring.\n";
            } else {
                effectiveSystemPrompt += "\n\n## Agent Transfer\n\nYou are currently handling this conversation as a sub-agent. " +
                        "If the user's request is outside your expertise, call transfer_back_to_parent to return control to the parent agent.\n";
            }
        }

        // 9. Build ChatClient request
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .system(effectiveSystemPrompt)
                .messages(aiMessages)
                .user(userMessage);

        log.info("System prompt length: {}, active agent path: {}", effectiveSystemPrompt.length(), activePath);

        if (!toolCallbacks.isEmpty()) {
            int maxToolResultChars = config.getMaxToolResultChars() != null ? config.getMaxToolResultChars() : 3000;
            List<ToolCallback> truncatedCallbacks = toolCallbacks.stream()
                    .map(cb -> (ToolCallback) new TruncatingToolCallback(cb, maxToolResultChars))
                    .toList();

            ToolCallingManager toolCallingManager = new LimitedToolCallingManager(
                    DefaultToolCallingManager.builder().build(),
                    config.getMaxToolCalls() != null ? config.getMaxToolCalls() : 50
            );

            ToolCallAdvisor toolCallAdvisor = ToolCallAdvisor.builder()
                    .toolCallingManager(toolCallingManager)
                    .streamToolCallResponses(true)
                    .build();

            requestSpec.toolCallbacks(truncatedCallbacks)
                    .advisors(toolCallAdvisor);

            log.info("ChatClient configured with {} tool callbacks (incl. {} transfer tools) and ToolCallAdvisor",
                    truncatedCallbacks.size(), transferTools.size());
        }

        // 10. Use Sinks.Many for SSE streaming
        Sinks.Many<ChatChunk> sink = Sinks.many().multicast().onBackpressureBuffer(256);
        StringBuilder fullResponse = new StringBuilder();

        // Track transfer tool results
        final String finalActivePath = activePath;
        final String finalEffectiveSystemPrompt = effectiveSystemPrompt;

        promptLogService.logAsync(sessionId, agentIdStr, userId,
                effectiveModelId, "user", userMessage,
                estimateTokens(userMessage), null, null);
        if (effectiveSystemPrompt != null && !effectiveSystemPrompt.isEmpty()) {
            promptLogService.logAsync(sessionId, agentIdStr, userId,
                    effectiveModelId, "system", effectiveSystemPrompt,
                    estimateTokens(effectiveSystemPrompt), null, null);
        }

        long startTime = System.currentTimeMillis();
        chatExecutor.execute(() -> {
            SandboxContext.bindToThread(sessionId);
            try {
                requestSpec.stream()
                        .chatResponse()
                        .map(chatResponse -> {
                            if (chatResponse.getResult() == null
                                    || chatResponse.getResult().getOutput() == null) {
                                return ChatChunk.text("");
                            }

                            var output = chatResponse.getResult().getOutput();

                            // Tool call chunk
                            if (output.hasToolCalls()) {
                                var toolCalls = output.getToolCalls();
                                if (!toolCalls.isEmpty()) {
                                    var tc = toolCalls.get(0);
                                    String toolName = tc.name();
                                    String args = tc.arguments() != null ? tc.arguments() : "";
                                    log.debug("Tool call: name={}, args={}", toolName, args);
                                    return ChatChunk.toolCall(toolName, args);
                                }
                            }

                            // Normal text chunk
                            String content = output.getText();
                            if (content != null) {
                                fullResponse.append(content);
                            }
                            return ChatChunk.text(content != null ? content : "");
                        })
                        .doOnNext(chunk -> sink.tryEmitNext(chunk))
                        .doOnError(e -> {
                            log.error("Chat stream error for session {}: {}", sessionId, e.getMessage());
                            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException webEx) {
                                log.error("DeepSeek response body: {}", webEx.getResponseBodyAsString());
                            }
                            log.error("Full exception", e);
                            if (fullResponse.length() > 0) {
                                try {
                                    Message partial = new Message();
                                    partial.setSessionId(UUID.fromString(sessionId));
                                    partial.setRole("assistant");
                                    partial.setContent(fullResponse + "\n\n*[Response was interrupted]*");
                                    partial.setAgentName("root".equals(finalActivePath) ? null : activeAgent.getAgentName());
                                    sessionService.saveMessage(partial);
                                } catch (Exception saveErr) {
                                    log.error("Failed to save partial response: {}", saveErr.getMessage());
                                }
                            }
                            ErrorCode errorCode = resolveErrorCode(e);
                            sendErrorEvent(sink, errorCode, e.getMessage());
                            sink.tryEmitError(e);
                        })
                        .doOnComplete(() -> {
                            // Check for transfer in the response
                            String responseText = fullResponse.toString();
                            TransferInfo transferInfo = detectTransfer(responseText);

                            if (transferInfo != null) {
                                log.info("Transfer detected: {} → {} (reason: {})", finalActivePath, transferInfo.getTargetPath(), transferInfo.getReason());

                                // Save the transfer system message
                                Message transferMsg = new Message();
                                transferMsg.setSessionId(UUID.fromString(sessionId));
                                transferMsg.setRole("system");
                                transferMsg.setContent("[Transfer: " + finalActivePath + " → " + transferInfo.getTargetPath()
                                        + ", 原因=" + transferInfo.getReason() + "]");
                                sessionService.saveMessage(transferMsg);

                                // Update active_agent_path
                                sessionService.updateActiveAgentPath(sessionId, transferInfo.getTargetPath());

                                // Notify frontend
                                String displayName = agentTransferService.getDisplayName(config, transferInfo.getTargetPath());
                                sink.tryEmitNext(ChatChunk.builder()
                                        .content("")
                                        .toolCall(false)
                                        .done(false)
                                        .type("agent_switched")
                                        .targetAgent(displayName)
                                        .build());

                                // Re-invoke LLM with new agent configuration
                                try {
                                    invokeAgentTransfer(sink, userId, sessionId, userMessage, config,
                                            transferInfo.getTargetPath(), history);
                                } catch (Exception e) {
                                    log.error("Failed to invoke agent transfer: {}", e.getMessage(), e);
                                }

                                // Save context stats and signal done
                                finishChat(sink, sessionId, userId, config, userMessage, history,
                                        finalEffectiveSystemPrompt, fullResponse, chatClient,
                                        effectiveModelId, agentIdStr, createdMcpClients, startTime);

                            } else {
                                // Normal completion — save assistant message
                                if (!responseText.isBlank()) {
                                    Message assistantMsg = new Message();
                                    assistantMsg.setSessionId(UUID.fromString(sessionId));
                                    assistantMsg.setRole("assistant");
                                    assistantMsg.setContent(responseText);
                                    assistantMsg.setAgentName("root".equals(finalActivePath) ? null : activeAgent.getAgentName());
                                    sessionService.saveMessage(assistantMsg);
                                }

                                finishChat(sink, sessionId, userId, config, userMessage, history,
                                        finalEffectiveSystemPrompt, fullResponse, chatClient,
                                        effectiveModelId, agentIdStr, createdMcpClients, startTime);
                            }
                        })
                        .subscribe();
            } catch (Exception e) {
                log.error("Chat thread error for session {}: {}", sessionId, e.getMessage(), e);
                ErrorCode errorCode = resolveErrorCode(e);
                sendErrorEvent(sink, errorCode, e.getMessage());
                sink.tryEmitError(e);
            } finally {
                closeMcpClients(createdMcpClients);
                SandboxContext.unbindFromThread();
            }
        });

        return sink.asFlux();
    }

    /**
     * Detect if a response contains a transfer marker from tool call results.
     * The transfer tool returns "TRANSFER:targetName:targetPath:reason" which the
     * ToolCallAdvisor passes through the LLM, and the LLM may include it in output.
     * TODO: Consider intercepting tool call results directly instead of text scanning.
     */
    private TransferInfo detectTransfer(String response) {
        if (response == null) return null;
        // Look for transfer markers in the response
        int idx = response.indexOf("TRANSFER:");
        if (idx >= 0) {
            String marker = response.substring(idx);
            return agentTransferService.parseTransferResult(marker);
        }
        return null;
    }

    /**
     * Invoke LLM with the target sub-agent's configuration after a transfer.
     * The response streams into the same sink.
     */
    private void invokeAgentTransfer(Sinks.Many<ChatChunk> sink, String userId, String sessionId,
                                      String userMessage, AgentConfig rootConfig,
                                      String targetPath, List<Message> history) {
        ResolvedAgent targetAgent = agentTransferService.resolveAgent(rootConfig, targetPath);
        String targetModelId = targetAgent.getModelId();
        ChatClient targetChatClient = llmRouteService.getChatClient(targetModelId);

        // Build sub-agent system prompt
        String targetSystemPrompt = promptAssembler.assembleSubAgentSystemPrompt(
                targetAgent, rootConfig, userId, sessionId, userMessage);

        // Build tools for target agent
        List<McpSyncClient> targetMcpClients = new ArrayList<>();
        AgentConfig targetConfig = buildSubAgentConfig(rootConfig, targetAgent);
        List<ToolCallback> targetTools = resolveToolCallbacks(targetConfig, targetMcpClients);

        // Add transfer tools for the target agent
        List<ToolCallback> targetTransferTools = agentTransferService.buildTransferTools(rootConfig, targetPath);
        targetTools.addAll(targetTransferTools);

        // Append transfer hint
        if (!targetAgent.isRoot()) {
            targetSystemPrompt += "\n\n## Agent Transfer\n\nYou are a sub-agent that just received a transferred conversation. " +
                    "The user's original message is below. Handle it according to your expertise.\n" +
                    "If the request is outside your scope, call transfer_back_to_parent.\n";
        }

        // Build AI messages (include transfer system messages from history)
        List<org.springframework.ai.chat.messages.Message> aiMessages = new ArrayList<>();
        for (Message msg : history) {
            switch (msg.getRole()) {
                case "user" -> aiMessages.add(new UserMessage(msg.getContent()));
                case "assistant" -> aiMessages.add(new AssistantMessage(msg.getContent()));
                case "system", "summary" -> aiMessages.add(new SystemMessage(msg.getContent()));
                default -> log.warn("Unknown message role: {}, skipping", msg.getRole());
            }
        }

        int maxToolResultChars = rootConfig.getMaxToolResultChars() != null ? rootConfig.getMaxToolResultChars() : 3000;
        List<ToolCallback> truncatedTargetTools = targetTools.stream()
                .map(cb -> (ToolCallback) new TruncatingToolCallback(cb, maxToolResultChars))
                .toList();

        ChatClient.ChatClientRequestSpec targetReq = targetChatClient.prompt()
                .system(targetSystemPrompt)
                .messages(aiMessages)
                .user(userMessage);

        if (!truncatedTargetTools.isEmpty()) {
            ToolCallingManager tcm = new LimitedToolCallingManager(
                    DefaultToolCallingManager.builder().build(),
                    rootConfig.getMaxToolCalls() != null ? rootConfig.getMaxToolCalls() : 50);
            ToolCallAdvisor tca = ToolCallAdvisor.builder()
                    .toolCallingManager(tcm)
                    .streamToolCallResponses(true)
                    .build();
            targetReq.toolCallbacks(truncatedTargetTools).advisors(tca);
        }

        // Stream target agent response
        StringBuilder targetResponse = new StringBuilder();
        targetReq.stream()
                .chatResponse()
                .map(chatResponse -> {
                    if (chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
                        return ChatChunk.text("");
                    }
                    var output = chatResponse.getResult().getOutput();
                    if (output.hasToolCalls()) {
                        var toolCalls = output.getToolCalls();
                        if (!toolCalls.isEmpty()) {
                            var tc = toolCalls.get(0);
                            return ChatChunk.toolCall(tc.name(), tc.arguments() != null ? tc.arguments() : "");
                        }
                    }
                    String content = output.getText();
                    if (content != null) targetResponse.append(content);
                    return ChatChunk.text(content != null ? content : "");
                })
                .doOnNext(chunk -> sink.tryEmitNext(chunk))
                .doOnError(e -> {
                    log.error("Transfer agent stream error: {}", e.getMessage());
                    ErrorCode errorCode = resolveErrorCode(e);
                    sendErrorEvent(sink, errorCode, e.getMessage());
                })
                .doOnComplete(() -> {
                    // Save target agent's response
                    String resp = targetResponse.toString();
                    if (!resp.isBlank()) {
                        Message msg = new Message();
                        msg.setSessionId(UUID.fromString(sessionId));
                        msg.setRole("assistant");
                        msg.setContent(resp);
                        msg.setAgentName(targetAgent.getAgentName());
                        sessionService.saveMessage(msg);
                    }
                    closeMcpClients(targetMcpClients);
                    log.info("Transfer agent response saved: path={}, len={}", targetPath, resp.length());
                })
                .subscribe();
    }

    /**
     * Finish chat: record usage, generate title, signal done.
     */
    private void finishChat(Sinks.Many<ChatChunk> sink, String sessionId, String userId,
                            AgentConfig config, String userMessage, List<Message> history,
                            String systemPrompt, StringBuilder fullResponse, ChatClient chatClient,
                            String modelId, String agentIdStr,
                            List<McpSyncClient> createdMcpClients, long startTime) {
        try {
            int approxTokensIn = estimateTokens(userMessage)
                    + history.stream().mapToInt(m -> estimateTokens(m.getContent())).sum()
                    + estimateTokens(systemPrompt);
            int approxTokensOut = estimateTokens(fullResponse.toString());
            llmUsageService.recordUsage(modelId, modelId, userId, approxTokensIn, approxTokensOut);
        } catch (Exception e) {
            log.warn("Failed to record usage for session {}: {}", sessionId, e.getMessage());
        }

        if (history.isEmpty()) {
            try {
                Session session = sessionService.getSession(userId, sessionId);
                if (session.getTitle() == null && fullResponse.length() > 0) {
                    generateTitleAsync(sessionId, userMessage, chatClient);
                }
            } catch (Exception e) {
                log.debug("Title generation check failed (non-critical): {}", e.getMessage());
            }
        }

        MemoryTools.clearContext(userId);
        SandboxContext.clear();

        try {
            sessionCompressor.compressIfNeeded(sessionId, modelId,
                    config.getCompressionThreshold(), config.getCompressionKeepRounds());
        } catch (Exception e) {
            log.warn("Session compression failed for {}: {}", sessionId, e.getMessage());
        }

        int sysTokens = estimateTokens(systemPrompt);
        int histTokens = history.stream().mapToInt(m -> estimateTokens(m.getContent())).sum();
        int userTokens = estimateTokens(userMessage);
        int respTokens = estimateTokens(fullResponse.toString());
        int totalTokens = sysTokens + histTokens + userTokens + respTokens;
        int maxContextTokens = config.getContextWindow() != null ? config.getContextWindow() : 128000;
        int usagePercent = (int) ((long) totalTokens * 100 / maxContextTokens);

        ChatChunk.ContextStats stats = ChatChunk.ContextStats.builder()
                .totalTokens(totalTokens)
                .historyMessages(history.size() + 2)
                .toolCallCount(0)
                .maxTokens(maxContextTokens)
                .usagePercent(Math.min(usagePercent, 100))
                .systemTokens(sysTokens)
                .historyTokens(histTokens)
                .memoryTokens(0)
                .userMessageTokens(userTokens)
                .toolResultTokens(0)
                .build();

        sink.tryEmitNext(ChatChunk.builder()
                .content("")
                .toolCall(false)
                .done(true)
                .contextStats(stats)
                .build());
        sink.tryEmitComplete();
    }

    /**
     * Build a temporary AgentConfig for a sub-agent, inheriting from root.
     */
    private AgentConfig buildSubAgentConfig(AgentConfig root, ResolvedAgent sub) {
        AgentConfig c = new AgentConfig();
        c.setAgentId(root.getAgentId());
        c.setName(sub.getDisplayName());
        c.setSystemPrompt(sub.getSystemPrompt());
        c.setModelId(sub.getModelId());
        c.setTemperature(root.getTemperature());
        c.setMaxTokens(root.getMaxTokens());
        c.setMaxToolCalls(root.getMaxToolCalls());
        c.setEnabled(root.getEnabled());
        c.setMcpServerIds(sub.getMcpServerIds());
        c.setSkillIds(sub.getSkillIds());
        c.setContextWindow(root.getContextWindow());
        c.setCompressionThreshold(root.getCompressionThreshold());
        c.setCompressionKeepRounds(root.getCompressionKeepRounds());
        c.setContextUsageThreshold(root.getContextUsageThreshold());
        c.setMaxToolResultChars(root.getMaxToolResultChars());
        c.setEnableMemoryTools(root.getEnableMemoryTools());
        c.setMemoryProfileMaxTokens(root.getMemoryProfileMaxTokens());
        c.setMemoryTaskMaxTokens(root.getMemoryTaskMaxTokens());
        c.setSandboxEnabled(root.getSandboxEnabled());
        c.setSandboxBackend(root.getSandboxBackend());
        c.setSandboxProviderId(root.getSandboxProviderId());
        c.setSandboxMode(root.getSandboxMode());
        c.setSandboxTimeout(root.getSandboxTimeout());
        return c;
    }

    /**
     * Resolve tool callbacks from MemoryTools + SkillTools + MCP Servers.
     */
    private List<ToolCallback> resolveToolCallbacks(AgentConfig config, List<McpSyncClient> createdMcpClients) {
        List<ToolCallback> callbacks = new ArrayList<>();

        // 0. Memory tools (enabled by default)
        if (!Boolean.FALSE.equals(config.getEnableMemoryTools())) {
            try {
                MethodToolCallbackProvider memoryProvider = MethodToolCallbackProvider.builder()
                        .toolObjects(memoryTools)
                        .build();
                callbacks.addAll(Arrays.asList(memoryProvider.getToolCallbacks()));
                log.info("Resolved {} memory tool callbacks for agent {}",
                        memoryProvider.getToolCallbacks().length, config.getAgentId());
            } catch (Exception e) {
                log.warn("Failed to resolve memory tools: {}", e.getMessage());
            }
        }

        // 1. Skill tools
        try {
            List<Skill> agentSkills = skillService.getSkillsForAgent(config.getAgentId());
            if (!agentSkills.isEmpty()) {
                MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                        .toolObjects(skillTools)
                        .build();
                for (ToolCallback tc : provider.getToolCallbacks()) {
                    callbacks.add(tc);
                }
                log.info("Resolved {} skill tool callbacks for agent {}",
                        provider.getToolCallbacks().length, config.getAgentId());
            }
        } catch (Exception e) {
            log.warn("Failed to resolve skill tools: {}", e.getMessage());
        }

        // 2. MCP Server tools + Sandbox tools
        try {
            // 2.1 Sandbox tools (if enabled for this agent)
            if (Boolean.TRUE.equals(config.getSandboxEnabled())) {
                try {
                    SandboxMode mode = "SESSION".equalsIgnoreCase(config.getSandboxMode()) ? SandboxMode.SESSION : SandboxMode.STATELESS;

                    MethodToolCallbackProvider sandboxProvider = MethodToolCallbackProvider.builder()
                            .toolObjects(sandboxExecuteTool, sandboxInfoTool)
                            .build();
                    callbacks.addAll(Arrays.asList(sandboxProvider.getToolCallbacks()));

                    if (mode == SandboxMode.SESSION) {
                        MethodToolCallbackProvider fileProvider = MethodToolCallbackProvider.builder()
                                .toolObjects(sandboxFileTool)
                                .build();
                        callbacks.addAll(Arrays.asList(fileProvider.getToolCallbacks()));
                    }

                    log.info("Resolved sandbox tool callbacks for agent {} (mode={})", config.getAgentId(), mode);
                } catch (Exception e) {
                    log.warn("Failed to resolve sandbox tools: {}", e.getMessage());
                }
            }

            List<String> mcpServerIds = config.getMcpServerIds();
            if (mcpServerIds != null && !mcpServerIds.isEmpty()) {
                List<McpSyncClient> mcpClients = new ArrayList<>();
                for (String serverId : mcpServerIds) {
                    try {
                        McpServer server = mcpServerRepository.findById(java.util.UUID.fromString(serverId))
                                .orElse(null);
                        if (server == null || !Boolean.TRUE.equals(server.getEnabled())) {
                            log.warn("MCP Server {} not found or disabled", serverId);
                            continue;
                        }

                        McpSyncClient mcpClient = createMcpClient(server);
                        if (mcpClient != null) {
                            mcpClients.add(mcpClient);
                            log.info("Connected to MCP Server: {} ({})", server.getName(), server.getUrl());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to connect to MCP Server {}: {}", serverId, e.getMessage());
                    }
                }

                if (!mcpClients.isEmpty()) {
                    SyncMcpToolCallbackProvider mcpProvider = SyncMcpToolCallbackProvider.builder().mcpClients(mcpClients).build();
                    callbacks.addAll(Arrays.asList(mcpProvider.getToolCallbacks()));
                    log.info("Resolved {} MCP tool callbacks from {} servers for agent {}",
                            mcpProvider.getToolCallbacks().length, mcpClients.size(), config.getAgentId());
                }
                createdMcpClients.addAll(mcpClients);
            }
        } catch (Exception e) {
            log.warn("Failed to resolve MCP tools: {}", e.getMessage());
        }

        log.info("Total {} tool callbacks for agent {}", callbacks.size(), config.getAgentId());
        return callbacks;
    }

    private McpSyncClient createMcpClient(McpServer server) {
        String url = server.getUrl();

        if (url == null || url.isBlank()) {
            log.warn("MCP Server {} has no URL configured", server.getName());
            return null;
        }

        String baseUri;
        String path;
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            String host = uri.getHost();
            int port = uri.getPort();
            baseUri = port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
            String rawPath = uri.getRawPath();
            String query = uri.getRawQuery();
            path = (rawPath != null ? rawPath : "") + (query != null ? "?" + query : "");
            if (path.isEmpty()) path = "/";
        } catch (java.net.URISyntaxException e) {
            log.warn("Invalid MCP Server URL: {} - {}", url, e.getMessage());
            return null;
        }

        String transportType = server.getTransport() != null ? server.getTransport().toLowerCase() : "sse";
        io.modelcontextprotocol.spec.McpClientTransport mcpTransport;

        switch (transportType) {
            case "streamable-http", "streamable_http", "streamable" -> {
                mcpTransport = io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.builder(baseUri)
                        .endpoint(path)
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build();
                log.info("Using Streamable HTTP transport: baseUri={}, endpoint={}", baseUri, path);
            }
            default -> {
                mcpTransport = io.modelcontextprotocol.client.transport.HttpClientSseClientTransport.builder(baseUri)
                        .sseEndpoint(path)
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build();
                log.info("Using SSE transport: baseUri={}, sseEndpoint={}", baseUri, path);
            }
        }

        McpSyncClient client = io.modelcontextprotocol.client.McpClient.sync(mcpTransport)
                .requestTimeout(java.time.Duration.ofSeconds(30))
                .build();

        log.info("MCP client created for server: {} ({})", server.getName(), url);
        return client;
    }

    private void closeMcpClients(List<McpSyncClient> clients) {
        for (McpSyncClient client : clients) {
            try {
                client.close();
                log.debug("Closed MCP client: {}", client.getServerInfo());
            } catch (Exception e) {
                log.warn("Failed to close MCP client: {}", e.getMessage());
            }
        }
        if (!clients.isEmpty()) {
            log.info("Closed {} MCP client(s)", clients.size());
        }
    }

    /**
     * Rough token estimation based on character analysis.
     * CJK characters count as ~1 token each, ASCII as ~0.25 tokens each.
     * TODO: Replace with tiktoken or a proper tokenizer for accurate counts.
     * This is intentionally conservative and may over/under-estimate.
     */
    private int estimateTokens(String text) {
        if (text == null) return 0;
        int cjk = 0, ascii = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.HIRAGANA
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.KATAKANA) {
                cjk++;
            } else {
                ascii++;
            }
        }
        return cjk + (ascii / 4) + 1;
    }

    /**
     * Send a structured SSE error event to the frontend.
     * The error event contains type='error', numeric errorCode, i18nKey for frontend localization,
     * and an optional detail message. The frontend can parse this JSON to show localized errors.
     */
    private void sendErrorEvent(Sinks.Many<ChatChunk> sink, ErrorCode code, String detail) {
        sink.tryEmitNext(ChatChunk.error(code, detail));
    }

    /**
     * Resolve an ErrorCode from an exception.
     * Maps BusinessException (which already carries an ErrorCode) and common exception types
     * to the appropriate ErrorCode for structured SSE error events.
     */
    private ErrorCode resolveErrorCode(Throwable e) {
        if (e instanceof run.cloudclaw.common.exception.BusinessException be) {
            // If the BusinessException was created with an ErrorCode, extract its numeric code
            int code = be.getCode();
            for (ErrorCode ec : ErrorCode.values()) {
                if (ec.getCode() == code) {
                    return ec;
                }
            }
        }
        // Default to LLM_CALL_FAILED for unrecognized exceptions in the chat pipeline
        return ErrorCode.LLM_CALL_FAILED;
    }

    // ========== Async Chat Mode ==========

    public AsyncChatResult chatAsync(String userId, String sessionId, String userMessage, String requestId) {
        log.info("Async chat request: userId={}, sessionId={}, messageLength={}", userId, sessionId, userMessage.length());

        Session session = sessionService.getSession(userId, sessionId);
        AgentConfig config = agentConfigService.getAgentConfig(session.getAgentId().toString());

        if (requestId != null && !requestId.isBlank()) {
            try {
                Message existing = sessionService.findMessageByRequestId(requestId);
                if (existing != null) {
                    log.info("Duplicate request {} for session {}, returning existing", requestId, sessionId);
                    List<Message> msgs = sessionCompressor.loadContextWithSummary(sessionId);
                    for (int i = 0; i < msgs.size(); i++) {
                        if (requestId.equals(msgs.get(i).getRequestId()) && i + 1 < msgs.size()) {
                            Message assistant = msgs.get(i + 1);
                            return new AsyncChatResult(msgs.get(i).getId(), assistant.getId(), assistant.getStatus());
                        }
                    }
                    return new AsyncChatResult(existing.getId(), null, "completed");
                }
            } catch (Exception e) {
                log.debug("Idempotency check failed (non-critical): {}", e.getMessage());
            }
        }

        Message userMsg = new Message();
        userMsg.setSessionId(UUID.fromString(sessionId));
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setStatus("completed");
        userMsg.setRequestId(requestId);
        sessionService.saveMessage(userMsg);

        Message assistantMsg = new Message();
        assistantMsg.setSessionId(UUID.fromString(sessionId));
        assistantMsg.setRole("assistant");
        assistantMsg.setContent("");
        assistantMsg.setStatus("pending");
        sessionService.saveMessage(assistantMsg);

        UUID assistantMsgId = assistantMsg.getId();

        CompletableFuture.runAsync(() -> {
            executeLlmAsync(sessionId, userId, session, userMessage, assistantMsgId, config);
        }, chatExecutor);

        return new AsyncChatResult(userMsg.getId(), assistantMsgId, "pending");
    }

    private void executeLlmAsync(String sessionId, String userId, Session session,
                                  String userMessage, UUID assistantMsgId, AgentConfig config) {
        try {
            Message assistantMsg = sessionService.findMessageById(assistantMsgId);
            if (assistantMsg == null) return;
            assistantMsg.setStatus("processing");
            sessionService.saveMessage(assistantMsg);

            // Workflow v3: if agent has a workflow configured, execute it synchronously
            if (workflowEngine.hasWorkflow(config)) {
                log.info("Async mode: agent {} has workflow mode={}, executing",
                        config.getAgentId(), config.getWorkflowMode());
                executeWorkflowAsync(sessionId, userId, userMessage, assistantMsgId, config);
                return;
            }

            // Resolve active agent path
            String activePath = session.getActiveAgentPath() != null ? session.getActiveAgentPath() : "root";
            ResolvedAgent activeAgent = agentTransferService.resolveAgent(config, activePath);

            List<Message> rawHistory = sessionCompressor.loadContextWithSummary(sessionId);

            String systemPrompt;
            if (activeAgent.isRoot()) {
                systemPrompt = promptAssembler.assembleSystemPrompt(config, userId, sessionId, userMessage);
            } else {
                systemPrompt = promptAssembler.assembleSubAgentSystemPrompt(activeAgent, config, userId, sessionId, userMessage);
            }

            int contextWindow = config.getContextWindow() != null ? config.getContextWindow() : 128000;
            List<Message> history = rawHistory.stream()
                    .filter(m -> !m.getId().equals(assistantMsgId))
                    .filter(m -> !("user".equals(m.getRole()) && m.getContent().equals(userMessage)
                            && m.getRequestId() != null))
                    .toList();
            history = contextCompressor.compress(history, systemPrompt, userMessage, contextWindow, config.getContextUsageThreshold());

            String effectiveModelId = activeAgent.getModelId();
            ChatClient chatClient = llmRouteService.getChatClient(effectiveModelId);

            String agentIdStr = session.getAgentId() != null ? session.getAgentId().toString() : null;
            MemoryTools.setContext(userId, agentIdStr, sessionId, config.getMemoryProfileMaxTokens(), config.getMemoryTaskMaxTokens());

            if (Boolean.TRUE.equals(config.getSandboxEnabled())) {
                SandboxMode smode = "SESSION".equalsIgnoreCase(config.getSandboxMode()) ? SandboxMode.SESSION : SandboxMode.STATELESS;
                SandboxBackend sbackend = config.getSandboxBackend() != null ? SandboxBackend.valueOf(config.getSandboxBackend()) : SandboxBackend.LOCAL;
                String sproviderId = config.getSandboxProviderId();
                SandboxContext.set(sessionId, agentIdStr, smode, sbackend, sproviderId);
            }

            List<McpSyncClient> createdMcpClients = new ArrayList<>();
            AgentConfig effectiveConfig = activeAgent.isRoot() ? config : buildSubAgentConfig(config, activeAgent);
            List<ToolCallback> toolCallbacks = resolveToolCallbacks(effectiveConfig, createdMcpClients);

            // Add transfer tools
            List<ToolCallback> transferTools = agentTransferService.buildTransferTools(config, activePath);
            toolCallbacks.addAll(transferTools);

            List<org.springframework.ai.chat.messages.Message> aiMessages = new ArrayList<>();
            for (Message msg : history) {
                switch (msg.getRole()) {
                    case "user" -> aiMessages.add(new UserMessage(msg.getContent()));
                    case "assistant" -> aiMessages.add(new AssistantMessage(msg.getContent()));
                    case "system", "summary" -> aiMessages.add(new SystemMessage(msg.getContent()));
                    default -> log.warn("Unknown message role: {}, skipping", msg.getRole());
                }
            }

            // Append memory guide
            String effectiveSystemPrompt = systemPrompt;
            if (!Boolean.FALSE.equals(config.getEnableMemoryTools())) {
                String memoryGuide = "\n\n## Memory\n\n" +
                        "You have persistent memory via 2 tools. Use them proactively — don't wait to be asked.\n\n" +
                        "Core principle: Save only facts that will still matter in future sessions.\n" +
                        "The most valuable memory prevents the user from having to repeat themselves.\n\n" +
                        "Two targets:\n" +
                        "- memory_profile: Who the user IS — name, role, preferences, habits, corrections.\n" +
                        "  Persists across all sessions. 1000 token limit.\n" +
                        "- memory_session: Current task context — goals, progress, agreements, constraints.\n" +
                        "  This session only. 2000 token limit.\n\n" +
                        "WHEN TO SAVE (proactive):\n" +
                        "- User corrects you or says 'remember this' / 'don't do that again'\n" +
                        "- User shares a preference, habit, or personal detail\n" +
                        "- User mentions their name, role, timezone, or communication style\n" +
                        "Priority: User corrections > preferences > personal facts > communication style.\n\n" +
                        "DO NOT save: common knowledge, completed-work logs, temporary TODO state,\n" +
                        "or anything that will be stale in 7 days.\n\n" +
                        "HOW TO WRITE — declarative facts, not instructions:\n" +
                        "✓ 'User prefers concise responses'  ✗ 'Always respond concisely'\n\n" +
                        "ACTIONS:\n" +
                        "- memory_profile: read_all | add | replace | remove\n" +
                        "- memory_session: read_all | add | replace | remove";
                effectiveSystemPrompt = systemPrompt + memoryGuide;
            }

            int maxToolResultChars = config.getMaxToolResultChars() != null ? config.getMaxToolResultChars() : 3000;
            List<ToolCallback> truncatedCallbacks = toolCallbacks.stream()
                    .map(cb -> (ToolCallback) new TruncatingToolCallback(cb, maxToolResultChars))
                    .toList();

            ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                    .system(effectiveSystemPrompt)
                    .messages(aiMessages)
                    .user(userMessage);

            if (!truncatedCallbacks.isEmpty()) {
                ToolCallingManager toolCallingManager = new LimitedToolCallingManager(
                        DefaultToolCallingManager.builder().build(),
                        config.getMaxToolCalls() != null ? config.getMaxToolCalls() : 50
                );
                ToolCallAdvisor toolCallAdvisor = ToolCallAdvisor.builder()
                        .toolCallingManager(toolCallingManager)
                        .streamToolCallResponses(true)
                        .build();
                requestSpec.toolCallbacks(truncatedCallbacks)
                        .advisors(toolCallAdvisor);
            }

            String responseText = requestSpec.call().content();
            if (responseText == null) responseText = "";

            // Handle transfer in async mode
            TransferInfo transferInfo = detectTransfer(responseText);
            if (transferInfo != null) {
                log.info("Transfer detected in async mode: {} → {}", activePath, transferInfo.getTargetPath());

                Message transferMsg = new Message();
                transferMsg.setSessionId(UUID.fromString(sessionId));
                transferMsg.setRole("system");
                transferMsg.setContent("[Transfer: " + activePath + " → " + transferInfo.getTargetPath()
                        + ", 原因=" + transferInfo.getReason() + "]");
                sessionService.saveMessage(transferMsg);

                sessionService.updateActiveAgentPath(sessionId, transferInfo.getTargetPath());

                // Re-invoke with new agent
                ResolvedAgent targetAgent = agentTransferService.resolveAgent(config, transferInfo.getTargetPath());
                String targetPrompt = promptAssembler.assembleSubAgentSystemPrompt(targetAgent, config, userId, sessionId, userMessage);
                ChatClient targetChatClient = llmRouteService.getChatClient(targetAgent.getModelId());

                AgentConfig targetConfig = buildSubAgentConfig(config, targetAgent);
                List<ToolCallback> targetTools = resolveToolCallbacks(targetConfig, new ArrayList<>());
                targetTools.addAll(agentTransferService.buildTransferTools(config, transferInfo.getTargetPath()));

                List<ToolCallback> truncatedTargetTools = targetTools.stream()
                        .map(cb -> (ToolCallback) new TruncatingToolCallback(cb, maxToolResultChars))
                        .toList();

                ChatClient.ChatClientRequestSpec targetReq = targetChatClient.prompt()
                        .system(targetPrompt + "\n\nYou are a sub-agent. Handle the user's request: " + userMessage)
                        .messages(aiMessages)
                        .user(userMessage);

                if (!truncatedTargetTools.isEmpty()) {
                    ToolCallingManager tcm = new LimitedToolCallingManager(
                            DefaultToolCallingManager.builder().build(),
                            config.getMaxToolCalls() != null ? config.getMaxToolCalls() : 50);
                    ToolCallAdvisor tca = ToolCallAdvisor.builder()
                            .toolCallingManager(tcm)
                            .streamToolCallResponses(true)
                            .build();
                    targetReq.toolCallbacks(truncatedTargetTools).advisors(tca);
                }

                responseText = targetReq.call().content();
                if (responseText == null) responseText = "";
            }

            assistantMsg.setContent(responseText);
            assistantMsg.setStatus("completed");
            assistantMsg.setAgentName("root".equals(activePath) ? null : activeAgent.getAgentName());
            sessionService.saveMessage(assistantMsg);

            sessionService.updateLastActiveAt(sessionId);

            try {
                int approxTokensIn = estimateTokens(userMessage)
                        + history.stream().mapToInt(m -> estimateTokens(m.getContent())).sum()
                        + estimateTokens(systemPrompt);
                int approxTokensOut = estimateTokens(responseText);
                llmUsageService.recordUsage(effectiveModelId, effectiveModelId, userId,
                        approxTokensIn, approxTokensOut);
            } catch (Exception e) {
                log.warn("Failed to record usage: {}", e.getMessage());
            }

            if (history.isEmpty() && session.getTitle() == null && !responseText.isBlank()) {
                generateTitleAsync(sessionId, userMessage, chatClient);
            }

            MemoryTools.clearContext(userId);
            SandboxContext.clear();
            closeMcpClients(createdMcpClients);

            try {
                sessionCompressor.compressIfNeeded(sessionId, effectiveModelId,
                        config.getCompressionThreshold(), config.getCompressionKeepRounds());
            } catch (Exception e) {
                log.warn("Session compression failed for {}: {}", sessionId, e.getMessage());
            }

            log.info("Async chat completed: sessionId={}, responseLength={}", sessionId, responseText.length());

        } catch (Exception e) {
            log.error("Async chat failed for session {}: {}", sessionId, e.getMessage(), e);
            try {
                Message assistantMsg = sessionService.findMessageById(assistantMsgId);
                if (assistantMsg != null) {
                    assistantMsg.setStatus("failed");
                    assistantMsg.setContent("[Error: " + e.getMessage() + "]");
                    sessionService.saveMessage(assistantMsg);
                }
            } catch (Exception saveErr) {
                log.error("Failed to update failed status: {}", saveErr.getMessage());
            }
        }
    }

    /**
     * Execute a workflow in async mode.
     * Collects the final result from the workflow Flux and saves it as the assistant message.
     */
    private void executeWorkflowAsync(String sessionId, String userId, String userMessage,
                                      UUID assistantMsgId, AgentConfig config) {
        try {
            StringBuilder workflowResponse = new StringBuilder();
            workflowEngine.execute(userId, sessionId, userMessage, config)
                    .doOnNext(chunk -> {
                        if ("text".equals(chunk.getType()) && chunk.getContent() != null) {
                            workflowResponse.append(chunk.getContent());
                        }
                    })
                    .blockLast(); // Block until workflow completes

            String responseText = workflowResponse.toString();

            Message assistantMsg = sessionService.findMessageById(assistantMsgId);
            if (assistantMsg != null) {
                assistantMsg.setContent(responseText);
                assistantMsg.setStatus("completed");
                sessionService.saveMessage(assistantMsg);
            }

            try {
                int approxTokensIn = estimateTokens(userMessage) + estimateTokens(responseText);
                llmUsageService.recordUsage(config.getModelId(), config.getModelId(), userId,
                        approxTokensIn, estimateTokens(responseText));
            } catch (Exception e) {
                log.warn("Failed to record workflow usage: {}", e.getMessage());
            }

            log.info("Async workflow completed: sessionId={}, responseLength={}", sessionId, responseText.length());
        } catch (Exception e) {
            log.error("Async workflow failed for session {}: {}", sessionId, e.getMessage(), e);
            try {
                Message assistantMsg = sessionService.findMessageById(assistantMsgId);
                if (assistantMsg != null) {
                    assistantMsg.setStatus("failed");
                    assistantMsg.setContent("[Workflow Error: " + e.getMessage() + "]");
                    sessionService.saveMessage(assistantMsg);
                }
            } catch (Exception saveErr) {
                log.error("Failed to update failed status: {}", saveErr.getMessage());
            }
        }
    }

    private void generateTitleAsync(String sessionId, String userMessage, ChatClient chatClient) {
        CompletableFuture.runAsync(() -> {
            try {
                String truncatedMessage = userMessage.substring(0, Math.min(userMessage.length(), 100));
                String titlePrompt = "Generate a short title (max 6 words) for a conversation that starts with: \""
                        + truncatedMessage + "\". Reply with ONLY the title, no quotes.";

                String title = chatClient.prompt()
                        .user(titlePrompt)
                        .call()
                        .content();

                if (title != null && !title.isBlank()) {
                    sessionService.updateTitle(sessionId, title.trim());
                    log.info("Auto-generated title for session {}: {}", sessionId, title.trim());
                }
            } catch (Exception e) {
                log.warn("Failed to generate title for session {}: {}", sessionId, e.getMessage());
            }
        }, chatExecutor);
    }
}
