package com.jobscout.persistence.repository;

import com.jobscout.persistence.entity.JobObservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobObservationRepository extends JpaRepository<JobObservation, Long> {
    boolean existsByJobIdAndRunId(Long jobId, Long runId);
    List<JobObservation> findByJobIdOrderByObservedAtDesc(Long jobId);
}
