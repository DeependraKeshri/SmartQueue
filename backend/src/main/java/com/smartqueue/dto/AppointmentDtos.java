package com.smartqueue.dto;

import com.smartqueue.entity.AppointmentStatus;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public final class AppointmentDtos {
    private AppointmentDtos() {}

    public record SlotDto(Instant startAt, Instant endAt, int capacity, int remaining) {}

    public record BookRequest(@NotNull Long serviceId, @NotNull Instant startAt, @Size(max = 500) String notes) {}

    public record RescheduleRequest(@NotNull Instant startAt) {}

    public record CancelRequest(@Size(max = 255) String reason) {}

    public record AppointmentDto(Long id, Long serviceId, String serviceName, Long businessId, String businessName,
                                 UUID businessPublicId, Long customerId, String customerName, Instant startAt,
                                 Instant endAt, AppointmentStatus status, String notes, int rescheduleCount,
                                 UUID queueEntryPublicId, String timezone) {}
}
