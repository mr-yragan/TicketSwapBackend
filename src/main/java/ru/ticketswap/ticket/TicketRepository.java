package ru.ticketswap.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketRepository extends JpaRepository<TicketLot, Long> {
    List<TicketLot> findAllBySellerId(Long sellerId);
}
