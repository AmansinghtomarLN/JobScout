package com.jobscout.persistence.repository;

import com.jobscout.persistence.entity.JobPosting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long>, JpaSpecificationExecutor<JobPosting> {
    Optional<JobPosting> findByDedupeKey(String dedupeKey);
}
