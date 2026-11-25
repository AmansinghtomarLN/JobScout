package com.jobscout.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "company_task")
public class CompanyTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long runId;
    public String companyId;
    public String companyName;
    public String profileId;
    public String status;
    public String stage;
    public int attempts;
    public int pagesVisited;
    public int urlsDiscovered;
    public int jobsExtracted;
    public int newJobs;
    public int rejectedByDate;
    public Instant startedAt;
    public Instant completedAt;
    public String errorCode;
    public String errorMessage;
}
