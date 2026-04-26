package ru.ticketswap.event;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @EntityGraph(attributePaths = {"organizer", "venue"})
    List<Event> findAllByOrganizerIdOrderByStartsAtAscIdAsc(Long organizerId);

    @EntityGraph(attributePaths = {"organizer", "venue"})
    List<Event> findAllByOrganizerApiKeyIgnoreCaseOrderByStartsAtAscIdAsc(String apiKey);

    @EntityGraph(attributePaths = {"organizer", "venue"})
    Optional<Event> findByIdAndOrganizerId(Long id, Long organizerId);

    @EntityGraph(attributePaths = {"organizer", "venue"})
    Optional<Event> findByOrganizerApiKeyIgnoreCaseAndEventIdIgnoreCase(String apiKey, String eventId);

    boolean existsByOrganizerIdAndEventIdIgnoreCase(Long organizerId, String eventId);

    boolean existsByOrganizerIdAndEventIdIgnoreCaseAndIdNot(Long organizerId, String eventId, Long id);

    long countByOrganizerId(Long organizerId);
}
