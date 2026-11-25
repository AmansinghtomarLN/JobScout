package com.jobscout.persistence.repository;

import com.jobscout.persistence.entity.ConfigSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigSnapshotRepository extends JpaRepository<ConfigSnapshot, Long> {}
