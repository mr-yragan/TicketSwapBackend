package ru.ticketswap.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.admin.dto.CreateOrganizerRequest;
import ru.ticketswap.admin.dto.OrganizerResponse;
import ru.ticketswap.organizer.Organizer;

@RestController
@RequestMapping("/api/admin/organizers")
public class AdminOrganizerController {

    private final AdminOrganizerService adminOrganizerService;

    public AdminOrganizerController(AdminOrganizerService adminOrganizerService) {
        this.adminOrganizerService = adminOrganizerService;
    }

    @PostMapping
    public ResponseEntity<OrganizerResponse> createOrganizer(@Valid @RequestBody CreateOrganizerRequest request) {
        Organizer organizer = adminOrganizerService.createOrganizer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrganizerResponse.fromEntity(organizer));
    }
}
