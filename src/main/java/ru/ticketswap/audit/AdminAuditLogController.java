package ru.ticketswap.audit;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/admin/audit-log", "/api/admin/audit-logs"})
public class AdminAuditLogController {

    private final AuditLogRepository repository;

    public AdminAuditLogController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> listAuditLog(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) Long entityId,
            @RequestParam(value = "actorUserId", required = false) Long actorUserId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        List<AuditLogResponse> response = repository.search(
                        normalize(action),
                        normalize(entityType),
                        entityId,
                        actorUserId,
                        PageRequest.of(0, normalizeLimit(limit))
                ).stream()
                .map(AuditLogResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 200;
        }
        return Math.max(1, Math.min(limit, 500));
    }
}
