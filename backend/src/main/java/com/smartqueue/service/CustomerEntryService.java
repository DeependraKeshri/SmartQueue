package com.smartqueue.service;

import com.smartqueue.common.ApiException;
import com.smartqueue.dto.QueueDtos.EntryView;
import com.smartqueue.entity.QueueEntry;
import com.smartqueue.repository.QueueEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerEntryService {
    private final QueueEntryRepository entries;
    private final QueueViewService view;

    public CustomerEntryService(QueueEntryRepository entries, QueueViewService view) {
        this.entries = entries;
        this.view = view;
    }

    @Transactional(readOnly = true)
    public List<EntryView> recent(Long customerId) {
        Map<Long, QueueViewService.Snapshot> cache = new HashMap<>();
        return entries.findByCustomerIdOrderByJoinedAtDesc(customerId, PageRequest.of(0, 30)).stream().map(e -> {
            QueueViewService.Snapshot snap = e.getStatus() == com.smartqueue.entity.EntryStatus.WAITING
                    ? cache.computeIfAbsent(e.getQueue().getId(), id -> view.snapshot(e.getQueue())) : null;
            return view.customerView(e, snap);
        }).toList();
    }

    @Transactional(readOnly = true)
    public EntryView get(Long customerId, Long id) {
        QueueEntry e = entries.findByIdAndCustomerId(id, customerId).orElseThrow(() -> ApiException.notFound("Queue entry not found"));
        return view.customerView(e);
    }

    @Transactional(readOnly = true)
    public EntryView getByPublicId(UUID publicId) {
        QueueEntry e = entries.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("Queue entry not found"));
        return view.customerView(e);
    }
}
