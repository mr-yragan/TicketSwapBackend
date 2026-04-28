package ru.ticketswap.audit;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-log")
public class AdminAuditLogController {

    private final AuditLogRepository repository;

    public AdminAuditLogController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> listAuditLog() {
        return ResponseEntity.ok(repository.findTop200ByOrderByCreatedAtDescIdDesc().stream().map(AuditLogResponse::fromEntity).toList());
    }
}
