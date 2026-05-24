package run.cloudclaw.admin.controller;

import run.cloudclaw.agent.prompt.PromptLogService;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.model.PromptLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/prompt-logs")
@RequiredArgsConstructor
public class PromptLogController {

    private final PromptLogService promptLogService;

    @GetMapping
    public Result<Page<PromptLog>> list(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        Page<PromptLog> result = promptLogService.query(sessionId, agentId, startTime, endTime, keyword, page, size);
        return Result.ok(result);
    }
}
