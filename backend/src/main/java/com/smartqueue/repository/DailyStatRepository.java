package com.smartqueue.repository;

import com.smartqueue.entity.DailyStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyStatRepository extends JpaRepository<DailyStat, Long> {
    List<DailyStat> findByBusinessIdAndStatDateBetweenOrderByStatDateAsc(Long businessId, LocalDate from, LocalDate to);
    Optional<DailyStat> findByBusinessIdAndStatDate(Long businessId, LocalDate date);
}
