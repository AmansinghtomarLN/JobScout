package com.jobscout.persistence.repository;

import com.jobscout.persistence.entity.ExecutionRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRunRepository extends JpaRepository<ExecutionRun, Long> {
    Optional<ExecutionRun> findTopByOrderByIdDesc();
    List<ExecutionRun> findByStatus(String status);
}
