package com.smartqueue.repository;

import com.smartqueue.entity.Appointment;
import com.smartqueue.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    Optional<Appointment> findByIdAndBusinessId(Long id, Long businessId);
    Optional<Appointment> findByIdAndCustomerId(Long id, Long customerId);
    Optional<Appointment> findByCustomerIdAndIdempotencyKey(Long customerId, String key);
    List<Appointment> findByCustomerIdOrderByStartAtDesc(Long customerId);
    List<Appointment> findByBusinessIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(Long businessId, Instant from, Instant to);
    List<Appointment> findByStatusInAndEndAtBefore(Collection<AppointmentStatus> statuses, Instant before);

    @Query("select a from Appointment a where a.service.id = :serviceId and a.status in :statuses "
            + "and a.startAt < :to and a.endAt > :from")
    List<Appointment> findOverlapping(@Param("serviceId") Long serviceId,
                                      @Param("statuses") Collection<AppointmentStatus> statuses,
                                      @Param("from") Instant from, @Param("to") Instant to);

    @Query("select a from Appointment a where a.customer.id = :customerId and a.status in :statuses "
            + "and a.startAt < :to and a.endAt > :from")
    List<Appointment> findCustomerOverlapping(@Param("customerId") Long customerId,
                                              @Param("statuses") Collection<AppointmentStatus> statuses,
                                              @Param("from") Instant from, @Param("to") Instant to);
}
