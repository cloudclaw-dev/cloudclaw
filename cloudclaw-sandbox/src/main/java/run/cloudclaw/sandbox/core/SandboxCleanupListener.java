package run.cloudclaw.sandbox.core;

import run.cloudclaw.common.event.SessionDeleteEvent;
import run.cloudclaw.sandbox.model.SandboxSession;
import run.cloudclaw.sandbox.model.SandboxSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Listens for session deletion events and cleans up associated sandbox resources.
 */
@Component
public class SandboxCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(SandboxCleanupListener.class);

    private final SandboxManager sandboxManager;
    private final SandboxSessionRepository sandboxSessionRepository;

    public SandboxCleanupListener(SandboxManager sandboxManager, SandboxSessionRepository sandboxSessionRepository) {
        this.sandboxManager = sandboxManager;
        this.sandboxSessionRepository = sandboxSessionRepository;
    }

    @EventListener
    @Transactional
    public void onSessionDelete(SessionDeleteEvent event) {
        String sessionId = event.sessionId();
        log.info("Cleaning up sandbox sessions for deleted session: {}", sessionId);

        // Close live sandbox instance (if in memory)
        sandboxManager.close(sessionId);

        // Clean up DB records
        List<SandboxSession> sandboxes = sandboxSessionRepository.findBySessionId(UUID.fromString(sessionId));
        if (!sandboxes.isEmpty()) {
            sandboxSessionRepository.deleteAll(sandboxes);
            log.info("Deleted {} sandbox session records for session {}", sandboxes.size(), sessionId);
        }
    }
}
