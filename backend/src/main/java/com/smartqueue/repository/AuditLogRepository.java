package com.smartqueue.repository;

import com.smartqueue.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.time.Instant;

/** Deliberately NOT a JpaRepository: only append and read are exposed (audit logs are immutable). */
public interface AuditLogRepository extends Repository<AuditLog, Long> {
    AuditLog save(AuditLog log);

    Page<AuditLog> findByBusinessIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long businessId, Instant from, Instant to, Pageable pageable);

    Page<AuditLog> findByBusinessIdAndActionAndCreatedAtBetweenOrderByCreatedAtDesc(Long businessId, String action,
                                                                                     Instant from, Instant to, Pageable pageable);
}
