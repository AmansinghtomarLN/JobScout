package com.jobscout.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "execution_event")
public class ExecutionEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long taskId;
    public Instant ts;
    public String level;
    public String stage;
    public String eventCode;
    public String message;
}
