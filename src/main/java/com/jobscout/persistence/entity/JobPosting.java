package com.jobscout.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "job_posting")
public class JobPosting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String companyId;
    public String companyName;
    public String externalJobId;
    public String dedupeKey;
    public String canonicalUrl;
    public String title;
    public String location;
    public String description;
    public String employmentType;
    public LocalDate postedDate;
    public String postedDateRaw;
    public String postedDateStatus;
    public String currentStatus;
    public Instant firstSeenAt;
    public Instant lastSeenAt;
    public Instant lastVerifiedAt;
    public Long firstRunId;
    public Long lastRunId;
}
