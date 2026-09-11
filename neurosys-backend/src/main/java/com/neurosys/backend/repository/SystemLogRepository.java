package com.neurosys.backend.repository;

import com.neurosys.backend.entity.SystemLog;
import com.neurosys.backend.enums.LogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, String> {
    Page<SystemLog> findByComputerId(String computerId, Pageable pageable);
    Page<SystemLog> findByLogLevel(LogLevel logLevel, Pageable pageable);
    Page<SystemLog> findByComputerIdAndLogLevel(String computerId, LogLevel logLevel, Pageable pageable);
    long countByComputerId(String computerId);
    List<SystemLog> findTop20ByOrderByTimestampDesc();

    @Modifying
    @Query("DELETE FROM SystemLog sl WHERE sl.timestamp < :cutoff")
    int deleteLogsOlderThan(@Param("cutoff") Instant cutoff);
}
