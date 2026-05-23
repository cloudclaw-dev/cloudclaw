package run.cloudclaw.llm.config;

import run.cloudclaw.llm.service.LlmProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;

@Slf4j
@AutoConfiguration
@ComponentScan(basePackages = "run.cloudclaw.llm")
@RequiredArgsConstructor
public class LlmAutoConfiguration {

    private final LlmProviderService providerService;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("Initializing LLM module");
        providerService.initPresets();
    }
}
