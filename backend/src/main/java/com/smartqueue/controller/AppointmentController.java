package com.smartqueue.controller;

import com.smartqueue.dto.AppointmentDtos.*;
import com.smartqueue.service.AppointmentService;
import java.time.LocalDate;
import java.util.List;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class AppointmentController {
    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    // ---- customer ----
    @GetMapping("/api/customer/appointments")
    @PreAuthorize(Roles.CUSTOMER)
    public List<AppointmentDto> mine() {
        return appointments.forCustomer(CurrentUser.require().getId());
    }

    @PostMapping("/api/customer/appointments")
    @PreAuthorize(Roles.CUSTOMER)
    public ResponseEntity<AppointmentDto> book(@Valid @RequestBody BookRequest req,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointments.book(CurrentUser.require().getId(), req, key));
    }

    @PostMapping("/api/customer/appointments/{id}/reschedule")
    @PreAuthorize(Roles.CUSTOMER)
    public AppointmentDto reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest req) {
        return appointments.reschedule(CurrentUser.require().getId(), id, req);
    }

    @PostMapping("/api/customer/appointments/{id}/cancel")
    @PreAuthorize(Roles.CUSTOMER)
    public AppointmentDto cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest req) {
        return appointments.cancelByCustomer(CurrentUser.require().getId(), id, req == null ? null : req.reason());
    }

    @PostMapping("/api/customer/appointments/{id}/check-in")
    @PreAuthorize(Roles.CUSTOMER)
    public AppointmentDto checkIn(@PathVariable Long id) {
        return appointments.checkInByCustomer(CurrentUser.require().getId(), id);
    }

    // ---- staff ----
    @GetMapping("/api/appointments")
    @PreAuthorize(Roles.STAFF_UP)
    public List<AppointmentDto> forDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointments.forBusinessOnDate(CurrentUser.requireBusinessId(), date);
    }

    @PostMapping("/api/appointments/{id}/confirm")
    @PreAuthorize(Roles.STAFF_UP)
    public AppointmentDto confirm(@PathVariable Long id) {
        return appointments.confirm(CurrentUser.requireBusinessId(), id);
    }

    @PostMapping("/api/appointments/{id}/check-in")
    @PreAuthorize(Roles.STAFF_UP)
    public AppointmentDto staffCheckIn(@PathVariable Long id) {
        return appointments.checkInByStaff(CurrentUser.requireBusinessId(), id);
    }

    @PostMapping("/api/appointments/{id}/complete")
    @PreAuthorize(Roles.STAFF_UP)
    public AppointmentDto complete(@PathVariable Long id) {
        return appointments.complete(CurrentUser.requireBusinessId(), id);
    }

    @PostMapping("/api/appointments/{id}/no-show")
    @PreAuthorize(Roles.STAFF_UP)
    public AppointmentDto noShow(@PathVariable Long id) {
        return appointments.markNoShow(CurrentUser.requireBusinessId(), id);
    }

    @PostMapping("/api/appointments/{id}/cancel")
    @PreAuthorize(Roles.STAFF_UP)
    public AppointmentDto staffCancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest req) {
        return appointments.cancelByStaff(CurrentUser.requireBusinessId(), id, req == null ? null : req.reason());
    }
}
