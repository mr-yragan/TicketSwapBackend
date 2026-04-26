package ru.ticketswap.organizer;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.event.EventRepository;
import ru.ticketswap.organizer.dto.OrganizerDashboardResponse;
import ru.ticketswap.organizer.dto.OrganizerProfileResponse;
import ru.ticketswap.user.User;

@RestController
@RequestMapping("/api/organizer")
public class OrganizerController {

    private final OrganizerLookupService organizerLookupService;
    private final EventRepository eventRepository;

    public OrganizerController(OrganizerLookupService organizerLookupService, EventRepository eventRepository) {
        this.organizerLookupService = organizerLookupService;
        this.eventRepository = eventRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<OrganizerProfileResponse> getOrganizerProfile(
            @AuthenticationPrincipal UserDetails principal
    ) {
        OrganizerLookupService.OrganizerContext context = organizerLookupService.requireOrganizerContext(principal);
        User user = context.user();
        Organizer organizer = context.organizer();

        return ResponseEntity.ok(new OrganizerProfileResponse(
                new OrganizerProfileResponse.UserInfo(
                        user.getId(),
                        user.getEmail(),
                        user.getLogin(),
                        user.getRole(),
                        user.isEmailVerified()
                ),
                new OrganizerProfileResponse.OrganizerInfo(
                        organizer.getId(),
                        organizer.getName(),
                        organizer.getApiKey(),
                        organizer.getContactEmail()
                )
        ));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<OrganizerDashboardResponse> getOrganizerDashboard(
            @AuthenticationPrincipal UserDetails principal
    ) {
        Organizer organizer = organizerLookupService.requireOrganizerContext(principal).organizer();
        long eventsCount = eventRepository.countByOrganizerId(organizer.getId());

        return ResponseEntity.ok(new OrganizerDashboardResponse(
                organizer.getId(),
                organizer.getName(),
                organizer.getApiKey(),
                organizer.getContactEmail(),
                eventsCount,
                true
        ));
    }
}
