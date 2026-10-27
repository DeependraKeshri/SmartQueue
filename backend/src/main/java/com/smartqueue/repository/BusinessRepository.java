package com.smartqueue.repository;

import com.smartqueue.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, Long> {
    Optional<Business> findByPublicId(UUID publicId);
    boolean existsBySlug(String slug);
    List<Business> findByActiveTrueOrderByNameAsc();
    List<Business> findByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(String name);
    List<Business> findAllByOrderByNameAsc();
}
