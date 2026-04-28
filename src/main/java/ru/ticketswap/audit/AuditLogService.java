package ru.ticketswap.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketswap.user.User;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User actor, String action, String entityType, Long entityId, String details) {
        Long actorId = actor == null ? null : actor.getId();
        repository.save(new AuditLog(actorId, action, entityType, entityId, trim(details)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystem(String action, String entityType, Long entityId, String details) {
        repository.save(new AuditLog(null, action, entityType, entityId, trim(details)));
    }

    private String trim(String details) {
        if (details == null) {
            return null;
        }
        return details.length() <= 2000 ? details : details.substring(0, 2000);
    }
}
