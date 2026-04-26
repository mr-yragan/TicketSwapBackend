package ru.ticketswap.event;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.event.dto.OrganizerEventRequest;
import ru.ticketswap.event.dto.OrganizerEventResponse;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerLookupService;

import java.util.List;

@RestController
@RequestMapping("/api/organizer/events")
public class OrganizerEventController {

    private final OrganizerLookupService organizerLookupService;
    private final OrganizerEventService organizerEventService;

    public OrganizerEventController(
            OrganizerLookupService organizerLookupService,
            OrganizerEventService organizerEventService
    ) {
        this.organizerLookupService = organizerLookupService;
        this.organizerEventService = organizerEventService;
    }

    @PostMapping
    public ResponseEntity<OrganizerEventResponse> createEvent(
            @Valid @RequestBody OrganizerEventRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        Organizer organizer = currentOrganizer(principal);
        Event event = organizerEventService.createEvent(organizer, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(OrganizerEventResponse.fromEntity(event, true));
    }

    @GetMapping
    public ResponseEntity<List<OrganizerEventResponse>> listEvents(@AuthenticationPrincipal UserDetails principal) {
        Organizer organizer = currentOrganizer(principal);
        List<OrganizerEventResponse> response = organizerEventService.listEvents(organizer).stream()
                .map(event -> OrganizerEventResponse.fromEntity(event, false))
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganizerEventResponse> getEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        Organizer organizer = currentOrganizer(principal);
        Event event = organizerEventService.getEvent(organizer, id);
        return ResponseEntity.ok(OrganizerEventResponse.fromEntity(event, true));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrganizerEventResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody OrganizerEventRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        Organizer organizer = currentOrganizer(principal);
        Event event = organizerEventService.updateEvent(organizer, id, request);
        return ResponseEntity.ok(OrganizerEventResponse.fromEntity(event, true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        Organizer organizer = currentOrganizer(principal);
        organizerEventService.deleteEvent(organizer, id);
        return ResponseEntity.noContent().build();
    }

    private Organizer currentOrganizer(UserDetails principal) {
        return organizerLookupService.requireOrganizerContext(principal).organizer();
    }
}
