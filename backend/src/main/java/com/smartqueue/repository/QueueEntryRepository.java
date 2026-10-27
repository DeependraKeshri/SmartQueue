package com.smartqueue.repository;

import com.smartqueue.entity.EntryStatus;
import com.smartqueue.entity.QueueEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {
    Optional<QueueEntry> findByIdAndBusinessId(Long id, Long businessId);
    Optional<QueueEntry> findByPublicId(UUID publicId);
    Optional<QueueEntry> findByIdAndCustomerId(Long id, Long customerId);
    List<QueueEntry> findByQueueIdAndStatusOrderByQueueTimeAscIdAsc(Long queueId, EntryStatus status);
    List<QueueEntry> findByQueueIdAndStatusInOrderByQueueTimeAscIdAsc(Long queueId, Collection<EntryStatus> statuses);
    long countByQueueIdAndStatus(Long queueId, EntryStatus status);
    boolean existsByQueueIdAndCustomerIdAndStatusIn(Long queueId, Long customerId, Collection<EntryStatus> statuses);
    boolean existsByQueueIdAndGuestPhoneAndStatusIn(Long queueId, String guestPhone, Collection<EntryStatus> statuses);
    Optional<QueueEntry> findByQueueIdAndIdempotencyKey(Long queueId, String key);
    boolean existsByCounterIdAndStatusIn(Long counterId, Collection<EntryStatus> statuses);
    List<QueueEntry> findByCounterIdAndStatusIn(Long counterId, Collection<EntryStatus> statuses);
    List<QueueEntry> findByCustomerIdOrderByJoinedAtDesc(Long customerId, Pageable pageable);
    List<QueueEntry> findByCustomerIdAndStatusInOrderByJoinedAtDesc(Long customerId, Collection<EntryStatus> statuses);
    List<QueueEntry> findByStatusAndCalledAtBefore(EntryStatus status, Instant before);
    List<QueueEntry> findByBusinessIdAndJoinedAtGreaterThanEqualAndJoinedAtLessThan(Long businessId, Instant from, Instant to);
    List<QueueEntry> findByAppointmentIdAndStatusIn(Long appointmentId, Collection<EntryStatus> statuses);

    @Query("select e.queue.id from QueueEntry e where e.id = :id and e.business.id = :businessId")
    Optional<Long> findQueueId(@Param("id") Long id, @Param("businessId") Long businessId);

    @Query("select e.queue.id from QueueEntry e where e.id = :id and e.customer.id = :customerId")
    Optional<Long> findQueueIdForCustomer(@Param("id") Long id, @Param("customerId") Long customerId);

    @Query("select e.queue.id from QueueEntry e where e.id = :id")
    Optional<Long> findQueueIdById(@Param("id") Long id);

    Optional<QueueEntry> findFirstByAppointmentIdOrderByIdDesc(Long appointmentId);

    @Query("select e.queue.id from QueueEntry e where e.publicId = :publicId")
    Optional<Long> findQueueIdByPublicId(@Param("publicId") UUID publicId);

    @Query("select e.queue.id from QueueEntry e where e.appointment.id = :appointmentId")
    List<Long> findQueueIdsByAppointment(@Param("appointmentId") Long appointmentId);
}
