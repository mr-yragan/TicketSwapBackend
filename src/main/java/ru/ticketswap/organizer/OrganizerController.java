package ru.ticketswap.organizer;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.organizer.dto.OrganizerDashboardResponse;
import ru.ticketswap.organizer.dto.OrganizerProfileResponse;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.ticket.TicketStatus;

@RestController
@RequestMapping("/api/organizer")
public class OrganizerController {

    private final OrganizerLookupService organizerLookupService;
    private final TicketRepository ticketRepository;

    public OrganizerController(
            OrganizerLookupService organizerLookupService,
            TicketRepository ticketRepository
    ) {
        this.organizerLookupService = organizerLookupService;
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<OrganizerProfileResponse> getOrganizerProfile(
            @AuthenticationPrincipal UserDetails principal
    ) {
        Organizer organizer = organizerLookupService.requireOrganizerContext(principal).organizer();
        return ResponseEntity.ok(OrganizerProfileResponse.fromEntity(organizer));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<OrganizerDashboardResponse> getOrganizerDashboard(
            @AuthenticationPrincipal UserDetails principal
    ) {
        Organizer organizer = organizerLookupService.requireOrganizerContext(principal).organizer();
        long pendingValidationCount = ticketRepository
                .findAllByOrganizerIdAndStatusOrderByCreatedAtAsc(organizer.getId(), TicketStatus.PENDING_VALIDATION)
                .size();
        long pendingReissueCount = ticketRepository
                .findAllByOrganizerIdAndStatusAndBuyerIsNotNullOrderByCreatedAtAsc(organizer.getId(), TicketStatus.PROCESSING)
                .size();

        return ResponseEntity.ok(new OrganizerDashboardResponse(
                organizer.getId(),
                organizer.getName(),
                organizer.getOrganizerCode(),
                pendingValidationCount,
                pendingReissueCount
        ));
    }
}
