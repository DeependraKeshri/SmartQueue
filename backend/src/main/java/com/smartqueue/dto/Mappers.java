package com.smartqueue.dto;

import com.smartqueue.dto.AuthDtos.UserDto;
import com.smartqueue.dto.BusinessDtos.*;
import com.smartqueue.entity.*;

import java.util.Comparator;
import java.util.List;

/** Entity -> DTO mapping. Call only inside a transaction (lazy associations are touched). */
public final class Mappers {
    private Mappers() {}

    public static UserDto user(AppUser u) {
        Business b = u.getBusiness();
        List<Long> serviceIds = u.getAssignedServices().stream().map(BusinessService::getId).sorted().toList();
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole(),
                b == null ? null : b.getId(), b == null ? null : b.getName(), b == null ? null : b.getPublicId(),
                u.isActive(), serviceIds);
    }

    public static BusinessDto business(Business b) {
        return new BusinessDto(b.getId(), b.getPublicId(), b.getName(), b.getSlug(), b.getDescription(), b.getLogoUrl(),
                b.getTimezone(), b.getTokenPrefix(), b.isActive(), b.getDisplayMode(), b.isAllowGuestJoin(),
                b.isShowWaitOnDisplay(), b.isInAppNotificationsEnabled(), b.isEmailNotificationsEnabled(),
                b.isPushNotificationsEnabled(), b.isAutoConfirmAppointments(), b.getContactEmail(), b.getContactPhone());
    }

    public static PolicyDto policy(QueuePolicy p) {
        return new PolicyDto(p.getEmergencyWeight(), p.getAppointmentWeight(), p.getPriorityWeight(), p.getNormalWeight(),
                p.getAgingPointsPerMinute(), p.getMaxWaitMinutes(), p.getFairnessEveryN(), p.isEmergencyBypassesFairness(),
                p.getAppointmentPolicy(), p.getAppointmentEarlyWindowMinutes(), p.getAppointmentLateGraceMinutes(),
                p.getNoShowGraceMinutes(), p.isAutoNoShow(), p.getMaxRecalls(), p.getApproachingThreshold());
    }

    public static ServiceDto service(BusinessService s, ServiceQueue q) {
        return new ServiceDto(s.getId(), s.getPublicId(), s.getName(), s.getDescription(), s.getEstimatedDurationMinutes(),
                s.isActive(), s.isQueueEnabled(), s.isAppointmentsEnabled(), s.isPriorityAllowed(),
                s.getSlotIntervalMinutes(), s.getMaxAdvanceBookingDays(), s.getMinLeadMinutes(), s.getTokenPrefix(),
                q == null ? null : q.getId(), q == null ? null : q.getPublicId());
    }

    public static CounterDto counter(Counter c) {
        List<BusinessService> svc = c.getServices().stream().sorted(Comparator.comparing(BusinessService::getId)).toList();
        AppUser staff = c.getAssignedStaff();
        return new CounterDto(c.getId(), c.getName(), c.isActive(), c.getStatus(),
                staff == null ? null : staff.getId(), staff == null ? null : staff.getFullName(),
                svc.stream().map(BusinessService::getId).toList(), svc.stream().map(BusinessService::getName).toList());
    }

    public static HoursDto hours(BusinessHours h) {
        return new HoursDto(h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime(), h.isClosed());
    }

    public static BreakDto breakPeriod(BreakPeriod b) {
        return new BreakDto(b.getId(), b.getDayOfWeek(), b.getStartTime(), b.getEndTime(), b.getLabel());
    }

    public static HolidayDto holiday(Holiday h) {
        return new HolidayDto(h.getId(), h.getDate(), h.getName());
    }
}
