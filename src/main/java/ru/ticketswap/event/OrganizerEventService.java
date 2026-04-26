package ru.ticketswap.event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.common.ConflictException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.event.dto.OrganizerEventRequest;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.venue.Venue;
import ru.ticketswap.venue.VenueRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;

@Service
public class OrganizerEventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final TicketRepository ticketRepository;

    public OrganizerEventService(
            EventRepository eventRepository,
            VenueRepository venueRepository,
            TicketRepository ticketRepository
    ) {
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public Event createEvent(Organizer organizer, OrganizerEventRequest request) {
        String eventId = request.eventId().trim();
        if (eventRepository.existsByOrganizerIdAndEventIdIgnoreCase(organizer.getId(), eventId)) {
            throw new ConflictException("Мероприятие с таким ID уже существует у этого организатора");
        }

        Venue venue = findOrCreateVenue(request);
        Event event = new Event(
                eventId,
                request.name().trim(),
                venue,
                organizer,
                request.startsAt(),
                calculateEventDate(request, venue)
        );

        return eventRepository.save(event);
    }

    public List<Event> listEvents(Organizer organizer) {
        return eventRepository.findAllByOrganizerIdOrderByStartsAtAscIdAsc(organizer.getId());
    }

    public Event getEvent(Organizer organizer, Long id) {
        return eventRepository.findByIdAndOrganizerId(id, organizer.getId())
                .orElseThrow(() -> new NotFoundException("Мероприятие не найдено"));
    }

    @Transactional
    public Event updateEvent(Organizer organizer, Long id, OrganizerEventRequest request) {
        Event event = getEvent(organizer, id);
        String eventId = request.eventId().trim();

        if (eventRepository.existsByOrganizerIdAndEventIdIgnoreCaseAndIdNot(organizer.getId(), eventId, id)) {
            throw new ConflictException("Мероприятие с таким ID уже существует у этого организатора");
        }

        Venue venue = findOrCreateVenue(request);
        event.setEventId(eventId);
        event.setName(request.name().trim());
        event.setStartsAt(request.startsAt());
        event.setVenue(venue);
        event.setDate(calculateEventDate(request, venue));

        return eventRepository.save(event);
    }

    @Transactional
    public void deleteEvent(Organizer organizer, Long id) {
        Event event = getEvent(organizer, id);
        if (ticketRepository.existsByEvent_Id(event.getId())) {
            throw new ConflictException("Мероприятие нельзя удалить, потому что к нему привязаны билеты");
        }

        eventRepository.delete(event);
    }

    private Venue findOrCreateVenue(OrganizerEventRequest request) {
        VenueRequestParts venueParts = normalizeVenue(request);
        return venueRepository
                .findByNameIgnoreCaseAndAddressIgnoreCaseAndTimezone(
                        venueParts.name(),
                        venueParts.address(),
                        venueParts.timezone()
                )
                .orElseGet(() -> venueRepository.save(new Venue(
                        venueParts.name(),
                        venueParts.address(),
                        venueParts.timezone()
                )));
    }

    private LocalDate calculateEventDate(OrganizerEventRequest request, Venue venue) {
        ZoneId zoneId = parseZoneId(venue.getTimezone());
        return request.startsAt().atZone(zoneId).toLocalDate();
    }

    private VenueRequestParts normalizeVenue(OrganizerEventRequest request) {
        String name = request.venue().name().trim();
        String address = request.venue().address().trim();
        String timezone = request.venue().timezone().trim();
        parseZoneId(timezone);
        return new VenueRequestParts(name, address, timezone);
    }

    private ZoneId parseZoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (ZoneRulesException ex) {
            throw new BusinessRuleException("Часовой пояс площадки должен быть корректным");
        }
    }

    private record VenueRequestParts(String name, String address, String timezone) {
    }
}
