package ru.ticketswap.event;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @EntityGraph(attributePaths = {"organizer", "venue"})
    List<Event> findAllByOrganizerIdOrderByStartsAtAscIdAsc(Long organizerId);

    @EntityGraph(attributePaths = {"organizer", "venue"})
    List<Event> findAllByOrganizerOrganizerCodeIgnoreCaseOrderByStartsAtAscIdAsc(String organizerCode);

    @EntityGraph(attributePaths = {"organizer", "venue"})
    Optional<Event> findByIdAndOrganizerId(Long id, Long organizerId);

    @EntityGraph(attributePaths = {"organizer", "venue"})
    Optional<Event> findByOrganizerOrganizerCodeIgnoreCaseAndEventIdIgnoreCase(String organizerCode, String eventId);

    @EntityGraph(attributePaths = {"organizer", "venue"})
    Optional<Event> findByOrganizerIdAndEventIdIgnoreCase(Long organizerId, String eventId);

    @EntityGraph(attributePaths = {"organizer", "venue"})
    @Query("""
            select e
            from Event e
            join e.organizer o
            join e.venue v
            where e.startsAt > :now
              and o.banned = false
            order by e.startsAt asc, e.id asc
            """)
    List<Event> findUpcomingEvents(@Param("now") Instant now);

    @EntityGraph(attributePaths = {"organizer", "venue"})
    @Query("""
            select e
            from Event e
            join e.organizer o
            join e.venue v
            where e.startsAt > :now
              and o.banned = false
              and (:organizerId is null or o.id = :organizerId)
              and (
                    :query = ''
                    or lower(e.name) like concat('%', :query, '%')
                    or lower(o.name) like concat('%', :query, '%')
                    or lower(v.name) like concat('%', :query, '%')
                    or lower(v.address) like concat('%', :query, '%')
                    or lower(e.eventId) like concat('%', :query, '%')
              )
            order by e.startsAt asc, e.id asc
            """)
    List<Event> searchUpcomingEvents(
            @Param("query") String query,
            @Param("organizerId") Long organizerId,
            @Param("now") Instant now
    );

    boolean existsByOrganizerIdAndEventIdIgnoreCase(Long organizerId, String eventId);

    boolean existsByOrganizerIdAndEventIdIgnoreCaseAndIdNot(Long organizerId, String eventId, Long id);

    long countByOrganizerId(Long organizerId);
}
