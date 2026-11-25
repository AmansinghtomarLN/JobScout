package com.jobscout.api;

import com.jobscout.persistence.entity.JobPosting;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/** Query-string filter for the Job Results Explorer and the Excel export. */
public record JobFilter(String company, String keyword, String location, Integer postedWithinDays, String status,
                        Long runId, Boolean includeUnknownDate) {

    public Specification<JobPosting> toSpec() {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (company != null && !company.isBlank()) p.add(cb.equal(root.get("companyId"), company));
            if (keyword != null && !keyword.isBlank())
                p.add(cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase(Locale.ROOT) + "%"));
            if (location != null && !location.isBlank())
                p.add(cb.like(cb.lower(root.get("location")), "%" + location.toLowerCase(Locale.ROOT) + "%"));
            if (status != null && !status.isBlank()) p.add(cb.equal(root.get("currentStatus"), status));
            if (postedWithinDays != null) {
                Predicate recent = cb.greaterThanOrEqualTo(root.<LocalDate>get("postedDate"), LocalDate.now().minusDays(postedWithinDays));
                boolean unknown = includeUnknownDate == null || includeUnknownDate;
                p.add(unknown ? cb.or(recent, cb.isNull(root.get("postedDate"))) : recent);
            }
            if (runId != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<com.jobscout.persistence.entity.JobObservation> o = sq.from(com.jobscout.persistence.entity.JobObservation.class);
                sq.select(o.get("id")).where(cb.equal(o.get("jobId"), root.get("id")), cb.equal(o.get("runId"), runId));
                p.add(cb.exists(sq));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
