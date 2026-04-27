package ru.ticketswap.organizer.manual;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerLookupService;
import ru.ticketswap.organizer.manual.dto.ManualReissueRejectRequest;
import ru.ticketswap.organizer.manual.dto.ManualVerificationDecisionRequest;
import ru.ticketswap.organizer.manual.dto.OrganizerListingResponse;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.user.User;

import java.util.List;

@RestController
@RequestMapping("/api/organizer/listings")
public class ManualOrganizerWorkflowController {

    private final OrganizerLookupService organizerLookupService;
    private final ManualOrganizerWorkflowService manualOrganizerWorkflowService;

    public ManualOrganizerWorkflowController(
            OrganizerLookupService organizerLookupService,
            ManualOrganizerWorkflowService manualOrganizerWorkflowService
    ) {
        this.organizerLookupService = organizerLookupService;
        this.manualOrganizerWorkflowService = manualOrganizerWorkflowService;
    }

    @GetMapping("/pending-validation")
    public ResponseEntity<List<OrganizerListingResponse>> listPendingValidation(
            @AuthenticationPrincipal UserDetails principal
    ) {
        Organizer organizer = organizerLookupService.requireOrganizerContext(principal).organizer();
        List<OrganizerListingResponse> response = manualOrganizerWorkflowService.listPendingValidation(organizer).stream()
                .map(OrganizerListingResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending-reissue")
    public ResponseEntity<List<OrganizerListingResponse>> listPendingReissue(
            @AuthenticationPrincipal UserDetails principal
    ) {
        Organizer organizer = organizerLookupService.requireOrganizerContext(principal).organizer();
        List<OrganizerListingResponse> response = manualOrganizerWorkflowService.listPendingReissue(organizer).stream()
                .map(OrganizerListingResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<OrganizerListingResponse> verifyTicket(
            @PathVariable("id") Long id,
            @Valid @RequestBody ManualVerificationDecisionRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        OrganizerLookupService.OrganizerContext context = organizerLookupService.requireOrganizerContext(principal);
        TicketLot listing = manualOrganizerWorkflowService.verifyTicket(
                context.organizer(),
                id,
                Boolean.TRUE.equals(request.approved()),
                request.reason(),
                context.user()
        );
        return ResponseEntity.ok(OrganizerListingResponse.fromEntity(listing));
    }

    @PostMapping(value = "/{id}/reissue", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrganizerListingResponse> completeManualReissue(
            @PathVariable("id") Long id,
            @RequestParam("newTicketUid") String newTicketUid,
            @RequestPart("ticketFile") MultipartFile ticketFile,
            @AuthenticationPrincipal UserDetails principal
    ) {
        OrganizerLookupService.OrganizerContext context = organizerLookupService.requireOrganizerContext(principal);
        TicketLot listing = manualOrganizerWorkflowService.completeManualReissue(
                context.organizer(),
                id,
                newTicketUid,
                ticketFile,
                context.user()
        );
        return ResponseEntity.ok(OrganizerListingResponse.fromEntity(listing));
    }

    @PostMapping("/{id}/reissue/reject")
    public ResponseEntity<OrganizerListingResponse> rejectManualReissue(
            @PathVariable("id") Long id,
            @Valid @RequestBody ManualReissueRejectRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        OrganizerLookupService.OrganizerContext context = organizerLookupService.requireOrganizerContext(principal);
        TicketLot listing = manualOrganizerWorkflowService.rejectManualReissue(
                context.organizer(),
                id,
                request.reason(),
                context.user()
        );
        return ResponseEntity.ok(OrganizerListingResponse.fromEntity(listing));
    }
}
