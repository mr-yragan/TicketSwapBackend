package ru.ticketswap.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ticketswap.common.ConflictException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.event.dto.OrganizerEventRequest;
import ru.ticketswap.event.dto.VenueRequest;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.venue.Venue;
import ru.ticketswap.venue.VenueRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizerEventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Test
    void createEventReusesVenueAndCalculatesDateInVenueTimezone() {
        OrganizerEventService service = createService();
        Organizer organizer = new Organizer("Организация", "org3", "org3@example.com");
        org.springframework.test.util.ReflectionTestUtils.setField(organizer, "id", 3L);
        Venue venue = new Venue("Арена", "Адрес", "Asia/Almaty");
        OrganizerEventRequest request = request("EVT-1", "2030-01-01T20:30:00Z", "Asia/Almaty");

        when(eventRepository.existsByOrganizerIdAndEventIdIgnoreCase(3L, "EVT-1")).thenReturn(false);
        when(venueRepository.findByNameIgnoreCaseAndAddressIgnoreCaseAndTimezone("Арена", "Адрес", "Asia/Almaty"))
                .thenReturn(Optional.of(venue));
        when(eventRepository.save(org.mockito.ArgumentMatchers.any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Event event = service.createEvent(organizer, request);

        assertEquals("EVT-1", event.getEventId());
        assertSame(venue, event.getVenue());
        assertEquals(LocalDate.of(2030, 1, 2), event.getDate());
    }

    @Test
    void createEventRejectsDuplicateEventIdForOrganizer() {
        OrganizerEventService service = createService();
        Organizer organizer = new Organizer("Организация", "org3", "org3@example.com");
        org.springframework.test.util.ReflectionTestUtils.setField(organizer, "id", 3L);

        when(eventRepository.existsByOrganizerIdAndEventIdIgnoreCase(3L, "EVT-1")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createEvent(
                organizer,
                request("EVT-1", "2030-01-01T20:30:00Z", "Europe/Moscow")
        ));
        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getEventReturns404ForMissingOrForeignEvent() {
        OrganizerEventService service = createService();
        Organizer organizer = new Organizer("Организация", "org3", "org3@example.com");
        org.springframework.test.util.ReflectionTestUtils.setField(organizer, "id", 3L);

        when(eventRepository.findByIdAndOrganizerId(10L, 3L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getEvent(organizer, 10L));
    }

    @Test
    void deleteEventRejectsLinkedTickets() {
        OrganizerEventService service = createService();
        Organizer organizer = new Organizer("Организация", "org3", "org3@example.com");
        org.springframework.test.util.ReflectionTestUtils.setField(organizer, "id", 3L);
        Event event = new Event(
                "EVT-1",
                "Концерт",
                new Venue("Арена", "Адрес", "Europe/Moscow"),
                organizer,
                Instant.parse("2030-01-01T20:30:00Z"),
                LocalDate.of(2030, 1, 1)
        );
        org.springframework.test.util.ReflectionTestUtils.setField(event, "id", 10L);

        when(eventRepository.findByIdAndOrganizerId(10L, 3L)).thenReturn(Optional.of(event));
        when(ticketRepository.existsByEvent_Id(10L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.deleteEvent(organizer, 10L));
        verify(eventRepository, never()).delete(event);
    }

    private OrganizerEventRequest request(String eventId, String startsAt, String timezone) {
        return new OrganizerEventRequest(
                eventId,
                "Концерт",
                Instant.parse(startsAt),
                new VenueRequest("Арена", "Адрес", timezone)
        );
    }

    private OrganizerEventService createService() {
        return new OrganizerEventService(eventRepository, venueRepository, ticketRepository);
    }
}