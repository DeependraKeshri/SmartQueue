package com.smartqueue.service;

import com.smartqueue.common.ApiException;
import com.smartqueue.dto.PublicDtos.*;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public (unauthenticated) read model. Exposes only data safe for anyone: no customer information. */
@Service
public class PublicCatalogService {
    private final BusinessRepository businesses;
    private final BusinessServiceRepository services;
    private final ServiceQueueRepository queues;
    private final BusinessScheduleService scheduleService;
    private final QueueViewService view;

    public PublicCatalogService(BusinessRepository businesses, BusinessServiceRepository services,
                                ServiceQueueRepository queues, BusinessScheduleService scheduleService, QueueViewService view) {
        this.businesses = businesses;
        this.services = services;
        this.queues = queues;
        this.scheduleService = scheduleService;
        this.view = view;
    }

    @Transactional(readOnly = true)
    public List<PublicBusinessSummary> search(String q) {
        List<Business> list = (q == null || q.isBlank())
                ? businesses.findByActiveTrueOrderByNameAsc()
                : businesses.findByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(q.trim());
        Instant now = Instant.now();
        return list.stream().limit(100)
                .map(b -> new PublicBusinessSummary(b.getPublicId(), b.getName(), b.getDescription(), b.getLogoUrl(),
                        scheduleService.isOpen(b, now))).toList();
    }

    @Transactional(readOnly = true)
    public PublicBusinessDetail detail(UUID publicId) {
        Business b = businesses.findByPublicId(publicId).filter(Business::isActive)
                .orElseThrow(() -> ApiException.notFound("Business not found"));
        boolean open = scheduleService.isOpen(b, Instant.now());
        List<PublicService> list = services.findByBusinessIdAndDeletedFalseAndActiveTrueOrderByNameAsc(b.getId()).stream().map(s -> {
            ServiceQueue q = s.isQueueEnabled() ? queues.findByServiceId(s.getId()).orElse(null) : null;
            long waiting = 0;
            Integer wait = null;
            if (q != null) {
                QueueViewService.Snapshot snap = view.snapshot(q);
                waiting = snap.ordered().size();
                wait = snap.newJoinEta().estimatedWaitMinutes();
            }
            return new PublicService(s.getId(), s.getName(), s.getDescription(), s.getEstimatedDurationMinutes(),
                    q == null ? null : q.getPublicId(), q == null ? null : q.getStatus(), waiting, wait, s.isAppointmentsEnabled());
        }).toList();
        return new PublicBusinessDetail(b.getPublicId(), b.getName(), b.getDescription(), b.getLogoUrl(), b.getTimezone(),
                open, b.isAllowGuestJoin(), list);
    }
}
