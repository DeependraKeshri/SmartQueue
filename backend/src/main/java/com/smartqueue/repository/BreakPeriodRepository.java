package com.smartqueue.repository;

import com.smartqueue.entity.BreakPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BreakPeriodRepository extends JpaRepository<BreakPeriod, Long> {
    List<BreakPeriod> findByBusinessId(Long businessId);
    Optional<BreakPeriod> findByIdAndBusinessId(Long id, Long businessId);
}
