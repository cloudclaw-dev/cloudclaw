package run.cloudclaw.llm.controller;

import run.cloudclaw.common.config.ConfigChangeEvent;
import run.cloudclaw.common.config.ConfigChangeNotifier;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.llm.model.LlmModel;
import run.cloudclaw.llm.service.LlmModelService;
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
@RequestMapping("/api/admin/llm/models")
@RequiredArgsConstructor
public class AdminLlmModelController {

    private final LlmModelService modelService;
    private final ConfigChangeNotifier configChangeNotifier;

    @GetMapping
    public Result<List<LlmModel>> list() {
        return Result.ok(modelService.listAll());
    }

    @GetMapping("/{id}")
    public Result<LlmModel> get(@PathVariable String id) {
        return Result.ok(modelService.getById(id));
    }

    @PostMapping
    public Result<LlmModel> create(@Valid @RequestBody LlmModel model) {
        LlmModel saved = modelService.create(model);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.CREATE, "llm", saved.getId());
        return Result.ok(saved);
    }

    @PutMapping("/{id}")
    public Result<LlmModel> update(@PathVariable String id, @RequestBody LlmModel model) {
        LlmModel saved = modelService.update(id, model);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.UPDATE, "llm", id);
        return Result.ok(saved);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        modelService.delete(id);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.DELETE, "llm", id);
        return Result.ok();
    }
}
