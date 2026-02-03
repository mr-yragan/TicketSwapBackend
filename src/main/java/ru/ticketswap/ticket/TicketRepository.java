package ru.ticketswap.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface TicketRepository extends JpaRepository<TicketLot, Long> {

    @Override
    @EntityGraph(attributePaths = {"seller"})
    List<TicketLot> findAll();

    @Override
    @EntityGraph(attributePaths = {"seller"})
    Optional<TicketLot> findById(Long id);

    @EntityGraph(attributePaths = {"seller"})
    List<TicketLot> findAllBySellerId(Long sellerId);
}
