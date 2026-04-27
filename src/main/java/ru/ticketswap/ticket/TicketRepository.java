package ru.ticketswap.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<TicketLot, Long> {

    @Override
    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    List<TicketLot> findAll();

    @Override
    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    Optional<TicketLot> findById(Long id);

    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    List<TicketLot> findAllBySellerIdOrderByCreatedAtDesc(Long sellerId);

    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    List<TicketLot> findAllByBuyerIdOrderByEventDateAsc(Long buyerId);

    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    List<TicketLot> findAllByOrganizerIdAndStatusOrderByCreatedAtAsc(Long organizerId, TicketStatus status);

    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    List<TicketLot> findAllByOrganizerIdAndStatusAndBuyerIsNotNullOrderByCreatedAtAsc(Long organizerId, TicketStatus status);

    boolean existsByEvent_Id(Long eventId);
}
