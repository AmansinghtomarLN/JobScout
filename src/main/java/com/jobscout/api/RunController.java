package com.jobscout.api;

import com.jobscout.orchestration.RunService;
import com.jobscout.orchestration.RunService.RunRequest;
import com.jobscout.persistence.entity.CompanyTask;
import com.jobscout.persistence.entity.ExecutionEvent;
import com.jobscout.persistence.entity.ExecutionRun;
import com.jobscout.persistence.repository.CompanyTaskRepository;
import com.jobscout.persistence.repository.ExecutionEventRepository;
import com.jobscout.persistence.repository.ExecutionRunRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RunController {
    private final RunService service;
    private final ExecutionRunRepository runs;
    private final CompanyTaskRepository tasks;
    private final ExecutionEventRepository events;

    public RunController(RunService service, ExecutionRunRepository runs, CompanyTaskRepository tasks,
                         ExecutionEventRepository events) {
        this.service = service;
        this.runs = runs;
        this.tasks = tasks;
        this.events = events;
    }

    public record RunDetail(ExecutionRun run, List<CompanyTask> tasks) {}

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionRun start(@RequestBody(required = false) RunRequest req) {
        return service.start(req == null ? new RunRequest(null, null, false, null, "MANUAL") : req);
    }

    @GetMapping("/runs")
    public Page<ExecutionRun> list(@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable p) {
        return runs.findAll(p);
    }

    @GetMapping("/runs/{id}")
    public RunDetail detail(@PathVariable long id) {
        ExecutionRun r = runs.findById(id).orElseThrow(() -> new NoSuchElementException("Run " + id));
        return new RunDetail(r, tasks.findByRunId(id));
    }

    @GetMapping("/runs/{runId}/tasks/{taskId}/events")
    public List<ExecutionEvent> events(@PathVariable long runId, @PathVariable long taskId) {
        return events.findByTaskIdOrderByIdAsc(taskId);
    }

    @PostMapping("/runs/{id}/retry-failed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionRun retry(@PathVariable long id, @RequestParam(defaultValue = "false") boolean useLatestConfig) {
        return service.retryFailed(id, useLatestConfig);
    }

    @PostMapping("/runs/{id}/cancel")
    public void cancel(@PathVariable long id) { service.cancel(id); }
}
