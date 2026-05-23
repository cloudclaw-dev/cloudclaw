package run.cloudclaw.sandbox.core;

import run.cloudclaw.sandbox.config.SandboxProperties;
import run.cloudclaw.sandbox.model.SandboxProvider;
import run.cloudclaw.sandbox.model.SandboxProviderRepository;
import run.cloudclaw.sandbox.model.SandboxSession;
import run.cloudclaw.sandbox.model.SandboxSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.sandbox.ExecResult;
import org.springaicommunity.sandbox.ExecSpec;
import org.springaicommunity.sandbox.Sandbox;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages session-mode sandbox instances.
 * Keeps live Sandbox instances in memory, tracks metadata in database.
 */
@Component
public class SandboxManager {

    private static final Logger log = LoggerFactory.getLogger(SandboxManager.class);

    private final SandboxSessionRepository repository;
    private final SandboxProviderRepository providerRepository;
    private final SandboxProperties properties;

    /** Live sandbox instances keyed by chat sessionId */
    private final Map<String, Sandbox> liveSandboxes = new ConcurrentHashMap<>();

    public SandboxManager(SandboxSessionRepository repository,
                          SandboxProviderRepository providerRepository,
                          SandboxProperties properties) {
        this.repository = repository;
        this.providerRepository = providerRepository;
        this.properties = properties;
    }

    /**
     * Resolve SandboxProvider from providerId string.
     */
    private SandboxProvider resolveProvider(String providerId) {
        if (providerId == null) return null;
        try {
            return providerRepository.findById(UUID.fromString(providerId)).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to resolve provider {}: {}", providerId, e.getMessage());
            return null;
        }
    }

    /**
     * Get or create a sandbox for the given chat session.
     */
    public Sandbox getOrCreate(String sessionId, String agentId, String backendStr, String providerId) {
        return liveSandboxes.computeIfAbsent(sessionId, sid -> {
            log.info("Creating new session sandbox for session={}, backend={}", sid, backendStr);
            try {
                SandboxProvider provider = resolveProvider(providerId);
                Sandbox sandbox = provider != null
                        ? SandboxFactory.create(provider, "cloudclaw-se-")
                        : SandboxFactory.create(backendStr, "cloudclaw-se-");

                // Persist metadata
                SandboxSession meta = new SandboxSession();
                meta.setSessionId(UUID.fromString(sid));
                meta.setAgentId(UUID.fromString(agentId));
                meta.setBackend(backendStr);
                meta.setStatus("ACTIVE");
                meta.setWorkDir(sandbox.workDir().toString());
                if (providerId != null) {
                    meta.setProviderId(providerId);
                }
                repository.save(meta);

                log.info("Session sandbox created: id={}, workDir={}", meta.getId(), sandbox.workDir());
                return sandbox;
            } catch (Exception e) {
                log.error("Failed to create session sandbox for {}: {}", sid, e.getMessage(), e);
                throw new RuntimeException("Failed to create sandbox: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Execute code in a session-mode sandbox.
     */
    public String execute(String sessionId, String agentId, String backendStr,
                          String providerId, String language, String code, Duration timeout) {
        String filename = StatelessExecutor.languageToFilename(language);
        List<String> command = StatelessExecutor.buildCommand(language, filename);

        Sandbox sandbox = getOrCreate(sessionId, agentId, backendStr, providerId);
        try {
            sandbox.files().create(filename, code);

            ExecSpec spec = ExecSpec.builder()
                    .command(command)
                    .timeout(timeout)
                    .build();

            ExecResult result = sandbox.exec(spec);
            return StatelessExecutor.formatResult(result);
        } catch (Exception e) {
            log.error("Session execution failed for {}: {}", sessionId, e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Read a file from the session sandbox.
     */
    public String readFile(String sessionId, String agentId, String backendStr,
                           String providerId, String path) {
        Sandbox sandbox = getOrCreate(sessionId, agentId, backendStr, providerId);
        try {
            if (!sandbox.files().exists(path)) {
                return "Error: File not found: " + path;
            }
            return sandbox.files().read(path);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Write a file to the session sandbox.
     */
    public String writeFile(String sessionId, String agentId, String backendStr,
                            String providerId, String path, String content) {
        Sandbox sandbox = getOrCreate(sessionId, agentId, backendStr, providerId);
        try {
            sandbox.files().create(path, content);
            return "File written: " + path;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * List files in the session sandbox.
     */
    public String listFiles(String sessionId, String agentId, String backendStr,
                            String providerId, String dir) {
        Sandbox sandbox = getOrCreate(sessionId, agentId, backendStr, providerId);
        try {
            var entries = sandbox.files().list(dir != null ? dir : ".");
            if (entries.isEmpty()) return "No files found.";
            StringBuilder sb = new StringBuilder();
            for (var entry : entries) {
                sb.append(entry.type().name().toLowerCase())
                  .append(" ").append(entry.path())
                  .append(" (").append(entry.size()).append(" bytes)\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Get sandbox info for the session.
     */
    public String getInfo(String sessionId) {
        Sandbox sandbox = liveSandboxes.get(sessionId);
        if (sandbox == null) return "No active sandbox for this session.";

        var meta = repository.findActiveBySessionId(UUID.fromString(sessionId));
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ACTIVE\n");
        sb.append("WorkDir: ").append(sandbox.workDir()).append("\n");
        if (!meta.isEmpty()) {
            sb.append("Backend: ").append(meta.get(0).getBackend()).append("\n");
            if (meta.get(0).getProviderId() != null) {
                sb.append("Provider: ").append(meta.get(0).getProviderId()).append("\n");
            }
            sb.append("DB record: ").append(meta.get(0).getId()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Close and clean up a session sandbox.
     */
    public void close(String sessionId) {
        Sandbox sandbox = liveSandboxes.remove(sessionId);
        if (sandbox != null) {
            try {
                sandbox.close();
                log.info("Closed session sandbox for {}", sessionId);
            } catch (Exception e) {
                log.warn("Failed to close sandbox for {}: {}", sessionId, e.getMessage());
            }
        }

        var records = repository.findActiveBySessionId(UUID.fromString(sessionId));
        for (SandboxSession record : records) {
            record.setStatus("CLOSED");
            repository.save(record);
        }
    }

    /**
     * Mark all ACTIVE sandbox sessions as ORPHANED on startup.
     */
    public void markOrphanedOnStartup() {
        List<SandboxSession> active = repository.findByStatus("ACTIVE");
        if (!active.isEmpty()) {
            for (SandboxSession s : active) {
                s.setStatus("ORPHANED");
                repository.save(s);
            }
            log.warn("Marked {} orphaned sandbox sessions on startup", active.size());
        }
    }
}
