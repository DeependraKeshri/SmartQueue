package com.smartqueue.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.dto.AuditDtos.AuditLogDto;
import com.smartqueue.dto.PageDto;
import com.smartqueue.entity.AuditLog;
import com.smartqueue.repository.AuditLogRepository;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Writes immutable audit records in the caller's transaction (so a rolled-back action leaves no audit trail). */
@Service
public class AuditService {
    private static final Set<String> SENSITIVE = Set.of("password", "passwordhash", "token", "secret", "authorization", "jwt", "apikey");
    private final AuditLogRepository repo;
    private final ObjectMapper mapper;

    public AuditService(AuditLogRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional
    public void record(Long businessId, String action, String resourceType, Object resourceId, Map<String, Object> metadata) {
        AuditLog log = new AuditLog();
        log.setBusinessId(businessId);
        UserPrincipal actor = CurrentUser.get().orElse(null);
        log.setActorId(actor == null ? null : actor.getId());
        log.setActorRole(actor == null ? "SYSTEM_OR_GUEST" : actor.getRole().name());
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
        log.setMetadata(serialize(metadata));
        log.setCreatedAt(Instant.now());
        repo.save(log);
    }

    public void record(Long businessId, String action, String resourceType, Object resourceId) {
        record(businessId, action, resourceType, resourceId, Map.of());
    }

    private String serialize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        metadata.forEach((k, v) -> {
            if (!SENSITIVE.contains(k.toLowerCase())) {
                clean.put(k, v);
            }
        });
        try {
            String json = mapper.writeValueAsString(clean);
            return json.length() > 2000 ? json.substring(0, 1997) + "..." : json;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public PageDto<AuditLogDto> search(Long businessId, String action, Instant from, Instant to, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200));
        var result = (action == null || action.isBlank())
                ? repo.findByBusinessIdAndCreatedAtBetweenOrderByCreatedAtDesc(businessId, from, to, pr)
                : repo.findByBusinessIdAndActionAndCreatedAtBetweenOrderByCreatedAtDesc(businessId, action, from, to, pr);
        return PageDto.of(result, a -> new AuditLogDto(a.getId(), a.getAction(), a.getResourceType(), a.getResourceId(),
                a.getActorId(), a.getActorRole(), a.getMetadata(), a.getCreatedAt()));
    }
}
