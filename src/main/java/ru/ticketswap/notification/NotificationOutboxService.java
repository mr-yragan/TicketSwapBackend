package ru.ticketswap.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationOutboxService {

    private final NotificationOutboxRepository repository;

    public NotificationOutboxService(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(String recipientEmail, String type, String subject, String payload) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }
        repository.save(new NotificationOutbox(recipientEmail.trim().toLowerCase(), type, subject, payload));
    }
}
