package com.smartqueue.dto;

import com.smartqueue.entity.*;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BusinessDtos {
    private BusinessDtos() {}

    public record BusinessDto(Long id, UUID publicId, String name, String slug, String description, String logoUrl,
                              String timezone, String tokenPrefix, boolean active, DisplayMode displayMode,
                              boolean allowGuestJoin, boolean showWaitOnDisplay, boolean inAppNotificationsEnabled,
                              boolean emailNotificationsEnabled, boolean pushNotificationsEnabled,
                              boolean autoConfirmAppointments, String contactEmail, String contactPhone) {}

    /** PATCH semantics: null fields are left unchanged. */
    public record UpdateBusinessRequest(@Size(min = 1, max = 120) String name, @Size(max = 500) String description,
                                        @Size(max = 500) String logoUrl, @Size(max = 64) String timezone,
                                        @Pattern(regexp = "^[A-Za-z]{1,5}$") String tokenPrefix,
                                        DisplayMode displayMode, Boolean allowGuestJoin, Boolean showWaitOnDisplay,
                                        Boolean inAppNotificationsEnabled, Boolean emailNotificationsEnabled,
                                        Boolean pushNotificationsEnabled, Boolean autoConfirmAppointments,
                                        @Email @Size(max = 190) String contactEmail,
                                        @Size(max = 30) String contactPhone) {}

    public record PolicyDto(
            @Min(0) @Max(100000) int emergencyWeight, @Min(0) @Max(100000) int appointmentWeight,
            @Min(0) @Max(100000) int priorityWeight, @Min(0) @Max(100000) int normalWeight,
            @DecimalMin("0.0") @DecimalMax("1000.0") double agingPointsPerMinute,
            @Min(0) @Max(1440) int maxWaitMinutes, @Min(0) @Max(100) int fairnessEveryN,
            boolean emergencyBypassesFairness, @NotNull AppointmentPolicy appointmentPolicy,
            @Min(0) @Max(240) int appointmentEarlyWindowMinutes, @Min(0) @Max(240) int appointmentLateGraceMinutes,
            @Min(1) @Max(120) int noShowGraceMinutes, boolean autoNoShow, @Min(0) @Max(10) int maxRecalls,
            @Min(1) @Max(50) int approachingThreshold) {}

    public record HoursDto(@NotNull DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean closed) {}

    public record UpdateHoursRequest(@NotEmpty @Size(max = 7) List<@jakarta.validation.Valid HoursDto> days) {}

    public record BreakDto(Long id, DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                           @Size(max = 80) String label) {}

    public record HolidayDto(Long id, @NotNull LocalDate date, @Size(max = 120) String name) {}

    public record ScheduleDto(List<HoursDto> hours, List<BreakDto> breaks, List<HolidayDto> holidays, String timezone,
                              boolean openNow) {}

    public record ServiceRequest(
            @NotBlank @Size(max = 120) String name, @Size(max = 500) String description,
            @Min(1) @Max(600) Integer estimatedDurationMinutes, Boolean active, Boolean queueEnabled,
            Boolean appointmentsEnabled, Boolean priorityAllowed, @Min(5) @Max(240) Integer slotIntervalMinutes,
            @Min(1) @Max(365) Integer maxAdvanceBookingDays, @Min(0) @Max(10080) Integer minLeadMinutes,
            @Pattern(regexp = "^[A-Za-z]{1,5}$", message = "Token prefix must be 1-5 letters") String tokenPrefix) {}

    public record ServiceDto(Long id, UUID publicId, String name, String description, int estimatedDurationMinutes,
                             boolean active, boolean queueEnabled, boolean appointmentsEnabled, boolean priorityAllowed,
                             int slotIntervalMinutes, int maxAdvanceBookingDays, int minLeadMinutes, String tokenPrefix,
                             Long queueId, UUID queuePublicId) {}

    public record CounterRequest(@NotBlank @Size(max = 80) String name, Boolean active, Long assignedStaffId,
                                 Set<Long> serviceIds) {}

    public record CounterStatusRequest(@NotNull CounterStatus status) {}

    public record CounterDto(Long id, String name, boolean active, CounterStatus status, Long assignedStaffId,
                             String assignedStaffName, List<Long> serviceIds, List<String> serviceNames) {}
}
