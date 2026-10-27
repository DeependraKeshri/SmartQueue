package com.smartqueue.repository;

import com.smartqueue.entity.BusinessService;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BusinessServiceRepository extends JpaRepository<BusinessService, Long> {
    Optional<BusinessService> findByIdAndBusinessIdAndDeletedFalse(Long id, Long businessId);
    Optional<BusinessService> findByIdAndDeletedFalse(Long id);
    List<BusinessService> findByBusinessIdAndDeletedFalseOrderByNameAsc(Long businessId);
    List<BusinessService> findByBusinessIdAndDeletedFalseAndActiveTrueOrderByNameAsc(Long businessId);
    boolean existsByBusinessIdAndNameIgnoreCaseAndDeletedFalse(Long businessId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BusinessService s where s.id = :id")
    Optional<BusinessService> lockById(@Param("id") Long id);
}
