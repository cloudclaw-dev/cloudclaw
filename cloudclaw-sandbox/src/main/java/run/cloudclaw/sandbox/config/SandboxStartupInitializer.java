package run.cloudclaw.sandbox.config;

import run.cloudclaw.sandbox.core.SandboxManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup, mark any ACTIVE sandbox sessions as ORPHANED
 * (they belong to previous process instances that are now gone).
 */
@Component
public class SandboxStartupInitializer {

    private static final Logger log = LoggerFactory.getLogger(SandboxStartupInitializer.class);

    private final SandboxManager sandboxManager;

    public SandboxStartupInitializer(SandboxManager sandboxManager) {
        this.sandboxManager = sandboxManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Checking for orphaned sandbox sessions...");
        sandboxManager.markOrphanedOnStartup();
    }
}
