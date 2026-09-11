package com.neurosys.backend.repository;

import com.neurosys.backend.entity.DiagnosticEvent;
import com.neurosys.backend.enums.DiagnosticCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DiagnosticEventRepository extends JpaRepository<DiagnosticEvent, String> {
    List<DiagnosticEvent> findByComputerIdOrderByOccurredAtDesc(String computerId, Pageable pageable);
    List<DiagnosticEvent> findByComputerIdAndCategoryAndOccurredAtAfter(String computerId, DiagnosticCategory category, Instant after);
    long countByComputerIdAndCategoryAndOccurredAtAfter(String computerId, DiagnosticCategory category, Instant after);

    @Modifying
    @Query("DELETE FROM DiagnosticEvent de WHERE de.occurredAt < :cutoff")
    int deleteEventsOlderThan(@Param("cutoff") Instant cutoff);
}
