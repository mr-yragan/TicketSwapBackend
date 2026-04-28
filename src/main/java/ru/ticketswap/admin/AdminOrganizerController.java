package ru.ticketswap.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.admin.dto.CreateOrganizerRequest;
import ru.ticketswap.admin.dto.OrganizerResponse;
import ru.ticketswap.common.UnauthorizedException;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserRepository;

import java.util.List;

@RestController
@RequestMapping("/api/admin/organizers")
public class AdminOrganizerController {

    private final AdminOrganizerService adminOrganizerService;
    private final UserRepository userRepository;

    public AdminOrganizerController(AdminOrganizerService adminOrganizerService, UserRepository userRepository) {
        this.adminOrganizerService = adminOrganizerService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<OrganizerResponse>> listOrganizers() {
        List<OrganizerResponse> response = adminOrganizerService.listOrganizers().stream()
                .map(OrganizerResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<OrganizerResponse> createOrganizer(
            @Valid @RequestBody CreateOrganizerRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        User actor = requireUser(principal);
        AdminOrganizerService.OrganizerCreationResult result = adminOrganizerService.createOrganizer(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                OrganizerResponse.fromEntity(result.organizer(), result.generatedIntegrationSecret())
        );
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<OrganizerResponse> banOrganizer(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        Organizer organizer = adminOrganizerService.banOrganizer(id, requireUser(principal));
        return ResponseEntity.ok(OrganizerResponse.fromEntity(organizer));
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<OrganizerResponse> unbanOrganizer(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        Organizer organizer = adminOrganizerService.unbanOrganizer(id, requireUser(principal));
        return ResponseEntity.ok(OrganizerResponse.fromEntity(organizer));
    }

    private User requireUser(UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            throw new UnauthorizedException("Не авторизован");
        }
        return userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Не авторизован"));
    }
}
