package com.smartqueue.dto;

import java.time.Instant;

public final class AuditDtos {
    private AuditDtos() {}

    public record AuditLogDto(Long id, String action, String resourceType, String resourceId, Long actorId,
                              String actorRole, String metadata, Instant createdAt) {}
}
