package com.smartqueue.controller;

import com.smartqueue.dto.QueueDtos.PublicBoardDto;
import com.smartqueue.repository.ServiceQueueRepository;
import com.smartqueue.service.QueueViewService;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Boards of every queue of the display account's own business (waiting-room screens). */
@RestController
@RequestMapping("/api/display")
public class DisplayController {
    private final ServiceQueueRepository queues;
    private final QueueViewService view;

    public DisplayController(ServiceQueueRepository queues, QueueViewService view) {
        this.queues = queues;
        this.view = view;
    }

    @GetMapping("/boards")
    @PreAuthorize("hasAnyRole('DISPLAY','STAFF','MANAGER','BUSINESS_ADMIN')")
    @Transactional(readOnly = true)
    public List<PublicBoardDto> boards() {
        return queues.findByBusinessIdOrderByNameAsc(CurrentUser.requireBusinessId()).stream()
                .filter(q -> q.getStatus() != com.smartqueue.entity.QueueStatus.INACTIVE)
                .map(view::board).toList();
    }
}
