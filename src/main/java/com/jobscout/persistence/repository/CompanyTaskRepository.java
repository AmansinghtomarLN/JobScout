package com.jobscout.persistence.repository;

import com.jobscout.persistence.entity.CompanyTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyTaskRepository extends JpaRepository<CompanyTask, Long> {
    List<CompanyTask> findByRunId(Long runId);
    List<CompanyTask> findByStatus(String status);
    Optional<CompanyTask> findTopByCompanyIdOrderByIdDesc(String companyId);

    @Query("select t from CompanyTask t where t.errorCode is not null "
            + "and (:company is null or t.companyId = :company) "
            + "and (:runId is null or t.runId = :runId) "
            + "and (:code is null or t.errorCode = :code)")
    Page<CompanyTask> errors(@Param("company") String company, @Param("runId") Long runId,
                             @Param("code") String code, Pageable pageable);
}
