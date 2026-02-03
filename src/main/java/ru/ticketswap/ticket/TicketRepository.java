package ru.ticketswap.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<TicketLot, Long> {

    @Override
    @EntityGraph(attributePaths = {"seller", "buyer"})
    List<TicketLot> findAll();

    @Override
    @EntityGraph(attributePaths = {"seller", "buyer"})
    Optional<TicketLot> findById(Long id);

    @EntityGraph(attributePaths = {"seller", "buyer"})
    List<TicketLot> findAllBySellerIdOrderByCreatedAtDesc(Long sellerId);

    @EntityGraph(attributePaths = {"seller", "buyer"})
    List<TicketLot> findAllByBuyerIdOrderByEventDateAsc(Long buyerId);
}
