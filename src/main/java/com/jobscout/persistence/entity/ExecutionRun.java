package com.jobscout.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "execution_run")
public class ExecutionRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Instant startedAt;
    public Instant completedAt;
    public String triggerType;
    public Long configSnapshotId;
    public String status;
    public int jobsFound;
    public int newJobs;
    public String summary;
}
