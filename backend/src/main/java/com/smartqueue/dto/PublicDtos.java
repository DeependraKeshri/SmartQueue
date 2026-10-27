package com.smartqueue.dto;

import com.smartqueue.entity.QueueStatus;

import java.util.List;
import java.util.UUID;

public final class PublicDtos {
    private PublicDtos() {}

    public record PublicBusinessSummary(UUID publicId, String name, String description, String logoUrl, boolean openNow) {}

    public record PublicService(Long id, String name, String description, int estimatedDurationMinutes,
                                UUID queuePublicId, QueueStatus queueStatus, long waitingCount,
                                Integer estimatedWaitMinutes, boolean appointmentsEnabled) {}

    public record PublicBusinessDetail(UUID publicId, String name, String description, String logoUrl, String timezone,
                                       boolean openNow, boolean allowGuestJoin, List<PublicService> services) {}
}
