package com.smartqueue.repository;

import com.smartqueue.entity.ServiceDurationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ServiceDurationRecordRepository extends JpaRepository<ServiceDurationRecord, Long> {

    /** Row: [count, sum(duration), sum(duration^2)] for ETA statistics. */
    @Query("select count(r), sum(r.durationSeconds), sum(r.durationSeconds * r.durationSeconds) "
            + "from ServiceDurationRecord r where r.serviceId = :serviceId and r.completedAt >= :since")
    List<Object[]> statsForService(@Param("serviceId") Long serviceId, @Param("since") Instant since);

    /** Rows: [serviceId, count, avgDuration, avgWait]. */
    @Query("select r.serviceId, count(r), avg(r.durationSeconds), avg(r.waitSeconds) from ServiceDurationRecord r "
            + "where r.businessId = :businessId and r.completedAt >= :from and r.completedAt < :to group by r.serviceId")
    List<Object[]> byService(@Param("businessId") Long businessId, @Param("from") Instant from, @Param("to") Instant to);

    /** Rows: [counterId, count, sum(duration)]. */
    @Query("select r.counterId, count(r), sum(r.durationSeconds) from ServiceDurationRecord r "
            + "where r.businessId = :businessId and r.counterId is not null and r.completedAt >= :from and r.completedAt < :to "
            + "group by r.counterId")
    List<Object[]> byCounter(@Param("businessId") Long businessId, @Param("from") Instant from, @Param("to") Instant to);

    /** Rows: [staffId, count, sum(duration), avg(duration)]. */
    @Query("select r.staffId, count(r), sum(r.durationSeconds), avg(r.durationSeconds) from ServiceDurationRecord r "
            + "where r.businessId = :businessId and r.staffId is not null and r.completedAt >= :from and r.completedAt < :to "
            + "group by r.staffId")
    List<Object[]> byStaff(@Param("businessId") Long businessId, @Param("from") Instant from, @Param("to") Instant to);
}
