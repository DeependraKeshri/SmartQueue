package com.smartqueue.realtime;

public record AppointmentChangedEvent(Long businessId, Long appointmentId, String reason) {}
