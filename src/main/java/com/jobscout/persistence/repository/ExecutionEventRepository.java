package com.jobscout.persistence.repository;

import com.jobscout.persistence.entity.ExecutionEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionEventRepository extends JpaRepository<ExecutionEvent, Long> {
    List<ExecutionEvent> findByTaskIdOrderByIdAsc(Long taskId);
}
