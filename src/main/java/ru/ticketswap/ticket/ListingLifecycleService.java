package ru.ticketswap.ticket;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Service
public class ListingLifecycleService {

    private static final long CREATED_TO_PENDING_VALIDATION_DELAY_MS = 300;

    private static final long PENDING_VALIDATION_TO_PENDING_RECIPIENT_DELAY_MS = 2_000;

    private final TicketRepository ticketRepository;
    private final TransactionTemplate tx;

    private final ScheduledExecutorService scheduler;

    public ListingLifecycleService(TicketRepository ticketRepository, PlatformTransactionManager transactionManager) {
        this.ticketRepository = ticketRepository;
        this.tx = new TransactionTemplate(transactionManager);

        ThreadFactory factory = r -> {
            Thread t = new Thread(r);
            t.setName("listing-lifecycle");
            t.setDaemon(true);
            return t;
        };

        this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public void onListingCreated(Long listingId) {
        if (listingId == null) {
            return;
        }

        scheduler.schedule(
                () -> tx.executeWithoutResult(status -> moveCreatedToPendingValidation(listingId)),
                CREATED_TO_PENDING_VALIDATION_DELAY_MS,
                TimeUnit.MILLISECONDS
        );

        scheduler.schedule(
                () -> tx.executeWithoutResult(status -> finalizeValidation(listingId)),
                CREATED_TO_PENDING_VALIDATION_DELAY_MS + PENDING_VALIDATION_TO_PENDING_RECIPIENT_DELAY_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private void moveCreatedToPendingValidation(Long listingId) {
        TicketLot lot = ticketRepository.findById(listingId).orElse(null);
        if (lot == null) {
            return;
        }

        if (lot.getStatus() != TicketStatus.CREATED) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (lot.getEventDate() != null && lot.getEventDate().isBefore(now)) {
            lot.setStatus(TicketStatus.FAILED);
            ticketRepository.save(lot);
            return;
        }

        lot.setStatus(TicketStatus.PENDING_VALIDATION);
        ticketRepository.save(lot);
    }

    private void finalizeValidation(Long listingId) {
        TicketLot lot = ticketRepository.findById(listingId).orElse(null);
        if (lot == null) {
            return;
        }

        if (lot.getStatus() != TicketStatus.PENDING_VALIDATION) {
            return;
        }

        lot.setStatus(TicketStatus.PENDING_RECIPIENT);
        ticketRepository.save(lot);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
