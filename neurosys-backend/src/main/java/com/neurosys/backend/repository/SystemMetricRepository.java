package com.neurosys.backend.repository;

import com.neurosys.backend.entity.SystemMetric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemMetricRepository extends JpaRepository<SystemMetric, Long> {
    List<SystemMetric> findByComputerIdOrderByRecordedAtDesc(String computerId, Pageable pageable);
    
    List<SystemMetric> findByComputerIdAndRecordedAtBetweenOrderByRecordedAtAsc(String computerId, Instant start, Instant end);

    @Query("SELECT sm FROM SystemMetric sm JOIN sm.computer c WHERE c.lab.id = :labId AND sm.recordedAt BETWEEN :start AND :end ORDER BY sm.recordedAt ASC")
    List<SystemMetric> findByComputerLabIdAndRecordedAtBetweenOrderByRecordedAtAsc(@Param("labId") String labId, @Param("start") Instant start, @Param("end") Instant end);

    List<SystemMetric> findByRecordedAtBetweenOrderByRecordedAtAsc(Instant start, Instant end);

    @Query("SELECT sm FROM SystemMetric sm WHERE sm.computer.id = :computerId ORDER BY sm.recordedAt DESC LIMIT 1")
    Optional<SystemMetric> findLatestByComputerId(@Param("computerId") String computerId);

    @Query("SELECT AVG(sm.cpuUsagePercent) FROM SystemMetric sm WHERE sm.recordedAt >= CURRENT_TIMESTAMP - 5 MINUTE")
    Double findFleetAverageCpuUsage();

    @Query("SELECT AVG(sm.memoryUsagePercent) FROM SystemMetric sm WHERE sm.recordedAt >= CURRENT_TIMESTAMP - 5 MINUTE")
    Double findFleetAverageMemoryUsage();

    @Query("SELECT AVG(sm.diskUsagePercent) FROM SystemMetric sm WHERE sm.recordedAt >= CURRENT_TIMESTAMP - 5 MINUTE")
    Double findFleetAverageDiskUsage();

    @Query("SELECT AVG(sm.networkRxBytesSec + sm.networkTxBytesSec) FROM SystemMetric sm WHERE sm.recordedAt >= CURRENT_TIMESTAMP - 5 MINUTE")
    Double findFleetAverageNetworkThroughput();

    @Modifying
    @Query("DELETE FROM SystemMetric sm WHERE sm.recordedAt < :cutoff")
    int deleteMetricsOlderThan(@Param("cutoff") Instant cutoff);
}
