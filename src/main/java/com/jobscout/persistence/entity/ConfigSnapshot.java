package com.jobscout.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "config_snapshot")
public class ConfigSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String source;
    public String fileId;
    public String checksum;
    public Instant modifiedAt;
    public Instant importedAt;
    public int companyCount;
}
