package ru.ticketswap.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketFileRepository extends JpaRepository<TicketFile, Long> {

    List<TicketFile> findAllByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

    Optional<TicketFile> findByIdAndTicketId(Long id, Long ticketId);
}
