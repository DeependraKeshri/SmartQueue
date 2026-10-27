package com.smartqueue.repository;

import com.smartqueue.entity.HourlyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface HourlyStatRepository extends JpaRepository<HourlyStat, Long> {
    List<HourlyStat> findByBusinessIdAndStatDateBetween(Long businessId, LocalDate from, LocalDate to);

    @Modifying
    @Query("delete from HourlyStat h where h.businessId = :businessId and h.statDate = :date")
    int deleteForDay(@Param("businessId") Long businessId, @Param("date") LocalDate date);
}
