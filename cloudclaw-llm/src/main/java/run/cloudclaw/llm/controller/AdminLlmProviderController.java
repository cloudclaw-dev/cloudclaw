package run.cloudclaw.llm.controller;

import run.cloudclaw.common.config.ConfigChangeEvent;
import run.cloudclaw.common.config.ConfigChangeNotifier;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.llm.model.LlmProvider;
import run.cloudclaw.llm.service.LlmProviderService;
import jakarta.validation.Valid;
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

@Slf4j
@RestController
@RequestMapping("/api/admin/llm/providers")
@RequiredArgsConstructor
public class AdminLlmProviderController {

    private final LlmProviderService providerService;
    private final ConfigChangeNotifier configChangeNotifier;

    @GetMapping
    public Result<List<LlmProvider>> list() {
        return Result.ok(providerService.listAll());
    }

    @GetMapping("/{id}")
    public Result<LlmProvider> get(@PathVariable String id) {
        return Result.ok(providerService.getById(id));
    }

    @PostMapping
    public Result<LlmProvider> create(@Valid @RequestBody LlmProvider provider) {
        LlmProvider saved = providerService.create(provider);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.CREATE, "llm", saved.getId());
        return Result.ok(saved);
    }

    @PutMapping("/{id}")
    public Result<LlmProvider> update(@PathVariable String id, @RequestBody LlmProvider provider) {
        LlmProvider saved = providerService.update(id, provider);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.UPDATE, "llm", id);
        return Result.ok(saved);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        providerService.delete(id);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.DELETE, "llm", id);
        return Result.ok();
    }
}
