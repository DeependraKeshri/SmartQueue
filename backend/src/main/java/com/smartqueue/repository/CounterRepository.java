package com.smartqueue.repository;

import com.smartqueue.entity.Counter;
import com.smartqueue.entity.CounterStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CounterRepository extends JpaRepository<Counter, Long> {
    Optional<Counter> findByIdAndBusinessId(Long id, Long businessId);
    List<Counter> findByBusinessIdOrderByNameAsc(Long businessId);
    List<Counter> findByAssignedStaffId(Long staffId);
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Counter c where c.id = :id")
    Optional<Counter> lockById(@Param("id") Long id);

    /** Number of active counters configured to serve the service (used as appointment slot capacity). */
    @Query("select count(distinct c.id) from Counter c join c.services s where s.id = :serviceId and c.active = true")
    long countActiveForService(@Param("serviceId") Long serviceId);

    /** OPEN counters able to serve the service (counters with no services serve everything). */
    @Query("select distinct c from Counter c left join c.services s "
            + "where c.business.id = :businessId and c.active = true and c.status = :status "
            + "and (s.id = :serviceId or c.services is empty)")
    List<Counter> findServingCounters(@Param("businessId") Long businessId, @Param("serviceId") Long serviceId,
                                      @Param("status") CounterStatus status);
}
