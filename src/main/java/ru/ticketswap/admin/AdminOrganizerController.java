package ru.ticketswap.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.admin.dto.CreateOrganizerRequest;
import ru.ticketswap.admin.dto.OrganizerResponse;
import ru.ticketswap.organizer.Organizer;

import java.util.List;

@RestController
@RequestMapping("/api/admin/organizers")
public class AdminOrganizerController {

    private final AdminOrganizerService adminOrganizerService;

    public AdminOrganizerController(AdminOrganizerService adminOrganizerService) {
        this.adminOrganizerService = adminOrganizerService;
    }

    @GetMapping
    public ResponseEntity<List<OrganizerResponse>> listOrganizers() {
        List<OrganizerResponse> response = adminOrganizerService.listOrganizers().stream()
                .map(OrganizerResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<OrganizerResponse> createOrganizer(@Valid @RequestBody CreateOrganizerRequest request) {
        Organizer organizer = adminOrganizerService.createOrganizer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrganizerResponse.fromEntity(organizer));
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<OrganizerResponse> banOrganizer(@PathVariable("id") Long id) {
        Organizer organizer = adminOrganizerService.banOrganizer(id);
        return ResponseEntity.ok(OrganizerResponse.fromEntity(organizer));
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<OrganizerResponse> unbanOrganizer(@PathVariable("id") Long id) {
        Organizer organizer = adminOrganizerService.unbanOrganizer(id);
        return ResponseEntity.ok(OrganizerResponse.fromEntity(organizer));
    }
}
