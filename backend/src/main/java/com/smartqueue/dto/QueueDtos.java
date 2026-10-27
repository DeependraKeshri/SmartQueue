package com.smartqueue.dto;

import com.smartqueue.entity.*;
import com.smartqueue.eta.EtaResult;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QueueDtos {
    private QueueDtos() {}

    public record QueueDto(Long id, UUID publicId, String name, Long serviceId, String serviceName, QueueStatus status,
                           String tokenPrefix, long waitingCount, long atCounterCount) {}

    public record CreateQueueRequest(@NotNull Long serviceId, @Size(max = 120) String name) {}

    public record QueueStatusRequest(@NotNull QueueStatus status, Boolean cancelWaiting) {}

    public record WalkInRequest(@NotBlank @Size(max = 120) String guestName,
                                @Pattern(regexp = AuthDtos.PHONE_REGEX, message = "Invalid phone number") String guestPhone,
                                PriorityLevel priority, @Size(max = 255) String note) {}

    public record GuestJoinRequest(@NotBlank @Size(max = 120) String guestName,
                                   @Pattern(regexp = AuthDtos.PHONE_REGEX, message = "Invalid phone number") String guestPhone) {}

    public record CallNextRequest(@NotNull Long counterId, Long queueId) {}

    public record CallEntryRequest(@NotNull Long counterId, @NotBlank @Size(max = 255) String reason) {}

    public record CounterRequestBody(Long counterId) {}

    public record PriorityRequest(@NotNull PriorityLevel priority, @NotBlank @Size(max = 255) String reason) {}

    /** Staff-facing view of an entry (contains customer contact data; never exposed publicly). */
    public record EntryDto(Long id, UUID publicId, String tokenLabel, int tokenNumber, String displayName,
                           String guestPhone, Long customerId, EntryStatus status, EntrySource source,
                           PriorityLevel priority, Long queueId, String queueName, String serviceName,
                           Long counterId, String counterName, Instant joinedAt, Instant calledAt,
                           Instant servingStartedAt, Instant completedAt, int recallCount, int skipCount,
                           int position, EtaResult eta, Instant appointmentStartAt, String note) {}

    /** Customer-facing view of their own entry. */
    public record EntryView(Long id, UUID publicId, String tokenLabel, EntryStatus status, EntrySource source,
                            PriorityLevel priority, int position, int peopleAhead, EtaResult eta,
                            UUID queuePublicId, String queueName, String serviceName, String businessName,
                            UUID businessPublicId, String counterName, Instant joinedAt, Instant calledAt,
                            Instant appointmentStartAt, long serverSequence) {}

    public record BoardItem(String token, String counter, String displayName) {}

    public record PublicBoardDto(UUID queuePublicId, String queueName, String serviceName, String businessName,
                                 QueueStatus status, boolean businessOpen, DisplayMode displayMode,
                                 List<BoardItem> nowServing, List<BoardItem> nextUp, long waitingCount,
                                 Integer estimatedWaitMinutes, boolean showWait, Instant serverTime, long sequence) {}

    public record StaffQueueDto(QueueDto queue, List<EntryDto> waiting, List<EntryDto> atCounter,
                                List<EntryDto> skipped, long sequence) {}

    public record JoinResultDto(EntryView entry, boolean duplicateRequest) {}
}
