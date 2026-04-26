package ru.ticketswap.mockpartner.data;

import org.springframework.stereotype.Component;
import ru.ticketswap.event.Event;
import ru.ticketswap.event.EventRepository;
import ru.ticketswap.organizer.OrganizerRepository;

import java.util.List;

@Component
public class MockPartnerDataProvider {

    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;

    public MockPartnerDataProvider(OrganizerRepository organizerRepository, EventRepository eventRepository) {
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
    }

    public boolean isSupportedOrganizer(String organizerCode) {
        return organizerCode != null
                && !organizerCode.isBlank()
                && organizerRepository.existsByApiKeyIgnoreCase(organizerCode.trim());
    }

    public List<Event> getEventsByOrganizerCode(String organizerCode) {
        if (!isSupportedOrganizer(organizerCode)) {
            return List.of();
        }

        return eventRepository.findAllByOrganizerApiKeyIgnoreCaseOrderByStartsAtAscIdAsc(organizerCode.trim());
    }
}
