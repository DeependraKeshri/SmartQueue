package com.smartqueue.repository;

import com.smartqueue.entity.QueuePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QueuePolicyRepository extends JpaRepository<QueuePolicy, Long> {
    Optional<QueuePolicy> findByBusinessId(Long businessId);
}
