package com.smartqueue.repository;

import com.smartqueue.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findByBusinessIdOrderByDateAsc(Long businessId);
    Optional<Holiday> findByIdAndBusinessId(Long id, Long businessId);
    boolean existsByBusinessIdAndDate(Long businessId, LocalDate date);
}
