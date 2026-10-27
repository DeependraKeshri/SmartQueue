package com.smartqueue.repository;

import com.smartqueue.entity.QueueLengthSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface QueueLengthSampleRepository extends JpaRepository<QueueLengthSample, Long> {
    List<QueueLengthSample> findByBusinessIdAndSampledAtGreaterThanEqualAndSampledAtLessThanOrderBySampledAtAsc(Long businessId, Instant from, Instant to);

    @Modifying
    @Query("delete from QueueLengthSample s where s.sampledAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
