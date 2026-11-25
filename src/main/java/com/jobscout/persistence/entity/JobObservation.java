package com.jobscout.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "job_observation")
public class JobObservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long jobId;
    public Long runId;
    public Instant observedAt;
    public String sourcePostedText;
    public String rawValues;
    public String searchKeyword;
    public String searchLocation;
    public Integer postedDays;
}
