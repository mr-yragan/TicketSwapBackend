package ru.ticketswap.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop200ByOrderByCreatedAtDescIdDesc();

    @Query("""
            select log
            from AuditLog log
            where (:action is null or log.action = :action)
              and (:entityType is null or log.entityType = :entityType)
              and (:entityId is null or log.entityId = :entityId)
              and (:actorUserId is null or log.actorUserId = :actorUserId)
            order by log.createdAt desc, log.id desc
            """)
    List<AuditLog> search(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("actorUserId") Long actorUserId,
            Pageable pageable
    );
}
