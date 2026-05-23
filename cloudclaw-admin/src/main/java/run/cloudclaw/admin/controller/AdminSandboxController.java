package run.cloudclaw.admin.controller;

import run.cloudclaw.common.dto.Result;
import run.cloudclaw.sandbox.model.SandboxProvider;
import run.cloudclaw.sandbox.model.SandboxProviderRepository;
import run.cloudclaw.sandbox.model.SandboxSession;
import run.cloudclaw.sandbox.model.SandboxSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/sandboxes")
@RequiredArgsConstructor
public class AdminSandboxController {

    private final SandboxSessionRepository sessionRepository;
    private final SandboxProviderRepository providerRepository;

    // ========== Provider CRUD ==========

    @GetMapping("/providers")
    public Result<List<SandboxProvider>> listProviders() {
        return Result.ok(providerRepository.findAll());
    }

    @PostMapping("/providers")
    public Result<SandboxProvider> createProvider(@RequestBody SandboxProvider provider) {
        log.info("Creating sandbox provider: name={}, type={}", provider.getName(), provider.getType());
        provider.setId(null);
        provider.setCreatedAt(Instant.now());
        provider.setUpdatedAt(Instant.now());
        SandboxProvider saved = providerRepository.save(provider);
        return Result.ok(saved);
    }

    @PutMapping("/providers/{id}")
    public Result<SandboxProvider> updateProvider(@PathVariable String id, @RequestBody SandboxProvider provider) {
        log.info("Updating sandbox provider: id={}", id);
        UUID providerId = UUID.fromString(id);
        SandboxProvider existing = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found: " + id));

        if (provider.getName() != null) existing.setName(provider.getName());
        if (provider.getType() != null) existing.setType(provider.getType());
        if (provider.getEnabled() != null) existing.setEnabled(provider.getEnabled());
        if (provider.getDockerImages() != null) existing.setDockerImages(provider.getDockerImages());
        if (provider.getDockerMemory() != null) existing.setDockerMemory(provider.getDockerMemory());
        if (provider.getDockerCpus() != null) existing.setDockerCpus(provider.getDockerCpus());
        if (provider.getDockerNetworkEnabled() != null) existing.setDockerNetworkEnabled(provider.getDockerNetworkEnabled());
        if (provider.getE2bApiKey() != null) existing.setE2bApiKey(provider.getE2bApiKey());
        if (provider.getE2bTemplateId() != null) existing.setE2bTemplateId(provider.getE2bTemplateId());
        if (provider.getE2bApiUrl() != null) existing.setE2bApiUrl(provider.getE2bApiUrl());
        if (provider.getLocalWorkDirBase() != null) existing.setLocalWorkDirBase(provider.getLocalWorkDirBase());
        if (provider.getDefaultTimeout() != null) existing.setDefaultTimeout(provider.getDefaultTimeout());
        if (provider.getMaxTimeout() != null) existing.setMaxTimeout(provider.getMaxTimeout());
        existing.setUpdatedAt(Instant.now());

        SandboxProvider saved = providerRepository.save(existing);
        return Result.ok(saved);
    }

    @DeleteMapping("/providers/{id}")
    public Result<Void> deleteProvider(@PathVariable String id) {
        log.info("Deleting sandbox provider: id={}", id);
        providerRepository.deleteById(UUID.fromString(id));
        return Result.ok();
    }

    // ========== Session Management ==========

    @GetMapping("/sessions")
    public Result<List<SandboxSession>> listSessions(@RequestParam(required = false) String status) {
        List<SandboxSession> sessions;
        if (status != null && !status.isEmpty()) {
            sessions = sessionRepository.findByStatus(status);
        } else {
            sessions = sessionRepository.findAll();
        }
        return Result.ok(sessions);
    }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> forceCloseSession(@PathVariable String id) {
        // Try by primary key
        sessionRepository.findById(UUID.fromString(id)).ifPresent(record -> {
            record.setStatus("CLOSED");
            sessionRepository.save(record);
        });
        log.info("Force closed sandbox session: {}", id);
        return Result.ok();
    }

    @DeleteMapping("/sessions/orphans")
    public Result<Integer> cleanOrphans() {
        List<SandboxSession> orphans = sessionRepository.findByStatus("ORPHANED");
        sessionRepository.deleteAll(orphans);
        log.info("Cleaned {} orphaned sandbox sessions", orphans.size());
        return Result.ok(orphans.size());
    }
}
