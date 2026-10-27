package com.smartqueue.controller;

import com.smartqueue.config.AppProperties;
import com.smartqueue.dto.AppointmentDtos.SlotDto;
import com.smartqueue.dto.PublicDtos.*;
import com.smartqueue.dto.QueueDtos.*;
import com.smartqueue.entity.ServiceQueue;
import com.smartqueue.realtime.QueueBroadcaster;
import com.smartqueue.repository.BusinessRepository;
import com.smartqueue.repository.ServiceQueueRepository;
import com.smartqueue.service.*;
import com.smartqueue.common.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Unauthenticated endpoints. Never returns customer personal data (see QueueViewService.shownName). */
@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final PublicCatalogService catalog;
    private final ServiceQueueRepository queues;
    private final BusinessRepository businesses;
    private final QueueViewService view;
    private final QueueBroadcaster broadcaster;
    private final QueueService queueService;
    private final CustomerEntryService entries;
    private final AppointmentService appointments;
    private final QrService qr;
    private final AppProperties props;

    public PublicController(PublicCatalogService catalog, ServiceQueueRepository queues, BusinessRepository businesses,
                            QueueViewService view, QueueBroadcaster broadcaster, QueueService queueService,
                            CustomerEntryService entries, AppointmentService appointments, QrService qr, AppProperties props) {
        this.catalog = catalog;
        this.queues = queues;
        this.businesses = businesses;
        this.view = view;
        this.broadcaster = broadcaster;
        this.queueService = queueService;
        this.entries = entries;
        this.appointments = appointments;
        this.qr = qr;
        this.props = props;
    }

    @GetMapping("/businesses")
    public List<PublicBusinessSummary> businesses(@RequestParam(required = false) String q) {
        return catalog.search(q);
    }

    @GetMapping("/businesses/{publicId}")
    public PublicBusinessDetail business(@PathVariable UUID publicId) {
        return catalog.detail(publicId);
    }

    @GetMapping("/queues/{publicId}/board")
    @Transactional(readOnly = true)
    public PublicBoardDto board(@PathVariable UUID publicId) {
        ServiceQueue q = queues.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("Queue not found"));
        return broadcaster.cachedBoard(q.getId()).orElseGet(() -> view.board(q));
    }

    @PostMapping("/queues/{publicId}/join")
    public ResponseEntity<JoinResultDto> guestJoin(@PathVariable UUID publicId, @Valid @RequestBody GuestJoinRequest req,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        JoinResultDto r = queueService.joinAsGuest(publicId, req, key);
        return ResponseEntity.status(r.duplicateRequest() ? HttpStatus.OK : HttpStatus.CREATED).body(r);
    }

    @GetMapping("/entries/{publicId}")
    public EntryView entry(@PathVariable UUID publicId) {
        return entries.getByPublicId(publicId);
    }

    @PostMapping("/entries/{publicId}/cancel")
    public EntryView cancel(@PathVariable UUID publicId) {
        return queueService.cancelByPublicId(publicId);
    }

    @GetMapping("/services/{serviceId}/slots")
    public List<SlotDto> slots(@PathVariable Long serviceId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointments.slots(serviceId, date);
    }

    @GetMapping(value = "/qr/queues/{publicId}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> queueQr(@PathVariable UUID publicId, @RequestParam(defaultValue = "320") int size) {
        queues.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("Queue not found"));
        return png(props.getPublicBaseUrl() + "/join/" + publicId, size);
    }

    @GetMapping(value = "/qr/businesses/{publicId}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> businessQr(@PathVariable UUID publicId, @RequestParam(defaultValue = "320") int size) {
        businesses.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("Business not found"));
        return png(props.getPublicBaseUrl() + "/b/" + publicId, size);
    }

    private ResponseEntity<byte[]> png(String url, int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic())
                .contentType(MediaType.IMAGE_PNG).body(qr.png(url, size));
    }
}
