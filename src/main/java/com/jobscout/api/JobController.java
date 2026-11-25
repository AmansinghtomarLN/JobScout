package com.jobscout.api;

import com.jobscout.persistence.entity.JobObservation;
import com.jobscout.persistence.entity.JobPosting;
import com.jobscout.persistence.repository.JobObservationRepository;
import com.jobscout.persistence.repository.JobPostingRepository;
import com.jobscout.reporting.ExportService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class JobController {
    private final JobPostingRepository jobs;
    private final JobObservationRepository observations;
    private final ExportService export;

    public JobController(JobPostingRepository jobs, JobObservationRepository observations, ExportService export) {
        this.jobs = jobs;
        this.observations = observations;
        this.export = export;
    }

    public record JobDetail(JobDto job, List<JobObservation> observations) {}

    @GetMapping("/jobs")
    public Page<JobDto> list(JobFilter filter,
                             @PageableDefault(size = 50, sort = "lastSeenAt", direction = Sort.Direction.DESC) Pageable p) {
        return jobs.findAll(filter.toSpec(), p).map(j -> JobDto.from(j, false));
    }

    @GetMapping("/jobs/{id}")
    public JobDetail detail(@PathVariable long id) {
        JobPosting j = jobs.findById(id).orElseThrow(() -> new NoSuchElementException("Job " + id));
        return new JobDetail(JobDto.from(j, true), observations.findByJobIdOrderByObservedAtDesc(id));
    }

    @GetMapping("/reports/jobs/export")
    public ResponseEntity<byte[]> exportJobs(JobFilter filter) {
        byte[] xlsx = export.toExcel(filter);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("jobscout-jobs.xlsx").build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}
