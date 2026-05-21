package run.cloudclaw.llm.controller;

import run.cloudclaw.common.config.ConfigChangeEvent;
import run.cloudclaw.common.config.ConfigChangeNotifier;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.llm.model.LlmCredential;
import run.cloudclaw.llm.service.LlmCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/llm/credentials")
@RequiredArgsConstructor
public class AdminLlmCredentialController {

    private final LlmCredentialService credentialService;
    private final ConfigChangeNotifier configChangeNotifier;

    @GetMapping
    public Result<List<LlmCredential>> list(@org.springframework.web.bind.annotation.RequestParam(required = false) String providerId) {
        List<LlmCredential> creds = (providerId != null && !providerId.isBlank())
                ? credentialService.listByProvider(providerId)
                : credentialService.listAll();
        creds.forEach(c -> c.setApiKeyEncrypted(maskKey(c.getApiKeyEncrypted())));
        return Result.ok(creds);
    }

    @GetMapping("/{id}")
    public Result<LlmCredential> get(@PathVariable String id) {
        LlmCredential cred = credentialService.getById(id);
        // Mask the encrypted key in response
        cred.setApiKeyEncrypted(maskKey(cred.getApiKeyEncrypted()));
        return Result.ok(cred);
    }

    @PostMapping
    public Result<LlmCredential> create(@RequestBody Map<String, Object> body) {
        String providerId = (String) body.get("providerId");
        String name = (String) body.get("name");
        String apiKey = (String) body.get("apiKey");

        LlmCredential credential = new LlmCredential();
        credential.setProviderId(providerId);
        credential.setName(name);
        credential.setEnabled(true);

        LlmCredential saved = credentialService.create(credential, apiKey);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.CREATE, "llm", saved.getProviderId());
        saved.setApiKeyEncrypted(maskKey(saved.getApiKeyEncrypted()));
        return Result.ok(saved);
    }

    @PutMapping("/{id}")
    public Result<LlmCredential> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String apiKey = (String) body.get("apiKey");
        Boolean enabled = (Boolean) body.get("enabled");
        LlmCredential saved = credentialService.update(id, apiKey, enabled);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.UPDATE, "llm", id);
        saved.setApiKeyEncrypted(maskKey(saved.getApiKeyEncrypted()));
        return Result.ok(saved);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        credentialService.delete(id);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.DELETE, "llm", id);
        return Result.ok();
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
