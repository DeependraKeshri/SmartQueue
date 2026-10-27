package com.smartqueue.repository;

import com.smartqueue.entity.QueueStatus;
import com.smartqueue.entity.ServiceQueue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceQueueRepository extends JpaRepository<ServiceQueue, Long> {
    Optional<ServiceQueue> findByIdAndBusinessId(Long id, Long businessId);
    Optional<ServiceQueue> findByPublicId(UUID publicId);
    Optional<ServiceQueue> findByServiceId(Long serviceId);
    List<ServiceQueue> findByBusinessIdOrderByNameAsc(Long businessId);
    List<ServiceQueue> findByStatus(QueueStatus status);
    boolean existsByBusinessIdAndTokenPrefix(Long businessId, String tokenPrefix);

    /** Serialises all state changes of one queue. Always lock queues in ascending id order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ServiceQueue q where q.id = :id")
    Optional<ServiceQueue> lockById(@Param("id") Long id);
}
